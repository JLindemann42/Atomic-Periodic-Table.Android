package com.jlindemann.science.activities

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.R2.id.view
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_info.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.activity_solubility.*
import kotlinx.android.synthetic.main.activity_solubility.back_btn
import kotlinx.android.synthetic.main.solubility_group_1.*
import kotlinx.android.synthetic.main.solubility_group_2.*
import kotlinx.android.synthetic.main.solubility_group_3.*
import kotlinx.android.synthetic.main.solubility_group_4.*
import kotlinx.android.synthetic.main.solubility_group_5.*
import kotlinx.android.synthetic.main.solubility_group_6.*

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)
        
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
        setContentView(R.layout.activity_solubility)

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_info.layoutParams as ViewGroup.LayoutParams
        params.height += top
        common_title_back_info.layoutParams = params

        val params2 = imageView3.layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin += top
        imageView3.layoutParams = params2
    }
}



