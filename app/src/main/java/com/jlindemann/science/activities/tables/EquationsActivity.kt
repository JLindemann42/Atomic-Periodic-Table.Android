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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.EquationsAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.EquationModel
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_equations.*
import kotlinx.android.synthetic.main.equations_info.*
import java.util.*
import kotlin.collections.ArrayList


class EquationsActivity : BaseActivity(), EquationsAdapter.OnEquationClickListener  {
    private var equationList = ArrayList<Equation>()
    var mAdapter = EquationsAdapter(equationList, this, this)

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
        setContentView(R.layout.activity_equations) //REMEMBER: Never move any function calls above this

        recyclerView()
        clickSearch()
        e_back_btn.setOnClickListener { hideInfoPanel() }
        l_background_e.setOnClickListener { hideInfoPanel() }

        view_equ.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        back_btn_equ.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = equ_recycler.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            equ_recycler.layoutParams = params

            val params2 = common_title_back_equ.layoutParams as ViewGroup.LayoutParams
            params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_equ.layoutParams = params2

    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.equ_recycler)
        val equation = ArrayList<Equation>()

        EquationModel.getList(equation)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = EquationsAdapter(equation, this, this)
        recyclerView.adapter = adapter

        equation.sortWith(Comparator { lhs, rhs ->
            if (lhs.equationTitle < rhs.equationTitle) -1 else if (lhs.equationTitle < rhs.equationTitle) 1 else 0
        })

        adapter.notifyDataSetChanged()

        edit_equ.addTextChangedListener(object : TextWatcher {
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
                filter(s.toString(), equation, recyclerView)
            }
        })
    }

    override fun onBackPressed() {
        if (e_inc.visibility == View.VISIBLE) {
            hideInfoPanel()
            return
        } else { super.onBackPressed() }
    }

    private fun filter(text: String, list: ArrayList<Equation>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Equation> = ArrayList()
        for (item in list) {
            if (item.equationTitle.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
            }
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = EquationsAdapter(filteredList, this, this)
    }

    private fun clickSearch() {
        search_btn_equ.setOnClickListener {
            Utils.fadeInAnim(search_bar_equ, 150)

            val delayOpen = Handler()
            delayOpen.postDelayed({
                Utils.fadeOutAnim(title_box_equ, 150)
            }, 151)

            edit_equ.requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_equ, InputMethodManager.SHOW_IMPLICIT)
        }
        close_equ_search.setOnClickListener {
            Utils.fadeOutAnim(search_bar_equ, 150)

            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(title_box_equ, 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun equationClickListener(item: Equation, position: Int) {
        showInfoPanel(item.equation, item.description)
    }

    private fun showInfoPanel(title: Int, text: String) {
        Anim.fadeIn(e_inc, 150)

        e_title.setImageResource(title)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 1) {
            e_title.colorFilter = ColorMatrixColorFilter(NEGATIVE)
        }
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> { e_title.colorFilter = ColorMatrixColorFilter(NEGATIVE) }
            }
        }
        e_text.text = text
    }

    private fun hideInfoPanel() {
        Anim.fadeOutAnim(e_inc, 150)
    }

    private val NEGATIVE = floatArrayOf(
        -1.0f, 0f, 0f, 0f, 255f,
        0f, -1.0f, 0f, 0f, 255f,
        0f, 0f, -1.0f, 0f, 255f,
        0f, 0f, 0f, 1.0f, 0f
    )
}



