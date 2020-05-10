package com.jlindemann.science.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.R2.id.view
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.activity_solubility.*
import kotlinx.android.synthetic.main.solubility_group_1.*
import kotlinx.android.synthetic.main.solubility_group_2.*
import kotlinx.android.synthetic.main.solubility_group_3.*
import kotlinx.android.synthetic.main.solubility_group_4.*
import kotlinx.android.synthetic.main.solubility_group_5.*
import kotlinx.android.synthetic.main.solubility_group_6.*

class SolubilityActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_solubility) //Don't move down (Needs to be before we call our functions)

        //onClickListeners() //Disabled as a result of conflicts between ACTION_DOWN and ScrollView

        back_btn.setOnClickListener {
            this.onBackPressed()
        }

    }

    private fun onClickListeners() {
        nh4_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { nh4_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { nh4_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
        li_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { li_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { li_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
        na_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { na_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { na_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
        k_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { k_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { k_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
        mg_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { mg_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { mg_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
        ca_view.setOnTouchListener(OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { ca_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_down)); }
                MotionEvent.ACTION_UP -> { ca_view.setBackground(ContextCompat.getDrawable(this, R.drawable.row_up)); }
            }
            false
        })
    }

}



