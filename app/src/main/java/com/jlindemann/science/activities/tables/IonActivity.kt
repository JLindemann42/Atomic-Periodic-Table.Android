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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.IonAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Ion
import com.jlindemann.science.model.IonModel
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ElementDataLoader
import com.jlindemann.science.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

class IonActivity : BaseActivity(), IonAdapter.OnIonClickListener {
    private var ionList = ArrayList<Ion>()
    var mAdapter = IonAdapter(ionList, this, this)

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
        setContentView(R.layout.activity_ion) //REMEMBER: Never move any function calls above this

        // Register lifecycle-aware OnBackPressedCallback (disabled by default).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // Not consumed by overlays -> fall back to default behaviour.
                    // Temporarily disable the callback to avoid recursion, then dispatch.
                    isEnabled = false
                    try {
                        onBackPressedDispatcher.onBackPressed()
                    } finally {
                        // leave disabled by default
                        isEnabled = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // Register platform callback for Android 14+ to forward gestures to the dispatcher when enabled.
        // Start with interception disabled (we only need it when overlays are visible).
        setBackInterceptionEnabled(false)

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "ion"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        recyclerView()
        clickSearch()
        findViewById<ConstraintLayout>(R.id.view_ion).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        // When tapping the background detail area we should hide the detail and update interception.
        findViewById<TextView>(R.id.detail_background_ion).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.ion_detail), 300)
            // update back interception after hiding
            setBackInterceptionEnabled(anyOverlayOpen())
        }

        findViewById<View>(R.id.back_btn_ion).setOnClickListener { this.onBackPressed() }
    }

    override fun ionClickListener(item: Ion, position: Int) {
        if (item.count > 1) {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.ion_detail), 300)

            // Info shown -> enable back interception
            setBackInterceptionEnabled(true)

            findViewById<TextView>(R.id.ion_detail_title).text = ((item.name).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + " " + "ionization")
            try {
                val jsonObject = ElementDataLoader.loadElementData(this, item.name)
                if (jsonObject != null) {
                    for (i in 1..item.count) {
                        val text = "element_ionization_energy"
                        val add = i.toString()
                        val final = (text + add)
                        val ionization = jsonObject.optString(final, "---")
                        val extText = i.toString()
                        val nameId = "ion_text_$extText"
                        val iText = findViewById<TextView>(resources.getIdentifier(nameId, "id", packageName))
                        val dot = "."
                        val space = " "
                        iText.text = "$i$dot$space$ionization"
                        iText.visibility = View.VISIBLE
                    }
                    for (i in (item.count+1)..30) {
                        val extText = i.toString()
                        val nameId = "ion_text_$extText"
                        val iText = findViewById<TextView>(resources.getIdentifier(nameId, "id", packageName))
                    iText.visibility = View.GONE
                }
                    }
            } catch (e: IOException) {
                // If reading fails, show a toast and ensure interception state is consistent.
                Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.ion_detail), 300)
                setBackInterceptionEnabled(anyOverlayOpen())
                e.printStackTrace()
            }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<RecyclerView>(R.id.ion_view).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val params2 = findViewById<FrameLayout>(R.id.common_title_back_ion).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_ion).layoutParams = params2

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box_ion).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box_ion).layoutParams = searchEmptyImgPrm
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.ion_view)
        val ionList = ArrayList<Ion>()
        IonModel.getList(ionList)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = IonAdapter(ionList, this, this)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()

        findViewById<EditText>(R.id.edit_ion).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int){}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), ionList, recyclerView) }
        })
    }

    private fun filter(text: String, list: ArrayList<Ion>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Ion> = ArrayList()
        for (item in list) { if (item.name.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) { filteredList.add(item) } }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box_ion), 300)
            } else {
                findViewById<LinearLayout>(R.id.empty_search_box_ion).visibility = View.GONE
            }
        }, 10)
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = IonAdapter(filteredList, this, this)
    }

    private fun clickSearch() {
        findViewById<View>(R.id.search_btn_ion).setOnClickListener {
            Utils.fadeInAnim(findViewById<View>(R.id.search_bar_ion), 150)
            Utils.fadeOutAnim(findViewById<View>(R.id.title_box), 1)
            findViewById<EditText>(R.id.edit_ion).requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_ion), InputMethodManager.SHOW_IMPLICIT)

            // Search bar shown -> enable back interception
            setBackInterceptionEnabled(true)
        }
        findViewById<View>(R.id.close_ele_search_ion).setOnClickListener {
            Utils.fadeOutAnim(findViewById<View>(R.id.search_bar_ion), 1)
            val delayClose = Handler(Looper.getMainLooper())
            delayClose.postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box), 150)
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

    override fun onBackPressed() {
        // Centralized back handling
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    // Basic handler for in-activity overlays
    private fun anyOverlayOpen(): Boolean {
        val detailVisible = findViewById<View>(R.id.ion_detail).visibility == View.VISIBLE
        val searchBarVisible = findViewById<View>(R.id.search_bar_ion).visibility == View.VISIBLE
        return detailVisible || searchBarVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val detail = findViewById<View>(R.id.ion_detail)
        val searchBar = findViewById<View>(R.id.search_bar_ion)

        // If ion detail visible, hide it
        if (detail.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(detail, 300)
            // update interception state after hiding
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        // If search bar visible, close it
        if (searchBar.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(searchBar, 1)
            Handler(Looper.getMainLooper()).postDelayed({
                Utils.fadeInAnim(findViewById<View>(R.id.title_box), 150)
            }, 151)
            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }

        return false
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