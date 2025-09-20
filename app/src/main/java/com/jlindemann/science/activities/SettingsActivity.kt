package com.jlindemann.science.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.AboutActivity
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.LicensesActivity
import com.jlindemann.science.activities.settings.OrderActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.activities.settings.UnitActivity
import com.jlindemann.science.activities.tools.TitleBarAnimator
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.hideNavPreference
import com.jlindemann.science.preferences.offlinePreference
import com.jlindemann.science.settings.ExperimentalActivity
import com.jlindemann.science.utils.*
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow
import java.util.*

class SettingsActivity : BaseActivity() {

    data class LanguageOption(val englishName: String, val nativeName: String, val locale: Locale) {
        fun getDisplayName(): String =
            if (englishName == nativeName) englishName else "$englishName ($nativeName)"
    }

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
        setContentView(R.layout.activity_settings)

        if (themePrefValue == 100) {
            findViewById<TextView>(R.id.system_default_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_checked, 0, 0, 0)
            findViewById<TextView>(R.id.light_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
            findViewById<TextView>(R.id.dark_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
        }
        if (themePrefValue == 0) {
            findViewById<TextView>(R.id.system_default_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
            findViewById<TextView>(R.id.light_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_checked, 0, 0, 0)
            findViewById<TextView>(R.id.dark_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
        }
        if (themePrefValue == 1) {
            findViewById<TextView>(R.id.system_default_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
            findViewById<TextView>(R.id.light_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_unchecked, 0, 0, 0)
            findViewById<TextView>(R.id.dark_btn).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_radio_checked, 0, 0, 0)
        }

        openPages()
        themeSettings()
        initializeCache()
        cacheSettings()
        initOfflineSwitches()
        initNavSwitches()

        findViewById<RelativeLayout>(R.id.offline_settings).setOnClickListener {
            findViewById<SwitchCompat>(R.id.offline_internet_switch).toggle()
        }
        findViewById<RelativeLayout>(R.id.nav_bar_settings).setOnClickListener {
            findViewById<SwitchCompat>(R.id.nav_bar_switch).toggle()
        }

        findViewById<ConstraintLayout>(R.id.view).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        // Title Controller with animated visibility
        findViewById<FrameLayout>(R.id.common_title_settings_color).visibility = View.VISIBLE
        findViewById<TextView>(R.id.element_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_set).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.scroll_settings).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                private var isTitleVisible = false // Track animation state

                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.scroll_settings).scrollY
                    val threshold = 158

                    val titleColorBackground = findViewById<FrameLayout>(R.id.common_title_settings_color)
                    val titleText = findViewById<TextView>(R.id.element_title)
                    val titleDownstateText = findViewById<TextView>(R.id.element_title_downstate)
                    val titleBackground = findViewById<FrameLayout>(R.id.common_title_back_set)

                    if (scrollY > threshold) {
                        if (!isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                            TitleBarAnimator.animateVisibility(titleText, true)
                            TitleBarAnimator.animateVisibility(titleDownstateText, false)
                            titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                            isTitleVisible = true
                        }
                    } else {
                        if (isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                            TitleBarAnimator.animateVisibility(titleText, false)
                            TitleBarAnimator.animateVisibility(titleDownstateText, true)
                            titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                            isTitleVisible = false
                        }
                    }
                }
            })

        findViewById<RelativeLayout>(R.id.about_settings).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.back_btn_set).setOnClickListener {
            this.onBackPressed()
        }
        findViewById<RelativeLayout>(R.id.submit_settings).setOnClickListener {
            val intent = Intent(this, SubmitActivity::class.java)
            startActivity(intent)
        }
        findViewById<RelativeLayout>(R.id.licenses_settings).setOnClickListener {
            val intent = Intent(this, LicensesActivity::class.java)
            startActivity(intent)
        }
        findViewById<RelativeLayout>(R.id.unit_settings).setOnClickListener {
            val intent = Intent(this, UnitActivity::class.java)
            startActivity(intent)
        }
        findViewById<RelativeLayout>(R.id.git_settings).setOnClickListener{
            val packageManager = packageManager
            val blogURL = "https://github.com/JLindemann42/Atomic-Periodic-Table.Android"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
        findViewById<RelativeLayout>(R.id.win_settings).setOnClickListener{
            val packageManager = packageManager
            val winURL = "https://apps.microsoft.com/detail/9NBT1TCW2CNT"
            TabUtil.openCustomTab(winURL, packageManager, this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val backCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (handleBackPress()) {
                        // We consumed the back press (closed the panel), do nothing
                    } else {
                        remove() // Remove callback so system handles back normally
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
            onBackPressedDispatcher.addCallback(this, backCallback)

            // Predictive Back Gesture for Android 14+ (UPSIDEDOWN_CAKE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    if (!handleBackPress()) {
                        // Do not consume, allow predictive back to show previous activity preview
                        finish()
                    }
                }
            }
        }

        // --- Language selection setup ---
        findViewById<RelativeLayout>(R.id.language_setting).setOnClickListener {
            showLanguageSelectionDialog()
        }
        updateLanguageContentText()
    }

    // --- Language selection logic ---

    private fun getSupportedLanguages(): List<LanguageOption> = listOf(
        LanguageOption("English", Locale("en").getDisplayLanguage(Locale("en")), Locale("en")),
        LanguageOption("Swedish", Locale("sv", "SE").getDisplayLanguage(Locale("sv", "SE")), Locale("sv", "SE")),
        LanguageOption("Spanish", Locale("es", "ES").getDisplayLanguage(Locale("es", "ES")), Locale("es", "ES")),
        LanguageOption("Italian", Locale("it", "IT").getDisplayLanguage(Locale("it", "IT")), Locale("it", "IT")),
        // FIXED: Only set language, not country, to match values-af/
        LanguageOption("Afrikaans", Locale("af").getDisplayLanguage(Locale("af")), Locale("af"))
    )

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)
        } else {
            resources.configuration.locale
        }
    }

    private fun getSystemLanguageOption(): LanguageOption {
        val systemLocale = getSystemLocale()
        return getSupportedLanguages().find {
            it.locale.language == systemLocale.language && it.locale.country == systemLocale.country
        } ?: getSupportedLanguages()[0]
    }

    private fun setupLanguageListener() {
        findViewById<RelativeLayout>(R.id.language_setting).setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = getSupportedLanguages()
        val systemLangOption = getSystemLanguageOption()
        val languageNames = mutableListOf<String>()
        languageNames.add("System default")
        languageNames.addAll(languages.map { it.getDisplayName() })

        val currentPref = getCurrentLanguagePreference()
        var checkedItem = 0
        if (currentPref != null) {
            checkedItem = languages.indexOfFirst {
                it.locale.language == currentPref.language && it.locale.country == currentPref.country
            }
            if (checkedItem != -1) checkedItem += 1 else checkedItem = 0
        }

        // Use custom ArrayAdapter for the list items
        val adapter = ArrayAdapter<String>(this, R.layout.dialog_language_item, R.id.lang_name, languageNames)

        // Build AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.language_title))
            .setSingleChoiceItems(adapter, checkedItem) { dialogInterface, which ->
                when (which) {
                    0 -> setLanguagePreference(null) // System default
                    else -> setLanguagePreference(languages[which - 1].locale)
                }
                updateLanguageContentText()
                dialogInterface.dismiss()
                Handler().postDelayed({
                    LocaleUtil.restartForLocaleChange(this)
                }, 150)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        // Set rounded background for the entire dialog window
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_popup_bg)

        dialog.show()
    }
    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updateLanguageContentText() {
        val langOption = when (val pref = getCurrentLanguagePreference()) {
            null -> getSystemLanguageOption()
            else -> getSupportedLanguages().find {
                it.locale.language == pref.language && it.locale.country == pref.country
            } ?: getSystemLanguageOption()
        }
        findViewById<TextView>(R.id.language_content).text = langOption.getDisplayName()
    }

    private fun getCurrentLanguagePreference(): Locale? {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val lang = prefs.getString("app_language", null)
        val country = prefs.getString("app_country", null)
        return if (lang.isNullOrBlank()) null
        else if (country.isNullOrBlank()) Locale(lang)
        else Locale(lang, country)
    }

    private fun setLanguagePreference(locale: Locale?) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (locale == null) {
            prefs.edit().remove("app_language").remove("app_country").apply()
        } else {
            prefs.edit()
                .putString("app_language", locale.language)
                .putString("app_country", locale.country)
                .apply()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_set).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_set).layoutParams = params

        val titleParam = findViewById<FrameLayout>(R.id.title_box_settings).layoutParams as ViewGroup.MarginLayoutParams
        titleParam.rightMargin = right
        titleParam.leftMargin = left
        findViewById<FrameLayout>(R.id.title_box_settings).layoutParams = titleParam

        val params2 = findViewById<TextView>(R.id.element_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<TextView>(R.id.element_title_downstate).layoutParams = params2

        findViewById<LinearLayout>(R.id.personalization_box).setPadding(left, 0, right, 0)
        findViewById<LinearLayout>(R.id.advanced_box).setPadding(left, 0, right, 0)
    }

    // Handle back logic
    private fun handleBackPress(): Boolean {
        val themePanel = findViewById<FrameLayout>(R.id.theme_panel)

        return if (themePanel.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(themePanel, 300) // Close panel
            true // Consume back press
        } else {
            false // Allow system to handle it (shows preview animation)
        }
    }

    // Override for Android < 13
    override fun onBackPressed() {
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    private fun initOfflineSwitches() {
        val offlinePreferences = offlinePreference(this)
        val offlinePrefValue = offlinePreferences.getValue()
        findViewById<SwitchCompat>(R.id.offline_internet_switch).isChecked = offlinePrefValue == 1

        findViewById<SwitchCompat>(R.id.offline_internet_switch).setOnCheckedChangeListener { _, _ ->
            if (findViewById<SwitchCompat>(R.id.offline_internet_switch).isChecked) { offlinePreferences.setValue(1) }
            else { offlinePreferences.setValue(0) }
        }
    }

    private fun initNavSwitches() {
        val navPreferences = hideNavPreference(this)
        val navPrefValue = navPreferences.getValue()
        findViewById<SwitchCompat>(R.id.nav_bar_switch).isChecked = navPrefValue == 1

        findViewById<SwitchCompat>(R.id.nav_bar_switch).setOnCheckedChangeListener { _, _ ->
            if (findViewById<SwitchCompat>(R.id.nav_bar_switch).isChecked) { navPreferences.setValue(1) }
            else { navPreferences.setValue(0) }
        }
    }

    private fun openPages() {
        findViewById<RelativeLayout>(R.id.favorite_settings).setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        findViewById<RelativeLayout>(R.id.order_settings).setOnClickListener {
            val intent = Intent(this, OrderActivity::class.java)
            startActivity(intent)
        }
        findViewById<RelativeLayout>(R.id.experimental_settings).setOnClickListener {
            val intent = Intent(this, ExperimentalActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cacheSettings() {
        findViewById<LinearLayout>(R.id.cache_lay).setOnClickListener {
            this.cacheDir.deleteRecursively()
            initializeCache()
        }
    }

    private fun initializeCache() {
        try {
            var size: Long = 0
            size += getDirSize(this.cacheDir)
            size += getDirSize(this.externalCacheDir)
            (findViewById<View>(R.id.clear_cache_content) as TextView).text = readableFileSize(size)
        }
        catch(e: IOException) {
            ToastUtil.showToast(this, "Error Code: 11002")
        }
    }

    private fun getDirSize(dir: File?): Long {
        var size: Long = 0
        if (dir != null) {
            for (file in dir.listFiles()!!) {
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
        findViewById<TextView>(R.id.system_default_btn).setOnClickListener {
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
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)
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
        findViewById<TextView>(R.id.light_btn).setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(0)
            setTheme(R.style.AppTheme)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)

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
        findViewById<TextView>(R.id.dark_btn).setOnClickListener {
            val themePreference = ThemePreference(this)
            themePreference.setValue(1)
            setTheme(R.style.AppThemeDark)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)

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
        findViewById<RelativeLayout>(R.id.themes_settings).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)
        }
        findViewById<TextView>(R.id.theme_background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)
        }
        findViewById<TextView>(R.id.cancel_btn).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.theme_panel), 300)
        }
    }
}