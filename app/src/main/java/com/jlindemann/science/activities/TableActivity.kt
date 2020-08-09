package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Insets
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.android.synthetic.main.activity_solubility.*
import kotlinx.android.synthetic.main.activity_solubility.back_btn
import kotlinx.android.synthetic.main.activity_submit.*
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

        tableListeners()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
            val params = common_title_back_tab.layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_tab.layoutParams = params

            val params2 = ph_table.layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + 36
            ph_table.layoutParams = params2
    }

    private fun tableListeners() {
        sol_table.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        ele_table.setOnClickListener {
            val intent = Intent(this, ElectrodeActivity::class.java)
            startActivity(intent)
        }
        equ_table.setOnClickListener {
            val intent = Intent(this, EquationsActivity::class.java)
            startActivity(intent)
        }
        ph_table.setOnClickListener {
            val intent = Intent(this, phActivity::class.java)
            startActivity(intent)
        }
    }

}



