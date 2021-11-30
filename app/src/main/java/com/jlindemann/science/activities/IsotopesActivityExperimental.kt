package com.jlindemann.science.activities

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.android.synthetic.main.activity_favorite_settings_page.*
import kotlinx.android.synthetic.main.activity_isotopes_experimental.*
import kotlinx.android.synthetic.main.activity_isotopes_experimental.title_box
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_nuclide.*
import kotlinx.android.synthetic.main.filter_view.*
import kotlinx.android.synthetic.main.filter_view_iso.*
import kotlinx.android.synthetic.main.isotope_panel.*
import kotlinx.android.synthetic.main.search_layout.*
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
        sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val adapter = IsotopeAdapter(elements, this, this)
        recyclerView.adapter = adapter

        edit_iso.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), elements, recyclerView) }
        })

        sliding_layout_i.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) { }
            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                if (sliding_layout_i.panelState === SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    Utils.fadeOutAnim(background_i2, 300)
                    Utils.fadeOutAnim(slid_panel, 300)
                }
            }
        })

        background_i2.setOnClickListener{
            if (panel_info.visibility == View.VISIBLE) {
                Utils.fadeOutAnim(panel_info, 300)
                Utils.fadeOutAnim(background_i2, 300)
            }
            else {
                Utils.fadeOutAnim(sliding_layout_i, 300)
                Utils.fadeOutAnim(background_i2, 300)
            }
        }

        view1.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        clickSearch()
        searchFilter(elements, recyclerView)
        sentIsotope()
        back_btn.setOnClickListener { this.onBackPressed() }
    }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        filter_btn2.setOnClickListener {
            Utils.fadeInAnim(iso_filter_box, 150)
            Utils.fadeInAnim(filter_background, 150)
        }
        filter_background.setOnClickListener {
            Utils.fadeOutAnim(iso_filter_box, 150)
            Utils.fadeOutAnim(filter_background, 150)
        }
        iso_alphabet_btn.setOnClickListener {
            val isoPreference = IsoPreferences(this)
            isoPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(iso_filter_box, 150)
            Utils.fadeOutAnim(filter_background, 150)
            filtList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element < rhs.element) 1 else 0
            })
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = IsotopeAdapter(filtList, this, this)
        }
        iso_element_numb_btn.setOnClickListener {
            val isoPreference = IsoPreferences(this)
            isoPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(iso_filter_box, 150)
            Utils.fadeOutAnim(filter_background, 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = IsotopeAdapter(filtList, this, this)
        }
    }

    private fun clickSearch() {
        search_btn.setOnClickListener {
            Utils.fadeInAnim(search_bar_iso, 300)
            Utils.fadeOutAnim(title_box, 300)

            edit_iso.requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_iso, InputMethodManager.SHOW_IMPLICIT)
        }
        close_iso_search.setOnClickListener {
            Utils.fadeOutAnim(search_bar_iso, 300)
            Utils.fadeInAnim(title_box, 300)

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
            if (item.element.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        if (isoPrefValue == 0) {
            filteredList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element < rhs.element) 1 else 0
            })
        }
        val handler = android.os.Handler()
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(empty_search_box_iso, 300)
            }
            else {
                empty_search_box_iso.visibility = View.GONE
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

        Utils.fadeInAnimBack(background_i2, 300)
        Utils.fadeInAnim(slid_panel, 300)
        sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    private fun sentIsotope() {
        val isoSent = sendIso(this)
        if (isoSent.getValue() == "true") {
            drawCard(elementList)
            Utils.fadeInAnimBack(background_i2, 300)
            Utils.fadeInAnim(slid_panel, 300)
            sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            isoSent.setValue("false")
        }
    }

    override fun onBackPressed() {
        if (background_i2.visibility == View.VISIBLE) {
            sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return
        }
        if (filter_background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(filter_background, 150)
            Utils.fadeOutAnim(iso_filter_box, 150)
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

                    frame_iso.removeAllViews()

                    val aLayout = frame_iso
                    val inflater = layoutInflater
                    val fLayout: View = inflater.inflate(R.layout.row_iso_panel_title_item, aLayout, false)

                    val iTitle = fLayout.findViewById(R.id.iso_title) as TextView
                    val iExt = " Isotopes"
                    iTitle.text = "${nameVal.capitalize()}$iExt"

                    aLayout.addView(fLayout)

                    for (i in 1..item.isotopes) {
                        val mainLayout = frame_iso
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
        r_view.setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))
        val params2 = common_title_back_iso.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_iso.layoutParams = params2

        val params3 = sliding_layout_i.layoutParams as ViewGroup.MarginLayoutParams
        params3.topMargin = top + resources.getDimensionPixelSize(R.dimen.panel_margin)
        sliding_layout_i.layoutParams = params3

        val searchEmptyImgPrm = empty_search_box_iso.layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        empty_search_box_iso.layoutParams = searchEmptyImgPrm
    }
}



