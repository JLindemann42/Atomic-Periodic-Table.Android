package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.DegreePreference
import com.jlindemann.science.preferences.TemperatureUnits
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_favorite_settings_page.*
import kotlinx.android.synthetic.main.activity_ph.*
import kotlinx.android.synthetic.main.activity_unit.*
import kotlinx.android.synthetic.main.activity_unit.celsius_btn
import kotlinx.android.synthetic.main.activity_unit.fahrenheit_btn
import kotlinx.android.synthetic.main.activity_unit.kelvin_btn


class UnitActivity : BaseActivity()  {
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

        setContentView(R.layout.activity_unit) //REMEMBER: Never move any function calls above this
        view_unit.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        common_title_back_unit_color.visibility = View.INVISIBLE
        unit_title.visibility = View.INVISIBLE
        common_title_back_unit.elevation = (resources.getDimension(R.dimen.zero_elevation))
        unit_scroll.getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (unit_scroll.getScrollY() > 150) {
                        common_title_back_unit_color.visibility = View.VISIBLE
                        unit_title.visibility = View.VISIBLE
                        unit_title_downstate.visibility = View.INVISIBLE
                        common_title_back_unit.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        common_title_back_unit_color.visibility = View.INVISIBLE
                        unit_title.visibility = View.INVISIBLE
                        unit_title_downstate.visibility = View.VISIBLE
                        common_title_back_unit.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = unit_scroll.getScrollY().toFloat()
                }
            })

        tempUnits()

        back_btn_unit.setOnClickListener {
            this.onBackPressed()
        }
    }

    private fun tempUnits() {
        val tempPreference = TemperatureUnits(this)
        val tempPrefValue = tempPreference.getValue()
        if (tempPrefValue == "kelvin") {
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        if (tempPrefValue == "celsius") {
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        if (tempPrefValue == "fahrenheit") {
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
        }
        kelvin_btn.setOnClickListener {
            tempPreference.setValue("kelvin")
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        celsius_btn.setOnClickListener {
            tempPreference.setValue("celsius")
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        fahrenheit_btn.setOnClickListener {
            tempPreference.setValue("fahrenheit")
            kelvin_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            celsius_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            fahrenheit_btn.background = ContextCompat.getDrawable(this, R.drawable.chip_active)
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val paramsTitle = common_title_back_unit.layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_unit.layoutParams = paramsTitle

        val paramsLin = unit_title_downstate.layoutParams as ViewGroup.MarginLayoutParams
        paramsLin.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        unit_title_downstate.layoutParams = paramsLin
    }
}



