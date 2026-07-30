package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.TabUtil
import com.jlindemann.science.utils.UnifiedTitleBarController

class CreditsActivity : BaseActivity() {

    // Unified back handling fields
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var titleBar: UnifiedTitleBarController

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
        setContentView(R.layout.activity_settings_credits) //REMEMBER: Never move any function calls above this

        findViewById<ConstraintLayout>(R.id.view_cre).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // No overlay consumed it -> fallback to default behaviour.
                    isEnabled = false
                    try {
                        onBackPressedDispatcher.onBackPressed()
                    } finally {
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // Start with platform OnBackInvoked interception disabled; enable when overlays appear.
        setBackInterceptionEnabled(false)

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.credits_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }
        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        findViewById<ScrollView>(R.id.credits_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : android.view.ViewTreeObserver.OnScrollChangedListener {
                private var isTitleVisible = false

                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.credits_scroll).scrollY
                    val threshold = 158

                    val titleColorBackground = titleSurface
                    val titleText = titleBar.titleView
                    val titleDownstateText = findViewById<TextView>(R.id.credits_title_downstate)
                    val titleBackground = titleBar.container

                    if (scrollY > threshold) {
                        if (!isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                            TitleBarAnimator.animateVisibility(titleText, true)
                            TitleBarAnimator.animateVisibility(titleDownstateText, false)
                            titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                            isTitleVisible = true
                        }
                    } else {
                        if (isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, false)
                            TitleBarAnimator.animateVisibility(titleText, false)
                            TitleBarAnimator.animateVisibility(titleDownstateText, true)
                            titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                            isTitleVisible = false
                        }
                    }
                }
            })

        listeners()
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params2 = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params2

        val params3 = findViewById<TextView>(R.id.credits_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params3.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<TextView>(R.id.credits_title_downstate).layoutParams = params3
    }

    override fun onBackPressed() {
        // Use centralized handler so gestures and hardware back behave consistently.
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    private fun listeners() {
        findViewById<LinearLayout>(R.id.c_giancarlo).setOnClickListener {
            val packageManager = packageManager
            val blogURL = "https://x.com/ungiancarlo"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
        findViewById<LinearLayout>(R.id.c_electro_boy).setOnClickListener {
            val packageManager = packageManager
            val blogURL = "https://github.com/ElectroBoy10"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
    }

    // Centralized overlay detection (none currently in this activity)
    private fun anyOverlayOpen(): Boolean {
        // No overlays in CreditsActivity at the moment.
        return false
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        // No overlays to close; return false so system handles back.
        return false
    }

    /**
     * Centralized management of platform back interception for Android 14+.
     * Forward platform back invocations to the OnBackPressedDispatcher so gestures and
     * hardware back buttons use the same logic.
     */
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        // Keep OnBackPressedCallback state in sync
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        uiHandler.post {
                            try {
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                val consumed = handleBackPress()
                                if (!consumed) {
                                    finish()
                                }
                            }
                        }
                    }
                    try {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            onBackInvokedCb!!
                        )
                    } catch (_: Exception) {
                        // ignore registration errors on some devices
                    }
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) {
                        // ignore
                    }
                    onBackInvokedCb = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup back interception hooks
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            } catch (_: Exception) { }
            onBackInvokedCb = null
        }
    }
}