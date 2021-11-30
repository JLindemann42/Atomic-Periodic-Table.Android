package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.*
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.activity_solubility.back_btn
import kotlinx.android.synthetic.main.activity_submit.view_sub
import kotlinx.android.synthetic.main.activity_tables.*

class TableActivity : BaseActivity() {

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
        setContentView(R.layout.activity_tables)

        view_sub.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        common_title_table_color.visibility = View.INVISIBLE
        tables_title.visibility = View.INVISIBLE
        common_title_back_tab.elevation = (resources.getDimension(R.dimen.zero_elevation))
        table_scroll.getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (table_scroll.getScrollY() > 150) {
                        common_title_table_color.visibility = View.VISIBLE
                        tables_title.visibility = View.VISIBLE
                        tables_title_downstate.visibility = View.INVISIBLE
                        common_title_back_tab.elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        common_title_table_color.visibility = View.INVISIBLE
                        tables_title.visibility = View.INVISIBLE
                        tables_title_downstate.visibility = View.VISIBLE
                        common_title_back_tab.elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = table_scroll.getScrollY().toFloat()
                }
            })

        tableListeners()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = common_title_back_tab.layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_tab.layoutParams = params

            val params2 = tables_title_downstate.layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            tables_title_downstate.layoutParams = params2

    }

    private fun tableListeners() {
        sol_table.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        sol_button.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        ele_table.setOnClickListener {
            val intent = Intent(this, ElectrodeActivity::class.java)
            startActivity(intent)
        }
        ele_button.setOnClickListener {
            val intent = Intent(this, ElectrodeActivity::class.java)
            startActivity(intent)
        }
        equ_table.setOnClickListener {
            val intent = Intent(this, EquationsActivity::class.java)
            startActivity(intent)
        }
        equ_button.setOnClickListener {
            val intent = Intent(this, EquationsActivity::class.java)
            startActivity(intent)
        }
        ion_table.setOnClickListener {
            val intent = Intent(this, IonActivity::class.java)
            startActivity(intent)
        }
        ion_button.setOnClickListener {
            val intent = Intent(this, IonActivity::class.java)
            startActivity(intent)
        }
        nuc_table.setOnClickListener {
            val intent = Intent(this, NuclideActivity::class.java)
            startActivity(intent)
        }
        nuc_button.setOnClickListener {
            val intent = Intent(this, NuclideActivity::class.java)
            startActivity(intent)
        }
        ph_table.setOnClickListener {
            val intent = Intent(this, phActivity::class.java)
            startActivity(intent)
        }
        ph_button.setOnClickListener {
            val intent = Intent(this, phActivity::class.java)
            startActivity(intent)
        }
    }

}



