package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.theme_panel.*
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow


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
        themeSettings()
        initializeCache()
        cacheSettings()

        back_btn.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onBackPressed() {
        if (theme_panel.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(theme_panel, 300) //Start Close Animation
            return
        }
        super.onBackPressed()
    }

    private fun openFavoriteBarSettingPage() {
        favorite_settings.setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cacheSettings() {
        cache_lay.setOnClickListener {
            this.cacheDir.deleteRecursively()
            initializeCache()
        }
    }

    private fun initializeCache() {
        var size: Long = 0
        size += getDirSize(this.cacheDir)
        size += getDirSize(this.externalCacheDir)
        (findViewById<View>(R.id.clear_cache_content) as TextView).text = readableFileSize(size)
    }

    private fun getDirSize(dir: File?): Long {
        var size: Long = 0
        for (file in dir!!.listFiles()) {
            if (file != null && file.isDirectory) {
                size += getDirSize(file)
            } else if (file != null && file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    private fun readableFileSize(size: Long): String? {
        if (size <= 0) return "0 Bytes"
        val units = arrayOf("Bytes", "kB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())).toString() + " " + units[digitGroups]
    }

    private fun themeSettings() {
        system_button.setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(100)
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            when (currentNightMode) {
                Configuration.UI_MODE_NIGHT_NO -> {
                    setTheme(R.style.AppTheme)
                } // Night mode is not active, we're using the light theme
                Configuration.UI_MODE_NIGHT_YES -> {
                    setTheme(R.style.AppThemeDark)
                } // Night mode is active, we're using dark theme
            }
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
}



