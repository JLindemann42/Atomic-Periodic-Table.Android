package com.jlindemann.science.activities.tables

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import com.google.android.material.button.MaterialButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.model.*
import com.jlindemann.science.utils.UnifiedTitleBarController
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import kotlin.collections.ArrayList


class phActivity : BaseActivity()  {
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
        setContentView(R.layout.activity_ph) //REMEMBER: Never move any function calls above this

        indicatorListener()
        findViewById<ConstraintLayout>(R.id.view_ph).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        
        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.activity_ph_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }

        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        // Title Controller
        findViewById<ScrollView>(R.id.ph_scroll).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.ph_scroll).scrollY
                if (scrollY > 150) {
                    titleSurface.visibility = View.VISIBLE
                    titleBar.titleView.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.ph_title_downstate).visibility = View.INVISIBLE
                    titleBar.container.elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    titleSurface.visibility = View.INVISIBLE
                    titleBar.titleView.visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.ph_title_downstate).visibility = View.VISIBLE
                    titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "phi"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        //Set-up for back button
    }

    private fun indicatorListener() {
        val indicatorList = ArrayList<Indicator>()
        IndicatorModel.getList(indicatorList)
        val acidText = "[H+]>[OH-] pH<"
        val neutralText = "[H+]=[OH-] pH="
        val alkalineText = "[H+]<[OH-] pH>"

        val updateUi = { item: Indicator ->
            findViewById<TextView>(R.id.acid_info).text = acidText + item.acid
            findViewById<TextView>(R.id.neutral_info).text = neutralText + item.neutral
            findViewById<TextView>(R.id.alkaline_info).text = alkalineText + item.alkali
            updatePhColor(item)
        }

        // Initialize with first indicator
        updateUi(indicatorList[0])

        findViewById<com.google.android.material.chip.Chip>(R.id.bromothymol_blue_btn).setOnClickListener { updateUi(indicatorList[0]) }
        findViewById<com.google.android.material.chip.Chip>(R.id.methyl_orange_btn).setOnClickListener { updateUi(indicatorList[1]) }
        findViewById<com.google.android.material.chip.Chip>(R.id.congo_red_btn).setOnClickListener { updateUi(indicatorList[2]) }
        findViewById<com.google.android.material.chip.Chip>(R.id.phenolphthalein_btn).setOnClickListener { updateUi(indicatorList[3]) }
    }

    private fun updatePhColor(item: Indicator) {
        val leftColor = resources.getIdentifier(item.acidColor, "color", packageName)
        val centerColor = resources.getIdentifier(item.neutralColor, "color", packageName)
        val rightColor = resources.getIdentifier(item.alkaliColor, "color", packageName)

        findViewById<ImageView>(R.id.left).setColorFilter(ContextCompat.getColor(this, leftColor), android.graphics.PorterDuff.Mode.SRC_IN)
        findViewById<ImageView>(R.id.center).setColorFilter(ContextCompat.getColor(this, centerColor), android.graphics.PorterDuff.Mode.SRC_IN)
        findViewById<ImageView>(R.id.right).setColorFilter(ContextCompat.getColor(this, rightColor), android.graphics.PorterDuff.Mode.SRC_IN)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val paramsTitle = titleBar.container.layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = paramsTitle

        val params2 = findViewById<TextView>(R.id.ph_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<TextView>(R.id.ph_title_downstate).layoutParams = params2
    }
}



