package com.jlindemann.science.activities

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jlindemann.science.R
import com.jlindemann.science.adapter.SolubilityAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.SolubilityData
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController

class SolubilityActivity : BaseActivity() {

    private lateinit var titleBar: UnifiedTitleBarController
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        }
        if (themePrefValue == 0) setTheme(R.style.AppTheme)
        if (themePrefValue == 1) setTheme(R.style.AppThemeDark)
        setContentView(R.layout.activity_solubility)

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.solubility_table)
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.setAction(R.drawable.ic_info) { openInfoPanel() }
        titleBar.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupAnions()
        setupColumns()
        setupInfoPanel()

        // Register lifecycle-aware OnBackPressedCallback
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!handleBackPress()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
        setBackInterceptionEnabled(false)

        // Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "sol"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }
    }

    private fun setupAnions() {
        val container = findViewById<LinearLayout>(R.id.anions_container)
        val inflater = LayoutInflater.from(this)
        for (anion in SolubilityData.anions) {
            val view = inflater.inflate(R.layout.item_solubility_anion, container, false) as TextView
            view.text = anion
            container.addView(view)
        }
    }

    private fun setupColumns() {
        val recyclerView = findViewById<RecyclerView>(R.id.columns_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        recyclerView.adapter = SolubilityAdapter(SolubilityData.getColumns())
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

    }

    private fun setupInfoPanel() {
        findViewById<MaterialButton>(R.id.info_back_btn).setOnClickListener { closeInfoPanel() }
        findViewById<TextView>(R.id.info_background).setOnClickListener { closeInfoPanel() }
    }

    private fun openInfoPanel() {
        Anim.fadeIn(findViewById<ConstraintLayout>(R.id.info_panel), 300)
        findViewById<TextView>(R.id.info_title).text = resources.getString(R.string.solubility_info_t)
        findViewById<TextView>(R.id.info_text).text = resources.getString(R.string.solubility_info_c)
        setBackInterceptionEnabled(true)
    }

    private fun closeInfoPanel() {
        Anim.fadeOutAnim(findViewById<ConstraintLayout>(R.id.info_panel), 300)
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    private fun anyOverlayOpen(): Boolean {
        return findViewById<ConstraintLayout>(R.id.info_panel).visibility == View.VISIBLE
    }

    private fun handleBackPress(): Boolean {
        if (anyOverlayOpen()) {
            closeInfoPanel()
            return true
        }
        return false
    }

    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        uiHandler.post {
                            try {
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                if (!handleBackPress()) finish()
                            }
                        }
                    }
                    try {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            onBackInvokedCb!!
                        )
                    } catch (_: Exception) {}
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) {}
                    onBackInvokedCb = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            } catch (_: Exception) {}
            onBackInvokedCb = null
        }
    }
}
