package com.jlindemann.science.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.*
import android.text.style.SubscriptSpan
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tables.PoissonActivity
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import org.json.JSONArray
import java.io.IOException

class CalculatorActivity : BaseActivity() {

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

        setContentView(R.layout.activity_calculator)
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

        inputController()

        val editText = findViewById<EditText>(R.id.edit_text_cal)
        editText.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            val blockCharacterSet = "/-%¤#!@&£$€{}^¨~<>`´="
            if (blockCharacterSet.any { it in source }) "" else null
        })

        findViewById<ImageButton>(R.id.back_btn_cal).setOnClickListener { this.onBackPressed() }

        //Show hint text at start:
        findViewById<TextView>(R.id.hint_calc_text).visibility = View.VISIBLE

        // Check if favorite list should be shown or not (PRO)
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        if (proPrefValue == 1) {
            findViewById<RecyclerView>(R.id.fav_rec_list).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.no_pro_text).visibility = View.VISIBLE
            findViewById<TextView>(R.id.pro_button_cal).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.fav_star_btn).visibility = View.GONE
        }
        if (proPrefValue == 100) {
            findViewById<RecyclerView>(R.id.fav_rec_list).visibility = View.VISIBLE
            findViewById<TextView>(R.id.no_pro_text).visibility = View.GONE
            findViewById<TextView>(R.id.pro_button_cal).visibility = View.GONE
            findViewById<ImageButton>(R.id.fav_star_btn).visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.pro_button_cal).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }

        // Initialize RecyclerView for included elements
        includedElementsAdapter = IncludedElementsAdapter()
        findViewById<RecyclerView>(R.id.inc_weight_list).apply {
            layoutManager = LinearLayoutManager(this@CalculatorActivity)
            adapter = includedElementsAdapter
        }

        // Initialize RecyclerView for favorite compounds
        favoriteCompoundsAdapter = FavoriteCompoundsAdapter({ compound -> removeFavorite(compound) }, { compound -> copyToClipboard(compound) })
        findViewById<RecyclerView>(R.id.fav_rec_list).apply {
            layoutManager = LinearLayoutManager(this@CalculatorActivity)
            adapter = favoriteCompoundsAdapter
        }

        // Load saved favorite compounds
        loadFavorites()

        // Set up favorite button
        findViewById<ImageButton>(R.id.fav_star_btn).setOnClickListener {
            val compound = findViewById<EditText>(R.id.edit_text_cal).text.toString()
            val molarWeight = findViewById<TextView>(R.id.out_text).text.toString()
            if (compound.isNotEmpty() && molarWeight.isNotEmpty()) {
                saveFavorite(compound, molarWeight)
                loadFavorites()
            } else {
                ToastUtil.showToast(this, "Please enter a valid compound and calculate its molar weight first.")
            }
        }
    }

    private fun inputController() {
        findViewById<EditText>(R.id.edit_text_cal).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (!isFormatting) {
                    formatChemicalFormula(s)
                    collectJsonData(s.toString())
                }
            }
        })
    }

    private fun formatChemicalFormula(editable: Editable) {
        isFormatting = true
        val spannableString = SpannableString(editable.toString())
        val elementRegex = Regex("([A-Z][a-z]*)(\\d*)")
        val groupRegex = Regex("(\\)|\\])(\\d+)")

        elementRegex.findAll(editable).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val element = matchResult.groupValues[1]
            val number = matchResult.groupValues[2]

            if (number.isNotEmpty()) {
                spannableString.setSpan(SubscriptSpan(), start + element.length, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        groupRegex.findAll(editable).forEach { matchResult ->
            spannableString.setSpan(SubscriptSpan(), matchResult.range.first + 1, matchResult.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        findViewById<EditText>(R.id.edit_text_cal).apply {
            removeTextChangedListener(textWatcher)
            setText(spannableString)
            setSelection(spannableString.length)
            addTextChangedListener(textWatcher)
        }
        isFormatting = false
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            if (!isFormatting) {
                formatChemicalFormula(s)
                collectJsonData(s.toString())
            }
        }
    }

    private fun collectJsonData(input: String?) {
        if (input.isNullOrEmpty()) return

        var result = 0.0
        val elementList = ArrayList<Element>()
        ElementModel.getList(elementList)

        val elementWeights = mutableMapOf<String, Double>()
        val elementNames = mutableMapOf<String, String>()
        val includedElements = mutableListOf<Quadruple<String, Double, Double, Double, String>>()

        for (element in elementList) {
            try {
                val elementJson = "${element.element}.json"
                val inputStream = assets.open(elementJson)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val jsonObject = jsonArray.getJSONObject(0)
                val weightVal = jsonObject.optString("element_atomicmass", "---").removeSuffix(" (u)")
                val shortEl = jsonObject.optString("short", "---")
                val fullName = jsonObject.optString("element", "---")

                elementWeights[shortEl] = weightVal.toDoubleOrNull() ?: 0.0
                elementNames[shortEl] = fullName

            } catch (e: IOException) {
                ToastUtil.showToast(this, "Error")
            }
        }

        fun computeWeight(formula: String, multiplier: Double = 1.0): Double {
            val elementRegex = Regex("([A-Z][a-z]*)(\\d*)")
            var weight = 0.0
            elementRegex.findAll(formula).forEach { matchResult ->
                val element = matchResult.groupValues[1]
                val elementMultiplier = matchResult.groupValues[2].toDoubleOrNull() ?: 1.0
                val atomicWeight = elementWeights[element] ?: 0.0
                val fullName = elementNames[element] ?: "Unknown"
                weight += (elementMultiplier * atomicWeight)
                includedElements.add(Quadruple(element, elementMultiplier * multiplier, atomicWeight, 0.0, fullName))
            }
            return weight * multiplier
        }

        val mainRegex = Regex("(\\d+)?([A-Z][a-z]*\\d*|\\([A-Za-z0-9]*\\)\\d*|\\[[A-Za-z0-9]*\\]\\d*)")
        val parts = input.split(" ")

        for (part in parts) {
            val matches = mainRegex.findAll(part)
            var prefixMultiplier = 1.0

            matches.forEachIndexed { index, matchResult ->
                if (index == 0) {
                    prefixMultiplier = matchResult.groupValues[1].toDoubleOrNull() ?: 1.0
                }
                val elementGroup = matchResult.groupValues[2]

                val groupRegex = Regex("\\(([A-Za-z0-9]*)\\)(\\d*)|\\[([A-Za-z0-9]*)\\](\\d*)")
                val groupMatch = groupRegex.matchEntire(elementGroup)

                if (groupMatch != null) {
                    val groupFormula = groupMatch.groupValues[1].ifEmpty { groupMatch.groupValues[3] }
                    val groupMultiplier = groupMatch.groupValues[2].toDoubleOrNull() ?: groupMatch.groupValues[4].toDoubleOrNull() ?: 1.0
                    result += computeWeight(groupFormula, prefixMultiplier * groupMultiplier)
                } else {
                    result += computeWeight(elementGroup, prefixMultiplier)
                }
            }
        }

        includedElements.forEachIndexed { index, (element, quantity, atomicWeight, _, fullName) ->
            val percentage = (quantity * atomicWeight) / result * 100
            includedElements[index] = Quadruple(element, quantity, atomicWeight, percentage, fullName)
        }

        val resultText = result.toString()
        val unitText = " (g/mol)"
        val outText = findViewById<TextView>(R.id.out_text)
        outText.text = "$resultText$unitText"
        if (resultText == "0.0") {
            findViewById<TextView>(R.id.hint_calc_text).visibility = View.VISIBLE
            findViewById<RecyclerView>(R.id.inc_weight_list).visibility = View.GONE
        }
        else {
            findViewById<TextView>(R.id.hint_calc_text).visibility = View.GONE
            findViewById<RecyclerView>(R.id.inc_weight_list).visibility = View.VISIBLE
        }

        includedElementsAdapter.updateElements(includedElements)
    }

    private fun saveFavorite(compound: String, molarWeight: String) {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        favorites.add("$compound:$molarWeight")
        sharedPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
    }

    private fun removeFavorite(compound: String) {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        favorites.removeIf { it.startsWith(compound) }
        sharedPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
        loadFavorites()
    }

    private fun loadFavorites() {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableList() ?: mutableListOf()
        val favoriteCompounds = favorites.map {
            val parts = it.split(":")
            FavoriteCompound(parts[0], parts[1])
        }
        favoriteCompoundsAdapter.updateCompounds(favoriteCompounds)
    }

    private fun copyToClipboard(compound: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Compound Formula", compound)
        clipboard.setPrimaryClip(clip)
        ToastUtil.showToast(this, "Copied to clipboard")
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