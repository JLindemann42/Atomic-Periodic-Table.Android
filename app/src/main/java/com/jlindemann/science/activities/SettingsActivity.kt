package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.offlinePreference
import com.jlindemann.science.settings.ExperimentalActivity
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.theme_panel.*
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class SettingsActivity : BaseActivity() {

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
        setContentView(R.layout.activity_settings)

        openPages()
        themeSettings()
        initializeCache()
        cacheSettings()
        initOfflineSwitches()

        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        info_btn_set.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        back_btn_set.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_set.layoutParams as ViewGroup.LayoutParams
        params.height += top
        common_title_back_set.layoutParams = params

        val params2 = personalization_header.layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin += top
        personalization_header.layoutParams = params2
    }

    override fun onBackPressed() {
        if (theme_panel.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(theme_panel, 300) //Start Close Animation
            return
        }
        else {
            super.onBackPressed()
        }
    }

    private fun initOfflineSwitches() {
        val offlinePreferences = offlinePreference(this)
        val offlinePrefValue = offlinePreferences.getValue()
        offline_internet_switch.isChecked = offlinePrefValue == 1

        offline_internet_switch.setOnCheckedChangeListener { compoundButton, b ->
            if (offline_internet_switch.isChecked) {
                val offlinePreference = offlinePreference(this)
                offlinePreference.setValue(1)
            } else {
                val offlinePreference = offlinePreference(this)
                offlinePreference.setValue(0)
            }
        }
    }

    private fun openPages() {
        favorite_settings.setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        experimental_settings.setOnClickListener {
            val intent = Intent(this, ExperimentalActivity::class.java)
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
        if (dir != null) {
            for (file in dir.listFiles()) {
                if (file != null && file.isDirectory) {
                    size += getDirSize(file)
                } else if (file != null && file.isFile) {
                    size += file.length()
                }
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
            Utils.fadeOutAnim(theme_panel, 300)
            val delayChange = Handler()
            delayChange.postDelayed({
                finish()
                overridePendingTransition(0, 0)
                startActivity(intent)
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
                startActivity(intent)
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
                startActivity(intent)
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



