package com.jlindemann.science.activities

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.adapter.IsotopeAdapter
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.activity_favorite_settings_page.*
import kotlinx.android.synthetic.main.activity_isotopes_experimental.*
import kotlinx.android.synthetic.main.activity_isotopes_experimental.title_box
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

        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = IsotopeAdapter(filteredList, this, this)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        r_view.setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))
        val params2 = common_title_back_iso.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_iso.layoutParams = params2

    }

    override fun elementClickListener(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)
        drawCard()

        Utils.fadeInAnimBack(background_i2, 300)
        Utils.fadeInAnim(slid_panel, 300)
        sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    private fun sentIsotope() {
        val isoSent = sendIso(this)
        if (isoSent.getValue() == "true") {
            drawCard()
            Utils.fadeInAnimBack(background_i2, 300)
            Utils.fadeInAnim(slid_panel, 300)
            sliding_layout_i.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
            isoSent.setValue("false")
        }
    }

    override fun onBackPressed() {
        if (background_i2.visibility == View.VISIBLE) {
            return
        }
        if (filter_background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(filter_background, 150)
            Utils.fadeOutAnim(iso_filter_box, 150)
        }
        else {
            super.onBackPressed()
        }
    }

    private fun drawCard() {
        var jsonstring : String? = null

        try {
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
            val name = ElementSendAndLoadValue
            val ext = ".json"
            val iext = " Isotopes"
            val ElementJson: String? = "$name$ext"
            val inputStream: InputStream = assets.open(ElementJson.toString())
            jsonstring = inputStream.bufferedReader().use { it.readText() }

            val jsonArray = JSONArray(jsonstring)
            val jsonObject: JSONObject = jsonArray.getJSONObject(0)

            val element= jsonObject.optString("element", "---")
            val elementProtons = jsonObject.optString("element_protons", "---")
            val elementNeutronsCommon = jsonObject.optString("element_neutron_common", "---")
            val elementElectrons = jsonObject.optString("element_electrons", "---")

            val element_iso_name_1 = jsonObject.optString("iso_1", "---")
            val element_z_1 = jsonObject.optString("iso_Z_1", "---")
            val element_n_1 = jsonObject.optString("iso_N_1", "---")
            val element_a_1 = jsonObject.optString("iso_A_1", "---")
            val element_half_1 = jsonObject.optString("iso_half_1", "---")
            val element_mass_1 = jsonObject.optString("iso_mass_1", "---")
            val element_iso_name_2 = jsonObject.optString("iso_2", "---")
            val element_z_2 = jsonObject.optString("iso_Z_2", "---")
            val element_n_2 = jsonObject.optString("iso_N_2", "---")
            val element_a_2 = jsonObject.optString("iso_A_2", "---")
            val element_half_2 = jsonObject.optString("iso_half_2", "---")
            val element_mass_2 = jsonObject.optString("iso_mass_2", "---")
            val element_iso_name_3 = jsonObject.optString("iso_3", "---")
            val element_z_3 = jsonObject.optString("iso_Z_3", "---")
            val element_n_3 = jsonObject.optString("iso_N_3", "---")
            val element_a_3 = jsonObject.optString("iso_A_3", "---")
            val element_half_3 = jsonObject.optString("iso_half_3", "---")
            val element_mass_3 = jsonObject.optString("iso_mass_3", "---")
            val element_iso_name_4 = jsonObject.optString("iso_4", "---")
            val element_z_4 = jsonObject.optString("iso_Z_4", "---")
            val element_n_4 = jsonObject.optString("iso_N_4", "---")
            val element_a_4 = jsonObject.optString("iso_A_4", "---")
            val element_half_4 = jsonObject.optString("iso_half_4", "---")
            val element_mass_4 = jsonObject.optString("iso_mass_4", "---")
            val element_iso_name_5 = jsonObject.optString("iso_5", "---")
            val element_z_5 = jsonObject.optString("iso_Z_5", "---")
            val element_n_5 = jsonObject.optString("iso_N_5", "---")
            val element_a_5 = jsonObject.optString("iso_A_5", "---")
            val element_half_5 = jsonObject.optString("iso_half_5", "---")
            val element_mass_5 = jsonObject.optString("iso_mass_5", "---")
            val element_iso_name_6 = jsonObject.optString("iso_6", "---")
            val element_z_6 = jsonObject.optString("iso_Z_6", "---")
            val element_n_6 = jsonObject.optString("iso_N_6", "---")
            val element_a_6 = jsonObject.optString("iso_A_6", "---")
            val element_half_6 = jsonObject.optString("iso_half_6", "---")
            val element_mass_6 = jsonObject.optString("iso_mass_6", "---")
            val element_iso_name_7 = jsonObject.optString("iso_7", "---")
            val element_z_7 = jsonObject.optString("iso_Z_7", "---")
            val element_n_7 = jsonObject.optString("iso_N_7", "---")
            val element_a_7 = jsonObject.optString("iso_A_7", "---")
            val element_half_7 = jsonObject.optString("iso_half_7", "---")
            val element_mass_7 = jsonObject.optString("iso_mass_7", "---")
            val element_iso_name_8 = jsonObject.optString("iso_8", "---")
            val element_z_8 = jsonObject.optString("iso_Z_8", "---")
            val element_n_8 = jsonObject.optString("iso_N_8", "---")
            val element_a_8 = jsonObject.optString("iso_A_8", "---")
            val element_half_8 = jsonObject.optString("iso_half_8", "---")
            val element_mass_8 = jsonObject.optString("iso_mass_8", "---")
            val element_iso_name_9 = jsonObject.optString("iso_9", "---")
            val element_z_9 = jsonObject.optString("iso_Z_9", "---")
            val element_n_9 = jsonObject.optString("iso_N_9", "---")
            val element_a_9 = jsonObject.optString("iso_A_9", "---")
            val element_half_9 = jsonObject.optString("iso_half_9", "---")
            val element_mass_9 = jsonObject.optString("iso_mass_9", "---")
            val element_iso_name_10 = jsonObject.optString("iso_10", "---")
            val element_z_10 = jsonObject.optString("iso_Z_10", "---")
            val element_n_10 = jsonObject.optString("iso_N_10", "---")
            val element_a_10 = jsonObject.optString("iso_A_10", "---")
            val element_half_10 = jsonObject.optString("iso_half_10", "---")
            val element_mass_10 = jsonObject.optString("iso_mass_10", "---")
            val element_iso_name_11 = jsonObject.optString("iso_11", "---")
            val element_z_11 = jsonObject.optString("iso_Z_11", "---")
            val element_n_11 = jsonObject.optString("iso_N_11", "---")
            val element_a_11 = jsonObject.optString("iso_A_11", "---")
            val element_half_11 = jsonObject.optString("iso_half_11", "---")
            val element_mass_11 = jsonObject.optString("iso_mass_11", "---")
            val element_iso_name_12 = jsonObject.optString("iso_12", "---")
            val element_z_12 = jsonObject.optString("iso_Z_12", "---")
            val element_n_12 = jsonObject.optString("iso_N_12", "---")
            val element_a_12 = jsonObject.optString("iso_A_12", "---")
            val element_half_12 = jsonObject.optString("iso_half_12", "---")
            val element_mass_12 = jsonObject.optString("iso_mass_12", "---")

            val element_iso_name_13 = jsonObject.optString("iso_13", "---")
            val element_z_13 = jsonObject.optString("iso_Z_13", "---")
            val element_n_13 = jsonObject.optString("iso_N_13", "---")
            val element_a_13 = jsonObject.optString("iso_A_13", "---")
            val element_half_13 = jsonObject.optString("iso_half_13", "---")
            val element_mass_13 = jsonObject.optString("iso_mass_13", "---")
            val element_iso_name_14 = jsonObject.optString("iso_14", "---")
            val element_z_14 = jsonObject.optString("iso_Z_14", "---")
            val element_n_14 = jsonObject.optString("iso_N_14", "---")
            val element_a_14 = jsonObject.optString("iso_A_14", "---")
            val element_half_14 = jsonObject.optString("iso_half_14", "---")
            val element_mass_14 = jsonObject.optString("iso_mass_14", "---")
            val element_iso_name_15 = jsonObject.optString("iso_15", "---")
            val element_z_15 = jsonObject.optString("iso_Z_15", "---")
            val element_n_15 = jsonObject.optString("iso_N_15", "---")
            val element_a_15 = jsonObject.optString("iso_A_15", "---")
            val element_half_15 = jsonObject.optString("iso_half_15", "---")
            val element_mass_15 = jsonObject.optString("iso_mass_15", "---")

            card_title.text = "$element$iext"
            element_electrons_i.text = elementElectrons
            element_protons_i.text = elementProtons
            element_neutrons_common_i.text = elementNeutronsCommon

            val half = "Halftime: "
            val half1 = "$half$element_half_1"
            val half2 = "$half$element_half_2"
            val half3 = "$half$element_half_3"
            val half4 = "$half$element_half_4"
            val half5 = "$half$element_half_5"
            val half6 = "$half$element_half_6"
            val half7 = "$half$element_half_7"
            val half8 = "$half$element_half_8"
            val half9 = "$half$element_half_9"
            val half10 = "$half$element_half_10"
            val half11 = "$half$element_half_11"
            val half12 = "$half$element_half_12"
            val half13 = "$half$element_half_13"
            val half14 = "$half$element_half_14"
            val half15 = "$half$element_half_15"
            val Mass = "Mass: "
            val u = "u"
            val mass1 = "$Mass$element_mass_1$u"
            val mass2 = "$Mass$element_mass_2$u"
            val mass3 = "$Mass$element_mass_3$u"
            val mass4 = "$Mass$element_mass_4$u"
            val mass5 = "$Mass$element_mass_5$u"
            val mass6 = "$Mass$element_mass_6$u"
            val mass7 = "$Mass$element_mass_7$u"
            val mass8 = "$Mass$element_mass_8$u"
            val mass9 = "$Mass$element_mass_9$u"
            val mass10 = "$Mass$element_mass_10$u"
            val mass11 = "$Mass$element_mass_11$u"
            val mass12 = "$Mass$element_mass_12$u"
            val mass13 = "$Mass$element_mass_13$u"
            val mass14 = "$Mass$element_mass_14$u"
            val mass15 = "$Mass$element_mass_15$u"

            iso_name_1.text = element_iso_name_1
            iso_z_1.text = element_z_1
            iso_n_1.text = element_n_1
            iso_a_1.text = element_a_1
            iso_half_1.text = half1
            iso_massa_1.text = mass1
            iso_name_2.text = element_iso_name_2
            iso_z_2.text = element_z_2
            iso_n_2.text = element_n_2
            iso_a_2.text = element_a_2
            iso_half_2.text = half2
            iso_massa_2.text = mass2
            iso_name_3.text = element_iso_name_3
            iso_z_3.text = element_z_3
            iso_n_3.text = element_n_3
            iso_a_3.text = element_a_3
            iso_half_3.text = half3
            iso_massa_3.text = mass3
            iso_name_4.text = element_iso_name_4
            iso_z_4.text = element_z_4
            iso_n_4.text = element_n_4
            iso_a_4.text = element_a_4
            iso_half_4.text = half4
            iso_massa_4.text = mass4
            iso_name_5.text = element_iso_name_5
            iso_z_5.text = element_z_5
            iso_n_5.text = element_n_5
            iso_a_5.text = element_a_5
            iso_half_5.text = half5
            iso_massa_5.text = mass5
            iso_name_6.text = element_iso_name_6
            iso_z_6.text = element_z_6
            iso_n_6.text = element_n_6
            iso_a_6.text = element_a_6
            iso_half_6.text = half6
            iso_massa_6.text = mass6
            iso_name_7.text = element_iso_name_7
            iso_z_7.text = element_z_7
            iso_n_7.text = element_n_7
            iso_a_7.text = element_a_7
            iso_half_7.text = half7
            iso_massa_7.text = mass7
            iso_name_8.text = element_iso_name_8
            iso_z_8.text = element_z_8
            iso_n_8.text = element_n_8
            iso_a_8.text = element_a_8
            iso_half_8.text = half8
            iso_massa_8.text = mass8
            iso_name_9.text = element_iso_name_9
            iso_z_9.text = element_z_9
            iso_n_9.text = element_n_9
            iso_a_9.text = element_a_9
            iso_half_9.text = half9
            iso_massa_9.text = mass9
            iso_name_10.text = element_iso_name_10
            iso_z_10.text = element_z_10
            iso_n_10.text = element_n_10
            iso_a_10.text = element_a_10
            iso_half_10.text = half10
            iso_massa_10.text = mass10
            iso_name_11.text = element_iso_name_11
            iso_z_11.text = element_z_11
            iso_n_11.text = element_n_11
            iso_a_11.text = element_a_11
            iso_half_11.text = half11
            iso_massa_11.text = mass11
            iso_name_12.text = element_iso_name_12
            iso_z_12.text = element_z_12
            iso_n_12.text = element_n_12
            iso_a_12.text = element_a_12
            iso_half_12.text = half12
            iso_massa_12.text = mass12

            iso_name_13.text = element_iso_name_13
            iso_z_13.text = element_z_13
            iso_n_13.text = element_n_13
            iso_a_13.text = element_a_13
            iso_half_13.text = half13
            iso_massa_13.text = mass13
            iso_name_14.text = element_iso_name_14
            iso_z_14.text = element_z_14
            iso_n_14.text = element_n_14
            iso_a_14.text = element_a_14
            iso_half_14.text = half14
            iso_massa_14.text = mass14
            iso_name_15.text = element_iso_name_15
            iso_z_15.text = element_z_15
            iso_n_15.text = element_n_15
            iso_a_15.text = element_a_15
            iso_half_15.text = half15
            iso_massa_15.text = mass15

            val delay = Handler()

            box_2.visibility = View.VISIBLE
            box_3.visibility = View.VISIBLE
            box_4.visibility = View.VISIBLE
            box_5.visibility = View.VISIBLE
            box_6.visibility = View.VISIBLE
            box_7.visibility = View.VISIBLE
            box_8.visibility = View.VISIBLE
            box_9.visibility = View.VISIBLE
            box_10.visibility = View.VISIBLE
            box_11.visibility = View.VISIBLE
            box_12.visibility = View.VISIBLE
            box_13.visibility = View.VISIBLE
            box_15.visibility = View.VISIBLE

            delay.postDelayed({
                if (iso_name_2.text == "---") {
                    box_2.visibility = View.GONE
                }
                if (iso_name_3.text == "---") {
                    box_3.visibility = View.GONE
                }
                if (iso_name_4.text == "---") {
                    box_4.visibility = View.GONE
                }
                if (iso_name_5.text == "---") {
                    box_5.visibility = View.GONE
                }
                if (iso_name_6.text == "---") {
                    box_6.visibility = View.GONE
                }
                if (iso_name_7.text == "---") {
                    box_7.visibility = View.GONE
                }
                if (iso_name_8.text == "---") {
                    box_8.visibility = View.GONE
                }
                if (iso_name_9.text == "---") {
                    box_9.visibility = View.GONE
                }
                if (iso_name_10.text == "---") {
                    box_10.visibility = View.GONE
                }
                if (iso_name_11.text == "---") {
                    box_11.visibility = View.GONE
                }
                if (iso_name_12.text == "---") {
                    box_12.visibility = View.GONE
                }
                if (iso_name_13.text == "---") {
                    box_13.visibility = View.GONE
                }
                if (iso_name_14.text == "---") {
                    box_14.visibility = View.GONE
                }
                if (iso_name_15.text == "---") {
                    box_15.visibility = View.GONE
                }
            }, 5)

        }
        catch (e: IOException) {


            ToastUtil.showToast(this, "Couldn't load Data")
        }

    }

}



