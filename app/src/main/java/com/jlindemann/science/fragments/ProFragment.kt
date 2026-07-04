package com.jlindemann.science.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.jlindemann.science.R
import com.jlindemann.science.billing.BillingManager
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProFragment : BaseFragment(), BillingManager.Listener {

    private val PRO_PAGE_PRODUCT_ID = BillingManager.PRO_VERSION_ID
    private val PRO_PLUS_PRODUCT_ID = BillingManager.PRO_PLUS_VERSION_ID
    private val PRO_PLUS_UPGRADE_PRODUCT_ID = BillingManager.PRO_PLUS_UPGRADE_ID

    private lateinit var billingManager: BillingManager
    private var productDetailMap = mutableMapOf<String, ProductDetails>()
    private var ownsProVersion = false
    private var ownsProPlusVersion = false

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


        view.findViewById<TextView>(R.id.product_text)?.setOnClickListener {
            checkAndSetPreferences()
            showUserProductsToast()
        }

        updateProOptionsUI(view)
        updatePurchaseCardsUI(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        billingManager.endConnection()
    }

    override fun onProductsUpdated() {
        billingManager.getProductDetail(PRO_PAGE_PRODUCT_ID)?.let { productDetailMap[PRO_PAGE_PRODUCT_ID] = it }
        billingManager.getProductDetail(PRO_PLUS_PRODUCT_ID)?.let { productDetailMap[PRO_PLUS_PRODUCT_ID] = it }
        billingManager.getProductDetail(PRO_PLUS_UPGRADE_PRODUCT_ID)?.let { productDetailMap[PRO_PLUS_UPGRADE_PRODUCT_ID] = it }

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
        activity?.runOnUiThread {
            view?.let {
                updateProOptionsUI(it)
                updatePurchaseCardsUI(it)
            }
            (activity as? com.jlindemann.science.activities.MainActivity)?.updateVersionBadge()
        }
    }

    override fun onPurchaseCompleted(productId: String) {
        val proPref = ProVersion(requireContext())
        val proPlusPref = ProPlusVersion(requireContext())

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

    private fun checkAndSetPreferences() {
        val proPref = ProVersion(requireContext())
        val proPlusPref = ProPlusVersion(requireContext())
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
        ToastUtil.showToast(requireContext(), "Preferences updated according to your products.")
    }

    private fun showUserProductsToast() {
        val ownsPro = billingManager.isOwnsProVersion()
        val ownsProPlus = billingManager.isOwnsProPlusVersion()
        val productsOwned = when {
            ownsProPlus -> "PRO Plus"
            ownsPro -> "PRO"
            else -> "None"
        }
        Toast.makeText(requireContext(), "Products on account: $productsOwned", Toast.LENGTH_SHORT).show()
    }

    private fun getFormattedPrice(productDetails: ProductDetails?): String {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: getString(R.string.price_not_available)
    }

    private fun updateProOptionsUI(view: View) {
        val proBuyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.pro_buy_btn)
        val proPlusBuyBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.pro_plus_buy_btn)
        val proPriceView = view.findViewById<TextView>(R.id.pro_price)
        val proPlusPriceView = view.findViewById<TextView>(R.id.pro_plus_price)
        val proPref = ProVersion(requireContext())
        val proPlusPref = ProPlusVersion(requireContext())

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

    private fun updatePurchaseCardsUI(view: View) {
        val proCard = view.findViewById<FrameLayout>(R.id.pro_bg)
        val proPlusCard = view.findViewById<FrameLayout>(R.id.pro_plus_bg)

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
