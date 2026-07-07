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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.EmissionAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ElementDataLoader
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.util.*
import kotlin.collections.ArrayList

class EmissionActivity : BaseActivity(), EmissionAdapter.OnEmissionClickListener {
    private var emiList = ArrayList<Element>()
    var mAdapter = EmissionAdapter(emiList, this, this)

    // Back handling fields (unified pattern)
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
        setContentView(R.layout.activity_emission) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // Not consumed by overlays -> fallback to default behaviour.
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

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "emi"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        recyclerView()
        clickSearch()

        // Update interception when panel state changes
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) { }
            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                if (newState == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    Utils.fadeOutAnim(findViewById<TextView>(R.id.background_emi), 300)
                    Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.emission_detail), 300)
                }
                // Keep platform callback registered while an overlay is open
                setBackInterceptionEnabled(anyOverlayOpen())
            }
        })

        findViewById<TextView>(R.id.background_emi).setOnClickListener{
            if (findViewById<FrameLayout>(R.id.emission_detail).visibility == View.VISIBLE) {
                Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.emission_detail), 300)
                Utils.fadeOutAnim(findViewById<TextView>(R.id.background_emi), 300)
            }
            else {
                Utils.fadeOutAnim(findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e), 300)
                Utils.fadeOutAnim(findViewById<TextView>(R.id.background_emi), 300)
            }
            // update interception after hiding overlays
            setBackInterceptionEnabled(anyOverlayOpen())
        }

        findViewById<ConstraintLayout>(R.id.view_emi).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        findViewById<View>(R.id.back_btn_emi).setOnClickListener { this.onBackPressed() }
    }

    override fun emiClickListener(item: Element, position: Int) {
        try {
            val jsonObject = ElementDataLoader.loadElementData(this, item.elementKey)
            if (jsonObject != null) {
                val url = jsonObject.optString("short", "---")
                val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
                val extg = ".gif"
                val fURL = hUrl + url + extg
                findViewById<TextView>(R.id.emi_title).text = item.elementKey
                try {
                    Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.emi_img_detail))
                    Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_emi), 300)
                }
                catch(e: ConnectException) {
                    // network errors ignored gracefully
                }
            }
        }
        catch (e: IOException) { }
        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.emission_detail), 300)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).panelState = SlidingUpPanelLayout.PanelState.EXPANDED

        // emission detail / panel shown -> enable back interception
        setBackInterceptionEnabled(true)
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.emi_view)
        val emiList = ArrayList<Element>()
        ElementModel.getList(emiList, this)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = EmissionAdapter(emiList, this, this)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()

        findViewById<EditText>(R.id.edit_emi).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), emiList, recyclerView) }
        })
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) { if (item.element.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) { filteredList.add(item) } }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_emi), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_emi).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = EmissionAdapter(filteredList, this, this)
    }

    private fun clickSearch() {
        findViewById<View>(R.id.search_btn_emi).setOnClickListener {
            Utils.fadeInAnim(findViewById<View>(R.id.search_bar_emi), 150)
            Utils.fadeOutAnim(findViewById<View>(R.id.title_box), 1)
            findViewById<EditText>(R.id.edit_emi).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_emi), InputMethodManager.SHOW_IMPLICIT)

            // Search bar shown -> enable back interception
            setBackInterceptionEnabled(true)
        }
        findViewById<View>(R.id.close_ele_search_emi).setOnClickListener {
            Utils.fadeOutAnim(findViewById<View>(R.id.search_bar_emi), 1)
            val delayClose = Handler(Looper.getMainLooper())
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box), 150)
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

    override fun onBackPressed() {
        // Centralized handling: close overlays first
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    // Centralized overlay detection
    private fun anyOverlayOpen(): Boolean {
        val panel = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e)
        val panelExpanded = panel.panelState == SlidingUpPanelLayout.PanelState.EXPANDED
        val backgroundVisible = findViewById<TextView>(R.id.background_emi).visibility == View.VISIBLE
        val detailVisible = findViewById<View>(R.id.emission_detail).visibility == View.VISIBLE
        val searchBarVisible = findViewById<View>(R.id.search_bar_emi).visibility == View.VISIBLE
        return panelExpanded || backgroundVisible || detailVisible || searchBarVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val panel = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e)
        val background = findViewById<TextView>(R.id.background_emi)
        val detail = findViewById<View>(R.id.emission_detail)
        val searchBar = findViewById<View>(R.id.search_bar_emi)

        // If emission detail visible, hide it and collapse panel
        if (detail.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(detail, 300)
            Utils.fadeOutAnim(background, 300)
            panel.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If sliding panel expanded, collapse it
        if (panel.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            panel.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            Utils.fadeOutAnim(background, 300)
            Utils.fadeOutAnim(detail, 300)
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If search bar visible, close it
        if (searchBar.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(searchBar, 1)
            Handler(Looper.getMainLooper()).postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box), 150)
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
     * Forward platform back invocations to the OnBackPressedDispatcher so gestures and
     * hardware back buttons use the same logic.
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

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.emi_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val params2 = findViewById<FrameLayout>(R.id.common_title_back_emi).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_emi).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_emi).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_emi).layoutParams = searchEmptyImgPrm

        val params3 = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).layoutParams as ViewGroup.MarginLayoutParams
        params3.topMargin = top + resources.getDimensionPixelSize(R.dimen.panel_margin)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).layoutParams = params3
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