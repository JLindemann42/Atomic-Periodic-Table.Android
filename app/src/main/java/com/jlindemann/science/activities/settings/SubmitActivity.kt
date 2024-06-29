package com.jlindemann.science.activities.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils

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
        findViewById<FrameLayout>(R.id.view_sub).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        dropSelector()

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_sub_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.submit_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_sub).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.submit_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 200f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.submit_scroll).getScrollY() > 150f) {
                        findViewById<FrameLayout>(R.id.common_title_back_sub_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.submit_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.submit_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_sub).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_back_sub_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.submit_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.submit_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_sub).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.submit_scroll).getScrollY().toFloat()
                }
            })

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_sub).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_sub).layoutParams = params

            val params2 = findViewById<TextView>(R.id.submit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.submit_title_downstate).layoutParams = params2

    }

    override fun onBackPressed() {
        if (findViewById<ConstraintLayout>(R.id.drop_issue).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            return
        }
        super.onBackPressed()
    }

    private fun dropSelector() {
        var type = "#data_issue"
        buildForm(type)
        findViewById<TextView>(R.id.drop_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            Utils.fadeInAnim(findViewById<TextView>(R.id.background), 150)
        }
        findViewById<TextView>(R.id.background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
        }

        findViewById<TextView>(R.id.data_issue).setOnClickListener {
            type = "#data_issue"
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            findViewById<TextView>(R.id.drop_btn).text = getString(R.string.data_issue)
            buildForm(type)
        }
        findViewById<TextView>(R.id.bug).setOnClickListener {
            type = "#bug"
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            findViewById<TextView>(R.id.drop_btn).text = getString(R.string.bug)
            buildForm(type)
        }
        findViewById<TextView>(R.id.question).setOnClickListener {
            type = "#question"
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.drop_issue), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            findViewById<TextView>(R.id.drop_btn).text = getString(R.string.question)
            buildForm(type)
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

}



