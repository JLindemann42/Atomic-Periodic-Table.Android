package com.jlindemann.science.activities.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.ImmutableList
import com.jlindemann.science.databinding.ActivityProBinding
import com.jlindemann.science.preferences.ElectronegativityPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Timer
import java.util.concurrent.Executors
import kotlin.concurrent.schedule


class ProActivity : BaseActivity(), BillingClientStateListener {

    val NON_CONSUMALBE_COUNTER_ID = "pro_version"

    private var billingClient: BillingClient? = null
    private var productDetailList = mutableListOf<ProductDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { setTheme(R.style.AppTheme) }
                Configuration.UI_MODE_NIGHT_YES -> { setTheme(R.style.AppThemeDark) }
            }
        }
        if (themePrefValue == 0) { setTheme(R.style.AppTheme) }
        if (themePrefValue == 1) { setTheme(R.style.AppThemeDark) }
        setContentView(R.layout.activity_pro)
        findViewById<FrameLayout>(R.id.view_pro).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        checkSale()

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_pro_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.pro_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_pro).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.pro_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 200f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.pro_scroll).getScrollY() > 150f) {
                        findViewById<FrameLayout>(R.id.common_title_back_pro_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.pro_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.pro_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_pro).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_back_pro_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.pro_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.pro_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_pro).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.pro_scroll).getScrollY().toFloat()
                }
            })
        findViewById<ImageButton>(R.id.back_btn_pro).setOnClickListener {
            this.onBackPressed()
        }
        findViewById<TextView>(R.id.buy_btn).setOnClickListener {
            try {
                launchBillingFlow(productDetailList[0])
            }
            catch (e: IOException) {
                ToastUtil.showToast(this, "Try again")
            }
        }
        val purchasesUpdateListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (purchases?.isNotEmpty() == true) {
                            for (purchase in purchases) {
                                lifecycleScope.launch {
                                    val jsonString = purchase.originalJson
                                    try {
                                        val jsonObject = JSONObject(jsonString)
                                        val productId = jsonObject.getString("productId")
                                        if (productId == NON_CONSUMALBE_COUNTER_ID) {
                                            acknowledgePurchase(purchase)
                                        }
                                    } catch (e: Exception) {
                                        println()
                                        ToastUtil.showToast(this@ProActivity, "Error parsing JSON")
                                    }
                                }
                            }
                        }
                    }
                }
        }
        initBillingClient(purchasesUpdateListener)
    }

    private fun initBillingClient(purschasesUpdateListener: PurchasesUpdatedListener) {
        billingClient = BillingClient.newBuilder(this)
            .setListener(purschasesUpdateListener)
            .enablePendingPurchases()
            .build()
        billingClient?.startConnection(this)
    }

    override fun onBillingServiceDisconnected() {
        if (billingClient?.isReady == true) {
            billingClient?.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            queryProducts()
            queryPurchases()
        } else {
            ToastUtil.showToast(this, responseCode.toString())
        }
    }

    private fun queryProducts() {
        lifecycleScope.launch {
            queryProducts(BillingClient.ProductType.INAPP, NON_CONSUMALBE_COUNTER_ID)
        }
    }

    private suspend fun queryProducts(productType: String, product: String) {
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(product)
                            .setProductType(productType)
                            .build()
                    )
                )
                .build()
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient?.queryProductDetails(queryProductDetailsParams)
        }

        when (val responseCode = productDetailsResult?.billingResult?.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (productDetailsResult.productDetailsList?.isNotEmpty() == true) {
                    productDetailList.addAll(productDetailsResult.productDetailsList!!)
                }
            }
        }
    }

    private fun queryPurchases() {
        queryPurchases(BillingClient.ProductType.INAPP)
    }

    private fun queryPurchases(type: String) {
        if (billingClient?.isReady == false) {
            ToastUtil.showToast(this, "Billing client not ready")
            return
        }
        val queryPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(type)
            .build()

        billingClient?.queryPurchasesAsync(queryPurchaseParams) { billingResult, productDetailList ->
            when (val responseCode = billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (productDetailList.isNotEmpty()) {
                        for (purchase in productDetailList) {
                            lifecycleScope.launch {
                                val jsonString = purchase.originalJson
                                try {
                                    val jsonObject = JSONObject(jsonString)
                                    val productId = jsonObject.getString("productId")
                                    if (productId == NON_CONSUMALBE_COUNTER_ID)
                                        acknowledgePurchase(purchase)
                                } catch (e: java.lang.Exception){
                                    ToastUtil.showToast(this@ProActivity, e.message.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                withContext(Dispatchers.IO) {
                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams.build())
                    { billingResult ->
                        when (val responseCode = billingResult.responseCode) {
                            BillingClient.BillingResponseCode.OK -> {
                                showMemberInfo() //purschase accepted
                            }
                            else -> {
                                ToastUtil.showToast(this@ProActivity, "Error")
                            }
                        }

                    }
                }
            }
            else {
                showMemberInfo()
            }
        }
    }

    private fun checkSale() {
        val saleStartDate = SimpleDateFormat("yyyy/MM/dd").parse("2024/08/02") //Back to school sale
        val saleEndDate = SimpleDateFormat("yyyy/MM/dd").parse("2024/09/15") //Back to school sale
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val date = calendar.time
        val proText = findViewById<TextView>(R.id.pro_price)
        val proDiscText = findViewById<TextView>(R.id.pro_price_discount)

        if (date > saleStartDate) {
            //Set new attributes for DonateBtn
            proText.text = "1.79 USD"
            proText.setTextColor(getColorStateList(R.color.orange))
            proDiscText.visibility = View.VISIBLE
            proDiscText.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
        }

        if (date > saleEndDate) {
            Timer().schedule(2) {
                proText.text = "1.99 USD"
                proDiscText.visibility = View.GONE
            }
        }
        else {
        }
    }

    private fun launchBillingFlow(productDetails: ProductDetails) {
        try {
            val productDetailsParamsList =
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
            if (billingClient?.isReady == false) {
                ToastUtil.showToast(this, "Billing client not ready, try again!")
            }
            val billingResult = billingClient?.launchBillingFlow(this, billingFlowParams)

            when (val responseCode = billingResult?.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val proPref = ProVersion(this@ProActivity)
                    var proPrefValue = proPref.getValue()
                }
        }
        }
        catch (e: IOException) {
            ToastUtil.showToast(this, "Try again")
        }
    }

    private fun showMemberInfo() {
        val proPref = ProVersion(this@ProActivity)
        var proPrefValue = proPref.getValue()
        proPref.setValue(100)
        findViewById<LinearLayout>(R.id.pro_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.member_box).visibility = View.VISIBLE
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_pro).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back_pro).layoutParams = params

            val params2 = findViewById<TextView>(R.id.pro_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.pro_title_downstate).layoutParams = params2

    }

}




