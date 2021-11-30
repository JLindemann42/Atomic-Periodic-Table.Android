package com.jlindemann.science.activities.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_favorite_settings_page.*
import kotlinx.android.synthetic.main.activity_ph.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.activity_solubility.back_btn
import kotlinx.android.synthetic.main.activity_submit.*
import kotlinx.android.synthetic.main.activity_submit.submit_title
import kotlinx.android.synthetic.main.activity_submit.view_sub
import kotlinx.android.synthetic.main.drop_issue.*


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
        dropSelector()

        //Title Controller
        common_title_back_sub_color.visibility = View.INVISIBLE
        submit_title.visibility = View.INVISIBLE
        common_title_back_sub.elevation = (resources.getDimension(R.dimen.zero_elevation))
        submit_scroll.getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 200f
                override fun onScrollChanged() {
                    if (submit_scroll.getScrollY() > 150f) {
                        common_title_back_sub_color.visibility = View.VISIBLE
                        submit_title.visibility = View.VISIBLE
                        submit_title_downstate.visibility = View.INVISIBLE
                        common_title_back_sub.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        common_title_back_sub_color.visibility = View.INVISIBLE
                        submit_title.visibility = View.INVISIBLE
                        submit_title_downstate.visibility = View.VISIBLE
                        common_title_back_sub.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = submit_scroll.getScrollY().toFloat()
                }
            })

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = common_title_back_sub.layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_sub.layoutParams = params

            val params2 = submit_title_downstate.layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        submit_title_downstate.layoutParams = params2

    }

    override fun onBackPressed() {
        if (drop_issue.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(background, 150)
            Utils.fadeOutAnim(drop_issue, 150)
            return
        }
        super.onBackPressed()
    }

    private fun dropSelector() {
        var type = "#data_issue"
        buildForm(type)
        drop_btn.setOnClickListener {
            Utils.fadeInAnim(drop_issue, 150)
            Utils.fadeInAnim(background, 150)
        }
        background.setOnClickListener {
            Utils.fadeOutAnim(drop_issue, 150)
            Utils.fadeOutAnim(background, 150)
        }

        data_issue.setOnClickListener {
            type = "#data_issue"
            Utils.fadeOutAnim(drop_issue, 150)
            Utils.fadeOutAnim(background, 150)
            drop_btn.text = getString(R.string.data_issue)
            buildForm(type)
        }
        bug.setOnClickListener {
            type = "#bug"
            Utils.fadeOutAnim(drop_issue, 150)
            Utils.fadeOutAnim(background, 150)
            drop_btn.text = getString(R.string.bug)
            buildForm(type)
        }
        question.setOnClickListener {
            type = "#question"
            Utils.fadeOutAnim(drop_issue, 150)
            Utils.fadeOutAnim(background, 150)
            drop_btn.text = getString(R.string.question)
            buildForm(type)
        }
    }

    private fun buildForm(type: String) {
        i_btn.setOnClickListener {
            val title = i_title.text.toString()
            val content = i_content.text.toString()
            val request = Intent(Intent.ACTION_VIEW)
            request.data = Uri.parse(Uri.parse("mailto:jlindemann.dev@gmail.com?subject=$type $title&body=$content").toString())
            startActivity(request)
        }
    }

}



