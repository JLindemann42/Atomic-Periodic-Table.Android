package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import butterknife.OnClick
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.theme_panel.*


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

        setContentView(R.layout.solubility_table)


        back_btn.setOnClickListener {
            this.onBackPressed()
        }

    }
}



