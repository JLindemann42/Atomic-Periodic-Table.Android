package com.jlindemann.science.activities.tables

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.DictionaryAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.*
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.preferences.DictionaryPreferences
import com.jlindemann.science.preferences.IsoPreferences
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.UnifiedTitleBarController
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class DictionaryActivity : BaseActivity(), DictionaryAdapter.OnDictionaryClickListener {
    private var dictionaryList = ArrayList<Dictionary>()
    var mAdapter = DictionaryAdapter(dictionaryList, this, this)

    private lateinit var titleBar: UnifiedTitleBarController

    // back interception members
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())

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
        setContentView(R.layout.activity_dictionary) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback in DISABLED state.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!handleBackPress()) {
                    isEnabled = false
                    try {
                        onBackPressedDispatcher.onBackPressed()
                    } finally {
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        setBackInterceptionEnabled(false)

        val itemse = ArrayList<Dictionary>()
        DictionaryModel.getList(itemse)

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.dictionary)
        titleBar.setAction(R.drawable.ic_search) { titleBar.showSearch() }
        titleBar.searchCloseButton.setOnClickListener {
            titleBar.hideSearch()
            titleBar.searchInput.setText("")
        }
        titleBar.backButton.setOnClickListener { onBackPressed() }

        setupChips(itemse)

        val recyclerView = findViewById<RecyclerView>(R.id.rc_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = DictionaryAdapter(itemse, this, this)
        recyclerView.adapter = adapter
        mAdapter = adapter

        // Reset preference and filter to show all initially
        DictionaryPreferences(this).setValue("")
        filter("", itemse, recyclerView)

        titleBar.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), itemse, recyclerView)
            }
        })

        //Set achievement
        val achievements = java.util.ArrayList<Achievement>()
        AchievementModel.getList(this, achievements)
        val achievement6 = achievements.find { it.id == 6 }
        achievement6?.incrementProgress(this, 1)

        //Add value to most used:
        val mostUsedPreference = MostUsedToolPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "dic"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        } else {
            // Add new entry if it doesn't exist
            val newValue = "$mostUsedPrefValue dic=1.0"
            mostUsedPreference.setValue(newValue)
        }

        findViewById<ConstraintLayout>(R.id.view_dic).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.container.elevation = 0f

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val scrollY = recyclerView.computeVerticalScrollOffset()
                if (scrollY > 20) {
                    if (titleSurface.visibility != View.VISIBLE) {
                        titleSurface.visibility = View.VISIBLE
                        titleBar.container.elevation = resources.getDimension(R.dimen.one_elevation)
                    }
                } else {
                    if (titleSurface.visibility != View.INVISIBLE) {
                        titleSurface.visibility = View.INVISIBLE
                        titleBar.container.elevation = 0f
                    }
                }
            }
        })
    }

    private fun setupChips(list: ArrayList<Dictionary>) {
        val dictionaryPreference = DictionaryPreferences(this)
        val recyclerView = findViewById<RecyclerView>(R.id.rc_view)
        val categories = listOf(
            0 to getString(R.string.clear_filter),
            1 to getString(R.string.chip_chemistry),
            2 to getString(R.string.chip_physics),
            3 to getString(R.string.chip_math),
            4 to getString(R.string.chip_reactions)
        )
        
        titleBar.setCategories(categories) { id ->
            val filter = when (id) {
                1 -> "chemistry"
                2 -> "physics"
                3 -> "math"
                4 -> "reactions"
                else -> ""
            }
            dictionaryPreference.setValue(filter)
            titleBar.searchInput.setText("")
            filter(titleBar.searchInput.text.toString(), list, recyclerView)
        }
    }

    private fun anyOverlayOpen(): Boolean {
        return titleBar.searchRow.visibility == View.VISIBLE
    }

    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        uiHandler.post {
                            try {
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                val consumed = handleBackPress()
                                if (!consumed) finish()
                            }
                        }
                    }
                    try {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            onBackInvokedCb!!
                        )
                    } catch (_: Exception) { }
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) { }
                    onBackInvokedCb = null
                }
            }
        }
    }

    private fun handleBackPress(): Boolean {
        if (titleBar.searchRow.visibility == View.VISIBLE) {
            titleBar.hideSearch()
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }
        return false
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.rc_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        titleBar.container.layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_dic).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_dic).layoutParams = searchEmptyImgPrm
    }

    private fun filter(text: String, list: ArrayList<Dictionary>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Dictionary> = ArrayList()
        val dictionaryPreference = DictionaryPreferences(this)
        val dictionaryPrefValue1 = dictionaryPreference.getValue()
        for (item in list) {
            if (item.heading.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                if (item.category.lowercase(Locale.ROOT).contains(dictionaryPrefValue1.lowercase(Locale.ROOT))) {
                    filteredList.add(item)
                }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (mAdapter.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_dic), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box_dic).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
    }

    override fun dictionaryClickListener(item: Dictionary, wiki: TextView, url: String, position: Int) {
        wiki.setOnClickListener {
            val packageNameString = "com.android.chrome"
            val customTabBuilder = CustomTabsIntent.Builder()

            customTabBuilder.setToolbarColor(ContextCompat.getColor(this, R.color.wikipediaColor))
            customTabBuilder.setSecondaryToolbarColor(ContextCompat.getColor(this ,R.color.wikipediaColor))
            customTabBuilder.setShowTitle(true)

            val customTab = customTabBuilder.build()
            val intent = customTab.intent
            intent.data = Uri.parse(url)

            val packageManager = packageManager
            val resolveInfoList = packageManager.queryIntentActivities(customTab.intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (TextUtils.equals(packageName, packageNameString))
                    customTab.intent.setPackage(packageNameString)
            }
            customTab.intent.data?.let { it1 -> customTab.launchUrl(this, it1) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            } catch (_: Exception) { }
            onBackInvokedCb = null
        }
    }
}
