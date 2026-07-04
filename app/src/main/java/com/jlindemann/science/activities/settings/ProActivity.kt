package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.billing.BillingManager
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Runnable

class ProActivity : BaseActivity(), BillingManager.Listener {

    private val PRO_PAGE_PRODUCT_ID = BillingManager.PRO_VERSION_ID
    private val PRO_PLUS_PRODUCT_ID = BillingManager.PRO_PLUS_VERSION_ID
    private val PRO_PLUS_UPGRADE_PRODUCT_ID = BillingManager.PRO_PLUS_UPGRADE_ID

    private lateinit var billingManager: BillingManager

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

        findViewById<ImageButton>(R.id.back_btn_pro).setOnClickListener {
            this.onBackPressed()
        }

        // NEW: Handle click on "product_text"
        findViewById<TextView>(R.id.product_text)?.setOnClickListener {
            checkAndSetPreferences()
            showUserProductsToast()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }

    // BillingManager.Listener implementations
    override fun onProductsUpdated() {
        // refresh local product map and UI
        productDetailMap[PRO_PAGE_PRODUCT_ID] =
            (billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID) ?: continueOnUi {}) as ProductDetails
        productDetailMap[PRO_PLUS_PRODUCT_ID] =
            (billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID) ?: continueOnUi {}) as ProductDetails
        productDetailMap[PRO_PLUS_UPGRADE_PRODUCT_ID] =
            (billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID) ?: continueOnUi {}) as ProductDetails

        runOnUiThread {
            updateProOptionsUI()
            updatePurchaseCardsUI()
        }
    }

    override fun onPurchasesUpdated() {
        // refresh internal flags and UI
        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()
        runOnUiThread {
            updateProOptionsUI()
            updatePurchaseCardsUI()
        }
    }

    override fun onPurchaseCompleted(productId: String) {
        // update preferences and UI similar to original behavior
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)

        when (productId) {
            PRO_PAGE_PRODUCT_ID -> {
                proPref.setValue(100)
                proPlusPref.setValue(1)
                ownsProVersion = true
                ownsProPlusVersion = false
            }
            PRO_PLUS_PRODUCT_ID, PRO_PLUS_UPGRADE_PRODUCT_ID -> {
                proPref.setValue(100)
                proPlusPref.setValue(100)
                ownsProPlusVersion = true
                ownsProVersion = true
            }
        }

        runOnUiThread {
            Toast.makeText(this@ProActivity, "Purchase complete!", Toast.LENGTH_SHORT).show()
            // small delay to let Google Play finalize state if needed
            android.os.Handler().postDelayed({
                updateProOptionsUI()
                updatePurchaseCardsUI()
            }, 500)
        }
    }

    override fun onError(message: String) {
        ToastUtil.showToast(this, message)
    }

    // small helper to avoid repetitive null-handling when updating product map
    private inline fun <T> continueOnUi(block: () -> T) {}

    private fun queryProducts() {
        // BillingManager already queries products on setup; this is kept for parity if needed
    }

    private fun queryPurchases() {
        // BillingManager already queries purchases on setup; this is kept for parity if needed
    }

    // Function to set preferences based on account products
    private fun checkAndSetPreferences() {
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)
        val ownsPro = billingManager.isOwnsProVersion()
        val ownsProPlus = billingManager.isOwnsProPlusVersion()
        when {
            ownsPro && !ownsProPlus -> {
                proPref.setValue(100)
                proPlusPref.setValue(1)
            }
            ownsProPlus -> {
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
        val ownsPro = billingManager.isOwnsProVersion()
        val ownsProPlus = billingManager.isOwnsProPlusVersion()
        val productsOwned = when {
            ownsProPlus -> "PRO Plus"
            ownsPro -> "PRO"
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
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)

        // use billingManager state if available
        ownsProVersion = billingManager.isOwnsProVersion()
        ownsProPlusVersion = billingManager.isOwnsProPlusVersion()

        if (ownsProVersion && !ownsProPlusVersion) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = getString(R.string.current_version)
            proPriceView.text = "---"
            proPref.setValue(100)
            proPlusPref.setValue(1)
        } else if (ownsProPlusVersion) {
            proBuyBtn.isEnabled = false
            proBuyBtn.text = getString(R.string.owns_pro_plus)
            proPriceView.text = "---"
            proPref.setValue(100)
            proPlusPref.setValue(100)
        } else {
            proBuyBtn.isEnabled = true
            proBuyBtn.text = getString(R.string.get_pro)
            val productDetails = billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID)
            proPriceView.text = getFormattedPrice(productDetails)
            proPref.setValue(1)
            proPlusPref.setValue(1)
        }

        if (ownsProPlusVersion) {
            proPlusBuyBtn.isEnabled = false
            proPlusBuyBtn.text = getString(R.string.current_version)
            proPlusPriceView.text = "---"
        } else if (ownsProVersion && !ownsProPlusVersion) {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = getString(R.string.upgrade_to_pro_plus)
            val upgradeDetails = billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID)
            proPlusPriceView.text = getFormattedPrice(upgradeDetails)
        } else {
            proPlusBuyBtn.isEnabled = true
            proPlusBuyBtn.text = getString(R.string.get_pro_plus)
            val productDetails = billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID)
            proPlusPriceView.text = getFormattedPrice(productDetails)
        }
    }

    // Update cards or other purchase UI
    private fun updatePurchaseCardsUI() {
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
}