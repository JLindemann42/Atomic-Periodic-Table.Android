package com.jlindemann.science.activities

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.adapter.EquationsAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.*
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_equations.*
import kotlinx.android.synthetic.main.activity_ph.*
import kotlinx.android.synthetic.main.equations_info.*
import java.util.*
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

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val paramsTitle = common_title_back_ph.layoutParams as ViewGroup.LayoutParams
        paramsTitle.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        common_title_back_ph.layoutParams = paramsTitle

        val paramsContent = ph_content.layoutParams as ViewGroup.MarginLayoutParams
        paramsContent.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        ph_content.layoutParams = paramsContent
    }
}



