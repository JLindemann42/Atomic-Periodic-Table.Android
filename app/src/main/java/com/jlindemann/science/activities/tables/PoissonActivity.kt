package com.jlindemann.science.activities.tables

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.PoissonAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.PoissonModel
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.PoissonPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class PoissonActivity : BaseActivity(), PoissonAdapter.OnPoissonClickListener {
    private var poissonList = ArrayList<Poisson>()
    var mAdapter = PoissonAdapter(poissonList, this, this)

    private lateinit var titleBar: UnifiedTitleBarController

    // Unified back handling fields
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

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
        setContentView(R.layout.activity_poisson) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    isEnabled = false
                    try {
                        onBackPressedDispatcher.onBackPressed()
                    } finally {
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        setBackInterceptionEnabled(false)

        val recyclerView = findViewById<RecyclerView>(R.id.poi_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val itempoi = ArrayList<Poisson>()
        PoissonModel.getList(itempoi)

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "poi"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.poissons_ratio_title)
        titleBar.setAction(R.drawable.ic_search) { titleBar.showSearch() }
        titleBar.searchCloseButton.setOnClickListener {
            titleBar.hideSearch()
            titleBar.searchInput.setText("")
        }
        titleBar.backButton.setOnClickListener { onBackPressed() }

        setupBottomSheet()
        setupChips(itempoi, recyclerView)

        // Initial filter to show all items
        PoissonPreferences(this).setValue("")
        filter("", itempoi, recyclerView)

        findViewById<EditText>(R.id.unified_titlebar_search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), itempoi, recyclerView)
            }
        })
    }

    private fun setupBottomSheet() {
        val poissonPanelRoot = findViewById<View>(R.id.poi_det_inc) ?: return
        val background = findViewById<TextView>(R.id.background_poi) ?: return
        
        bottomSheetBehavior = BottomSheetBehavior.from(poissonPanelRoot)
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior?.skipCollapsed = true
        
        bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    background.visibility = View.GONE
                    background.alpha = 0f
                } else {
                    background.visibility = View.VISIBLE
                }
                setBackInterceptionEnabled(anyOverlayOpen())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                background.visibility = View.VISIBLE
                background.alpha = slideOffset.coerceAtLeast(0f) * 0.6f
            }
        })
        
        background.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }

        poissonPanelRoot.findViewById<View>(R.id.drag_frame_poisson)?.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.poi_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        titleBar.container.layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_poi).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_poi).layoutParams = searchEmptyImgPrm
        
        // Handle Poisson Panel insets
        findViewById<View>(R.id.scroll_poisson)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
    }

    private fun setupChips(list: ArrayList<Poisson>, recyclerView: RecyclerView) {
        val poissonPreference = PoissonPreferences(this)
        val categories = listOf(
            0 to getString(R.string.poi_clear_filter),
            1 to getString(R.string.chip_rocks),
            2 to getString(R.string.chip_soils),
            3 to getString(R.string.chip_minerals)
        )
        
        titleBar.setCategories(categories) { id ->
            val filter = when (id) {
                1 -> "rock"
                2 -> "soil"
                3 -> "mineral"
                else -> ""
            }
            poissonPreference.setValue(filter)
            titleBar.searchInput.setText("")
            filter(titleBar.searchInput.text.toString(), list, recyclerView)
        }
    }

    override fun poissonClickListener(item: Poisson, position: Int) {
        showInfoPanel(item.name, item.start, item.end, item.type)
    }

    private fun showInfoPanel(title: String, start: Double, end: Double, type: String) {
        val slider = findViewById<com.google.android.material.slider.RangeSlider>(R.id.rs_poisson_detail)
        
        // Poisson values can be outside [0.0, 0.5] for auxetic materials or other cases.
        // We must update the slider range before setting values to avoid IllegalStateException.
        val minVal = Math.min(start, end).toFloat()
        val maxVal = Math.max(start, end).toFloat()
        
        // Ensure values are within [valueFrom, valueTo]
        slider.valueFrom = Math.min(0.0f, Math.floor(minVal.toDouble()).toFloat())
        slider.valueTo = Math.max(0.5f, Math.ceil(maxVal.toDouble()).toFloat())

        slider.setValues(start.toFloat(), end.toFloat())

        findViewById<TextView>(R.id.detail_poisson_title).text = title
        
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    private fun hideInfoPanel() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    private fun filter(text: String, list: ArrayList<Poisson>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Poisson> = ArrayList()
        for (item in list) {
            val poissonPreference = PoissonPreferences(this)
            val poissonPrefValue = poissonPreference.getValue()
            if (item.name.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                if (item.type.lowercase(Locale.ROOT).contains(poissonPrefValue.lowercase(Locale.ROOT))) {
                    filteredList.add(item)
                }
            }
        }
        val handler = android.os.Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_poi), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_poi).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = PoissonAdapter(filteredList, this, this)
    }

    private fun anyOverlayOpen(): Boolean {
        val infoVisible = bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN
        val backgroundVisible = findViewById<TextView>(R.id.background_poi).visibility == View.VISIBLE
        val searchBarVisible = titleBar.searchRow.visibility == View.VISIBLE
        return infoVisible || backgroundVisible || searchBarVisible
    }

    private fun handleBackPress(): Boolean {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        }

        if (titleBar.searchRow.visibility == View.VISIBLE) {
            titleBar.hideSearch()
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        return false
    }

    override fun onBackPressed() {
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    /**
     * Centralized management of platform back interception for Android 14+.
     */
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        uiHandler.post {
                            try {
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                val consumed = handleBackPress()
                                if (!consumed) {
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
                    } catch (_: Exception) { }
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) { }
                    onBackInvokedCb = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            } catch (_: Exception) { }
            onBackInvokedCb = null
        }
    }
}
