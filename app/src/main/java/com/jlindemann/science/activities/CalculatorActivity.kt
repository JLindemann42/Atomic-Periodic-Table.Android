package com.jlindemann.science.activities

import android.content.res.Configuration
import android.os.Bundle
import android.text.*
import android.text.style.SubscriptSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

class CalculatorActivity : BaseActivity() {

    private lateinit var includedElementsAdapter: IncludedElementsAdapter
    private var isFormatting: Boolean = false

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
        setContentView(R.layout.activity_calculator)
        findViewById<FrameLayout>(R.id.view_cal).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn_cal).setOnClickListener {
            this.onBackPressed()
        }

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.calculator_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.calculator_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 200f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.calculator_scroll).getScrollY() > 150f) {
                        findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.calculator_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.calculator_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_back_cal_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.calculator_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.calculator_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_cal).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.calculator_scroll).getScrollY().toFloat()
                }
            })

        inputController()

        val editText = findViewById<EditText>(R.id.edit_text_cal)
        editText.filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            val blockCharacterSet = "/-%¤#!@&£$€{}^¨~<>`´="
            for (i in start until end) {
                if (blockCharacterSet.contains(source[i])) {
                    return@InputFilter ""
                }
            }
            null
        })

        findViewById<ImageButton>(R.id.back_btn_cal).setOnClickListener {
            this.onBackPressed()
        }

        // Initialize RecyclerView
        includedElementsAdapter = IncludedElementsAdapter()
        findViewById<RecyclerView>(R.id.inc_weight_list).apply {
            layoutManager = LinearLayoutManager(this@CalculatorActivity)
            adapter = includedElementsAdapter
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
        val spannableString = SpannableString(editable.toString()) // Create a new SpannableString based on the editable text
        val elementRegex = Regex("([A-Z][a-z]*)(\\d*)")
        val groupRegex = Regex("(\\)|\\])(\\d+)")

        // Format element numbers
        elementRegex.findAll(editable).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val element = matchResult.groupValues[1]
            val number = matchResult.groupValues[2]

            if (number.isNotEmpty()) {
                val numberStart = start + element.length
                val numberEnd = end
                spannableString.setSpan(SubscriptSpan(), numberStart, numberEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Format group numbers
        groupRegex.findAll(editable).forEach { matchResult ->
            val start = matchResult.range.first + 1 // +1 to skip the closing bracket
            val end = matchResult.range.last + 1
            spannableString.setSpan(SubscriptSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        findViewById<EditText>(R.id.edit_text_cal).apply {
            removeTextChangedListener(textWatcher) // Temporarily remove TextWatcher
            setText(spannableString)
            setSelection(spannableString.length)
            addTextChangedListener(textWatcher) // Re-attach TextWatcher
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

        // Create a map to store element symbols and their corresponding atomic weights
        val elementWeights = mutableMapOf<String, Double>()
        val includedElements = mutableListOf<Quadruple<String, Double, Double, Double>>()

        for (element in elementList) {
            try {
                val elementJson = "${element.element}.json"
                val inputStream = assets.open(elementJson)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val jsonObject = jsonArray.getJSONObject(0)
                val weightVal = jsonObject.optString("element_atomicmass", "---").removeSuffix(" (u)")
                val shortEl = jsonObject.optString("short", "---")

                // Store the atomic weight of the element in the map
                elementWeights[shortEl] = weightVal.toDoubleOrNull() ?: 0.0

            } catch (e: IOException) {
                ToastUtil.showToast(this, "Error")
            }
        }

        // Function to compute the weight of a chemical formula part
        fun computeWeight(formula: String, multiplier: Double = 1.0): Double {
            val elementRegex = Regex("([A-Z][a-z]*)(\\d*)")
            var weight = 0.0
            elementRegex.findAll(formula).forEach { matchResult ->
                val element = matchResult.groupValues[1]
                val elementMultiplier = matchResult.groupValues[2].toDoubleOrNull() ?: 1.0
                val atomicWeight = elementWeights[element] ?: 0.0
                weight += (elementMultiplier * atomicWeight)
                includedElements.add(Quadruple(element, elementMultiplier * multiplier, atomicWeight, 0.0))  // Initialize percentage as 0.0
            }
            return weight * multiplier
        }

        // Main regex to handle both element groups and standalone elements
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

                // Handle groups within parentheses and square brackets
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

        // Calculate the percentage of each element
        includedElements.forEachIndexed { index, (element, quantity, atomicWeight, _) ->
            val percentage = (quantity * atomicWeight) / result * 100
            includedElements[index] = Quadruple(element, quantity, atomicWeight, percentage)
        }

        // Display the result in the TextView
        val resultText = result.toString()
        val unitText =" (g/mol)"
        findViewById<TextView>(R.id.out_text).text = "$resultText$unitText"

        // Update the RecyclerView with included elements
        includedElementsAdapter.updateElements(includedElements)
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