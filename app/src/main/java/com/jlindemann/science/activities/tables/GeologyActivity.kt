package com.jlindemann.science.activities.tables

import android.content.Context
import android.content.res.Configuration
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.ElectrodeAdapter
import com.jlindemann.science.adapter.EquationsAdapter
import com.jlindemann.science.adapter.GeologyAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.model.DictionaryModel
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.Geology
import com.jlindemann.science.model.GeologyModel
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.preferences.DictionaryPreferences
import com.jlindemann.science.preferences.GeologyPreference
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList


class GeologyActivity : BaseActivity(), GeologyAdapter.OnGeologyClickListener {
    private var geologyList = ArrayList<Geology>()
    var mAdapter = GeologyAdapter(geologyList, this, this)

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

        val recyclerView = findViewById<RecyclerView>(R.id.geo_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val item = ArrayList<Geology>()
        GeologyModel.getList(item)

        recyclerView()
        clickSearch()
        chipListeners(item, recyclerView)
        findViewById<Button>(R.id.clear_btn).visibility = View.GONE

        findViewById<FrameLayout>(R.id.view_geo).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        findViewById<TextView>(R.id.detail_background_geo).setOnClickListener { Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.geo_details), 300) }
        findViewById<ImageButton>(R.id.back_btn_geo).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun geologyClickListener(item: Geology, position: Int) {
        // Set textViews:
        findViewById<TextView>(R.id.geo_detail_title).text = item.name
        findViewById<TextView>(R.id.geo_type).text = "Type: " + item.type
        findViewById<TextView>(R.id.geo_group).text = "Group: " + item.group
        findViewById<TextView>(R.id.geo_color).text = "Color: " + item.color
        findViewById<TextView>(R.id.geo_strike).text = "Streak: " + item.streak
        findViewById<TextView>(R.id.geo_cristal).text = "Cristal Structure: " + item.cristal
        findViewById<TextView>(R.id.geo_hardness).text = "Hardness: " + item.hardness
        findViewById<TextView>(R.id.geo_density).text = "Density: " + item.density
        findViewById<TextView>(R.id.geo_magnetism).text = "Magnetism: " +item.magnetism
        findViewById<TextView>(R.id.geo_hydrochloride).text = item.hydrochloride

        //Fade in geo_details
        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.geo_details), 300)
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.geo_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = findViewById<FrameLayout>(R.id.common_title_back_geo).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<FrameLayout>(R.id.common_title_back_geo).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_geo).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_geo).layoutParams = searchEmptyImgPrm
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.geo_view)
        val geology = ArrayList<Geology>()

        GeologyModel.getList(geology)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = GeologyAdapter(geology, this, this)
        recyclerView.adapter = adapter

        adapter.notifyDataSetChanged()

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "geo"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        findViewById<EditText>(R.id.edit_geo).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), geology, recyclerView)
            }
        })
    }



    //Filters the listView by different sorts of material by using the geossonPreference to filter by the stringValue.
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
        val handler = android.os.Handler()
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

    private fun clickSearch() {
        findViewById<ImageButton>(R.id.search_btn_geo).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_bar_geo), 150)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.title_box_geo), 1)

            findViewById<EditText>(R.id.edit_geo).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_geo), InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<ImageButton>(R.id.close_geo_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_bar_geo), 1)

            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box_geo), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun chipListeners(list: ArrayList<Geology>, recyclerView: RecyclerView) {
        findViewById<Button>(R.id.rocks_btn).setOnClickListener {
            updateButtonColor("rocks_btn")
            val geoPreference = GeologyPreference(this)
            geoPreference.setValue("rock")
            findViewById<EditText>(R.id.edit_geo).setText("test")
            findViewById<EditText>(R.id.edit_geo).setText("")
        }
        findViewById<Button>(R.id.soils_btn).setOnClickListener {
            updateButtonColor("soils_btn")
            val geoPreference = GeologyPreference(this)
            geoPreference.setValue("soil")
            findViewById<EditText>(R.id.edit_geo).setText("test")
            findViewById<EditText>(R.id.edit_geo).setText("")
        }
        findViewById<Button>(R.id.minerals_btn).setOnClickListener {
            updateButtonColor("minerals_btn")
            val geoPreference = GeologyPreference(this)
            geoPreference.setValue("mineral")
            findViewById<EditText>(R.id.edit_geo).setText("test")
            findViewById<EditText>(R.id.edit_geo).setText("")
        }
    }

    private fun updateButtonColor(btn: String) {
        findViewById<Button>(R.id.rocks_btn).background = getDrawable(R.drawable.chip)
        findViewById<Button>(R.id.soils_btn).background = getDrawable(R.drawable.chip)
        findViewById<Button>(R.id.minerals_btn).background = getDrawable(R.drawable.chip)

        val delay = Handler()
        delay.postDelayed({
            val resIDB = resources.getIdentifier(btn, "id", packageName)
            val button = findViewById<Button>(resIDB)
            button.background = getDrawable(R.drawable.chip_active)
        }, 200)

        findViewById<Button>(R.id.clear_btn).visibility = View.VISIBLE
        findViewById<Button>(R.id.clear_btn).setOnClickListener {
            val resIDB = resources.getIdentifier(btn, "id", packageName)
            val button = findViewById<Button>(resIDB)
            val geoPreference = GeologyPreference(this)
            button.background = getDrawable(R.drawable.chip)
            geoPreference.setValue("")
            findViewById<EditText>(R.id.edit_geo).setText("test")
            findViewById<EditText>(R.id.edit_geo).setText("")
            findViewById<Button>(R.id.clear_btn).visibility = View.GONE
        }
    }
}



