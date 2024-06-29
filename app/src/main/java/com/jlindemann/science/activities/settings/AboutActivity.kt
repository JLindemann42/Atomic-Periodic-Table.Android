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

        findViewById<FrameLayout>(R.id.view_info).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        setupLinks()

        findViewById<FloatingActionButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_info).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back_info).layoutParams = params

            val params2 = findViewById<ImageView>(R.id.imageView3).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin += top
            findViewById<ImageView>(R.id.imageView3).layoutParams = params2

            val titleParam = findViewById<FrameLayout>(R.id.title_box_info).layoutParams as ViewGroup.MarginLayoutParams
            titleParam.rightMargin = right
            titleParam.leftMargin = left
            findViewById<FrameLayout>(R.id.title_box_info).layoutParams = titleParam

    }

    private fun setupLinks() {
        findViewById<FrameLayout>(R.id.pro).setOnClickListener {
            val marketUri: Uri = Uri.parse("market://details?id=com.jlindemannpro.papersplash")
            startActivity(Intent(Intent.ACTION_VIEW, marketUri))
        }
        findViewById<FrameLayout>(R.id.sta).setOnClickListener {
            val marketUri: Uri = Uri.parse("market://details?id=com.jlindemann.papersplash")
            startActivity(Intent(Intent.ACTION_VIEW, marketUri))
        }
    }
}



