package com.jlindemann.science.settings

import android.graphics.Insets
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils

class ExperimentalActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_experimental_settings_page) //Don't move down (Needs to be before we call our functions)

        //onClickListeners() //Disabled as a result of conflicts between ACTION_DOWN and ScrollView
        findViewById<ConstraintLayout>(R.id.viewe).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        
        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_exp_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.element_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_exp).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<android.widget.ScrollView>(R.id.scroll_exp).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<android.widget.ScrollView>(R.id.scroll_exp).scrollY
                if (scrollY > 150) {
                    findViewById<FrameLayout>(R.id.common_title_back_exp_color).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.element_title).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.element_title_downstate).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_exp).elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    findViewById<FrameLayout>(R.id.common_title_back_exp_color).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.element_title).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.element_title_downstate).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_exp).elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }

        findViewById<View>(R.id.back_btn_exp).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_exp).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_exp).layoutParams = params

        val params2 = findViewById<TextView>(R.id.element_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.element_title_downstate).layoutParams = params2
    }


}



