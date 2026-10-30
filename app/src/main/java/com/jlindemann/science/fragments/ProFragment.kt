package com.jlindemann.science.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.jlindemann.science.R
import com.jlindemann.science.billing.BillingManager
import com.jlindemann.science.sync.AutoSync
import com.jlindemann.science.utils.EntitlementManager
import com.jlindemann.science.utils.EntitlementRules
import com.jlindemann.science.utils.Tier
import com.jlindemann.science.utils.ToastUtil

class ProFragment : BaseFragment(), BillingManager.Listener {

    private val PRO_PAGE_PRODUCT_ID = BillingManager.PRO_VERSION_ID
    private val PRO_PLUS_PRODUCT_ID = BillingManager.PRO_PLUS_VERSION_ID
    private val PRO_PLUS_UPGRADE_PRODUCT_ID = BillingManager.PRO_PLUS_UPGRADE_ID
    private val ULTIMATE_PRODUCT_ID = BillingManager.PRO_PLUS_ULTIMATE_ID
    private val ULTIMATE_UPGRADE_PRODUCT_ID = BillingManager.PRO_PLUS_ULTIMATE_UPGRADE_ID

    private lateinit var billingManager: BillingManager
    private var productDetailMap = mutableMapOf<String, ProductDetails>()
    private var ownsProVersion = false
    private var ownsProPlusVersion = false
    private var ownsUltimate = false

    /** Set while the account is being asked about Ultimate, so a second tap cannot double-buy. */
    private var verifyingUltimate = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_pro_v2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        billingManager = BillingManager(requireActivity(), viewLifecycleOwner.lifecycleScope, this)
        billingManager.initialize()

        view.findViewById<View>(R.id.pro_buy_btn).setOnClickListener {
            if (!billingManager.isOwnsProVersion() && !billingManager.isOwnsProPlusVersion()) {
                billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
            }
        }
        view.findViewById<View>(R.id.pro_plus_buy_btn).setOnClickListener {
            if (!billingManager.isOwnsProPlusVersion()) {
                if (billingManager.isOwnsProVersion()) {
                    billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
                } else {
                    billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
                }
            }
        }
        view.findViewById<View>(R.id.ultimate_buy_btn).setOnClickListener { buyUltimate() }


        view.findViewById<TextView>(R.id.product_text)?.setOnClickListener {
            checkAndSetPreferences()
            showUserProductsToast()
        }

        updateProOptionsUI(view)
        updatePurchaseCardsUI(view)
        verifyUltimateOnOpen()
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        view?.let {
            val params = it.findViewById<View>(R.id.pro_header_container).layoutParams as ViewGroup.LayoutParams
            params.height = resources.getDimensionPixelSize(R.dimen.pro_text)
            it.findViewById<View>(R.id.pro_header_container).layoutParams = params

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        billingManager.endConnection()
    }

    override fun onProductsUpdated() {
        listOf(
            PRO_PAGE_PRODUCT_ID,
            PRO_PLUS_PRODUCT_ID,
            PRO_PLUS_UPGRADE_PRODUCT_ID,
            ULTIMATE_PRODUCT_ID,
            ULTIMATE_UPGRADE_PRODUCT_ID,
        ).forEach { id -> billingManager.getProductDetail(id)?.let { productDetailMap[id] = it } }

        activity?.runOnUiThread {
            view?.let {
                updateProOptionsUI(it)
                updatePurchaseCardsUI(it)
            }
            (activity as? com.jlindemann.science.activities.MainActivity)?.updateVersionBadge()
        }
    }

    override fun onPurchasesUpdated() {
        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()
        ownsUltimate = billingManager.isOwnsUltimate()
        activity?.runOnUiThread {
            view?.let {
                updateProOptionsUI(it)
                updatePurchaseCardsUI(it)
            }
            (activity as? com.jlindemann.science.activities.MainActivity)?.updateVersionBadge()
        }
    }

    override fun onPurchaseCompleted(productId: String) {
        val context = requireContext()

        when (productId) {
            PRO_PAGE_PRODUCT_ID -> {
                EntitlementManager.apply(context, Tier.PRO, ultimate = false)
                ownsProVersion = true
            }
            PRO_PLUS_PRODUCT_ID, PRO_PLUS_UPGRADE_PRODUCT_ID -> {
                EntitlementManager.apply(context, Tier.PRO_PLUS, ultimate = false)
                ownsProPlusVersion = true
                ownsProVersion = true
            }
            ULTIMATE_PRODUCT_ID, ULTIMATE_UPGRADE_PRODUCT_ID -> {
                EntitlementManager.grantUltimate(context)
                ownsUltimate = true
                ownsProPlusVersion = true
                ownsProVersion = true
                // Forced, so the PC learns about this purchase now rather than at the next
                // foreground. Ultimate is the one product whose whole value is the other platform.
                AutoSync.syncNow(context, force = true)
            }
        }

        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Purchase complete!", Toast.LENGTH_SHORT).show()
            (activity as? com.jlindemann.science.activities.MainActivity)?.updateVersionBadge()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                view?.let {
                    updateProOptionsUI(it)
                    updatePurchaseCardsUI(it)
                }
            }, 500)
        }
    }

    override fun onError(message: String) {
        ToastUtil.showToast(requireContext(), message)
    }

    /**
     * Buy Ultimate, but only after the account says it is not already owned.
     *
     * Play will not sell a one-time product the Play account already owns, but an Ultimate bought
     * on the Microsoft Store is not a Play purchase - Play cannot see it and would charge again.
     * The account document is where the two stores meet, so it is checked first.
     *
     * The read is cheap and usually already cached, but it is not instant, so the button is
     * disabled while it is in flight rather than left live for a second tap.
     */
    private fun buyUltimate() {
        val context = requireContext()
        if (verifyingUltimate) return
        if (EntitlementManager.hasUltimate(context)) return

        verifyingUltimate = true
        view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.ultimate_buy_btn)
            ?.isEnabled = false

        EntitlementManager.verifyUltimateWithAccount(context) { owned ->
            activity?.runOnUiThread {
                verifyingUltimate = false

                if (owned) {
                    ToastUtil.showToast(context, getString(R.string.ultimate_already_owned_account))
                } else {
                    billingManager.getProductDetail(ultimateProductId())
                        ?.let { billingManager.launchPurchase(it) }
                }

                // Either way the card is redrawn: owned now shows as owned, and a purchase that
                // did not start must not leave a disabled button behind.
                view?.let {
                    updateProOptionsUI(it)
                    updatePurchaseCardsUI(it)
                }
            }
        }
    }

    /**
     * Bring the Ultimate flag up to date with the account before the user can tap anything.
     *
     * Without this the page can render a live "Get PRO+ Ultimate" button for somebody who already
     * owns it, in the window between opening the app and the first sync finishing.
     */
    private fun verifyUltimateOnOpen() {
        val context = requireContext()
        if (EntitlementManager.hasUltimate(context)) return

        EntitlementManager.verifyUltimateWithAccount(context) { owned ->
            if (!owned) return@verifyUltimateWithAccount
            activity?.runOnUiThread {
                view?.let {
                    updateProOptionsUI(it)
                    updatePurchaseCardsUI(it)
                }
                (activity as? com.jlindemann.science.activities.MainActivity)?.updateVersionBadge()
            }
        }
    }

    /**
     * PRO+ owners are offered the cheaper upgrade; everybody else buys Ultimate outright.
     */
    private fun ultimateProductId(): String =
        if (EntitlementManager.isProPlus(requireContext())) ULTIMATE_UPGRADE_PRODUCT_ID
        else ULTIMATE_PRODUCT_ID

    /**
     * Fold what Play said into what is stored.
     *
     * The store's answer is null until it has actually replied. Passing that null through
     * [EntitlementRules.reconcile] is the whole point: this method used to write PRO away in its
     * else branch, and it runs on view creation, before any purchase query has come back. A paying
     * user who opened this tab offline lost what they had bought.
     */
    private fun reconcileEntitlements(context: Context) {
        val answer = if (billingManager.hasStoreAnswered()) {
            EntitlementRules.fromLicences(
                hasPro = billingManager.isOwnsProVersion(),
                hasProPlus = billingManager.isOwnsProPlusVersion(),
                hasProUpgrade = false, // BillingManager already folds the upgrade into PRO+
                hasUltimate = billingManager.isOwnsUltimate(),
            )
        } else {
            null
        }

        EntitlementManager.apply(
            context,
            EntitlementRules.reconcile(EntitlementManager.currentTier(context), answer),
            ultimate = billingManager.isOwnsUltimate(),
        )
    }

    private fun checkAndSetPreferences() {
        reconcileEntitlements(requireContext())
        ToastUtil.showToast(requireContext(), "Preferences updated according to your products.")
    }

    private fun showUserProductsToast() {
        val context = requireContext()
        val productsOwned = when {
            EntitlementManager.hasUltimate(context) -> "PRO+ Ultimate"
            EntitlementManager.isProPlus(context) -> "PRO Plus"
            EntitlementManager.hasAnyPro(context) -> "PRO"
            else -> "None"
        }
        Toast.makeText(context, "Products on account: $productsOwned", Toast.LENGTH_SHORT).show()
    }

    private fun getFormattedPrice(productDetails: ProductDetails?): String {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: getString(R.string.price_not_available)
    }

    private fun updateProOptionsUI(view: View) {
        val context = requireContext()
        val proBuyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.pro_buy_btn)
        val proPlusBuyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.pro_plus_buy_btn)
        val proPriceView = view.findViewById<TextView>(R.id.pro_price)
        val proPlusPriceView = view.findViewById<TextView>(R.id.pro_plus_price)

        reconcileEntitlements(context)

        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()
        ownsUltimate = billingManager.isOwnsUltimate()

        // Driven by the stored tier rather than the raw Play flags, so a user whose PRO+ came from
        // an Ultimate bought on the PC sees it as owned here too.
        val tier = EntitlementManager.currentTier(context)

        if (tier == Tier.PRO) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = getString(R.string.current_version)
            proPriceView.text = "---"
        } else if (tier == Tier.PRO_PLUS) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = getString(R.string.owns_pro_plus)
            proPriceView.text = "---"
        } else {
            proBuyBtn.isEnabled = true
            proBuyBtn.text = getString(R.string.get_pro)
            proPriceView.text = getFormattedPrice(billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID))
        }

        if (tier == Tier.PRO_PLUS) {
            proPlusBuyBtn.isEnabled = false
            proPlusBuyBtn.text = getString(R.string.current_version)
            proPlusPriceView.text = "---"
        } else if (tier == Tier.PRO) {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = getString(R.string.upgrade_to_pro_plus)
            proPlusPriceView.text = getFormattedPrice(billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID))
        } else {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = getString(R.string.get_pro_plus)
            proPlusPriceView.text = getFormattedPrice(billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID))
        }

        updateUltimateUI(view, tier)
    }

    /**
     * The Ultimate card.
     *
     * Until the products are live in the Play Console the catalogue returns nothing for them, so
     * the button holds a disabled "coming soon" rather than a dead control with no price.
     */
    private fun updateUltimateUI(view: View, tier: Tier) {
        val context = requireContext()
        val buyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.ultimate_buy_btn)
        val priceView = view.findViewById<TextView>(R.id.ultimate_price)

        val productId = if (tier == Tier.PRO_PLUS) ULTIMATE_UPGRADE_PRODUCT_ID else ULTIMATE_PRODUCT_ID
        val details = billingManager.getProductDetail(productId)

        when {
            EntitlementManager.hasUltimate(context) -> {
                buyBtn.isEnabled = false
                buyBtn.text = getString(R.string.ultimate_owned)
                priceView.text = "---"
            }
            details == null -> {
                buyBtn.isEnabled = false
                buyBtn.text = getString(R.string.ultimate_coming_soon)
                priceView.text = "---"
            }
            tier == Tier.PRO_PLUS -> {
                buyBtn.isEnabled = true
                buyBtn.text = getString(R.string.ultimate_upgrade_title)
                priceView.text = getFormattedPrice(details)
            }
            else -> {
                buyBtn.isEnabled = true
                buyBtn.text = getString(R.string.ultimate_buy)
                priceView.text = getFormattedPrice(details)
            }
        }
    }

    private fun updatePurchaseCardsUI(view: View) {
        val context = requireContext()
        val proCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.pro_bg)
        val proPlusCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.pro_plus_bg)
        val ultimateCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.ultimate_bg)

        val tier = EntitlementManager.currentTier(context)

        proCard?.alpha = if (tier == Tier.FREE) 1.0f else 0.5f
        proPlusCard?.alpha = if (tier == Tier.PRO_PLUS) 0.5f else 1.0f
        ultimateCard?.alpha = if (EntitlementManager.hasUltimate(context)) 0.5f else 1.0f
    }


}
