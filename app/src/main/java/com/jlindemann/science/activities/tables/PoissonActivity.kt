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
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.PoissonAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.PoissonModel
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.PoissonPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class PoissonActivity : BaseActivity(), PoissonAdapter.OnPoissonClickListener {
    private var poissonList = ArrayList<Poisson>()
    var mAdapter = PoissonAdapter(poissonList, this, this)

    // Unified back handling fields
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())

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
                    // Not consumed by overlays -> fall back to default behaviour.
                    // Temporarily disable the callback to avoid recursion, then dispatch.
                    isEnabled = false
                    try {
                        onBackPressedDispatcher.onBackPressed()
                    } finally {
                        // leave disabled by default
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // Register platform callback for Android 14+ to forward gestures to the dispatcher when enabled.
        // Start with interception disabled (we only need it when overlays are visible).
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

        recyclerView()
        clickSearch()
        chipListeners(itempoi, recyclerView)

        // When tapping background, hide panel and update interception
        findViewById<View>(R.id.poi_det_inc_background).setOnClickListener {
            hideInfoPanel()
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        findViewById<View>(R.id.close_detail_poisson_btn).setOnClickListener {
            hideInfoPanel()
            setBackInterceptionEnabled(anyOverlayOpen())
        }

        findViewById<View>(R.id.back_btn_poi).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.poi_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = findViewById<FrameLayout>(R.id.common_title_back_poi).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<FrameLayout>(R.id.common_title_back_poi).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_poi).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_poi).layoutParams = searchEmptyImgPrm
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.poi_view)
        val poisson = ArrayList<Poisson>()

        PoissonModel.getList(poisson)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = PoissonAdapter(poisson, this, this)
        recyclerView.adapter = adapter

        adapter.notifyDataSetChanged()

        findViewById<EditText>(R.id.edit_poi).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), poisson, recyclerView)
            }
        })
    }

    //Overrides the clickListener from PoissonAdapter to show InfoPanel when clicking on elments
    override fun poissonClickListener(item: Poisson, position: Int) {
        showInfoPanel(item.name, item.start, item.end, item.type)
    }

    //Show the info panel with detailed information about poisson interavls for materials
    private fun showInfoPanel(title: String, start: Double, end: Double, type: String) {
        Anim.fadeIn(findViewById<ConstraintLayout>(R.id.poi_det_inc), 150)
        findViewById<FrameLayout>(R.id.poi_det_inc_background).visibility = View.VISIBLE

        // Info panel shown -> enable back interception
        setBackInterceptionEnabled(true)

        findViewById<ProgressBar>(R.id.pb_poisson_detail).progress = (start*100*2).toInt() //*2 as 100% is 0.5
        findViewById<ProgressBar>(R.id.pb_poisson_detail).secondaryProgress = (end*100*2).toInt() //*2 as 100% is 0.5
        findViewById<TextView>(R.id.detail_poisson_title).text = title
    }

    //function for hiding info panel
    private fun hideInfoPanel() {
        Anim.fadeOutAnim(findViewById<ConstraintLayout>(R.id.poi_det_inc), 150)
        findViewById<FrameLayout>(R.id.poi_det_inc_background).visibility = View.GONE

        // After hiding, update interception state
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    //Filters the listView by different sorts of material by using the PoissonPreference to filter by the stringValue.
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
        val handler = android.os.Handler()
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

    private fun clickSearch() {
        findViewById<View>(R.id.search_btn_poi).setOnClickListener {
            Utils.fadeInAnim(findViewById<View>(R.id.search_bar_poi), 150)
            Utils.fadeOutAnim(findViewById<View>(R.id.title_box_poi), 1)

            findViewById<EditText>(R.id.edit_poi).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_poi), InputMethodManager.SHOW_IMPLICIT)

            // Search bar shown -> enable back interception
            setBackInterceptionEnabled(true)
        }
        findViewById<View>(R.id.close_poi_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<View>(R.id.search_bar_poi), 1)

            val delayClose = Handler(Looper.getMainLooper())
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box_poi), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // closed -> update interception state
            setBackInterceptionEnabled(anyOverlayOpen())
        }
    }

    private fun chipListeners(list: ArrayList<Poisson>, recyclerView: RecyclerView) {
        val poissonPreference = PoissonPreferences(this)
        val clearBtn = findViewById<com.google.android.material.chip.Chip>(R.id.clear_btn)
        
        val applyFilter = { filter: String ->
            poissonPreference.setValue(filter)
            findViewById<EditText>(R.id.edit_poi).setText("")
            clearBtn.visibility = if (filter.isEmpty()) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.rocks_btn).setOnClickListener { applyFilter("rock") }
        findViewById<View>(R.id.soils_btn).setOnClickListener { applyFilter("soil") }
        findViewById<View>(R.id.minerals_btn).setOnClickListener { applyFilter("mineral") }
        clearBtn.setOnClickListener { applyFilter("") }
    }

    // Centralized overlay detection
    private fun anyOverlayOpen(): Boolean {
        val infoVisible = findViewById<ConstraintLayout>(R.id.poi_det_inc).visibility == View.VISIBLE
        val backgroundVisible = findViewById<View>(R.id.poi_det_inc_background).visibility == View.VISIBLE
        val searchBarVisible = findViewById<View>(R.id.search_bar_poi).visibility == View.VISIBLE
        return infoVisible || backgroundVisible || searchBarVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val infoPanel = findViewById<ConstraintLayout>(R.id.poi_det_inc)
        val background = findViewById<View>(R.id.poi_det_inc_background)
        val searchBar = findViewById<View>(R.id.search_bar_poi)

        if (infoPanel.visibility == View.VISIBLE || background.visibility == View.VISIBLE) {
            hideInfoPanel()
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        if (searchBar.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(searchBar, 1)
            Handler(Looper.getMainLooper()).postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box_poi), 150)
            }, 151)
            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        return false
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