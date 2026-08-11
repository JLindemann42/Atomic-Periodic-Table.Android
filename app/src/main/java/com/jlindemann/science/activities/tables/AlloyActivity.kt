package com.jlindemann.science.activities.tables

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.adapter.AlloyAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Alloy
import com.jlindemann.science.model.AlloyModel
import com.jlindemann.science.preferences.AlloyPreference
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController
import java.util.*
import kotlin.collections.ArrayList

class AlloyActivity : BaseActivity(), AlloyAdapter.OnAlloyClickListener {

    private companion object {
        /** The base metals the table can be filtered by, in chip order, with their labels. */
        val BASE_METALS = listOf(
            "iron" to R.string.alloy_base_iron,
            "copper" to R.string.alloy_base_copper,
            "aluminium" to R.string.alloy_base_aluminium,
            "nickel" to R.string.alloy_base_nickel,
            "tin" to R.string.alloy_base_tin,
            "silver" to R.string.alloy_base_silver,
            "gold" to R.string.alloy_base_gold,
            "titanium" to R.string.alloy_base_titanium,
            "mercury" to R.string.alloy_base_mercury
        )
    }

    private var alloyList = ArrayList<Alloy>()
    var mAdapter = AlloyAdapter(alloyList, this, this)

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
        setContentView(R.layout.activity_alloy) //REMEMBER: Never move any function calls above this

        bumpMostUsed()

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

        val recyclerView = findViewById<RecyclerView>(R.id.alloy_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val item = ArrayList<Alloy>()
        AlloyModel.getList(item)

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.activity_alloy_title)
        titleBar.setAction(R.drawable.ic_search) { titleBar.showSearch() }
        titleBar.searchCloseButton.setOnClickListener {
            titleBar.hideSearch()
            titleBar.searchInput.setText("")
        }
        titleBar.backButton.setOnClickListener { onBackPressed() }

        setupBottomSheet()
        setupChips(item, recyclerView)

        // Initial filter to show all items
        AlloyPreference(this).setValue("")
        filter("", item, recyclerView)

        findViewById<EditText>(R.id.unified_titlebar_search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), item, recyclerView)
            }
        })
    }

    /**
     * Bumps this table's counter in the most used bar. The entry is appended when it is missing,
     * so the chip also shows up for installs whose preference was written before this table existed.
     */
    private fun bumpMostUsed() {
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "alo"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=${value + 1}"))
        }
        else {
            mostUsedPreference.setValue("$mostUsedPrefValue, $targetLabel=1.0")
        }
    }

    private fun setupBottomSheet() {
        val alloyPanelRoot = findViewById<View>(R.id.alloy_details) ?: return
        val background = findViewById<TextView>(R.id.background_alloy) ?: return

        bottomSheetBehavior = BottomSheetBehavior.from(alloyPanelRoot)
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

        alloyPanelRoot.findViewById<View>(R.id.drag_frame_alloy)?.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun alloyClickListener(item: Alloy, position: Int) {
        // Set textViews:
        findViewById<TextView>(R.id.alloy_detail_title).text = item.name
        findViewById<TextView>(R.id.alloy_base).text = getString(R.string.base_label) + baseName(item.base)
        findViewById<TextView>(R.id.alloy_composition).text = getString(R.string.composition_label) + item.composition
        findViewById<TextView>(R.id.alloy_description).text = item.description

        // The base is a lowercase english element key, which is what ElementInfoActivity reads through
        // ElementSendAndLoad, the same way the periodic table and the widgets open an element.
        findViewById<MaterialButton>(R.id.alloy_base_btn).setOnClickListener {
            ElementSendAndLoad(this).setValue(item.base)
            startActivity(Intent(this, ElementInfoActivity::class.java))
        }

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.alloy_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        titleBar.container.layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_alloy).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_alloy).layoutParams = searchEmptyImgPrm

        // Handle Alloy Panel insets
        findViewById<View>(R.id.scroll_alloy)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
    }

    private fun setupChips(list: ArrayList<Alloy>, recyclerView: RecyclerView) {
        val alloyPreference = AlloyPreference(this)
        // Chip 0 clears the filter, so a base metal's chip id is its index in BASE_METALS plus one.
        val categories = listOf(0 to getString(R.string.alloy_clear_filter)) +
                BASE_METALS.mapIndexed { index, (_, label) -> index + 1 to getString(label) }

        titleBar.setCategories(categories) { id ->
            alloyPreference.setValue(BASE_METALS.getOrNull(id - 1)?.first ?: "")
            titleBar.searchInput.setText("")
            filter(titleBar.searchInput.text.toString(), list, recyclerView)
        }
    }

    /**
     * The translated name of a base metal.
     *
     * [Alloy.base] is a lowercase English element key, which is what opens the element page. Shown
     * as it is stored, the detail panel read "Basmetall: Iron" in a translated app, so it is looked
     * up against the same strings the filter chips use. An alloy whose base has no chip falls back
     * to the key itself rather than showing nothing.
     */
    private fun baseName(base: String): String =
        BASE_METALS.firstOrNull { it.first == base }
            ?.let { getString(it.second) }
            ?: base.replaceFirstChar { it.uppercase() }

    /**
     * Filters the list by the base metal chosen in the chips, and by the search text. The text is
     * matched against the composition as well as the name, so searching for a constituent that is
     * not in the title, like zinc, still finds the alloy.
     */
    private fun filter(text: String, list: ArrayList<Alloy>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Alloy> = ArrayList()
        val query = text.lowercase(Locale.ROOT)
        val basePrefValue = AlloyPreference(this).getValue()
        for (item in list) {
            val matchesText = item.name.lowercase(Locale.ROOT).contains(query) ||
                    item.composition.lowercase(Locale.ROOT).contains(query)
            val matchesBase = basePrefValue.isEmpty() || item.base == basePrefValue
            if (matchesText && matchesBase) {
                filteredList.add(item)
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_alloy), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_alloy).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = AlloyAdapter(filteredList, this, this)
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
