package com.jlindemann.science.activities.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil

class IdealGasCalculatorActivity : BaseActivity() {

    private lateinit var favoriteCalculationsAdapter: FavoriteIdealGasAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val FAVORITES_KEY = "ideal_gas_favorites"
    
    // Gas constant R in different units
    private val R_ATM = 0.08206 // L·atm/(mol·K)
    private val R_JOULE = 8.314 // J/(mol·K)
    
    // Input fields
    private lateinit var pressureInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var volumeInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var molesInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var temperatureInput: com.google.android.material.textfield.TextInputEditText
    
    // Spinners for units
    private lateinit var pressureUnitSpinner: Spinner
    private lateinit var volumeUnitSpinner: Spinner
    private lateinit var temperatureUnitSpinner: Spinner
    
    // Output text
    private lateinit var outputText: TextView
    
    // Selected units
    private var pressureUnit = "atm"
    private var volumeUnit = "L"
    private var temperatureUnit = "K"
    
    // Which field to calculate
    private var calculateField = "pressure" // pressure, volume, moles, or temperature
    
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

        setContentView(R.layout.activity_ideal_gas_calculator)
        findViewById<FrameLayout>(R.id.view_ideal_gas).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn_ideal_gas).setOnClickListener {
            this.onBackPressed()
        }

        updateStats()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("IdealGasCalculatorPrefs", Context.MODE_PRIVATE)

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_back_ideal_gas_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.ideal_gas_calculator_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_ideal_gas).elevation = resources.getDimension(R.dimen.zero_elevation)
        findViewById<ScrollView>(R.id.ideal_gas_calculator_scroll).viewTreeObserver
            .addOnScrollChangedListener {
                val scrollY = findViewById<ScrollView>(R.id.ideal_gas_calculator_scroll).scrollY.toFloat()
                if (scrollY > 150f) {
                    findViewById<FrameLayout>(R.id.common_title_back_ideal_gas_color).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.ideal_gas_calculator_title).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.ideal_gas_calculator_title_downstate).visibility = View.INVISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_ideal_gas).elevation = resources.getDimension(R.dimen.one_elevation)
                } else {
                    findViewById<FrameLayout>(R.id.common_title_back_ideal_gas_color).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.ideal_gas_calculator_title).visibility = View.INVISIBLE
                    findViewById<TextView>(R.id.ideal_gas_calculator_title_downstate).visibility = View.VISIBLE
                    findViewById<FrameLayout>(R.id.common_title_back_ideal_gas).elevation = resources.getDimension(R.dimen.zero_elevation)
                }
            }

        initializeViews()
        setupInputListeners()

        //Add value to most used:
        val mostUsedPreference = MostUsedToolPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "gas"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        } else {
            // Add new entry if it doesn't exist
            val newValue = "$mostUsedPrefValue gas=1.0"
            mostUsedPreference.setValue(newValue)
        }

        findViewById<ImageButton>(R.id.back_btn_ideal_gas).setOnClickListener { this.onBackPressed() }

        // Check if favorite list should be shown or not (PRO or PRO+)
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        val proPlusPref = ProPlusVersion(this)
        val proPlusPrefValue = proPlusPref.getValue()
        
        // Show favorites if user has either PRO or PRO+
        val hasProAccess = proPrefValue == 100 || proPlusPrefValue == 100
        
        if (!hasProAccess) {
            findViewById<RecyclerView>(R.id.fav_rec_list_ideal_gas).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.no_pro_text_ideal_gas).visibility = View.VISIBLE
            findViewById<View>(R.id.pro_button_ideal_gas).visibility = View.VISIBLE
            findViewById<View>(R.id.fav_star_btn_ideal_gas).visibility = View.GONE
        } else {
            findViewById<RecyclerView>(R.id.fav_rec_list_ideal_gas).visibility = View.VISIBLE
            findViewById<TextView>(R.id.no_pro_text_ideal_gas).visibility = View.GONE
            findViewById<View>(R.id.pro_button_ideal_gas).visibility = View.GONE
            findViewById<View>(R.id.fav_star_btn_ideal_gas).visibility = View.VISIBLE
        }

        findViewById<View>(R.id.pro_button_ideal_gas).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }

        // Initialize RecyclerView for favorite calculations
        favoriteCalculationsAdapter = FavoriteIdealGasAdapter({ calculation -> removeFavorite(calculation) }, { calculation -> copyToClipboard(calculation) })
        findViewById<RecyclerView>(R.id.fav_rec_list_ideal_gas).apply {
            layoutManager = LinearLayoutManager(this@IdealGasCalculatorActivity)
            adapter = favoriteCalculationsAdapter
        }

        // Load saved favorite calculations
        loadFavorites()

        // Set up favorite button
        findViewById<View>(R.id.fav_star_btn_ideal_gas).setOnClickListener {
            val result = findViewById<TextView>(R.id.out_text_ideal_gas).text.toString()
            if (result.isNotEmpty() && !result.contains("---")) {
                saveFavorite(getCurrentCalculationString(), result)
                loadFavorites()
            } else {
                ToastUtil.showToast(this, "Please perform a valid calculation first.")
            }
        }
    }

    private fun initializeViews() {
        pressureInput = findViewById(R.id.pressure_input)
        volumeInput = findViewById(R.id.volume_input)
        molesInput = findViewById(R.id.moles_input)
        temperatureInput = findViewById(R.id.temperature_input)
        
        pressureUnitSpinner = findViewById(R.id.pressure_unit_spinner)
        volumeUnitSpinner = findViewById(R.id.volume_unit_spinner)
        temperatureUnitSpinner = findViewById(R.id.temperature_unit_spinner)
        
        outputText = findViewById(R.id.out_text_ideal_gas)
        
        // Setup spinners
        setupSpinner(pressureUnitSpinner, arrayOf("atm", "Pa", "kPa", "bar", "mmHg")) { unit ->
            pressureUnit = unit
            calculateResult()
        }
        
        setupSpinner(volumeUnitSpinner, arrayOf("L", "mL", "m³", "cm³")) { unit ->
            volumeUnit = unit
            calculateResult()
        }
        
        setupSpinner(temperatureUnitSpinner, arrayOf("K", "°C", "°F")) { unit ->
            temperatureUnit = unit
            calculateResult()
        }
        
        // Setup calculate field selector
        val calculateFieldSpinner: Spinner = findViewById(R.id.calculate_field_spinner)
        setupSpinner(calculateFieldSpinner, arrayOf("Pressure", "Volume", "Moles", "Temperature")) { field ->
            calculateField = field.lowercase()
            updateInputFieldsState()
            calculateResult()
        }
    }
    
    private fun setupSpinner(spinner: Spinner, items: Array<String>, onItemSelected: (String) -> Unit) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onItemSelected(items[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupInputListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateResult()
            }
        }
        
        pressureInput.addTextChangedListener(textWatcher)
        volumeInput.addTextChangedListener(textWatcher)
        molesInput.addTextChangedListener(textWatcher)
        temperatureInput.addTextChangedListener(textWatcher)
    }
    
    private fun updateInputFieldsState() {
        // Enable all fields first
        pressureInput.isEnabled = true
        volumeInput.isEnabled = true
        molesInput.isEnabled = true
        temperatureInput.isEnabled = true
        pressureInput.hint = getString(R.string.ideal_gas_pressure_hint)
        volumeInput.hint = getString(R.string.ideal_gas_volume_hint)
        molesInput.hint = getString(R.string.ideal_gas_moles_hint)
        temperatureInput.hint = getString(R.string.ideal_gas_temperature_hint)
        pressureInput.alpha = 1.0f
        volumeInput.alpha = 1.0f
        molesInput.alpha = 1.0f
        temperatureInput.alpha = 1.0f
        
        // Disable the field we're calculating
        when (calculateField) {
            "pressure" -> {
                pressureInput.isEnabled = false
                pressureInput.setText("")
                pressureInput.hint = getString(R.string.ideal_calculated)
                pressureInput.alpha =0.7f
            }
            "volume" -> {
                volumeInput.isEnabled = false
                volumeInput.setText("")
                volumeInput.hint = getString(R.string.ideal_calculated)
                volumeInput.alpha =0.7f
            }
            "moles" -> {
                molesInput.isEnabled = false
                molesInput.setText("")
                molesInput.hint = getString(R.string.ideal_calculated)
                molesInput.alpha =0.7f
            }
            "temperature" -> {
                temperatureInput.isEnabled = false
                temperatureInput.setText("")
                temperatureInput.hint = getString(R.string.ideal_calculated)
                temperatureInput.alpha =0.7f
            }
        }
    }
    
    private fun calculateResult() {
        try {
            // Get values and convert to standard units (atm, L, K)
            val P = if (calculateField != "pressure") convertPressureToAtm(pressureInput.text.toString().toDoubleOrNull()) else null
            val V = if (calculateField != "volume") convertVolumeToL(volumeInput.text.toString().toDoubleOrNull()) else null
            val n = if (calculateField != "moles") molesInput.text.toString().toDoubleOrNull() else null
            val T = if (calculateField != "temperature") convertTemperatureToK(temperatureInput.text.toString().toDoubleOrNull()) else null
            
            // Calculate the missing value using PV = nRT
            val result = when (calculateField) {
                "pressure" -> {
                    if (n != null && T != null && V != null && V > 0) {
                        val pressure = (n * R_ATM * T) / V
                        convertPressureFromAtm(pressure)
                    } else null
                }
                "volume" -> {
                    if (n != null && T != null && P != null && P > 0) {
                        val volume = (n * R_ATM * T) / P
                        convertVolumeFromL(volume)
                    } else null
                }
                "moles" -> {
                    if (P != null && V != null && T != null && T > 0) {
                        (P * V) / (R_ATM * T)
                    } else null
                }
                "temperature" -> {
                    if (P != null && V != null && n != null && n > 0) {
                        val temp = (P * V) / (R_ATM * n)
                        convertTemperatureFromK(temp)
                    } else null
                }
                else -> null
            }
            
            if (result != null && result.isFinite() && !result.isNaN()) {
                val unitText = when (calculateField) {
                    "pressure" -> " $pressureUnit"
                    "volume" -> " $volumeUnit"
                    "moles" -> " mol"
                    "temperature" -> " $temperatureUnit"
                    else -> ""
                }
                outputText.text = String.format("%.4f", result) + unitText
            } else {
                outputText.text = "--- " + when (calculateField) {
                    "pressure" -> pressureUnit
                    "volume" -> volumeUnit
                    "moles" -> "mol"
                    "temperature" -> temperatureUnit
                    else -> ""
                }
            }
        } catch (e: Exception) {
            outputText.text = "--- "
        }
    }
    
    // Conversion functions
    private fun convertPressureToAtm(value: Double?): Double? {
        if (value == null) return null
        return when (pressureUnit) {
            "atm" -> value
            "Pa" -> value / 101325.0
            "kPa" -> value / 101.325
            "bar" -> value * 0.986923
            "mmHg" -> value / 760.0
            else -> value
        }
    }
    
    private fun convertPressureFromAtm(value: Double): Double {
        return when (pressureUnit) {
            "atm" -> value
            "Pa" -> value * 101325.0
            "kPa" -> value * 101.325
            "bar" -> value / 0.986923
            "mmHg" -> value * 760.0
            else -> value
        }
    }
    
    private fun convertVolumeToL(value: Double?): Double? {
        if (value == null) return null
        return when (volumeUnit) {
            "L" -> value
            "mL" -> value / 1000.0
            "m³" -> value * 1000.0
            "cm³" -> value / 1000.0
            else -> value
        }
    }
    
    private fun convertVolumeFromL(value: Double): Double {
        return when (volumeUnit) {
            "L" -> value
            "mL" -> value * 1000.0
            "m³" -> value / 1000.0
            "cm³" -> value * 1000.0
            else -> value
        }
    }
    
    private fun convertTemperatureToK(value: Double?): Double? {
        if (value == null) return null
        return when (temperatureUnit) {
            "K" -> value
            "°C" -> value + 273.15
            "°F" -> (value - 32.0) * 5.0 / 9.0 + 273.15
            else -> value
        }
    }
    
    private fun convertTemperatureFromK(value: Double): Double {
        return when (temperatureUnit) {
            "K" -> value
            "°C" -> value - 273.15
            "°F" -> (value - 273.15) * 9.0 / 5.0 + 32.0
            else -> value
        }
    }
    
    private fun getCurrentCalculationString(): String {
        val inputs = mutableListOf<String>()
        if (calculateField != "pressure") inputs.add("P=${pressureInput.text} $pressureUnit")
        if (calculateField != "volume") inputs.add("V=${volumeInput.text} $volumeUnit")
        if (calculateField != "moles") inputs.add("n=${molesInput.text} mol")
        if (calculateField != "temperature") inputs.add("T=${temperatureInput.text} $temperatureUnit")
        return inputs.joinToString(", ")
    }

    private fun saveFavorite(calculation: String, result: String) {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        favorites.add("$calculation:$result")
        sharedPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
    }

    private fun removeFavorite(calculation: String) {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        favorites.removeIf { it.startsWith(calculation) }
        sharedPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
        loadFavorites()
    }

    private fun loadFavorites() {
        val favorites = sharedPreferences.getStringSet(FAVORITES_KEY, mutableSetOf())?.toMutableList() ?: mutableListOf()
        val favoriteCalculations = favorites.map {
            val parts = it.split(":")
            if (parts.size >= 2) {
                FavoriteIdealGasCalculation(parts[0], parts[1])
            } else {
                FavoriteIdealGasCalculation(it, "")
            }
        }
        favoriteCalculationsAdapter.updateCalculations(favoriteCalculations)
    }

    private fun copyToClipboard(calculation: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Ideal Gas Calculation", calculation)
        clipboard.setPrimaryClip(clip)
        ToastUtil.showToast(this, "Copied to clipboard")
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_ideal_gas).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_ideal_gas).layoutParams = params

        val params2 = findViewById<TextView>(R.id.ideal_gas_calculator_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.ideal_gas_calculator_title_downstate).layoutParams = params2
    }

    private fun updateStats() {
        val statistics = java.util.ArrayList<Statistics>()
        StatisticsModel.getList(this, statistics)
        val stat2 = statistics.find { it.id == 2 } //calculation stat
        stat2?.incrementProgress(this, 1)
    }
}

data class FavoriteIdealGasCalculation(val calculation: String, val result: String)

class FavoriteIdealGasAdapter(
    private val onRemove: (String) -> Unit,
    private val onCopy: (String) -> Unit
) : RecyclerView.Adapter<FavoriteIdealGasAdapter.ViewHolder>() {

    private val calculations = mutableListOf<FavoriteIdealGasCalculation>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val calculationTextView: TextView = itemView.findViewById(R.id.compound_text)
        val resultTextView: TextView = itemView.findViewById(R.id.molar_weight_text)
        val optionsButton: TextView = itemView.findViewById(R.id.options_btn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_compound, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val calculation = calculations[position]
        holder.calculationTextView.text = calculation.calculation
        holder.resultTextView.text = calculation.result

        holder.optionsButton.setOnClickListener {
            val popupMenu = android.widget.PopupMenu(holder.itemView.context, holder.optionsButton)
            popupMenu.menuInflater.inflate(R.menu.favorite_compound_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_copy -> {
                        onCopy(calculation.calculation)
                        true
                    }
                    R.id.action_remove -> {
                        onRemove(calculation.calculation)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    override fun getItemCount(): Int = calculations.size

    fun updateCalculations(newCalculations: List<FavoriteIdealGasCalculation>) {
        calculations.clear()
        calculations.addAll(newCalculations)
        notifyDataSetChanged()
    }
}
