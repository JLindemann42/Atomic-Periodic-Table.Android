package com.jlindemann.science.activities.tables

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.ConstantsAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Constants
import com.jlindemann.science.model.ConstantsModel
import com.jlindemann.science.preferences.ConstantsPreference
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class ConstantsActivity : BaseActivity(), ConstantsAdapter.OnConstantsClickListener {
    private var constantsList = ArrayList<Constants>()
    var mAdapter = ConstantsAdapter(constantsList, this, this)

    // Unified back handling fields
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
        setContentView(R.layout.activity_constants) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // Not consumed by overlays -> fallback to default behavior.
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

        // Start with platform OnBackInvoked interception disabled; enable on overlays.
        setBackInterceptionEnabled(false)

        val recyclerView = findViewById<RecyclerView>(R.id.con_view)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val itemCon = ArrayList<Constants>()
        ConstantsModel.getList(itemCon)

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "con"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }
        recyclerView()
        chipListeners(itemCon, recyclerView)
        clickSearch()

        findViewById<View>(R.id.back_btn_con).setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.con_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar_ph) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar_ph))
        val params2 = findViewById<FrameLayout>(R.id.common_title_back_con).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_ph)
        findViewById<FrameLayout>(R.id.common_title_back_con).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_con).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_con).layoutParams = searchEmptyImgPrm
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.con_view)
        val constants = ArrayList<Constants>()

        ConstantsModel.getList(constants)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = ConstantsAdapter(constants, this, this)
        recyclerView.adapter = adapter

        adapter.notifyDataSetChanged()

        findViewById<EditText>(R.id.edit_con).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), constants, recyclerView)
            }
        })
    }

    // Overrides the clickListener from ConstantsAdapter (no-op currently)
    override fun constantsClickListener(item: Constants, position: Int) {
    }

    private fun clickSearch() {
        findViewById<View>(R.id.search_btn_con).setOnClickListener {
            Utils.fadeInAnim(findViewById<View>(R.id.search_bar_con), 150)
            Utils.fadeOutAnim(findViewById<View>(R.id.title_box_con), 1)

            findViewById<EditText>(R.id.edit_con).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_con), InputMethodManager.SHOW_IMPLICIT)

            // Search bar shown -> enable back interception so gestures/back close it first
            setBackInterceptionEnabled(true)
        }
        findViewById<View>(R.id.close_con_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<View>(R.id.search_bar_con), 1)

            val delayClose = Handler(Looper.getMainLooper())
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box_con), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // closed -> update interception state
            setBackInterceptionEnabled(anyOverlayOpen())
        }
    }

    // listen to button presses in filter
    private fun chipListeners(list: ArrayList<Constants>, recyclerView: RecyclerView) {
        val constantsPreference = ConstantsPreference(this)
        val clearBtn = findViewById<com.google.android.material.chip.Chip>(R.id.clear_btn_con)
        
        val applyFilter = { filter: String ->
            constantsPreference.setValue(filter)
            findViewById<EditText>(R.id.edit_con).setText("")
            clearBtn.visibility = if (filter.isEmpty()) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.mathematic_btn_con).setOnClickListener { applyFilter("mathematics") }
        findViewById<View>(R.id.physics_btn_con).setOnClickListener { applyFilter("physics") }
        findViewById<View>(R.id.water_btn_con).setOnClickListener { applyFilter("water") }
        clearBtn.setOnClickListener { applyFilter("") }
    }

    // Filters
    private fun filter(text: String, list: ArrayList<Constants>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Constants> = ArrayList()
        for (item in list) {
            val constantsPreference = ConstantsPreference(this)
            val constantsPrefValue = constantsPreference.getValue()
            if (item.name.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                if (item.category.lowercase(Locale.ROOT).contains(constantsPrefValue.lowercase(
                        Locale.ROOT
                    ))) {
                    filteredList.add(item)
                }
            }
        }
        val handler = android.os.Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<AppCompatImageView>(R.id.empty_con_search_image), 300)
            }
            else {
                findViewById<AppCompatImageView>(R.id.empty_con_search_image).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = ConstantsAdapter(filteredList, this, this)
    }

    // Basic handler for in-activity overlays
    private fun anyOverlayOpen(): Boolean {
        val searchBarVisible = findViewById<View>(R.id.search_bar_con).visibility == View.VISIBLE
        return searchBarVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val searchBar = findViewById<View>(R.id.search_bar_con)

        // If search bar visible, close it
        if (searchBar.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(searchBar, 1)
            // restore title box after a short delay to match original timing
            Handler(Looper.getMainLooper()).postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box_con), 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // update interception state after closing
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        return false
    }

    override fun onBackPressed() {
        // Centralized back handling
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    /**
     * Centralized management of platform back interception for Android 14+.
     * We forward platform back invocations to the OnBackPressedDispatcher to ensure
     * gestures and hardware back buttons call the same callbacks.
     */
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        // Keep OnBackPressedCallback state in sync
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
                                if (!consumed) {
                                    // fallback to finishing
                                    finish()
                                }
                            }
                        }
                    }
                    try {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            onBackInvokedCb!!
                        )
                    } catch (_: Exception) {
                        // ignore registration errors on some devices
                    }
                }
            } else {
                if (onBackInvokedCb != null) {
                    try {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                    } catch (_: Exception) {
                        // ignore
                    }
                    onBackInvokedCb = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup back interception hooks
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