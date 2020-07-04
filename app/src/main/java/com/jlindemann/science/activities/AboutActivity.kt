package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_info.*
import kotlinx.android.synthetic.main.activity_solubility.back_btn


class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)
        
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

        setupLinks()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_info.layoutParams as ViewGroup.LayoutParams
        params.height += top
        common_title_back_info.layoutParams = params

        val params2 = imageView3.layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin += top
        imageView3.layoutParams = params2
    }

    private fun setupLinks() {
        pro.setOnClickListener {
            val marketUri: Uri = Uri.parse("market://details?id=com.jlindemannpro.papersplash")
            startActivity(Intent(Intent.ACTION_VIEW, marketUri))
        }
        sta.setOnClickListener {
            val marketUri: Uri = Uri.parse("market://details?id=com.jlindemann.papersplash")
            startActivity(Intent(Intent.ACTION_VIEW, marketUri))
        }
    }
}



