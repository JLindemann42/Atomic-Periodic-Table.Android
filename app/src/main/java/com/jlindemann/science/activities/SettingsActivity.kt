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


class SettingsActivity : AppCompatActivity() {

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

        setContentView(R.layout.activity_settings)

        openFavoriteBarSettingPage()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }

        system_button.setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(100)
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            when (currentNightMode) {
                Configuration.UI_MODE_NIGHT_NO -> {setTheme(R.style.AppTheme)} // Night mode is not active, we're using the light theme
                Configuration.UI_MODE_NIGHT_YES -> {setTheme(R.style.AppThemeDark)} // Night mode is active, we're using dark theme
            }

            Utils.fadeOutAnim(theme_panel, 300)

            val delayChange = Handler()
            delayChange.postDelayed({
                finish()
                overridePendingTransition(0, 0)
                startActivity(getIntent())
                SettingsActivity().finish()
                System.exit(0)
                overridePendingTransition(0, 0)
            }, 302)
        }

        light_btn.setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(0)
            setTheme(R.style.AppTheme)

            Utils.fadeOutAnim(theme_panel, 300)

            val delayChange = Handler()
            delayChange.postDelayed({
                finish()
                overridePendingTransition(0, 0)
                startActivity(getIntent())
                SettingsActivity().finish()
                System.exit(0)
                overridePendingTransition(0, 0)
            }, 302)
        }

        dark_btn.setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(1)
            setTheme(R.style.AppThemeDark)

            Utils.fadeOutAnim(theme_panel, 300)

            val delayChange = Handler()
            delayChange.postDelayed({
                finish()
                overridePendingTransition(0, 0)
                startActivity(getIntent())
                SettingsActivity().finish()
                System.exit(0)
                overridePendingTransition(0, 0)
            }, 302)
        }

        themes_settings.setOnClickListener {
            Utils.fadeInAnim(theme_panel, 300)
        }
        theme_background.setOnClickListener {
            Utils.fadeOutAnim(theme_panel, 300)
        }
    }

    fun openFavoriteBarSettingPage() {
        favorite_settings.setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (theme_panel.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(theme_panel, 300) //Start Close Animation
            return
        }
        super.onBackPressed()
    }
}



