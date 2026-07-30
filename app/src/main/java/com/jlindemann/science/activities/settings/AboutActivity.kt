package com.jlindemann.science.activities.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController

class AboutActivity : BaseActivity() {
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
        setContentView(R.layout.activity_info)

        findViewById<ConstraintLayout>(R.id.view_info).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupLinks()

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.about_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.VISIBLE
        titleBar.backButton.setOnClickListener { onBackPressed() }
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        //Title Controller
        findViewById<ScrollView>(R.id.scroll_info).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.scroll_info).scrollY
                if (scrollY > 150) {
                    titleSurface.visibility = View.VISIBLE
                    titleBar.container.elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    titleSurface.visibility = View.INVISIBLE
                    titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

        val params2 = findViewById<ImageView>(R.id.imageView3).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<ImageView>(R.id.imageView3).layoutParams = params2
    }

    private fun setupLinks() {
    //empty
    }
}


