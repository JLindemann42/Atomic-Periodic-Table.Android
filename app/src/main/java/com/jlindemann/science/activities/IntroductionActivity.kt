package com.jlindemann.science.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jlindemann.science.R
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.adapter.IntroductionPagerAdapter
import com.jlindemann.science.billing.BillingManager
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.coroutines.launch

class IntroductionActivity : AppCompatActivity(), BillingManager.Listener {

    companion object {
        private const val PRO_PAGE_INDEX = 3
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var introductionAdapter: IntroductionPagerAdapter

    // Billing manager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme
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

        setContentView(R.layout.activity_introduction)

        viewPager = findViewById(R.id.intro_viewpager)
        tabLayout = findViewById(R.id.intro_tab_layout)
        nextButton = findViewById(R.id.intro_next_button)
        skipButton = findViewById(R.id.intro_skip_button)
        backButton = findViewById(R.id.intro_back_button)

        // Setup ViewPager + Adapter
        introductionAdapter = IntroductionPagerAdapter(this)
        viewPager.adapter = introductionAdapter
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        nextButton.setOnClickListener {
            if (viewPager.currentItem < introductionAdapter.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                completeIntroduction()
            }
        }

        skipButton.setOnClickListener { completeIntroduction() }

        backButton.setOnClickListener {
            if (viewPager.currentItem > 0) viewPager.currentItem -= 1
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonStates(position, introductionAdapter.itemCount)
            }
        })
        updateButtonStates(0, introductionAdapter.itemCount)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) viewPager.currentItem -= 1 else finish()
            }
        })

        // Initialize BillingManager
        billingManager = BillingManager(this, lifecycleScope, this)
        billingManager.initialize()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }

    // Methods used by the adapter (delegate to billingManager)
    fun getProductDetail(productId: String) = billingManager.getProductDetail(productId)
    fun isOwnsProVersion() = billingManager.isOwnsProVersion()
    fun isOwnsProPlusVersion() = billingManager.isOwnsProPlusVersion()
    fun getFormattedPrice(productDetails: com.android.billingclient.api.ProductDetails?) : String {
        val formattedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        return formattedPrice ?: getString(R.string.price_not_available)
    }
    fun purchaseProduct(productDetails: com.android.billingclient.api.ProductDetails) {
        billingManager.launchPurchase(productDetails)
    }

    // BillingManager.Listener callbacks
    override fun onProductsUpdated() {
        // refresh pro page to show prices
        runOnUiThread { viewPager.adapter?.notifyItemChanged(PRO_PAGE_INDEX) }
    }

    override fun onPurchasesUpdated() {
        // refresh pro page to reflect ownership flags
        runOnUiThread { viewPager.adapter?.notifyItemChanged(PRO_PAGE_INDEX) }
    }

    override fun onPurchaseCompleted(productId: String) {
        // update UI and optionally set preferences
        runOnUiThread {
            viewPager.adapter?.notifyItemChanged(PRO_PAGE_INDEX)
            // optionally give short delay or toast here
        }

    }

    override fun onError(message: String) {
        // optional logging / user feedback
        // e.g., Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonStates(position: Int, totalPages: Int) {
        backButton.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        if (position == totalPages - 1) {
            nextButton.text = getString(R.string.intro_finish)
            skipButton.visibility = View.GONE
        } else {
            nextButton.text = getString(R.string.intro_next)
            skipButton.visibility = View.VISIBLE
        }
    }

    private fun completeIntroduction() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("introduction_shown", true).apply()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}