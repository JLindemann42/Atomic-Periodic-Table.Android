package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.DegreePreference
import com.jlindemann.science.preferences.TemperatureUnits
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController


class UnitActivity : BaseActivity()  {
    private lateinit var titleBar: UnifiedTitleBarController

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
        findViewById<ConstraintLayout>(R.id.view_unit_root).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.activity_unit_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }
        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        //Title Controller
        findViewById<ScrollView>(R.id.unit_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.unit_scroll).getScrollY() > 150) {
                        titleSurface.visibility = View.VISIBLE
                        titleBar.titleView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.INVISIBLE
                        titleBar.container.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        titleSurface.visibility = View.INVISIBLE
                        titleBar.titleView.visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.VISIBLE
                        titleBar.container.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.unit_scroll).getScrollY().toFloat()
                }
            })
        tempUnits()
    }

    private fun tempUnits() {
        val tempPreference = TemperatureUnits(this)
        val tempPrefValue = tempPreference.getValue()
        val tempUnitGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.temp_unit_group)

        when (tempPrefValue) {
            "kelvin" -> tempUnitGroup.check(R.id.kelvin_btn)
            "celsius" -> tempUnitGroup.check(R.id.celsius_btn)
            "fahrenheit" -> tempUnitGroup.check(R.id.fahrenheit_btn)
        }

        tempUnitGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.kelvin_btn -> tempPreference.setValue("kelvin")
                    R.id.celsius_btn -> tempPreference.setValue("celsius")
                    R.id.fahrenheit_btn -> tempPreference.setValue("fahrenheit")
                }
            }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val paramsTitle = titleBar.container.layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = paramsTitle

        val paramsLin = findViewById<TextView>(R.id.unit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        paramsLin.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<TextView>(R.id.unit_title_downstate).layoutParams = paramsLin
    }
}
