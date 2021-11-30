package com.jlindemann.science.activities.tables

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import com.jlindemann.science.R
import com.jlindemann.science.R2.id.bottom
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.model.*
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_ph.*
import kotlinx.android.synthetic.main.activity_submit.*
import kotlinx.android.synthetic.main.bar_ph_chips.*
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
        view_ph.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        //Title Controller


        back_btn_ph.setOnClickListener {
            this.onBackPressed()
        }
    }

    private fun indicatorListener() {
        val indicatorList = ArrayList<Indicator>()
        IndicatorModel.getList(indicatorList)
        val acidText = "[H+]>[OH-] pH<"
        val neutralText = "[H+]=[OH-] pH="
        val alkalineText = "[H+]<[OH-] pH>"

        val item = indicatorList[0]
        acid_info.text = acidText + item.acid
        neutral_info.text = neutralText + item.neutral
        alkaline_info.text = alkalineText + item.alkali
        updatePhColor(item)
        updateButtonColor("bromothymol_blue_btn")

        bromothymol_blue_btn.setOnClickListener {
            val item = indicatorList[0]
            acid_info.text = acidText + item.acid
            neutral_info.text = neutralText + item.neutral
            alkaline_info.text = alkalineText + item.alkali
            updatePhColor(item)
            updateButtonColor("bromothymol_blue_btn")
        }
        methyl_orange_btn.setOnClickListener {
            val item = indicatorList[1]
            acid_info.text = acidText + item.acid
            neutral_info.text = neutralText + item.neutral
            alkaline_info.text = alkalineText + item.alkali
            updatePhColor(item)
            updateButtonColor("methyl_orange_btn")
        }
        congo_red_btn.setOnClickListener {
            val item = indicatorList[2]
            acid_info.text = acidText + item.acid
            neutral_info.text = neutralText + item.neutral
            alkaline_info.text = alkalineText + item.alkali
            updatePhColor(item)
            updateButtonColor("congo_red_btn")
        }
        phenolphthalein_btn.setOnClickListener {
            val item = indicatorList[3]
            acid_info.text = acidText + item.acid
            neutral_info.text = neutralText + item.neutral
            alkaline_info.text = alkalineText + item.alkali
            updatePhColor(item)
            updateButtonColor("phenolphthalein_btn")
        }
    }

    private fun updatePhColor(item: Indicator) {
        val leftColor = resources.getIdentifier(item.acidColor, "color", packageName)
        val centerColor = resources.getIdentifier(item.neutralColor, "color", packageName)
        val rightColor = resources.getIdentifier(item.alkaliColor, "color", packageName)

        left.setColorFilter(ContextCompat.getColor(this, leftColor), android.graphics.PorterDuff.Mode.SRC_IN)
        center.setColorFilter(ContextCompat.getColor(this, centerColor), android.graphics.PorterDuff.Mode.SRC_IN)
        right.setColorFilter(ContextCompat.getColor(this, rightColor), android.graphics.PorterDuff.Mode.SRC_IN)
    }

    private fun updateButtonColor(btn: String) {
        methyl_orange_btn.background = getDrawable(R.drawable.chip)
        bromothymol_blue_btn.background = getDrawable(R.drawable.chip)
        congo_red_btn.background = getDrawable(R.drawable.chip)
        phenolphthalein_btn.background = getDrawable(R.drawable.chip)

        val delay = Handler()
        delay.postDelayed({
            val resIDB = resources.getIdentifier(btn, "id", packageName)
            val button = findViewById<Button>(resIDB)
            button.background = getDrawable(R.drawable.chip_active)
        }, 1)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val paramsTitle = common_title_back_ph.layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        common_title_back_ph.layoutParams = paramsTitle

        val pScroll = ph_scroll.layoutParams as ViewGroup.MarginLayoutParams
        pScroll.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        ph_scroll.layoutParams = pScroll
    }
}



