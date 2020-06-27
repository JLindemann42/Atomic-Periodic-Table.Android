package com.jlindemann.science.settings

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.R2.id.view
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_experimental_settings_page.*
import kotlinx.android.synthetic.main.activity_isotopes_experimental.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.solubility_group_1.*
import kotlinx.android.synthetic.main.solubility_group_2.*
import kotlinx.android.synthetic.main.solubility_group_3.*
import kotlinx.android.synthetic.main.solubility_group_4.*
import kotlinx.android.synthetic.main.solubility_group_5.*
import kotlinx.android.synthetic.main.solubility_group_6.*

class ExperimentalActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)

        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_experimental_settings_page) //Don't move down (Needs to be before we call our functions)

        //onClickListeners() //Disabled as a result of conflicts between ACTION_DOWN and ScrollView
        viewe.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        back_btn_exp.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_exp.layoutParams as ViewGroup.LayoutParams
        params.height += top
        common_title_back_exp.layoutParams = params

        val params2 = general_header_exp.layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin += top
        general_header_exp.layoutParams = params2
    }


}



