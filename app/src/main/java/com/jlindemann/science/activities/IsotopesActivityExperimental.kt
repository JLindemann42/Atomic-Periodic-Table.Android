package com.jlindemann.science.activities

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.adapter.ElementAdapter
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
            }
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
        }
        findViewById<TextView>(R.id.filter_background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.filter_background), 150)
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
        }
    }

    private fun clickSearch() {
        findViewById<ImageButton>(R.id.search_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_bar_iso), 300)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.title_box), 300)

            findViewById<EditText>(R.id.edit_iso).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_iso), InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<ImageButton>(R.id.close_iso_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_bar_iso), 300)
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box), 300)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
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
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_iso), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_iso).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = IsotopeAdapter(filteredList, this, this)
    }

    override fun elementClickListener(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)
        drawCard(elementList)

        Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_i2), 300)
        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    private fun sentIsotope() {
        val isoSent = sendIso(this)
        if (isoSent.getValue() == "true") {
            drawCard(elementList)
            Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_i2), 300)
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.slid_panel), 300)
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            isoSent.setValue("false")
        }
    }

    override fun onBackPressed() {
        if (findViewById<TextView>(R.id.background_i2).visibility == View.VISIBLE) {
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_i).panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return
        }
        if (findViewById<TextView>(R.id.filter_background).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<TextView>(R.id.filter_background), 150)
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.iso_filter_box), 150)
            return
        }
        else {
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
                if (item.element.capitalize() == nameVal?.capitalize()) {
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
                    iTitle.text = "${nameVal.capitalize()}$iExt"

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
}



