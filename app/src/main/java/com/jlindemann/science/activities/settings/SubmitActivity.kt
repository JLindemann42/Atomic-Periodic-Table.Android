package com.jlindemann.science.activities.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController
import com.jlindemann.science.utils.Utils

class SubmitActivity : BaseActivity() {

    // Lifecycle-aware OnBackPressedCallback (starts DISABLED)
    private var backCallback: OnBackPressedCallback? = null

    // Optional OnBackInvokedCallback for newer platforms (registered only when interception is needed)
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
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
        setContentView(R.layout.activity_submit)
        findViewById<ConstraintLayout>(R.id.view_sub).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        dropSelector()

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.submit_activity_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }
        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        //Title Controller
        findViewById<ScrollView>(R.id.submit_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.submit_scroll).scrollY
                    if (scrollY > 150) {
                        titleSurface.visibility = View.VISIBLE
                        titleBar.titleView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.submit_title_downstate).visibility = View.INVISIBLE
                        titleBar.container.elevation = resources.getDimension(R.dimen.one_elevation)
                    } else {
                        titleSurface.visibility = View.INVISIBLE
                        titleBar.titleView.visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.submit_title_downstate).visibility = View.VISIBLE
                        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
                    }
                }
            })

        // Register a lifecycle-aware OnBackPressedCallback in DISABLED state.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

        val params2 = findViewById<TextView>(R.id.submit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<TextView>(R.id.submit_title_downstate).layoutParams = params2

    }

    // Override for Android < 13 - fallback
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun dropSelector() {
        var type = "#data_issue"
        buildForm(type)
        val dropBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.drop_btn)
        dropBtn.setOnClickListener {
            val popup = android.widget.PopupMenu(this, it)
            popup.menuInflater.inflate(R.menu.submit_type_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.type_data_issue -> {
                        type = "#data_issue"
                        dropBtn.text = getString(R.string.data_issue)
                        buildForm(type)
                        true
                    }
                    R.id.type_bug -> {
                        type = "#bug"
                        dropBtn.text = getString(R.string.bug)
                        buildForm(type)
                        true
                    }
                    R.id.type_question -> {
                        type = "#question"
                        dropBtn.text = getString(R.string.question)
                        buildForm(type)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun buildForm(type: String) {
        findViewById<TextView>(R.id.i_btn).setOnClickListener {
            val title = findViewById<EditText>(R.id.i_title).text.toString()
            val content = findViewById<EditText>(R.id.i_content).text.toString()
            val request = Intent(Intent.ACTION_VIEW)
            request.data = Uri.parse(Uri.parse("mailto:jlindemann.dev@gmail.com?subject=$type $title&body=$content").toString())
            startActivity(request)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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