package com.jlindemann.science.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*
import com.jlindemann.science.BuildConfig
import com.jlindemann.science.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

class BillingManager(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val listener: Listener
) : BillingClientStateListener {

    interface Listener {
        fun onProductsUpdated()
        fun onPurchasesUpdated()
        fun onPurchaseCompleted(productId: String)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "BillingManager"

        const val PRO_VERSION_ID = "pro_version"
        const val PRO_PLUS_VERSION_ID = "pro_version_plus"
        const val PRO_PLUS_UPGRADE_ID = "pro_version_plus_upgrade"

        /**
         * PRO+ Ultimate: PRO+ on every platform signed into the same account.
         *
         * The upgrade variant is offered to existing PRO+ owners. Both are one-time products, and
         * both mean exactly the same entitlement once owned - which one the user bought only
         * decides what they paid.
         */
        const val PRO_PLUS_ULTIMATE_ID = "pro_plus_ultimate"
        const val PRO_PLUS_ULTIMATE_UPGRADE_ID = "pro_plus_ultimate_upgrade"

        /**
         * The Firebase uid as Play's obfuscated account id.
         *
         * Ultimate is sold as one purchase for one account across two stores, and a purchase that
         * does not say which account it belongs to cannot be checked against one later. Binding it
         * here is what will let the server verify an Ultimate against the uid that claims it,
         * instead of trusting the client's word - and it only works going forward, so a purchase
         * made before this existed can never be bound retroactively.
         *
         * Hashed rather than sent raw, as Play's documentation asks, and SHA-256 hex is 64
         * characters, which is exactly the field's limit.
         */
        fun obfuscatedAccountId(uid: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private val productIds = listOf(
        PRO_VERSION_ID,
        PRO_PLUS_VERSION_ID,
        PRO_PLUS_UPGRADE_ID,
        PRO_PLUS_ULTIMATE_ID,
        PRO_PLUS_ULTIMATE_UPGRADE_ID,
    )

    private var billingClient: BillingClient? = null
    private val productDetailMap = mutableMapOf<String, ProductDetails>()

    @Volatile
    private var ownsProVersion = false

    @Volatile
    private var ownsProPlusVersion = false

    @Volatile
    private var ownsUltimate = false

    /**
     * Whether Play has actually answered a purchase query.
     *
     * Without this, "the store says you own nothing" and "the store has not spoken" are the same
     * empty flags - and treating the second as the first is what revokes PRO from a paying user who
     * opens the page offline. See EntitlementRules.reconcile.
     */
    @Volatile
    private var storeAnswered = false

    fun initialize() {
        if (billingClient != null) {
            endConnection()
        }

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(activity)
            .enablePendingPurchases(pendingPurchasesParams)
            .setListener(purchasesUpdatedListener)
            .build()

        billingClient?.startConnection(this)
    }

    fun endConnection() {
        try {
            billingClient?.endConnection()
        } catch (e: Exception) {
            // Ignore if already disconnected or not bound
        } finally {
            billingClient = null
        }
    }

    // --- Accessors used by UI / adapter ---
    fun getProductDetail(productId: String): ProductDetails? = productDetailMap[productId]
    fun isOwnsProVersion(): Boolean = ownsProVersion
    fun isOwnsProPlusVersion(): Boolean = ownsProPlusVersion
    fun isOwnsUltimate(): Boolean = ownsUltimate
    fun hasStoreAnswered(): Boolean = storeAnswered
    fun isReady(): Boolean = billingClient?.isReady == true

    // --- BillingClientStateListener ---
    override fun onBillingServiceDisconnected() {
        // try to reconnect
        billingClient?.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // Query products and purchases when ready
            queryProducts()
            queryPurchases()
        } else {
            listener.onError("Billing setup failed: ${billingResult.responseCode}")
        }
    }

    // --- PurchasesUpdatedListener ---
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases?.isNotEmpty() == true) {
                for (purchase in purchases) {
                    // Handle each purchase
                    scope.launch {
                        handlePurchase(purchase)
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // Play refused because this account already owns the product, which means the local
            // flags are behind rather than that anything went wrong. Re-reading the purchases
            // reconciles them - reporting an error code here would tell a user who owns the thing
            // that their purchase failed.
            queryPurchases()
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            // A cancelled purchase is a decision, not a fault, so it is not worth a message.
            listener.onError("Purchase update: ${billingResult.responseCode}")
        }
    }

    // --- Product and Purchase queries ---
    private fun queryProducts() {
        queryProductsAsync(BillingClient.ProductType.INAPP, productIds)
    }

    /**
     * Ask Play for the product catalogue.
     *
     * Uses the callback API rather than the Kotlin `queryProductDetails` extension on purpose: the
     * extension returns a result that carries only the products Play *did* return, and drops the
     * list of the ones it did not, with the reason. That list is the only thing that can tell
     * "the product is not active yet" from "this build is not set up for billing" from "the ID is
     * misspelt" - without it all three look like an empty card.
     */
    private fun queryProductsAsync(productType: String, products: List<String>) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                }
            )
            .build()

        val client = billingClient ?: return

        client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Query products failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                listener.onError("Query products failed: ${billingResult.responseCode}")
                return@queryProductDetailsAsync
            }

            result.productDetailsList.forEach { productDetailMap[it.productId] = it }

            val missing = productIds.filter { it !in productDetailMap }
            if (missing.isNotEmpty()) {
                val reasons = result.unfetchedProductList.joinToString("; ") {
                    "${it.productId}: ${unfetchedReason(it.statusCode)}"
                }.ifEmpty { "Play gave no reason" }

                Log.w(TAG, "No product details for $missing ($reasons)")

                // Shown in debug builds only. A real user in a region where Ultimate is not offered
                // has no use for a Play status code; the developer who just activated a product
                // does, and it is the difference between waiting and fixing something.
                if (BuildConfig.DEBUG) {
                    Handler(Looper.getMainLooper()).post { listener.onError(reasons) }
                }
            }

            Handler(Looper.getMainLooper()).post {
                listener.onProductsUpdated()
            }
        }
    }

    private fun unfetchedReason(statusCode: Int): String = when (statusCode) {
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND ->
            "not found (inactive, not yet propagated, or this build/account cannot see the app's products)"
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "invalid product id format"
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "no eligible offer for this user"
        else -> "unknown (status $statusCode)"
    }

    fun queryPurchases() {
        queryPurchases(BillingClient.ProductType.INAPP)
    }

    private fun queryPurchases(type: String) {
        val queryPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(type)
            .build()

        billingClient?.queryPurchasesAsync(queryPurchaseParams) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                ownsProVersion = false
                ownsProPlusVersion = false
                ownsUltimate = false
                if (purchasesList.isNotEmpty()) {
                    for (purchase in purchasesList) {
                        try {
                            val jsonObject = JSONObject(purchase.originalJson)
                            val productId = jsonObject.getString("productId")
                            if (productId == PRO_VERSION_ID) ownsProVersion = true
                            if (productId == PRO_PLUS_VERSION_ID || productId == PRO_PLUS_UPGRADE_ID) ownsProPlusVersion = true
                            if (productId == PRO_PLUS_ULTIMATE_ID || productId == PRO_PLUS_ULTIMATE_UPGRADE_ID) ownsUltimate = true
                        } catch (e: Exception) {
                            // ignore parse error
                        }
                    }
                }
                // PRO+ implies PRO, and Ultimate implies PRO+. Buying the top tier outright means
                // owning none of the products below it, so a gate reading only its own flag would
                // lock out the user who paid the most.
                if (ownsUltimate) ownsProPlusVersion = true
                if (ownsProPlusVersion) ownsProVersion = true

                // Only here: a successful query is the one thing that proves Play knows this
                // installation.
                storeAnswered = true
                Handler(Looper.getMainLooper()).post {
                    listener.onPurchasesUpdated()
                }
            } else {
                listener.onError("Query purchases failed: ${billingResult.responseCode}")
            }
        }
    }

    // --- Purchase handling / acknowledgement ---
    private suspend fun handlePurchase(purchase: Purchase) {
        val productId = try {
            val jsonObject = JSONObject(purchase.originalJson)
            jsonObject.getString("productId")
        } catch (e: Exception) {
            ""
        }

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            withContext(Dispatchers.IO) {
                billingClient?.acknowledgePurchase(params) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        // update flags on main thread
                        Handler(Looper.getMainLooper()).post {
                            updateOwnershipFlags(productId)
                            listener.onPurchaseCompleted(productId)
                        }
                    } else {
                        listener.onError("Acknowledge failed: ${result.responseCode}")
                    }
                }
            }
        } else {
            // already acknowledged or not PURCHASED yet; update ownership anyway
            Handler(Looper.getMainLooper()).post {
                updateOwnershipFlags(productId)
                listener.onPurchaseCompleted(productId)
            }
        }
    }

    private fun updateOwnershipFlags(productId: String) {
        when (productId) {
            PRO_VERSION_ID -> {
                ownsProVersion = true
                ownsProPlusVersion = false
            }
            PRO_PLUS_VERSION_ID, PRO_PLUS_UPGRADE_ID -> {
                ownsProPlusVersion = true
                ownsProVersion = true
            }
            PRO_PLUS_ULTIMATE_ID, PRO_PLUS_ULTIMATE_UPGRADE_ID -> {
                ownsUltimate = true
                ownsProPlusVersion = true
                ownsProVersion = true
            }
        }
    }

    // --- Launch purchase flow ---
    fun launchPurchase(productDetails: ProductDetails) {
        if (billingClient?.isReady != true) {
            listener.onError("Billing client not ready")
            return
        }

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )

        // Only when signed in. A signed-out purchase is still a valid purchase - it just unlocks
        // this device rather than the account, which is what the PRO and PRO+ products are anyway.
        AuthManager.getUid()?.let { builder.setObfuscatedAccountId(obfuscatedAccountId(it)) }

        billingClient?.launchBillingFlow(activity, builder.build())
    }
}