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
import com.jlindemann.science.preferences.MostUsedPreference
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
        mostUsedBar()

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

    private fun mostUsedBar() {
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()

        val regex = Regex("(\\w{3})=(\\d.\\d)")
        val matches = regex.findAll(mostUsedPrefValue).map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }.toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textView1: TextView = findViewById(R.id.most_1)
        val textView2: TextView = findViewById(R.id.most_2)
        val textView3: TextView = findViewById(R.id.most_3)
        val textView4: TextView = findViewById(R.id.most_4)
        val textView5: TextView = findViewById(R.id.most_5)
        val textView6: TextView = findViewById(R.id.most_6)
        val textView7: TextView = findViewById(R.id.most_7)
        val textView8: TextView = findViewById(R.id.most_8)
        val textView9: TextView = findViewById(R.id.most_9)

        val textViewList = listOf(textView1, textView2, textView3, textView4, textView5, textView6, textView7, textView8, textView9)

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                //Setup TextViews
                if (pair.first == "geo") {textViewList[index].text = getString(R.string.geo)}
                if (pair.first == "phi") {textViewList[index].text = getString(R.string.phi)}
                if (pair.first == "eqe") {textViewList[index].text = getString(R.string.eqe)}
                if (pair.first == "ion") {textViewList[index].text = getString(R.string.ion)}
                if (pair.first == "sol") {textViewList[index].text = getString(R.string.sol)}
                if (pair.first == "poi") {textViewList[index].text = getString(R.string.poi)}
                if (pair.first == "nuc") {textViewList[index].text = getString(R.string.nuc)}
                if (pair.first == "con") {textViewList[index].text = getString(R.string.con)}
                if (pair.first == "ele") {textViewList[index].text = getString(R.string.ele)}

                //Setup clickListener for non-pro
                if (proPrefValue==1) {
                    textViewList[index].setOnClickListener {
                        if (pair.first == "phi") {
                            val activity = phActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "eqe") {
                            val activity = EquationsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ion") {
                            val activity = IonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "sol") {
                            val activity = SolubilityActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ele") {
                            val activity = ElectrodeActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "poi") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "nuc") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "con") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "geo") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                        }
                    }
                }
                //Setup clickListener for pro
                if (proPrefValue==100) {
                    textViewList[index].setOnClickListener {
                        if (pair.first == "phi") {
                            val activity = phActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "eqe") {
                            val activity = EquationsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ion") {
                            val activity = IonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "sol") {
                            val activity = SolubilityActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ele") {
                            val activity = ElectrodeActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "poi") {
                            val activity = PoissonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "nuc") {
                            val activity = NuclideActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "con") {
                            val activity = ConstantsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "geo") {
                            val activity = GeologyActivity::class.java
                            val intent = Intent(this, activity)
                        }
                    }
                }
            }
        }
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
                val intent = Intent(this, GeologyActivity::class.java)
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
                val intent = Intent(this, GeologyActivity::class.java)
                startActivity(intent)
            }
        }
    }

}



