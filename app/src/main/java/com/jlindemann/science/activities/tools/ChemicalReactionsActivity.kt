package com.jlindemann.science.activities.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.*
import android.text.style.SubscriptSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.activities.FavoriteCompound
import com.jlindemann.science.activities.FavoriteCompoundsAdapter
import com.jlindemann.science.activities.IncludedElementsAdapter
import com.jlindemann.science.activities.Quadruple
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import org.json.JSONArray
import java.io.IOException

class ChemicalReactionsActivity : BaseActivity() {

    private lateinit var includedElementsAdapter: IncludedElementsAdapter
    private lateinit var favoriteCompoundsAdapter: FavoriteCompoundsAdapter
    private var isFormatting: Boolean = false

    private lateinit var sharedPreferences: SharedPreferences
    private val FAVORITES_KEY = "favorites"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        when {
            themePrefValue == 100 -> {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                    Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
                }
            }
            themePrefValue == 0 -> setTheme(R.style.AppTheme)
            themePrefValue == 1 -> setTheme(R.style.AppThemeDark)
        }

        setContentView(R.layout.activity_chemical_reactions)
        findViewById<FrameLayout>(R.id.view_cal).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn_cal).setOnClickListener {
            this.onBackPressed()
        }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("CalculatorPrefs", Context.MODE_PRIVATE)

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.calculator_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = resources.getDimension(R.dimen.zero_elevation)
        findViewById<ScrollView>(R.id.calculator_scroll).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.calculator_scroll).scrollY.toFloat()
                if (scrollY > 150f) {
                    findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.calculator_title).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.calculator_title_downstate).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.calculator_title).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.calculator_title_downstate).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }


        //Add value to most used:
        val mostUsedPreference = MostUsedToolPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "rec"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }


    }



    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_cal).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_cal).layoutParams = params

        val params2 = findViewById<TextView>(R.id.calculator_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.calculator_title_downstate).layoutParams = params2
    }
}