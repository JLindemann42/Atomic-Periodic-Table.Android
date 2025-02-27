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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.ConstantsAdapter
import com.jlindemann.science.adapter.DictionaryAdapter
import com.jlindemann.science.adapter.PoissonAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Constants
import com.jlindemann.science.model.ConstantsModel
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.PoissonModel
import com.jlindemann.science.preferences.ConstantsPreference
import com.jlindemann.science.preferences.DictionaryPreferences
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.PoissonPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList


class ConstantsActivity : BaseActivity(), ConstantsAdapter.OnConstantsClickListener {
    private var constantsList = ArrayList<Constants>()
    var mAdapter = ConstantsAdapter(constantsList, this, this)

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
        setContentView(R.layout.activity_constants) //REMEMBER: Never move any function calls above this

        val recyclerView = findViewById<RecyclerView>(R.id.con_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val itemCon = ArrayList<Constants>()
        ConstantsModel.getList(itemCon)

        recyclerView()
        chipListeners(itemCon, recyclerView)
        clickSearch()
        findViewById<Button>(R.id.clear_btn_con).visibility = View.GONE

        findViewById<FrameLayout>(R.id.view_con).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        findViewById<ImageButton>(R.id.back_btn_con).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.con_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = findViewById<FrameLayout>(R.id.common_title_back_con).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<FrameLayout>(R.id.common_title_back_con).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_con).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_con).layoutParams = searchEmptyImgPrm
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.con_view)
        val constants = ArrayList<Constants>()

        ConstantsModel.getList(constants)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = ConstantsAdapter(constants, this, this)
        recyclerView.adapter = adapter

        adapter.notifyDataSetChanged()

        findViewById<EditText>(R.id.edit_con).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), constants, recyclerView)
            }
        })
    }

    //Overrides the clickListener from PoissonAdapter to show InfoPanel when clicking on elments
    override fun constantsClickListener(item: Constants, position: Int) {
    }


    private fun clickSearch() {
        findViewById<ImageButton>(R.id.search_btn_con).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_bar_con), 150)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.title_box_con), 1)

            findViewById<EditText>(R.id.edit_con).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_con), InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<ImageButton>(R.id.close_con_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_bar_con), 1)

            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<FrameLayout>(R.id.title_box_con), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }


    //listen to button presses in filter
    private fun chipListeners(list: ArrayList<Constants>, recyclerView: RecyclerView) {
        val constantsPreference = ConstantsPreference(this)
        findViewById<Button>(R.id.mathematic_btn_con).setOnClickListener {
            updateButtonColor("math_btn_con")
            constantsPreference.setValue("mathematics")
            findViewById<EditText>(R.id.edit_con).setText("test")
            findViewById<EditText>(R.id.edit_con).setText("")
        }
        findViewById<Button>(R.id.physics_btn_con).setOnClickListener {
            updateButtonColor("physics_btn_con")
            constantsPreference.setValue("physics")
            findViewById<EditText>(R.id.edit_con).setText("test")
            findViewById<EditText>(R.id.edit_con).setText("")
        }
        findViewById<Button>(R.id.water_btn_con).setOnClickListener {
            updateButtonColor("water_btn_con")
            constantsPreference.setValue("water")
            findViewById<EditText>(R.id.edit_con).setText("test")
            findViewById<EditText>(R.id.edit_con).setText("")
        }
    }


    //Update colors of buttons after filtering
    private fun updateButtonColor(btn: String) {
        findViewById<Button>(R.id.mathematic_btn_con).background = getDrawable(R.drawable.chip)
        findViewById<Button>(R.id.physics_btn_con).background = getDrawable(R.drawable.chip)
        findViewById<Button>(R.id.water_btn_con).background = getDrawable(R.drawable.chip)

        val delay = Handler()
        delay.postDelayed({
            val resIDB = resources.getIdentifier(btn, "id", packageName)
            val button = findViewById<Button>(resIDB)
            button.background = getDrawable(R.drawable.chip_active)
        }, 200)

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "con"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        findViewById<Button>(R.id.clear_btn_con).visibility = View.VISIBLE
        findViewById<Button>(R.id.clear_btn_con).setOnClickListener {
            val resIDB = resources.getIdentifier(btn, "id", packageName)
            val button = findViewById<Button>(resIDB)
            val constantPreference = ConstantsPreference(this)
            button.background = getDrawable(R.drawable.chip)
            constantPreference.setValue("")
            findViewById<EditText>(R.id.edit_con).setText("test")
            findViewById<EditText>(R.id.edit_con).setText("")
            findViewById<Button>(R.id.clear_btn_con).visibility = View.GONE
        }
    }


    //Filters
    private fun filter(text: String, list: ArrayList<Constants>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Constants> = ArrayList()
        for (item in list) {
            val constantsPreference = ConstantsPreference(this)
            val constantsPrefValue = constantsPreference.getValue()
            if (item.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                if (item.category.toLowerCase(Locale.ROOT).contains(constantsPrefValue.toLowerCase(Locale.ROOT))) {
                    filteredList.add(item)
                }
            }
            val handler = android.os.Handler()
            handler.postDelayed({
                if (recyclerView.adapter!!.itemCount == 0) {
                    Anim.fadeIn(findViewById<AppCompatImageView
                            >(R.id.empty_con_search_image), 300)
                }
                else {
                    findViewById<AppCompatImageView>(R.id.empty_con_search_image).visibility = View.GONE
                }
            }, 10)
            mAdapter.notifyDataSetChanged()
            mAdapter.filterList(filteredList)
            recyclerView.adapter = ConstantsAdapter(filteredList, this, this)
        }
    }


}



