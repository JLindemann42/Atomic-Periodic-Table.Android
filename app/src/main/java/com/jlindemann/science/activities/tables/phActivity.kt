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
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.model.*
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import kotlin.collections.ArrayList


class phActivity : BaseActivity()  {
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
        findViewById<FrameLayout>(R.id.view_ph).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        //Title Controller

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
        findViewById<ImageButton>(R.id.back_btn_ph).setOnClickListener {
            this.onBackPressed()
        }
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
        val paramsTitle = findViewById<FrameLayout>(R.id.common_title_back_ph).layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<FrameLayout>(R.id.common_title_back_ph).layoutParams = paramsTitle

        val pScroll = findViewById<ScrollView>(R.id.ph_scroll).layoutParams as ViewGroup.MarginLayoutParams
        pScroll.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<ScrollView>(R.id.ph_scroll).layoutParams = pScroll
    }
}



