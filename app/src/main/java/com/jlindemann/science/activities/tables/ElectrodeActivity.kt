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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.ElectrodeAdapter
import com.jlindemann.science.model.Series
import com.jlindemann.science.model.SeriesModel
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_dictionary.search_btn
import kotlinx.android.synthetic.main.activity_dictionary.title_box
import kotlinx.android.synthetic.main.activity_electrode.*
import java.util.*
import kotlin.collections.ArrayList


class ElectrodeActivity : BaseActivity() {
    private var seriesList = ArrayList<Series>()
    var mAdapter = ElectrodeAdapter(seriesList, this, this)

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
        setContentView(R.layout.activity_electrode) //REMEMBER: Never move any function calls above this

        recyclerView()
        clickSearch()

        view_ele.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        e_view.setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val params2 = common_title_back_elo.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_elo.layoutParams = params2
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.e_view)
        val series = ArrayList<Series>()

        SeriesModel.getList(series)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = ElectrodeAdapter(series, this, this)
        recyclerView.adapter = adapter


        adapter.notifyDataSetChanged()

        edit_ele.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {}
            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ){}

            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), series, recyclerView)
            }
        })
    }

    private fun filter(text: String, list: ArrayList<Series>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Series> = ArrayList()
        for (item in list) {
            if (item.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
            }
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = ElectrodeAdapter(filteredList, this, this)
    }

    private fun clickSearch() {
        search_btn.setOnClickListener {
            Utils.fadeInAnim(search_bar_ele, 150)
            Utils.fadeOutAnim(title_box, 1)

            edit_ele.requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_ele, InputMethodManager.SHOW_IMPLICIT)
        }
        close_ele_search.setOnClickListener {
            Utils.fadeOutAnim(search_bar_ele, 1)

            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(title_box, 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }


}



