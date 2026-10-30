package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.google.android.material.card.MaterialCardView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.billing.BillingManager
import com.jlindemann.science.sync.AutoSync
import com.jlindemann.science.utils.EntitlementManager
import com.jlindemann.science.utils.EntitlementRules
import com.jlindemann.science.utils.Tier
import com.jlindemann.science.utils.ToastUtil

class ProActivity : BaseActivity(), BillingManager.Listener {

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

        // Initialize BillingManager
        billingManager = BillingManager(this, lifecycleScope, this)
        billingManager.initialize()

        // Hook up UI buttons to delegate to billingManager
        findViewById<TextView>(R.id.pro_buy_btn).setOnClickListener {
            if (!billingManager.isOwnsProVersion() && !billingManager.isOwnsProPlusVersion()) {
                billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
            }
        }
        findViewById<TextView>(R.id.pro_plus_buy_btn).setOnClickListener {
            if (!billingManager.isOwnsProPlusVersion()) {
                if (billingManager.isOwnsProVersion()) {
                    billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
                } else {
                    billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID)?.let { billingManager.launchPurchase(it) }
                }
            }
        }
        findViewById<TextView>(R.id.ultimate_buy_btn).setOnClickListener { buyUltimate() }

        // NEW: Handle click on "product_text"
        findViewById<TextView>(R.id.product_text)?.setOnClickListener {
            checkAndSetPreferences()
            showUserProductsToast()
        }

        findViewById<View>(R.id.back_btn_pro).visibility = View.VISIBLE
        findViewById<View>(R.id.back_btn_pro).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        verifyUltimateOnOpen()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }

    // BillingManager.Listener implementations
    override fun onProductsUpdated() {
        // A product missing from the catalogue is normal - Ultimate is not live in every region
        // yet - so this only records what came back rather than casting a null through.
        listOf(
            PRO_PAGE_PRODUCT_ID,
            PRO_PLUS_PRODUCT_ID,
            PRO_PLUS_UPGRADE_PRODUCT_ID,
            ULTIMATE_PRODUCT_ID,
            ULTIMATE_UPGRADE_PRODUCT_ID,
        ).forEach { id -> billingManager.getProductDetail(id)?.let { productDetailMap[id] = it } }

        runOnUiThread {
            updateProOptionsUI()
            updatePurchaseCardsUI()
        }
    }

    override fun onPurchasesUpdated() {
        // refresh internal flags and UI
        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()
        ownsUltimate = billingManager.isOwnsUltimate()
        runOnUiThread {
            updateProOptionsUI()
            updatePurchaseCardsUI()
        }
    }

    override fun onPurchaseCompleted(productId: String) {
        when (productId) {
            PRO_PAGE_PRODUCT_ID -> {
                EntitlementManager.apply(this, Tier.PRO, ultimate = false)
                ownsProVersion = true
            }
            PRO_PLUS_PRODUCT_ID, PRO_PLUS_UPGRADE_PRODUCT_ID -> {
                EntitlementManager.apply(this, Tier.PRO_PLUS, ultimate = false)
                ownsProPlusVersion = true
                ownsProVersion = true
            }
            ULTIMATE_PRODUCT_ID, ULTIMATE_UPGRADE_PRODUCT_ID -> {
                EntitlementManager.grantUltimate(this)
                ownsUltimate = true
                ownsProPlusVersion = true
                ownsProVersion = true
                // Forced, so the PC learns about this purchase now rather than at the next
                // foreground. Ultimate is the one product whose whole value is the other platform.
                AutoSync.syncNow(this, force = true)
            }
        }

        runOnUiThread {
            Toast.makeText(this@ProActivity, "Purchase complete!", Toast.LENGTH_SHORT).show()
            // small delay to let Google Play finalize state if needed
            android.os.Handler(mainLooper).postDelayed({
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }, 500)
        }
    }

    override fun onError(message: String) {
        ToastUtil.showToast(this, message)
    }

    /**
     * Buy Ultimate, but only after the account says it is not already owned.
     *
     * Play will not sell a one-time product the Play account already owns, but an Ultimate bought
     * on the Microsoft Store is not a Play purchase - Play cannot see it and would charge again.
     * The account document is where the two stores meet, so it is checked first.
     */
    private fun buyUltimate() {
        if (verifyingUltimate) return
        if (EntitlementManager.hasUltimate(this)) return

        verifyingUltimate = true
        findViewById<TextView>(R.id.ultimate_buy_btn)?.isEnabled = false

        EntitlementManager.verifyUltimateWithAccount(this) { owned ->
            runOnUiThread {
                verifyingUltimate = false

                if (owned) {
                    ToastUtil.showToast(this, getString(R.string.ultimate_already_owned_account))
                } else {
                    billingManager.getProductDetail(ultimateProductId())
                        ?.let { billingManager.launchPurchase(it) }
                }

                updateProOptionsUI()
                updatePurchaseCardsUI()
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
        if (EntitlementManager.hasUltimate(this)) return

        EntitlementManager.verifyUltimateWithAccount(this) { owned ->
            if (!owned) return@verifyUltimateWithAccount
            runOnUiThread {
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }
        }
    }

    /** PRO+ owners are offered the cheaper upgrade; everybody else buys Ultimate outright. */
    private fun ultimateProductId(): String =
        if (EntitlementManager.isProPlus(this)) ULTIMATE_UPGRADE_PRODUCT_ID else ULTIMATE_PRODUCT_ID

    /**
     * Fold what Play said into what is stored.
     *
     * Null until the store has actually replied. See EntitlementRules.reconcile - this method used
     * to write PRO away whenever the flags were empty, which includes every render before the
     * purchase query comes back.
     */
    private fun reconcileEntitlements() {
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
            this,
            EntitlementRules.reconcile(EntitlementManager.currentTier(this), answer),
            ultimate = billingManager.isOwnsUltimate(),
        )
    }

    // Function to set preferences based on account products
    private fun checkAndSetPreferences() {
        reconcileEntitlements()
        ToastUtil.showToast(this, "Preferences updated according to your products.")
    }

    private fun showUserProductsToast() {
        val productsOwned = when {
            EntitlementManager.hasUltimate(this) -> "PRO+ Ultimate"
            EntitlementManager.isProPlus(this) -> "PRO Plus"
            EntitlementManager.hasAnyPro(this) -> "PRO"
            else -> "None"
        }
        Toast.makeText(
            this,
            "Products on account: $productsOwned",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getFormattedPrice(productDetails: ProductDetails?): String {
        val formattedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        return formattedPrice ?: getString(R.string.price_not_available)
    }

    // Update preferences and buy buttons when displaying UI
    private fun updateProOptionsUI() {
        val proBuyBtn = findViewById<TextView>(R.id.pro_buy_btn)
        val proPlusBuyBtn = findViewById<TextView>(R.id.pro_plus_buy_btn)
        val proPriceView = findViewById<TextView>(R.id.pro_price)
        val proPlusPriceView = findViewById<TextView>(R.id.pro_plus_price)

        reconcileEntitlements()

        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()
        ownsUltimate = billingManager.isOwnsUltimate()

        // Driven by the stored tier rather than the raw Play flags, so a user whose PRO+ came from
        // an Ultimate bought on the PC sees it as owned here too.
        val tier = EntitlementManager.currentTier(this)

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

        updateUltimateUI(tier)
    }

    /**
     * The Ultimate card.
     *
     * Until the products are live in the Play Console the catalogue returns nothing for them, so
     * the button holds a disabled "coming soon" rather than a dead control with no price.
     */
    private fun updateUltimateUI(tier: Tier) {
        val buyBtn = findViewById<TextView>(R.id.ultimate_buy_btn)
        val priceView = findViewById<TextView>(R.id.ultimate_price)

        val productId = if (tier == Tier.PRO_PLUS) ULTIMATE_UPGRADE_PRODUCT_ID else ULTIMATE_PRODUCT_ID
        val details = billingManager.getProductDetail(productId)

        when {
            EntitlementManager.hasUltimate(this) -> {
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

    // Update cards or other purchase UI
    private fun updatePurchaseCardsUI() {
        val proCard = findViewById<MaterialCardView>(R.id.pro_bg)
        val proPlusCard = findViewById<MaterialCardView>(R.id.pro_plus_bg)
        val ultimateCard = findViewById<MaterialCardView>(R.id.ultimate_bg)

        val tier = EntitlementManager.currentTier(this)

        proCard?.alpha = if (tier == Tier.FREE) 1.0f else 0.5f
        proPlusCard?.alpha = if (tier == Tier.PRO_PLUS) 0.5f else 1.0f
        ultimateCard?.alpha = if (EntitlementManager.hasUltimate(this)) 0.5f else 1.0f
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<View>(R.id.pro_header_container).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<View>(R.id.pro_header_container).layoutParams = params

        val params2 = findViewById<LinearLayout>(R.id.pro_linear).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = 0 // Padding is enough
        findViewById<LinearLayout>(R.id.pro_linear).layoutParams = params2
    }
}
