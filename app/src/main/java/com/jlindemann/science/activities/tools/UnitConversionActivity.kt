package com.jlindemann.science.activities.tools

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jlindemann.science.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.view.ViewPropertyAnimator
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.model.UnitCatalog
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.utils.UnifiedTitleBarController
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference

class UnitConversionActivity : BaseActivity() {
    private lateinit var titleBar: UnifiedTitleBarController
    private val unitCategories: Map<String, List<UnitDefinition>> = UnitCatalog.categories

    private lateinit var categorySpinner: Spinner
    private lateinit var fromUnitSpinner: Spinner
    private lateinit var toUnitSpinner: Spinner
    private lateinit var inputValue: com.google.android.material.textfield.TextInputEditText
    private lateinit var outputValue: com.google.android.material.textfield.TextInputEditText
    private lateinit var formulaValue: TextView
    private lateinit var addFavoriteButton: com.google.android.material.button.MaterialButton
    private lateinit var favoritesList: RecyclerView

    private val favorites = mutableListOf<UnitConversionFavorite>()
    private lateinit var favoritesAdapter: FavoriteRecyclerAdapter
    private val favoritesKey = "unit_favorites"

    // Back handling fields (new unified logic used across activities)
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> {
                    setTheme(R.style.AppTheme)
                }

                Configuration.UI_MODE_NIGHT_YES -> {
                    setTheme(R.style.AppThemeDark)
                }
            }
        }
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_unit_converter)
        findViewById<ConstraintLayout>(R.id.view_unit).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.unit_converter_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }

        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        findViewById<ScrollView>(R.id.unit_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.unit_scroll).scrollY
                    if (scrollY > 150) {
                        titleSurface.visibility = View.VISIBLE
                        titleBar.titleView.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.INVISIBLE
                        titleBar.container.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        titleSurface.visibility = View.INVISIBLE
                        titleBar.titleView.visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.unit_title_downstate).visibility = View.VISIBLE
                        titleBar.container.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                }
            })

        // Setup unified back handling: register a lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // default behavior: no overlays in this activity; allow normal back navigation
                if (!handleBackPress()) {
                    // if not consumed, fall back to normal back action
                    isEnabled = false
                    // call framework onBackPressedDispatcher to let system handle (or finish)
                    onBackPressedDispatcher.onBackPressed()
                    // restore enabled state as appropriate (disabled by default for this activity)
                    isEnabled = false
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // Register platform OnBackInvoked callback on Android 14+ to forward gestures to dispatcher
        setBackInterceptionEnabled(true)

        // Check if favorite list should be shown or not (PRO)
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        if (proPrefValue == 1) {
            findViewById<RecyclerView>(R.id.favorites_list).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.no_pro_text).visibility = View.VISIBLE
            findViewById<View>(R.id.pro_button_cal).visibility = View.VISIBLE
            findViewById<View>(R.id.add_favorite_button).visibility = View.GONE
        }
        if (proPrefValue == 100) {
            findViewById<RecyclerView>(R.id.favorites_list).visibility = View.VISIBLE
            findViewById<TextView>(R.id.no_pro_text).visibility = View.GONE
            findViewById<View>(R.id.pro_button_cal).visibility = View.GONE
            findViewById<View>(R.id.add_favorite_button).visibility = View.VISIBLE
        }

        findViewById<View>(R.id.pro_button_cal).setOnClickListener {
            goToProPage()
        }

        //Add value to most used:
        val mostUsedPreference = MostUsedToolPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "uni"
        val regex = Regex("($targetLabel)=(\\d+\\.\\d+)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(
                mostUsedPrefValue.replace(
                    "$targetLabel=$value",
                    "$targetLabel=$newValue"
                )
            )
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

        formulaValue.text = getString(R.string.unit_display_formula)

        setupSpinners()
        setupListeners()
        setupRecyclerView()
        loadFavorites()
        updateFavoriteButtonState()


    }

    private fun setupSpinners() {
        val categories = unitCategories.keys.toList()
        val categoryAdapter = ArrayAdapter(this, R.layout.spinner_item_text, categories)
        categoryAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedCategory = categories[position]
                val units = unitCategories[selectedCategory]?.map { it.name } ?: listOf()
                val unitAdapter =
                    ArrayAdapter(this@UnitConversionActivity, R.layout.spinner_item_text, units)
                unitAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
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
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                convertUnits()
                updateFavoriteButtonState()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        toUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
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
        favoritesAdapter = FavoriteRecyclerAdapter(
            favorites,
            onItemClick = { fav ->
                inputValue.setText(fav.inputValue.toString())
                val catIdx =
                    (categorySpinner.adapter as ArrayAdapter<String>).getPosition(fav.category)
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
            outputValue.setText("...")
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
                formula =
                    "Divide ${category.lowercase()}-value with: ${fromDef.factor / toDef.factor}"
            }
        }
        outputValue.setText(result.toString())
        formulaValue.text = formula
    }

    private fun convertTemperature(from: String, to: String, value: Double): Pair<Double, String> =
        Pair(UnitCatalog.convertTemperature(from, to, value), UnitCatalog.temperatureFormula(from, to))

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
        val params =
            titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

        val params2 =
            findViewById<TextView>(R.id.unit_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin =
            top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(
                R.dimen.header_down_margin
            )
        findViewById<TextView>(R.id.unit_title_downstate).layoutParams = params2
    }

    // Basic handler for in-activity overlays (none for this activity currently)
    private fun anyOverlayOpen(): Boolean {
        // No overlays in this activity right now; return false.
        return false
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        // No overlays to close for this activity. Return false so default behavior occurs.
        return false
    }

    /**
     * Centralized management of platform back interception for Android 14+.
     * We forward platform back invocations to the OnBackPressedDispatcher to ensure
     * gestures and hardware back buttons call the same callbacks.
     */
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        // Keep the OnBackPressedCallback enabled state in sync when requested.
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        handler.post {
                            try {
                                // Forward to the OnBackPressedDispatcher which will invoke registered callbacks.
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                // Fallback if dispatcher fails
                                val consumed = handleBackPress()
                                if (!consumed) {
                                    // Default fallback: finish activity
                                    finish()
                                }
                            }
                        }
                    }
                    try {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            onBackInvokedCb!!
                        )
                    } catch (_: Exception) {
                        // ignore registration errors on some devices
                    }
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) {
                        // ignore
                    }
                    onBackInvokedCb = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup back interception hooks
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            } catch (_: Exception) {
            }
            onBackInvokedCb = null
        }
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
        holder.textView.text = holder.itemView.context.getString(R.string.unit_conversion_format, fav.inputValue, fav.fromUnit, fav.convertedValue, fav.toUnit, fav.category)
    }
}