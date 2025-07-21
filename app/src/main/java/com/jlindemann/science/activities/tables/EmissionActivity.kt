package com.jlindemann.science.activities.tables

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.adapter.EmissionAdapter
import com.jlindemann.science.adapter.EquationsAdapter
import com.jlindemann.science.adapter.IonAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.Ion
import com.jlindemann.science.model.IonModel
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
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

        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) { }
            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                if (findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).panelState === SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    Utils.fadeOutAnim(findViewById<TextView>(R.id.background_emi), 300)
                    Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.emission_detail), 300)
                }
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
        }

        findViewById<FrameLayout>(R.id.view_emi).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        findViewById<ImageButton>(R.id.back_btn_emi).setOnClickListener { this.onBackPressed() }
    }

    override fun emiClickListener(item: Element, position: Int) {
        var jsonString : String? = null
        try {
            val ext = ".json"
            val element = item.element
            val elementJson: String? = "$element$ext"
            val inputStream: InputStream = assets.open(elementJson.toString())
            jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val jsonObject: JSONObject = jsonArray.getJSONObject(0)

            val url = jsonObject.optString("short", "---")
            val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
            val extg = ".gif"
            val fURL = hUrl + url + extg
            findViewById<TextView>(R.id.emi_title).text = item.element.capitalize()
            try {
                Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.emi_img_detail))
                Utils.fadeInAnimBack(findViewById<TextView>(R.id.background_emi), 300)
            }
            catch(e: ConnectException) {
                //findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
                //findViewById<TextView>(R.id.sp_offline).text = "No Data"
                //findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
            }
        }
        catch (e: IOException) { }
        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.emission_detail), 300)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.emi_view)
        val emiList = ArrayList<Element>()
        ElementModel.getList(emiList)
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
        val handler = android.os.Handler()
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
        findViewById<ImageButton>(R.id.search_btn_emi).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_bar_emi), 150)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.title_box), 1)
            findViewById<EditText>(R.id.edit_emi).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_emi), InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<ImageButton>(R.id.close_ele_search_emi).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_bar_emi), 1)
            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun onBackPressed() {
        if (findViewById<TextView>(R.id.background_emi).visibility == View.VISIBLE) {
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout_e).panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return
        }
        else {
            super.onBackPressed()
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

}



