package com.jlindemann.science.activities.tools

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ThemePreference

class UnitConversionActivity : BaseActivity() {
    private val unitCategories: Map<String, List<UnitDefinition>> = mapOf(
        "Length" to listOf(
            UnitDefinition("Meter", 1.0), UnitDefinition("Kilometer", 1000.0), UnitDefinition("Centimeter", 0.01),
            UnitDefinition("Millimeter", 0.001), UnitDefinition("Inch", 0.0254), UnitDefinition("Foot", 0.3048),
            UnitDefinition("Yard", 0.9144), UnitDefinition("Mile", 1609.344)
        ),
        "Mass" to listOf(
            UnitDefinition("Kilogram", 1.0), UnitDefinition("Gram", 0.001), UnitDefinition("Milligram", 0.000001),
            UnitDefinition("Pound", 0.45359237), UnitDefinition("Ounce", 0.0283495231)
        ),
        "Volume" to listOf(
            UnitDefinition("Liter", 1.0), UnitDefinition("Milliliter", 0.001), UnitDefinition("Cubic meter", 1000.0),
            UnitDefinition("Gallon", 3.78541), UnitDefinition("Pint", 0.473176)
        ),
        "Area" to listOf(
            UnitDefinition("Square meter", 1.0), UnitDefinition("Square kilometer", 1_000_000.0),
            UnitDefinition("Square centimeter", 0.0001), UnitDefinition("Square mile", 2_589_988.11),
            UnitDefinition("Acre", 4046.85642)
        ),
        "Velocity" to listOf(
            UnitDefinition("Meter/second", 1.0), UnitDefinition("Kilometer/hour", 0.277778),
            UnitDefinition("Mile/hour", 0.44704), UnitDefinition("Foot/second", 0.3048)
        ),
        "Energy" to listOf(
            UnitDefinition("Joule", 1.0), UnitDefinition("Kilojoule", 1000.0), UnitDefinition("Calorie", 4.184),
            UnitDefinition("Kilocalorie", 4184.0), UnitDefinition("Watt hour", 3600.0)
        ),
        "Frequency" to listOf(
            UnitDefinition("Hertz", 1.0), UnitDefinition("Kilohertz", 1000.0),
            UnitDefinition("Megahertz", 1_000_000.0), UnitDefinition("Gigahertz", 1_000_000_000.0)
        ),
        "Temperature" to listOf(
            UnitDefinition("Celsius", 0.0), UnitDefinition("Fahrenheit", 0.0), UnitDefinition("Kelvin", 0.0)
        ),
        "Time" to listOf(
            UnitDefinition("Second", 1.0), UnitDefinition("Millisecond", 0.001),
            UnitDefinition("Minute", 60.0), UnitDefinition("Hour", 3600.0), UnitDefinition("Day", 86400.0)
        ),
        "Force" to listOf(
            UnitDefinition("Newton", 1.0), UnitDefinition("Kilonewton", 1000.0),
            UnitDefinition("Dyne", 0.00001), UnitDefinition("Pound-force", 4.4482216),
            UnitDefinition("Kilogram-force", 9.80665)
        ),
        "Power" to listOf(
            UnitDefinition("Watt", 1.0), UnitDefinition("Kilowatt", 1000.0),
            UnitDefinition("Megawatt", 1_000_000.0), UnitDefinition("Horsepower", 745.699872)
        ),
        "Voltage" to listOf(
            UnitDefinition("Volt", 1.0), UnitDefinition("Millivolt", 0.001), UnitDefinition("Kilovolt", 1000.0)
        ),
        "Resistance" to listOf(
            UnitDefinition("Ohm", 1.0), UnitDefinition("Milliohm", 0.001),
            UnitDefinition("Kiloohm", 1000.0), UnitDefinition("Megaohm", 1_000_000.0)
        ),
        "Pressure" to listOf(
            UnitDefinition("Pascal", 1.0), UnitDefinition("Kilopascal", 1000.0),
            UnitDefinition("Bar", 100_000.0), UnitDefinition("Atmosphere", 101_325.0),
            UnitDefinition("PSI", 6894.757)
        )
    )

    private lateinit var categorySpinner: Spinner
    private lateinit var fromUnitSpinner: Spinner
    private lateinit var toUnitSpinner: Spinner
    private lateinit var inputValue: EditText
    private lateinit var outputValue: EditText
    private lateinit var formulaValue: TextView
    private lateinit var addFavoriteButton: Button
    private lateinit var favoritesList: RecyclerView

    private val favorites = mutableListOf<UnitConversionFavorite>()
    private lateinit var favoritesAdapter: FavoriteRecyclerAdapter
    private val favoritesKey = "unit_favorites"

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
        setContentView(R.layout.activity_unit_converter)
        findViewById<FrameLayout>(R.id.view_unit).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<FrameLayout>(R.id.common_title_back_unit_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.unit_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_unit).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.unit_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.unit_scroll).scrollY > 150f) {
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
                }
            })

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }

        //Add value to most used:
        val mostUsedPreference = MostUsedToolPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "uni"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        //Setting up views from activity
        categorySpinner = findViewById(R.id.category_spinner)
        fromUnitSpinner = findViewById(R.id.from_unit_spinner)
        toUnitSpinner = findViewById(R.id.to_unit_spinner)
        inputValue = findViewById(R.id.input_value)
        outputValue = findViewById(R.id.output_value)
        formulaValue = findViewById(R.id.formula_value)
        addFavoriteButton = findViewById(R.id.add_favorite_button)
        favoritesList = findViewById(R.id.favorites_list)

        // Disable input in outputValue (plan is to later let user input here as well)
        outputValue.isFocusable = false
        outputValue.isFocusableInTouchMode = false
        outputValue.isClickable = false
        outputValue.isLongClickable = false
        outputValue.isCursorVisible = false
        outputValue.keyListener = null

        setupSpinners()
        setupListeners()
        setupRecyclerView()
        loadFavorites()
        updateFavoriteButtonState()
    }

    private fun setupSpinners() {
        val categories = unitCategories.keys.toList()
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                val units = unitCategories[selectedCategory]?.map { it.name } ?: listOf()
                val unitAdapter = ArrayAdapter(this@UnitConversionActivity, android.R.layout.simple_spinner_item, units)
                unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                fromUnitSpinner.adapter = unitAdapter
                toUnitSpinner.adapter = unitAdapter

                fromUnitSpinner.setSelection(0)
                toUnitSpinner.setSelection(if (units.size > 1) 1 else 0)
                convertUnits()
                updateFavoriteButtonState()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupListeners() {
        inputValue.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                convertUnits()
                updateFavoriteButtonState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fromUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                convertUnits()
                updateFavoriteButtonState()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        toUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                convertUnits()
                updateFavoriteButtonState()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        addFavoriteButton.setOnClickListener {
            addFavorite()
        }
    }

    private fun setupRecyclerView() {
        favoritesAdapter = FavoriteRecyclerAdapter(favorites,
            onItemClick = { fav ->
                inputValue.setText(fav.inputValue.toString())
                val catIdx = (categorySpinner.adapter as ArrayAdapter<String>).getPosition(fav.category)
                categorySpinner.setSelection(catIdx)
                val units = unitCategories[fav.category]?.map { it.name } ?: listOf()
                val fromIdx = units.indexOf(fav.fromUnit)
                val toIdx = units.indexOf(fav.toUnit)
                fromUnitSpinner.setSelection(fromIdx)
                toUnitSpinner.setSelection(toIdx)
            },
            onItemLongClick = { position ->
                removeFavorite(position)
            }
        )
        favoritesList.layoutManager = LinearLayoutManager(this)
        favoritesList.adapter = favoritesAdapter
    }

    private fun convertUnits() {
        val category = categorySpinner.selectedItem as? String ?: return
        val fromUnit = fromUnitSpinner.selectedItem as? String ?: return
        val toUnit = toUnitSpinner.selectedItem as? String ?: return
        val value = inputValue.text.toString().toDoubleOrNull() ?: run {
            outputValue.setText("")
            formulaValue.text = ""
            return
        }
        var result = 0.0
        var formula = ""
        if (category == "Temperature") {
            val conversion = convertTemperature(fromUnit, toUnit, value)
            result = conversion.first
            formula = conversion.second
        } else {
            val fromDef = unitCategories[category]?.find { it.name == fromUnit }
            val toDef = unitCategories[category]?.find { it.name == toUnit }
            if (fromDef != null && toDef != null) {
                result = value * fromDef.factor / toDef.factor
                formula = "Divide ${category.lowercase()}-value with: ${fromDef.factor / toDef.factor}"
            }
        }
        outputValue.setText(result.toString())
        formulaValue.text = formula
    }

    private fun convertTemperature(from: String, to: String, value: Double): Pair<Double, String> {
        val celsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5 / 9
            "Kelvin" -> value - 273.15
            else -> value
        }
        val result = when (to) {
            "Celsius" -> celsius
            "Fahrenheit" -> celsius * 9 / 5 + 32
            "Kelvin" -> celsius + 273.15
            else -> celsius
        }
        val formula = when {
            from == "Celsius" && to == "Fahrenheit" -> "Multiply with 9/5 and add 32"
            from == "Celsius" && to == "Kelvin" -> "Add 273.15"
            from == "Fahrenheit" && to == "Celsius" -> "Subtract 32, divide with 5/9"
            from == "Fahrenheit" && to == "Kelvin" -> "Subtract 32, multiply with 5/9, add 273.15"
            from == "Kelvin" && to == "Celsius" -> "subtract 273.15"
            from == "Kelvin" && to == "Fahrenheit" -> "subtract 273.15, multiply with 9/5, add 32"
            else -> "no conversion"
        }
        return Pair(result, formula)
    }

    private fun addFavorite() {
        val category = categorySpinner.selectedItem as? String ?: return
        val fromUnit = fromUnitSpinner.selectedItem as? String ?: return
        val toUnit = toUnitSpinner.selectedItem as? String ?: return
        val value = inputValue.text.toString().toDoubleOrNull() ?: return
        val convertedValue = outputValue.text.toString().toDoubleOrNull() ?: return
        val favorite = UnitConversionFavorite(category, fromUnit, toUnit, value, convertedValue)
        if (favorites.any {
                it.category == category && it.fromUnit == fromUnit &&
                        it.toUnit == toUnit && it.inputValue == value
            }) return
        favorites.add(favorite)
        saveFavorites()
        updateFavoritesList()
        updateFavoriteButtonState()
    }

    private fun removeFavorite(position: Int) {
        if (position < 0 || position >= favorites.size) return
        favorites.removeAt(position)
        saveFavorites()
        updateFavoritesList()
        updateFavoriteButtonState()
    }

    private fun updateFavoritesList() {
        favoritesAdapter.notifyDataSetChanged()
    }

    private fun loadFavorites() {
        val prefs = getSharedPreferences("unit_converter", Context.MODE_PRIVATE)
        val json = prefs.getString(favoritesKey, null)
        if (json != null) {
            val type = object : TypeToken<List<UnitConversionFavorite>>() {}.type
            val loaded = Gson().fromJson<List<UnitConversionFavorite>>(json, type)
            favorites.clear()
            favorites.addAll(loaded)
            updateFavoritesList()
        }
    }

    private fun saveFavorites() {
        val prefs = getSharedPreferences("unit_converter", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(favorites)
        editor.putString(favoritesKey, json)
        editor.apply()
    }

    private fun isCurrentConversionFavorite(): Boolean {
        val category = categorySpinner.selectedItem as? String ?: return false
        val fromUnit = fromUnitSpinner.selectedItem as? String ?: return false
        val toUnit = toUnitSpinner.selectedItem as? String ?: return false
        val value = inputValue.text.toString().toDoubleOrNull() ?: return false
        return favorites.any {
            it.category == category && it.fromUnit == fromUnit &&
                    it.toUnit == toUnit && it.inputValue == value
        }
    }

    private fun updateFavoriteButtonState() {
        addFavoriteButton.isEnabled = !isCurrentConversionFavorite()
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_unit).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_unit).layoutParams = params

        val params2 = findViewById<TextView>(R.id.unit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.unit_title_downstate).layoutParams = params2
    }
}

// RecyclerView Adapter for favorites
class FavoriteRecyclerAdapter(
    private val favorites: List<UnitConversionFavorite>,
    private val onItemClick: (UnitConversionFavorite) -> Unit,
    private val onItemLongClick: (Int) -> Unit
) : RecyclerView.Adapter<FavoriteRecyclerAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
        init {
            view.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(favorites[adapterPosition])
                }
            }
            view.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemLongClick(adapterPosition)
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun getItemCount(): Int = favorites.size

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val fav = favorites[position]
        holder.textView.text = "${fav.inputValue} ${fav.fromUnit} → ${fav.convertedValue} ${fav.toUnit} (${fav.category})"
    }
}