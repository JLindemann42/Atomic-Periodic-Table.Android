package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.logging.Handler

class ProActivity : BaseActivity(), BillingClientStateListener {

    private val PRO_VERSION_ID = "pro_version"
    private val PRO_PLUS_VERSION_ID = "pro_version_plus"
    private val PRO_PLUS_UPGRADE_ID = "pro_version_plus_upgrade"
    private val productIds = listOf(PRO_VERSION_ID, PRO_PLUS_VERSION_ID, PRO_PLUS_UPGRADE_ID)

    private var billingClient: BillingClient? = null
    private var productDetailMap = mutableMapOf<String, ProductDetails>()

    private var ownsProVersion = false
    private var ownsProPlusVersion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = com.jlindemann.science.preferences.ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        }
        if (themePrefValue == 0) setTheme(R.style.AppTheme)
        if (themePrefValue == 1) setTheme(R.style.AppThemeDark)
        setContentView(R.layout.activity_pro_v2)

        findViewById<TextView>(R.id.pro_buy_btn).setOnClickListener {
            if (!ownsProVersion && !ownsProPlusVersion) {
                productDetailMap[PRO_VERSION_ID]?.let { launchBillingFlow(it) }
            }
        }
        findViewById<TextView>(R.id.pro_plus_buy_btn).setOnClickListener {
            if (!ownsProPlusVersion) {
                if (ownsProVersion) {
                    productDetailMap[PRO_PLUS_UPGRADE_ID]?.let { launchBillingFlow(it) }
                } else {
                    productDetailMap[PRO_PLUS_VERSION_ID]?.let { launchBillingFlow(it) }
                }
            }
        }

        findViewById<ImageButton>(R.id.back_btn_pro).setOnClickListener {
            this.onBackPressed()
        }

        // NEW: Handle click on "product_text"
        findViewById<TextView>(R.id.product_text)?.setOnClickListener {
            checkAndSetPreferences()
            showUserProductsToast()
        }

        val purchasesUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (purchases?.isNotEmpty() == true) {
                        for (purchase in purchases) {
                            lifecycleScope.launch {
                                handlePurchase(purchase)
                                updateProOptionsUI()
                                updatePurchaseCardsUI()
                            }
                            updateProOptionsUI()
                            updatePurchaseCardsUI()
                        }
                    }
                }
            }
        }
        initBillingClient(purchasesUpdateListener)
    }

    private fun initBillingClient(purchasesUpdateListener: PurchasesUpdatedListener) {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdateListener)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()
        billingClient?.startConnection(this)

    }

    override fun onBillingServiceDisconnected() {
        billingClient?.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProducts()
            queryPurchases()
        } else {
            ToastUtil.showToast(this, billingResult.responseCode.toString())
        }
    }

    private fun queryProducts() {
        lifecycleScope.launch {
            queryProducts(BillingClient.ProductType.INAPP, productIds)
        }
    }

    private suspend fun queryProducts(productType: String, products: List<String>) {
        val productListForQuery = products.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productListForQuery)
                .build()
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient?.queryProductDetails(queryProductDetailsParams)
        }

        if (productDetailsResult?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetailsResult.productDetailsList?.forEach { productDetails ->
                productDetailMap[productDetails.productId] = productDetails
            }
            withContext(Dispatchers.Main) {
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }
        }
    }

    private fun queryPurchases() {
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
                if (purchasesList.isNotEmpty()) {
                    for (purchase in purchasesList) {
                        lifecycleScope.launch {
                            val jsonObject = JSONObject(purchase.originalJson)
                            val productId = jsonObject.getString("productId")
                            if (productId == PRO_VERSION_ID) ownsProVersion = true
                            if (productId == PRO_PLUS_VERSION_ID || productId == PRO_PLUS_UPGRADE_ID) ownsProPlusVersion = true
                        }
                    }
                }
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }
        }
    }

    // Function to set preferences based on account products
    private fun checkAndSetPreferences() {
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)
        when {
            ownsProVersion && !ownsProPlusVersion -> {
                proPref.setValue(100)
                proPlusPref.setValue(1)
            }
            ownsProPlusVersion -> {
                proPref.setValue(100)
                proPlusPref.setValue(100)
            }
            else -> {
                proPref.setValue(1)
                proPlusPref.setValue(1)
            }
        }
        ToastUtil.showToast(this, "Preferences updated according to your products.")
    }

    private fun showUserProductsToast() {
        val productsOwned = when {
            ownsProPlusVersion -> "PRO Plus"
            ownsProVersion -> "PRO"
            else -> "None"
        }
        Toast.makeText(
            this,
            "Products on account: $productsOwned",
            Toast.LENGTH_SHORT
        ).show()
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        val jsonObject = JSONObject(purchase.originalJson)
        val productId = jsonObject.getString("productId")
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            withContext(Dispatchers.IO) {
                billingClient?.acknowledgePurchase(params) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@ProActivity, "Purchase complete!", Toast.LENGTH_SHORT).show()
                            if (productId == PRO_VERSION_ID) {
                                ownsProVersion = true
                                proPref.setValue(100)
                                proPlusPref.setValue(1)
                                android.os.Handler().postDelayed({
                                    updateProOptionsUI()
                                    updatePurchaseCardsUI() }, 5000)
                            }
                            if (productId == PRO_PLUS_VERSION_ID || productId == PRO_PLUS_UPGRADE_ID) {
                                ownsProPlusVersion = true
                                proPref.setValue(100)
                                proPlusPref.setValue(100)
                                android.os.Handler().postDelayed({
                                    updateProOptionsUI()
                                    updatePurchaseCardsUI() }, 5000)
                            }
                            updateProOptionsUI()
                            updatePurchaseCardsUI()
                        }
                    }
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@ProActivity, "Purchase complete!", Toast.LENGTH_SHORT).show()
                if (productId == PRO_VERSION_ID) {
                    ownsProVersion = true
                    proPref.setValue(100)
                    proPlusPref.setValue(1)
                }
                if (productId == PRO_PLUS_VERSION_ID || productId == PRO_PLUS_UPGRADE_ID) {
                    ownsProPlusVersion = true
                    proPref.setValue(100)
                    proPlusPref.setValue(100)
                }
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }
        }
    }


    private fun getFormattedPrice(productDetails: ProductDetails?): String {
        val formattedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        return formattedPrice ?: "Price not available"
    }

    // Update preferences and buy buttons when displaying UI
    private fun updateProOptionsUI() {
        val proBuyBtn = findViewById<TextView>(R.id.pro_buy_btn)
        val proPlusBuyBtn = findViewById<TextView>(R.id.pro_plus_buy_btn)
        val proPriceView = findViewById<TextView>(R.id.pro_price)
        val proPlusPriceView = findViewById<TextView>(R.id.pro_plus_price)
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)

        if (ownsProVersion && !ownsProPlusVersion) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = "Current Version"
            proPriceView.text = "---"
            // Update preference to 100 for ProVersion
            proPref.setValue(100)
            proPlusPref.setValue(1)
        } else if (ownsProPlusVersion) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = "Owns PRO+"
            proPriceView.text = "---"
            // Update preference to 100 for both
            proPref.setValue(100)
            proPlusPref.setValue(100)
        } else {
            proBuyBtn.isEnabled = true
            proBuyBtn.text = "Get PRO"
            val productDetails = productDetailMap[PRO_VERSION_ID]
            proPriceView.text = getFormattedPrice(productDetails)
            // Reset preferences if neither owned
            proPref.setValue(1)
            proPlusPref.setValue(1)
        }

        if (ownsProPlusVersion) {
            proPlusBuyBtn.isEnabled = false
            proPlusBuyBtn.text = "Current Version"
            proPlusPriceView.text = "---"
        } else if (ownsProVersion && !ownsProPlusVersion) {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = "Upgrade to PRO+"
            val upgradeDetails = productDetailMap[PRO_PLUS_UPGRADE_ID]
            proPlusPriceView.text = getFormattedPrice(upgradeDetails)
        } else {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = "Get PRO+"
            val productDetails = productDetailMap[PRO_PLUS_VERSION_ID]
            proPlusPriceView.text = getFormattedPrice(productDetails)
        }
    }

    // Update cards or other purchase UI
    private fun updatePurchaseCardsUI() {
        // Example: update card backgrounds or visibility based on ownership
        val proCard = findViewById<FrameLayout>(R.id.pro_bg)
        val proPlusCard = findViewById<FrameLayout>(R.id.pro_plus_bg)

        if (ownsProPlusVersion) {
            proCard?.alpha = 0.5f
            proPlusCard?.alpha = 0.5f
        } else if (ownsProVersion) {
            proCard?.alpha = 0.5f
            proPlusCard?.alpha = 1.0f
        } else {
            proCard?.alpha = 1.0f
            proPlusCard?.alpha = 1.0f
        }
    }

    private fun launchBillingFlow(productDetails: ProductDetails) {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()
        if (billingClient?.isReady == false) {
            ToastUtil.showToast(this, "Billing client not ready, try again!")
            return
        }
        billingClient?.launchBillingFlow(this, billingFlowParams)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val titleBarHeight = resources.getDimensionPixelSize(R.dimen.title_bar)
        val navBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bar)
        val titleFrame = findViewById<FrameLayout>(R.id.common_title_back_pro)
        val purchaseBox = findViewById<FrameLayout>(R.id.pro_purschase_box)
        val proLinear = findViewById<LinearLayout>(R.id.pro_linear)

        val titleParams = titleFrame.layoutParams as ViewGroup.LayoutParams
        titleParams.height = top + titleBarHeight
        titleFrame.layoutParams = titleParams

        proLinear.setPadding(0, top, 0, 0)

        val purchaseParams = purchaseBox.layoutParams as ViewGroup.LayoutParams
        purchaseParams.height = bottom + navBarHeight
        purchaseBox.layoutParams = purchaseParams
    }
}