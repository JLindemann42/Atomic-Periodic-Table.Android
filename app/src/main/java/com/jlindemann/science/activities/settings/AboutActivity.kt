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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference

class AboutActivity : BaseActivity() {

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

        findViewById<FrameLayout>(R.id.view_info).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupLinks()

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_info_color).visibility = View.INVISIBLE
        findViewById<ScrollView>(R.id.scroll_info).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.scroll_info).scrollY
                if (scrollY > 150) {
                    findViewById<FrameLayout>(R.id.common_title_info_color).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_info).elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    findViewById<FrameLayout>(R.id.common_title_info_color).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_info).elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }

        findViewById<View>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_info).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_info).layoutParams = params

        val params2 = findViewById<ImageView>(R.id.imageView3).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<ImageView>(R.id.imageView3).layoutParams = params2
    }

    private fun setupLinks() {
    //empty
    }
}



