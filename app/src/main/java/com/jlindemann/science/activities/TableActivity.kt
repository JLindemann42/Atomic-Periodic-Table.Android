package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tables.*
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference

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

        findViewById<FrameLayout>(R.id.view_sub).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_table_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.tables_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.table_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.table_scroll).getScrollY() > 150) {
                        findViewById<FrameLayout>(R.id.common_title_table_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tables_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tables_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_table_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.tables_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.tables_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.table_scroll).getScrollY().toFloat()
                }
            })

        tableListeners()

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }
        //Update tables depending on PRO or Not:
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        if (proPrefValue == 1) {
            findViewById<TextView>(R.id.pro_poi_text).text = "PRO-Version"
            findViewById<TextView>(R.id.pro_poi_text).visibility = View.VISIBLE
            findViewById<TextView>(R.id.pro_nuc_text).text = "PRO-Version"
            findViewById<TextView>(R.id.pro_nuc_text).visibility = View.VISIBLE
            findViewById<TextView>(R.id.pro_con_text).text = "PRO-Version"
            findViewById<TextView>(R.id.pro_con_text).visibility = View.VISIBLE
            findViewById<TextView>(R.id.pro_geo_text).text = "PRO-Version"
            findViewById<TextView>(R.id.pro_geo_text).visibility = View.VISIBLE
        }
        if (proPrefValue == 100) {
            findViewById<TextView>(R.id.pro_poi_text).visibility = View.GONE
            findViewById<TextView>(R.id.pro_nuc_text).visibility = View.GONE
            findViewById<TextView>(R.id.pro_con_text).visibility = View.GONE
            findViewById<TextView>(R.id.pro_geo_text).visibility = View.GONE
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams = params

            val params2 = findViewById<TextView>(R.id.tables_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.tables_title_downstate).layoutParams = params2
    }

    private fun tableListeners() {
        findViewById<FrameLayout>(R.id.sol_table).setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.sol_button).setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.ele_table).setOnClickListener {
            val intent = Intent(this, ElectrodeActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.ele_button).setOnClickListener {
            val intent = Intent(this, ElectrodeActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.equ_table).setOnClickListener {
            val intent = Intent(this, EquationsActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.equ_button).setOnClickListener {
            val intent = Intent(this, EquationsActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.ion_table).setOnClickListener {
            val intent = Intent(this, IonActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.ion_button).setOnClickListener {
            val intent = Intent(this, IonActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.nuc_table).setOnClickListener {
            val intent = Intent(this, NuclideActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.nuc_button).setOnClickListener {
            val intent = Intent(this, NuclideActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.ph_table).setOnClickListener {
            val intent = Intent(this, phActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.ph_button).setOnClickListener {
            val intent = Intent(this, phActivity::class.java)
            startActivity(intent)
        }
        findViewById<FrameLayout>(R.id.poi_table).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, PoissonActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<TextView>(R.id.poi_button).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, PoissonActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<FrameLayout>(R.id.nuc_table).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, NuclideActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<TextView>(R.id.nuc_button).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, NuclideActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<FrameLayout>(R.id.con_table).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, ConstantsActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<TextView>(R.id.con_button).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, ConstantsActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<FrameLayout>(R.id.geo_table).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, ConstantsActivity::class.java)
                startActivity(intent)
            }
        }
        findViewById<TextView>(R.id.geo_button).setOnClickListener {
            val proPref = ProVersion(this)
            val proPrefValue = proPref.getValue()
            if (proPrefValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent)
            }
            if (proPrefValue == 100) {
                val intent = Intent(this, ConstantsActivity::class.java)
                startActivity(intent)
            }
        }
    }

}



