package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.DegreePreference
import com.jlindemann.science.preferences.TemperatureUnits
import com.jlindemann.science.preferences.ThemePreference


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
        findViewById<FrameLayout>(R.id.view_unit).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_unit_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.unit_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_unit).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.unit_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.unit_scroll).getScrollY() > 150) {
                        findViewById<FrameLayout>(R.id.common_title_back_unit_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.unit_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_unit).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_back_unit_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.unit_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_unit).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.unit_scroll).getScrollY().toFloat()
                }
            })
        tempUnits()
        findViewById<ImageButton>(R.id.back_btn_unit).setOnClickListener {
            this.onBackPressed()
        }
    }

    private fun tempUnits() {
        val tempPreference = TemperatureUnits(this)
        val tempPrefValue = tempPreference.getValue()
        if (tempPrefValue == "kelvin") {
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        if (tempPrefValue == "celsius") {
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        if (tempPrefValue == "fahrenheit") {
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
        }
        findViewById<Button>(R.id.kelvin_btn).setOnClickListener {
            tempPreference.setValue("kelvin")
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        findViewById<Button>(R.id.celsius_btn).setOnClickListener {
            tempPreference.setValue("celsius")
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
        }
        findViewById<Button>(R.id.fahrenheit_btn).setOnClickListener {
            tempPreference.setValue("fahrenheit")
            findViewById<Button>(R.id.kelvin_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.celsius_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_outline)
            findViewById<Button>(R.id.fahrenheit_btn).background = ContextCompat.getDrawable(this, R.drawable.chip_active)
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val paramsTitle = findViewById<FrameLayout>(R.id.common_title_back_unit).layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_unit).layoutParams = paramsTitle

        val paramsLin = findViewById<TextView>(R.id.unit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        paramsLin.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.unit_title_downstate).layoutParams = paramsLin
    }
}



