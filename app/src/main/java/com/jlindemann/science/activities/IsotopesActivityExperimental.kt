package com.jlindemann.science.activities

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.adapter.IsotopeAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

class IsotopesActivityExperimental : BaseActivity(), IsotopeAdapter.OnElementClickListener {
    private var elementList = ArrayList<Element>()
    var mAdapter = IsotopeAdapter(elementList, this, this)

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
        setContentView(R.layout.activity_isotopes_experimental) //Don't move down (Needs to be before we call our functions)

        // Register a lifecycle-aware OnBackPressedCallback (disabled by default).
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
                        // Keep disabled by default; will be enabled when overlays open.
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // Register platform callback for Android 14+ to forward gestures to the dispatcher when enabled.
        // Start with interception disabled (we only need it when overlays are visible).
        setBackInterceptionEnabled(false)

        val recyclerView = findViewById<RecyclerView>(R.id.r_view)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val adapter = IsotopeAdapter(elements, this, this)
        recyclerView.adapter = adapter

        findViewById<EditText>(R.id.edit_iso).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), elements, recyclerView) }
        })

        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) { }
            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                if (findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState === SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    Utils.fadeOutAnim(findViewById<TextView>(R.id.background_i2), 300)
                    Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
                }
                // Update back interception state whenever panel state changes
                setBackInterceptionEnabled(anyOverlayOpen())
            }
        })

        findViewById<TextView>(R.id.background_i2).setOnClickListener{
            if (findViewById<ConstraintLayout>(R.id.panel_info).visibility == View.VISIBLE) {
                Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.panel_info), 300)
                Utils.fadeOutAnim(findViewById<TextView>(R.id.background_i2), 300)
            }
            else {
                Utils.fadeOutAnim(findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i), 300)
                Utils.fadeOutAnim(findViewById<TextView>(R.id.background_i2), 300)
                findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            }
            // Update back interception state after click closes overlays
            setBackInterceptionEnabled(anyOverlayOpen())
        }

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "iso"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        findViewById<FrameLayout>(R.id.view1).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        clickSearch()
        searchFilter(elements, recyclerView)
        sentIsotope()
        findViewById<ImageButton>(R.id.back_btn).setOnClickListener { this.onBackPressed() }
    }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        findViewById<FloatingActionButton>(R.id.filter_btn2).setOnClickListener {
            Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeInAnim(findViewById<TextView>(R.id.filter_background), 150)
            // show overlay -> enable back interception
            setBackInterceptionEnabled(true)
        }
        findViewById<TextView>(R.id.filter_background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.filter_background), 150)
            // update interception after closing
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        findViewById<TextView>(R.id.iso_alphabet_btn).setOnClickListener {
            val isoPreference = IsoPreferences(this)
            isoPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.filter_background), 150)
            filtList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element > rhs.element) 1 else 0
            })
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = IsotopeAdapter(filtList, this, this)
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        findViewById<TextView>(R.id.iso_element_numb_btn).setOnClickListener {
            val isoPreference = IsoPreferences(this)
            isoPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.filter_background), 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = IsotopeAdapter(filtList, this, this)
            setBackInterceptionEnabled(anyOverlayOpen())
        }
    }

    private fun clickSearch() {
        findViewById<ImageButton>(R.id.search_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_bar_iso), 300)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.title_box), 300)

            findViewById<EditText>(R.id.edit_iso).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_iso), InputMethodManager.SHOW_IMPLICIT)

            // Search bar shown -> enable back interception
            setBackInterceptionEnabled(true)
        }
        findViewById<ImageButton>(R.id.close_iso_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_bar_iso), 300)
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box), 300)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // closed -> update interception state
            setBackInterceptionEnabled(anyOverlayOpen())
        }
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val isoPreference = IsoPreferences(this)
        val isoPrefValue = isoPreference.getValue()
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) {
            if (item.element.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        if (isoPrefValue == 0) {
            filteredList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element > rhs.element) 1 else 0
            })
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = IsotopeAdapter(filteredList, this, this)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter?.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_iso), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_iso).visibility = View.GONE
            }
        }, 10)
    }

    override fun elementClickListener(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)
        drawCard(elementList)

        Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_i2), 300)
        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.EXPANDED

        // Panel expanded -> enable back interception
        setBackInterceptionEnabled(true)
    }

    private fun sentIsotope() {
        val isoSent = sendIso(this)
        if (isoSent.getValue() == "true") {
            drawCard(elementList)
            Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_i2), 300)
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            isoSent.setValue("false")

            // Panel expanded -> enable back interception
            setBackInterceptionEnabled(true)
        }
    }

    override fun onBackPressed() {
        // Legacy/explicit back - prefer our centralized handler
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    private fun drawCard(list: ArrayList<Element>) {
        ElementModel.getList(list)
        var jsonString : String? = null
        for (item in list) {
            try {
                val elementSendLoad = ElementSendAndLoad(this)
                val nameVal = elementSendLoad.getValue()
                if (item.element.replaceFirstChar { it.uppercase() } == nameVal?.replaceFirstChar { it.uppercase() }) {
                    val ext = ".json"
                    val elementJson: String? = "$nameVal$ext"
                    val inputStream: InputStream = assets.open(elementJson.toString())
                    jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)

                    findViewById<LinearLayout>(R.id.frame_iso).removeAllViews()

                    val aLayout = findViewById<LinearLayout>(R.id.frame_iso)
                    val inflater = layoutInflater
                    val fLayout: View = inflater.inflate(R.layout.row_iso_panel_title_item, aLayout, false)

                    val iTitle = fLayout.findViewById(R.id.iso_title) as TextView
                    val iExt = " Isotopes"
                    iTitle.text = "${nameVal.replaceFirstChar { it.uppercase() }}$iExt"

                    aLayout.addView(fLayout)

                    for (i in 1..item.isotopes) {
                        val mainLayout = findViewById<LinearLayout>(R.id.frame_iso)
                        val inflater = layoutInflater
                        val myLayout: View = inflater.inflate(R.layout.row_iso_panel_item, mainLayout, false)
                        val name = "iso_"
                        val z = "iso_Z_"
                        val n = "iso_N_"
                        val a = "iso_A_"
                        val half = "iso_half_"
                        val mass = "iso_mass_"
                        val halfText = "Half-Time: "
                        val massText = "Mass: "

                        val isoName = jsonObject.optString("$name$i", "---")
                        val isoZ = jsonObject.optString("$z$i", "---")
                        val isoN = jsonObject.optString("$n$i", "---")
                        val isoA = jsonObject.optString("$a$i", "---")
                        val isoHalf = jsonObject.optString("$half$i", "---")
                        val isoMass = jsonObject.optString("$mass$i", "---")

                        val iName = myLayout.findViewById(R.id.i_name) as TextView
                        val iZ = myLayout.findViewById(R.id.i_z) as TextView
                        val iN = myLayout.findViewById(R.id.i_n) as TextView
                        val iA = myLayout.findViewById(R.id.i_a) as TextView
                        val iHalf = myLayout.findViewById(R.id.i_half) as TextView
                        val iMass = myLayout.findViewById(R.id.i_mass) as TextView

                        iName.text = isoName
                        iZ.text = isoZ
                        iN.text = isoN
                        iA.text = isoA
                        iHalf.text = "$halfText$isoHalf"
                        iMass.text = "$massText$isoMass"

                        mainLayout.addView(myLayout)
                    }
                }
            }
            catch (e: IOException) { ToastUtil.showToast(this, "Couldn't load Data") }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.r_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))
        val params2 = findViewById<FrameLayout>(R.id.common_title_back_iso).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_iso).layoutParams = params2

        val params3 = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).layoutParams as ViewGroup.MarginLayoutParams
        params3.topMargin = top + resources.getDimensionPixelSize(R.dimen.panel_margin)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).layoutParams = params3

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_iso).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_iso).layoutParams = searchEmptyImgPrm
    }

    // Basic handler for in-activity overlays
    private fun anyOverlayOpen(): Boolean {
        val panelExpanded = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState == SlidingUpPanelLayout.PanelState.EXPANDED
        val backgroundVisible = findViewById<TextView>(R.id.background_i2).visibility == View.VISIBLE
        val filterVisible = findViewById<TextView>(R.id.filter_background).visibility == View.VISIBLE
        val searchBarVisible = findViewById<FrameLayout>(R.id.search_bar_iso).visibility == View.VISIBLE
        val panelInfoVisible = findViewById<ConstraintLayout>(R.id.panel_info).visibility == View.VISIBLE
        return panelExpanded || backgroundVisible || filterVisible || searchBarVisible || panelInfoVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val slidingLayout = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i)
        val background = findViewById<TextView>(R.id.background_i2)
        val filterBackground = findViewById<TextView>(R.id.filter_background)
        val searchBar = findViewById<FrameLayout>(R.id.search_bar_iso)
        val panelInfo = findViewById<ConstraintLayout>(R.id.panel_info)

        // If panel info is visible, hide it
        if (panelInfo.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(panelInfo, 300)
            Utils.fadeOutAnim(background, 300)
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If sliding panel expanded, collapse
        if (slidingLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            slidingLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            Utils.fadeOutAnim(background, 300)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If filter overlay visible, hide it
        if (filterBackground.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeOutAnim(filterBackground, 150)
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If search bar visible, close it
        if (searchBar.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(searchBar, 300)
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box), 300)
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