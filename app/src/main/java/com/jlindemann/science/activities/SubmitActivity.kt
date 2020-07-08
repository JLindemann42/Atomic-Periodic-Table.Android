package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Insets
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_solubility.back_btn
import kotlinx.android.synthetic.main.activity_submit.*


class SubmitActivity : BaseActivity() {

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

        view_sub.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        issueListener()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_sub.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_sub.layoutParams = params

        val params2 = i_box.layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + 36
        i_box.layoutParams = params2
    }

    private fun issueListener() {
        i_btn.setOnClickListener {
            val title = i_title.text
            val content = i_content.text
            val uri = Uri.parse("mailto:jlindemann.dev@gmail.com?subject=#issue $title&body=$content")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }
}



