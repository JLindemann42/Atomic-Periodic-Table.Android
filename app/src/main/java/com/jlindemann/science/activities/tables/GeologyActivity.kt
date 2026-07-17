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
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.GeologyAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Geology
import com.jlindemann.science.model.GeologyModel
import com.jlindemann.science.preferences.GeologyPreference
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import com.jlindemann.science.utils.UnifiedTitleBarController
import android.widget.TextView
import java.util.*
import kotlin.collections.ArrayList

class GeologyActivity : BaseActivity(), GeologyAdapter.OnGeologyClickListener {
    private var geologyList = ArrayList<Geology>()
    var mAdapter = GeologyAdapter(geologyList, this, this)

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
        setContentView(R.layout.activity_geology) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // No overlay consumed it -> fall back to default behaviour.
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

        // Start with platform OnBackInvoked interception disabled; enable on overlays.
        setBackInterceptionEnabled(false)

        val recyclerView = findViewById<RecyclerView>(R.id.geo_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val item = ArrayList<Geology>()
        GeologyModel.getList(item)

        //recyclerView()
        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.activity_geology_title)
        titleBar.setAction(R.drawable.ic_search) { titleBar.showSearch() }
        titleBar.searchCloseButton.setOnClickListener {
            titleBar.hideSearch()
            titleBar.searchInput.setText("")
        }
        titleBar.backButton.setOnClickListener { onBackPressed() }

        setupBottomSheet()
        setupChips(item, recyclerView)

        // Initial filter to show all items
        GeologyPreference(this).setValue("")
        filter("", item, recyclerView)

        findViewById<EditText>(R.id.unified_titlebar_search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), item, recyclerView)
            }
        })
    }

    private fun setupBottomSheet() {
        val geologyPanelRoot = findViewById<View>(R.id.geo_details) ?: return
        val background = findViewById<TextView>(R.id.background_geo) ?: return
        
        bottomSheetBehavior = BottomSheetBehavior.from(geologyPanelRoot)
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

        geologyPanelRoot.findViewById<View>(R.id.drag_frame_geology)?.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun geologyClickListener(item: Geology, position: Int) {
        // Set textViews:
        findViewById<TextView>(R.id.geo_detail_title).text = item.name
        findViewById<TextView>(R.id.geo_type).text = getString(R.string.type_label) + item.type
        findViewById<TextView>(R.id.geo_group).text = getString(R.string.group_label) + item.group
        findViewById<TextView>(R.id.geo_color).text = getString(R.string.color_label) + item.color
        findViewById<TextView>(R.id.geo_strike).text = getString(R.string.streak_label) + item.streak
        findViewById<TextView>(R.id.geo_cristal).text = getString(R.string.cristal_structure_label) + item.cristal
        findViewById<TextView>(R.id.geo_hardness).text = getString(R.string.hardness_label) + item.hardness
        findViewById<TextView>(R.id.geo_density).text = getString(R.string.density_label) + item.density
        findViewById<TextView>(R.id.geo_magnetism).text = getString(R.string.magnetism_label) + item.magnetism
        findViewById<TextView>(R.id.geo_hydrochloride).text = item.hydrochloride

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.geo_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        titleBar.container.layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_geo).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_geo).layoutParams = searchEmptyImgPrm
        
        // Handle Geology Panel insets
        findViewById<View>(R.id.scroll_geology)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
    }

    private fun setupChips(list: ArrayList<Geology>, recyclerView: RecyclerView) {
        val geoPreference = GeologyPreference(this)
        val categories = listOf(
            0 to getString(R.string.geo_clear_filter),
            1 to getString(R.string.activity_geology_mineral),
            2 to getString(R.string.activity_geology_rock),
            3 to getString(R.string.activity_geology_soil)
        )
        
        titleBar.setCategories(categories) { id ->
            val filter = when (id) {
                1 -> "mineral"
                2 -> "rock"
                3 -> "soil"
                else -> ""
            }
            geoPreference.setValue(filter)
            titleBar.searchInput.setText("")
            filter(titleBar.searchInput.text.toString(), list, recyclerView)
        }
    }

    // Filters the listView by different sorts of material by using the geossonPreference to filter by the stringValue.
    private fun filter(text: String, list: ArrayList<Geology>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Geology> = ArrayList()
        for (item in list) {
            val geoPreference = GeologyPreference(this)
            val geoPrefValue = geoPreference.getValue()
            if (item.name.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                if (item.type.lowercase(Locale.ROOT).contains(geoPrefValue.lowercase(Locale.ROOT))) {
                    filteredList.add(item)
                }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_geo), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_geo).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = GeologyAdapter(filteredList, this, this)
    }

    // Centralized overlay detection
    private fun anyOverlayOpen(): Boolean {
        val detailsVisible = bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN
        val searchBarVisible = titleBar.searchRow.visibility == View.VISIBLE
        return detailsVisible || searchBarVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        // If details visible, hide them
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        }

        // If search bar visible, close it
        if (titleBar.searchRow.visibility == View.VISIBLE) {
            titleBar.hideSearch()
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        return false
    }

    override fun onBackPressed() {
        // Use centralized handler so gestures and hardware back behave consistently.
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    /**
     * Centralized management of platform back interception for Android 14+.
     * We forward platform back invocations to the OnBackPressedDispatcher to ensure
     * gestures and hardware back buttons call the same callbacks.
     */
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        // Keep OnBackPressedCallback state in sync
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
                                    // fallback to finishing
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
            } catch (_: Exception) { }
            onBackInvokedCb = null
        }
    }
}