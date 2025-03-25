package com.jlindemann.science.activities.tools

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
import com.jlindemann.science.activities.IncludedElementsAdapter
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import org.json.JSONArray
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ChemicalReactionsActivity : BaseActivity() {

    private lateinit var includedElementsAdapter: IncludedElementsAdapter
    private var isFormatting: Boolean = false

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
        findViewById<FrameLayout>(R.id.view_rec).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn_rec).setOnClickListener {
            this.onBackPressed()
        }

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_rec_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.reaction_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_rec).elevation = resources.getDimension(R.dimen.zero_elevation)
        findViewById<ScrollView>(R.id.reaction_scroll).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.reaction_scroll).scrollY.toFloat()
                if (scrollY > 150f) {
                    findViewById<FrameLayout>(R.id.common_title_back_rec_color).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.reaction_title).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.reaction_title_downstate).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_rec).elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    findViewById<FrameLayout>(R.id.common_title_back_rec_color).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.reaction_title).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.reaction_title_downstate).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_rec).elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }

        // Add value to most used
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

        // Setup input Controller & format
        inputController()

        // Initialize Included Elements Adapter
        includedElementsAdapter = IncludedElementsAdapter()
        findViewById<RecyclerView>(R.id.inc_comp_list).apply {
            layoutManager = LinearLayoutManager(this@ChemicalReactionsActivity)
            adapter = includedElementsAdapter
        }

        // Setup output TextView
        setupOutputTextView()
    }

    private fun inputController() {
        findViewById<EditText>(R.id.edit_text_rec).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (!isFormatting) {
                    formatChemicalReaction(s)
                    balanceChemicalReaction(s.toString())
                }
            }
        })
    }

    private fun formatChemicalReaction(editable: Editable) {
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

        findViewById<EditText>(R.id.edit_text_rec).apply {
            removeTextChangedListener(textWatcher)
            setText(spannableString)
            setSelection(spannableString.length)
            addTextChangedListener(textWatcher)
        }
        isFormatting = false
    }

    private fun balanceChemicalReaction(reaction: String) {
        val balancedReaction = balanceReaction(reaction)
        findViewById<TextView>(R.id.out_text).text = balancedReaction
    }

    private fun balanceReaction(reaction: String): String {
        // Parse the reaction into reactants and products
        val sides = reaction.split("->")
        if (sides.size != 2) return reaction // Invalid reaction format

        val reactants = sides[0].trim().split("+").map { it.trim() }
        val products = sides[1].trim().split("+").map { it.trim() }

        // Get a list of all unique elements in the reaction
        val elements = mutableSetOf<String>()
        val elementRegex = Regex("([A-Z][a-z]*)")

        reactants.forEach { compound ->
            elementRegex.findAll(compound).forEach { matchResult ->
                elements.add(matchResult.value)
            }
        }

        products.forEach { compound ->
            elementRegex.findAll(compound).forEach { matchResult ->
                elements.add(matchResult.value)
            }
        }

        val elementList = elements.toList()

        // Create the matrix for the system of linear equations
        val matrix = Array(elementList.size) { IntArray(reactants.size + products.size) }

        fun parseCompound(compound: String, side: Int) {
            val elementCountRegex = Regex("([A-Z][a-z]*)(\\d*)")
            elementCountRegex.findAll(compound).forEach { matchResult ->
                val element = matchResult.groupValues[1]
                val count = matchResult.groupValues[2].toIntOrNull() ?: 1
                val elementIndex = elementList.indexOf(element)
                matrix[elementIndex][side] += count
            }
        }

        reactants.forEachIndexed { index, compound ->
            parseCompound(compound, index)
        }

        products.forEachIndexed { index, compound ->
            parseCompound(compound, reactants.size + index)
        }

        // Solve the system of linear equations using Gaussian elimination
        val coefficients = solveMatrix(matrix)

        // Format the balanced equation
        val balancedReactants = reactants.mapIndexed { index, compound ->
            "${coefficients[index]}$compound"
        }.joinToString(" + ")

        val balancedProducts = products.mapIndexed { index, compound ->
            "${coefficients[reactants.size + index]}$compound"
        }.joinToString(" + ")

        return "$balancedReactants -> $balancedProducts"
    }

    private fun solveMatrix(matrix: Array<IntArray>): IntArray {
        val numEquations = matrix.size
        val numVariables = matrix[0].size

        // Create an augmented matrix with an additional column for the constants
        val augmentedMatrix = Array(numEquations) { DoubleArray(numVariables + 1) }
        for (i in 0 until numEquations) {
            for (j in 0 until numVariables) {
                augmentedMatrix[i][j] = matrix[i][j].toDouble()
            }
            augmentedMatrix[i][numVariables] = 0.0
        }

        // Perform Gaussian elimination
        for (i in 0 until numEquations) {
            // Make the diagonal element 1
            val divisor = augmentedMatrix[i][i]
            for (j in 0..numVariables) {
                augmentedMatrix[i][j] /= divisor
            }

            // Eliminate the current variable from all other rows
            for (k in 0 until numEquations) {
                if (k != i) {
                    val factor = augmentedMatrix[k][i]
                    for (j in 0..numVariables) {
                        augmentedMatrix[k][j] -= factor * augmentedMatrix[i][j]
                    }
                }
            }
        }

        // Extract the solution
        val solution = IntArray(numVariables)
        for (i in 0 until numVariables) {
            solution[i] = augmentedMatrix[i][numVariables].toInt()
        }

        return solution
    }

    private fun setupOutputTextView() {
        val outputTextView: TextView = findViewById(R.id.out_text)
        outputTextView.text = ""
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            if (!isFormatting) {
                formatChemicalReaction(s)
                balanceChemicalReaction(s.toString())
            }
        }
    }

    private fun collectJsonData(input: String?) {
        if (input.isNullOrEmpty()) return

        val elementList = ArrayList<Element>()
        ElementModel.getList(elementList)

        val elementWeights = mutableMapOf<String, Double>()
        val elementNames = mutableMapOf<String, String>()

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
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_rec).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_rec).layoutParams = params

        val params2 = findViewById<TextView>(R.id.reaction_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.reaction_title_downstate).layoutParams = params2
    }
}