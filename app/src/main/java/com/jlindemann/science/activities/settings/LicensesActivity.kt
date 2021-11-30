package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_dictionary.back_btn_d
import kotlinx.android.synthetic.main.activity_settings_licenses.*
import kotlinx.android.synthetic.main.activity_submit.*
import kotlinx.android.synthetic.main.license_info.*


class LicensesActivity : BaseActivity() {
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
        setContentView(R.layout.activity_settings_licenses) //REMEMBER: Never move any function calls above this

        view_lic.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        common_title_back_lic_color.visibility = View.INVISIBLE
        license_title.visibility = View.INVISIBLE
        common_title_back_lic.elevation = (resources.getDimension(R.dimen.zero_elevation))
        license_scroll.getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (license_scroll.getScrollY() > 150) {
                        common_title_back_lic_color.visibility = View.VISIBLE
                        license_title.visibility = View.VISIBLE
                        license_title_downstate.visibility = View.INVISIBLE
                        common_title_back_lic.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        common_title_back_lic_color.visibility = View.INVISIBLE
                        license_title.visibility = View.INVISIBLE
                        license_title_downstate.visibility = View.VISIBLE
                        common_title_back_lic.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = license_scroll.getScrollY().toFloat()
                }
            })

        listeners()
        back_btn_d.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params2 = common_title_back_lic.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_lic.layoutParams = params2

        val params3 = license_title_downstate.layoutParams as ViewGroup.MarginLayoutParams
        params3.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        license_title_downstate.layoutParams = params3
    }

    override fun onBackPressed() {
        if (l_inc.visibility == View.VISIBLE) {
            hideInfoPanel()
            return
        } else { super.onBackPressed() }
    }

    private fun listeners() {
        l_wiki_btn.setOnClickListener {
            val title = resources.getString(R.string.wikipedia_license)
            val text = resources.getString(R.string.wikipedia_license_text)
            showInfoPanel(title, text)
        }
        l_sothree_btn.setOnClickListener {
            val title = resources.getString(R.string.sothree_license)
            val text = resources.getString(R.string.sothree_license_text)
            showInfoPanel(title, text)
        }

        l_back_btn.setOnClickListener { hideInfoPanel() }
        l_background3.setOnClickListener { hideInfoPanel() }
    }

    private fun showInfoPanel(title: String, text: String) {
        Anim.fadeIn(l_inc, 150)

        l_title.text = title
        l_text.text = text
    }

    private fun hideInfoPanel() {
        Anim.fadeOutAnim(l_inc, 150)
    }
}



