package com.jlindemann.science.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tables.DictionaryActivity
import com.jlindemann.science.activities.tools.CalculatorActivity
import com.jlindemann.science.activities.tools.FlashCardActivity
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.extensions.TableExtension
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.SearchPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.hideNavPreference
import com.jlindemann.science.utils.TabUtil
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.otaliastudios.zoom.ZoomLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import org.deejdev.twowaynestedscrollview.TwoWayNestedScrollView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : TableExtension(), ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

    // Lifecycle-aware back callback reference
    private var backCallback: OnBackPressedCallback? = null

    // Optional OnBackInvokedCallback for newer platforms (registered only when interception is needed)
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null

    // Tracks whether we've already adjusted the scrollView upward while bars are hidden
    private var isScrollViewRaised = false

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
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.element_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val adapter = ElementAdapter(elements, this, this)
        recyclerView.adapter = adapter
        findViewById<EditText>(R.id.edit_element).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), elements, recyclerView)
                updateStats()
                updateStatAchievement()
            }
        })

        setOnCLickListenerSetups(elements)
        setupNavListeners()
        onClickNav()
        scrollAdapter()
        searchListener()
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).panelState = PanelState.COLLAPSED
        searchFilter(elements, recyclerView)
        mediaListeners()
        checkSale()
        initName(elements)
        val achievements = ArrayList<Achievement>()
        maybeShowProPopup()
        findViewById<FloatingActionButton>(R.id.more_btn).setOnClickListener { openHover() }
        findViewById<TextView>(R.id.hover_background).setOnClickListener { closeHover() }
        findViewById<Button>(R.id.random_btn).setOnClickListener {
            //Set achievement
            AchievementModel.getList(this, achievements)
            val achievement7 = achievements.find { it.id == 7 }
            achievement7?.incrementProgress(this, 1)
            getRandomItem() }
        findViewById<ConstraintLayout>(R.id.view_main).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Check if PRO version and if make changes:
        val proPref = ProVersion(this)
        var proPrefValue = proPref.getValue()
        if (proPrefValue==100) {
            proChanges()
        }
        hoverListeners(elements, proPrefValue)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            initName(elements)
        }, 250)

        findViewById<ImageButton>(R.id.user_btn).setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.flaschard_btn).setOnClickListener {
            val intent = Intent(this, FlashCardActivity::class.java)
            startActivity(intent)
        }

        // IMPORTANT: Panel listener handles fade/visibility and enabling/disabling back interception.
        // Keep it as the single source of truth for panel state transitions.
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {}
            override fun onPanelStateChanged(panel: View?, previousState: PanelState, newState: PanelState) {
                val navMenuInclude = findViewById<FrameLayout>(R.id.nav_menu_include)
                val navBackground = findViewById<TextView>(R.id.nav_background)
                if (newState == PanelState.COLLAPSED) {
                    // perform fade and hide when collapse finished
                    Utils.fadeOutAnim(navBackground, 150)
                    navMenuInclude.visibility = View.GONE
                    // Nothing left to intercept: disable callback
                    setBackInterceptionEnabled(false)
                } else if (newState == PanelState.EXPANDED) {
                    // Panel expanded -> intercept back
                    // make sure menu is visible while expanding (click to open already sets visibility)
                    setBackInterceptionEnabled(true)
                }
            }
        })

        // Register a lifecycle-aware OnBackPressedCallback in DISABLED state.
        // We'll enable it only when overlays are shown (search, filter, nav, hover, popup).
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    private fun maybeShowProPopup() {
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        if (proPrefValue == 100) return // Already PRO, do not show popup

        val prefs = getSharedPreferences("popup_prefs", Context.MODE_PRIVATE)
        val starts = prefs.getInt("app_starts", 0) + 1
        prefs.edit().putInt("app_starts", starts).apply()

        // Never show in first 10 launches
        if (starts <= 10) return

        // Suppression logic: don't show if suppressed
        val now = System.currentTimeMillis()
        val suppressedUntil = prefs.getLong("popup_suppressed_until", 0L)
        if (now < suppressedUntil) return

        // 10% chance to show
        if ((0..9).random() != 0) return

        val popupView = findViewById<ConstraintLayout>(R.id.pro_popup_include) ?: return
        Anim.fadeIn(popupView, 300)
        // An overlay is visible — enable our back interceptor
        setBackInterceptionEnabled(true)

        findViewById<Button>(R.id.popup_action_button)?.setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
            // leaving the activity — make sure callback disabled when returning default state
            setBackInterceptionEnabled(false)
        }

        val suppressPopup: () -> Unit = {
            Anim.fadeOutAnim(popupView, 300)
            // Suppress for 30 days (in millis)
            val suppressUntil = now + 30 * 24 * 60 * 60 * 1000L
            prefs.edit().putLong("popup_suppressed_until", suppressUntil).apply()
            // popup closed -> disable callback
            setBackInterceptionEnabled(false)
        }

        findViewById<Button>(R.id.popup_secondary_button)?.setOnClickListener { suppressPopup() }
        findViewById<ConstraintLayout>(R.id.background_popup_pro)?.setOnClickListener { suppressPopup() }
    }


    private fun checkSale() {
        val saleStartDate = SimpleDateFormat("yyyy/MM/dd").parse(getString(R.string.next_sale_start)) //Back to school sale
        val saleEndDate = SimpleDateFormat("yyyy/MM/dd").parse(getString(R.string.next_sale_end)) //Back to school sale
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val date = calendar.time
        val proText = findViewById<TextView>(R.id.pro_btn)

        if (date > saleStartDate) {
            //Set new attributes for DonateBtn
            proText.text = "GO PRO - SCHOOL START SALE"
            proText.setTextColor(getColorStateList(R.color.orange))
            proText.setCompoundDrawableTintList(getColorStateList(R.color.orange))
        }
        if (date > saleEndDate) {
            Timer().schedule(2) {
                proText.text = "Go Pro - more features"
            }
        }
        else {
        }
    }

    private fun scrollAdapter() {
        val zoomLay = findViewById<ZoomLayout>(R.id.scrollView)
        val yScroll = findViewById<ScrollView>(R.id.leftBar)
        val xScroll = findViewById<HorizontalScrollView>(R.id.topBar)
        val corner = findViewById<TextView>(R.id.corner)

        // We'll convert 32dp to pixels once and reuse
        val raisePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (zoomLay.zoom < 1) {
                    // bars hidden
                    yScroll.visibility = View.INVISIBLE
                    xScroll.visibility = View.INVISIBLE
                    corner.visibility = View.INVISIBLE

                    // raise scrollView by 32dp if not already raised
                    if (!isScrollViewRaised) {
                        try {
                            val params6 = zoomLay.layoutParams as ViewGroup.MarginLayoutParams
                            // reduce topMargin by raisePx but ensure non-negative
                            params6.topMargin = (params6.topMargin - raisePx).coerceAtLeast(0)
                            zoomLay.layoutParams = params6
                            isScrollViewRaised = true
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Failed to raise scrollView: ${e.message}")
                        }
                    }
                }
                else {
                    // bars visible
                    yScroll.visibility = View.VISIBLE
                    xScroll.visibility = View.VISIBLE
                    corner.visibility = View.VISIBLE

                    // lower scrollView back by 32dp if we previously raised it
                    if (isScrollViewRaised) {
                        try {
                            val params6 = zoomLay.layoutParams as ViewGroup.MarginLayoutParams
                            // increase topMargin by raisePx
                            params6.topMargin = params6.topMargin + raisePx
                            zoomLay.layoutParams = params6
                            isScrollViewRaised = false
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Failed to lower scrollView: ${e.message}")
                        }
                    }
                }

                yScroll.scrollTo(0, -zoomLay.panY.toInt())
                xScroll.scrollTo(-zoomLay.panX.toInt(), 0)
                handler.postDelayed(this, 1)
            }
        }
        handler.post(runnable)
    }


    private fun getRandomItem() {
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val randomNumber = (0..117).random()
        val item = elements[randomNumber]

        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)
        val intent = Intent(this, ElementInfoActivity::class.java)
        startActivity(intent)
    }

    private fun openHover() {
        Utils.fadeInAnimBack(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
        // overlay shown -> intercept back
        setBackInterceptionEnabled(true)
    }

    private fun closeHover() {
        Utils.fadeOutAnim(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
        // closed -> no interception needed
        setBackInterceptionEnabled(false)
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) {
            if (item.element.lowercase(Locale.ROOT).contains(text.lowercase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        val searchPreference = SearchPreferences(this)
        val searchPrefValue = searchPreference.getValue()
        if (searchPrefValue == 2) {
            filteredList.sortWith(Comparator { lhs, rhs ->
                lhs.element.compareTo(rhs.element)
            })
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = ElementAdapter(filteredList, this, this)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (recyclerView.adapter?.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box).visibility = View.GONE
            }
        }, 10)
    }

    override fun elementClickListener2(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)

        val intent = Intent(this, ElementInfoActivity::class.java)
        startActivity(intent)
    }

    // Keep onBackPressed as a fallback; main logic is in handleBack()
    override fun onBackPressed() {
        handleBack()
    }

    // Modern back gesture with predictive preview
    private fun handleBack() {
        // Cache views we will check/manipulate
        val popupView = findViewById<ConstraintLayout>(R.id.pro_popup_include)
        val hoverBackground = findViewById<TextView>(R.id.hover_background)
        val hoverMenu = findViewById<ConstraintLayout>(R.id.hover_menu_include)
        val slidingLayout = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout)
        val navBackground = findViewById<TextView>(R.id.nav_background)
        val navMenuInclude = findViewById<FrameLayout>(R.id.nav_menu_include)
        val searchMenu = findViewById<FrameLayout>(R.id.search_menu_include)
        val navBarMain = findViewById<FrameLayout>(R.id.nav_bar_main)
        val moreBtn = findViewById<FloatingActionButton>(R.id.more_btn)
        val searchBackground = findViewById<TextView>(R.id.background) // filter overlay background
        val filterBox = findViewById<ConstraintLayout>(R.id.filter_box)

        // 1) pro popup
        if (popupView?.visibility == View.VISIBLE) {
            Anim.fadeOutAnim(popupView, 300)
            val prefs = getSharedPreferences("popup_prefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val suppressUntil = now + 30 * 24 * 60 * 60 * 1000L
            prefs.edit().putLong("popup_suppressed_until", suppressUntil).apply()
            // Immediately disable interception so next back goes to system
            setBackInterceptionEnabled(false)
            return
        }

        // 2) hover menu
        if (hoverBackground?.visibility == View.VISIBLE || hoverMenu?.visibility == View.VISIBLE) {
            if (hoverBackground?.visibility == View.VISIBLE) Utils.fadeOutAnim(hoverBackground, 150)
            if (hoverMenu?.visibility == View.VISIBLE) Utils.fadeOutAnim(hoverMenu, 150)
            // Immediately disable interception so next back goes to system
            setBackInterceptionEnabled(false)
            return
        }

        // 3) Navigation (sliding panel) - prefer collapsing if expanded
        if (slidingLayout != null && slidingLayout.panelState == PanelState.EXPANDED) {
            // Request collapse and let the PanelSlideListener handle the fade/visibility and back interception state.
            slidingLayout.setPanelState(PanelState.COLLAPSED)
            return
        }
        // Also handle scenario where nav background/menu is visible but panel not expanded:
        if (navBackground?.visibility == View.VISIBLE || navMenuInclude?.visibility == View.VISIBLE) {
            // Request collapse and let the PanelSlideListener handle the fade/visibility and back interception state.
            slidingLayout?.setPanelState(PanelState.COLLAPSED)
            return
        }

        // 4) Filter overlay inside search (IMPORTANT: handle before closing the entire search view)
        if ((filterBox?.visibility == View.VISIBLE) || (searchBackground?.visibility == View.VISIBLE)) {
            if (searchBackground?.visibility == View.VISIBLE) Utils.fadeOutAnim(searchBackground, 150)
            if (filterBox?.visibility == View.VISIBLE) Utils.fadeOutAnim(filterBox, 150)
            if (moreBtn != null) Utils.fadeInAnim(moreBtn, 300)
            // Keep interception enabled if the search menu (or any other overlay) remains open.
            setBackInterceptionEnabled(anyOverlayOpen())
            return
        }

        // 5) Search UI: if search layout open, close it and hide keyboard
        if (searchMenu?.visibility == View.VISIBLE) {
            if (navBarMain != null) Utils.fadeInAnim(navBarMain, 150) else navBarMain?.visibility = View.VISIBLE
            if (navBackground != null) Utils.fadeOutAnim(navBackground, 150)
            Utils.fadeOutAnim(searchMenu, 150)
            if (moreBtn != null) Utils.fadeInAnim(moreBtn, 300)

            // hide keyboard if open
            val view = this.currentFocus
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    findViewById<ConstraintLayout>(R.id.view_main).doOnLayout { window.insetsController?.hide(WindowInsets.Type.ime()) }
                } else {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }

            // Immediately disable interception so next back goes to system
            setBackInterceptionEnabled(false)
            return
        }

        // Nothing special open — let system handle back (homescreen preview will appear when callback disabled)
        setBackInterceptionEnabled(false)
        if (isTaskRoot) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }

    private fun searchListener() {
        findViewById<ImageButton>(R.id.search_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_menu_include), 300)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.GONE
            Utils.fadeOutAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)

            // search overlay -> enable interception
            setBackInterceptionEnabled(true)

            findViewById<EditText>(R.id.edit_element).requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.ime())
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(findViewById<EditText>(R.id.edit_element), InputMethodManager.SHOW_IMPLICIT)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
        }
        findViewById<FloatingActionButton>(R.id.close_element_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_menu_include), 300)
            Utils.fadeInAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.VISIBLE

            // search closed -> disable interception
            setBackInterceptionEnabled(false)

            val view = this.currentFocus
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    findViewById<ConstraintLayout>(R.id.view_main).doOnLayout { window.insetsController?.hide(WindowInsets.Type.ime()) }
                } else {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
    }

    private fun mediaListeners() {
        findViewById<FloatingActionButton>(R.id.bluesky_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.bluesky))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.facebook_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.facebook))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.instagram_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.instagram))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.homepage_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.homepage))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    // Navmenu listeners
    private fun onClickNav() {
        findViewById<ImageButton>(R.id.menu_btn).setOnClickListener {
            findViewById<FrameLayout>(R.id.nav_menu_include).visibility = View.VISIBLE
            findViewById<TextView>(R.id.nav_background).visibility = View.VISIBLE
            Utils.fadeInAnimBack(findViewById<TextView>(R.id.nav_background), 200)
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).panelState = PanelState.EXPANDED
            // nav opened -> intercept back
            setBackInterceptionEnabled(true)
        }
        findViewById<TextView>(R.id.nav_background).setOnClickListener {
            findViewById<FrameLayout>(R.id.search_menu_include).visibility = View.GONE
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).setPanelState(PanelState.COLLAPSED)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.VISIBLE
            Utils.fadeOutAnim(findViewById<TextView>(R.id.nav_background), 100)
            // nav closed -> disable interception
            setBackInterceptionEnabled(false)
        }
        findViewById<TextView>(R.id.pro_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.pro_fab).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.solubility_btn).setOnClickListener {
            val intent = Intent(this, TableActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.calculator_btn).setOnClickListener {
            val intent = Intent(this, ToolsActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.dictionary_btn).setOnClickListener {
            val intent = Intent(this, DictionaryActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.blog_btn).setOnClickListener{
            val packageManager = packageManager
            val blogURL = "https://www.jlindemann.se/homepage/blog"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
    }

    //Setup clickListeners for hover menu.
    private fun hoverListeners(elements: ArrayList<Element>, proValue: Int) {
        findViewById<TextView>(R.id.h_name_btn).setOnClickListener { initName(elements) }
        findViewById<TextView>(R.id.h_group_btn).setOnClickListener { initGroups(elements) }
        findViewById<TextView>(R.id.h_electronegativity_btn).setOnClickListener { initElectro(elements) }
        findViewById<TextView>(R.id.atomic_weight_btn).setOnClickListener { initTableChange(elements, "element_atomicmass") }
        findViewById<TextView>(R.id.boiling_btn).setOnClickListener { initBoiling(elements) }
        findViewById<TextView>(R.id.melting_point).setOnClickListener { initMelting(elements) }
        findViewById<TextView>(R.id.h_phase_btn).setOnClickListener { initTableChange(elements, "element_phase") }
        findViewById<TextView>(R.id.h_year_btn).setOnClickListener { initTableChange(elements, "element_year") }
        findViewById<TextView>(R.id.h_fusion_btn).setOnClickListener { initTableChange(elements, "element_fusion_heat") }
        findViewById<TextView>(R.id.h_specific_btn).setOnClickListener { initTableChange(elements, "element_specific_heat_capacity") }
        findViewById<TextView>(R.id.h_vaporizaton_btn).setOnClickListener { initTableChange(elements, "element_vaporization_heat") }
        findViewById<TextView>(R.id.h_electrical_type_btn).setOnClickListener { initTableChange(elements, "electrical_type") }
        findViewById<TextView>(R.id.h_superconducting_point_btn).setOnClickListener { initTableChange(elements, "superconducting_point") }
        findViewById<TextView>(R.id.h_magnetic_type_btn).setOnClickListener { initTableChange(elements, "magnetic_type") }
        findViewById<TextView>(R.id.h_electrical_resistivity_btn).setOnClickListener { initTableChange(elements, "resistivity") }
        findViewById<TextView>(R.id.h_cas_number_btn).setOnClickListener { initTableChange(elements, "cas_number") }
        findViewById<TextView>(R.id.h_eg_number_btn).setOnClickListener { initTableChange(elements, "eg_number") }


        //Check if user has PRO version on not and give additional features if.
        findViewById<TextView>(R.id.h_poisson_ratio_btn).setOnClickListener {
            if (proValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent) }
            if (proValue == 100) { initTableChange(elements, "poisson_ratio") }
        }
        findViewById<TextView>(R.id.h_young_modulus_btn).setOnClickListener {
            if (proValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent) }
            if (proValue == 100) { initTableChange(elements, "young_modulus") }
        }
        findViewById<TextView>(R.id.h_bulk_modulus_btn).setOnClickListener {
            if (proValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent) }
            if (proValue == 100) { initTableChange(elements, "bulk_modulus") }
        }
        findViewById<TextView>(R.id.h_shear_modulus_btn).setOnClickListener {
            if (proValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent) }
            if (proValue == 100) { initTableChange(elements, "shear_modulus") }
        }
        findViewById<TextView>(R.id.h_poisson_constant_btn).setOnClickListener {
            if (proValue == 1) {
                val intent = Intent(this, ProActivity::class.java)
                startActivity(intent) }
            if (proValue == 100) { initTableChange(elements, "poisson_ratio") }
        }
    }


    private fun setupNavListeners() {
        findViewById<ImageButton>(R.id.settings_btn).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setOnCLickListenerSetups(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val btn = findViewById<TextView>(resIDB)
            btn.foreground = ContextCompat.getDrawable(this,
                R.drawable.t_ripple
            );
            btn.isClickable = true
            btn.isFocusable = true
            btn.setOnClickListener {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend = ElementSendAndLoad(this)
                ElementSend.setValue(item.element)
                startActivity(intent)
            }
        }
    }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        findViewById<ConstraintLayout>(R.id.filter_box).visibility = View.GONE
        findViewById<TextView>(R.id.background).visibility = View.GONE
        findViewById<FloatingActionButton>(R.id.filter_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeInAnim(findViewById<TextView>(R.id.background), 150)
            // filter overlay shown -> intercept back
            setBackInterceptionEnabled(true)
        }
        // click on filter background should close filter but keep interception if search menu is still open
        findViewById<TextView>(R.id.background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            // filter closed -> keep interception if other overlays (like search menu) remain open
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        findViewById<TextView>(R.id.elmt_numb_btn2).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            // filter closed -> keep interception if other overlays remain (e.g. search menu)
            setBackInterceptionEnabled(anyOverlayOpen())
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        findViewById<TextView>(R.id.electro_btn).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            // filter closed -> keep interception if other overlays (like search menu) remain open
            setBackInterceptionEnabled(anyOverlayOpen())
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        findViewById<TextView>(R.id.alphabet_btn).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(2)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            // filter closed -> keep interception if other overlays remain
            setBackInterceptionEnabled(anyOverlayOpen())
            filtList.sortWith(Comparator { lhs, rhs ->
                lhs.element.compareTo(rhs.element)
            })
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
    }

    private fun proChanges() {
        findViewById<TextView>(R.id.pro_btn).text = getString(R.string.member_btn)
        findViewById<ImageButton>(R.id.pro_fab).visibility = View.GONE
    }

    //For updating when user purschases PRO Version in ProActivity
    private fun setProFabVisibilityGoneIfProValue100() {
        val proPref = ProVersion(this)
        val value = proPref.getValue()
        val proFab = findViewById<View>(R.id.pro_fab)
        if (value == 100) {
            proFab?.visibility = View.GONE
        } else {
            proFab?.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        setProFabVisibilityGoneIfProValue100()
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<LinearLayout>(R.id.navLin).setPadding(left, 0, right, 0)

        val params = findViewById<FrameLayout>(R.id.common_title_back_main).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<FrameLayout>(R.id.common_title_back_main).layoutParams = params

        val params2 = findViewById<FrameLayout>(R.id.nav_bar_main).layoutParams as ViewGroup.LayoutParams
        params2.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar)
        findViewById<FrameLayout>(R.id.nav_bar_main).layoutParams = params2

        val paramsFAB = findViewById<ImageButton>(R.id.pro_fab).layoutParams as ViewGroup.MarginLayoutParams
        paramsFAB.bottomMargin = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar) + resources.getDimensionPixelSize(R.dimen.margin)
        findViewById<ImageButton>(R.id.pro_fab).layoutParams = paramsFAB

        val params3 = findViewById<FloatingActionButton>(R.id.more_btn).layoutParams as ViewGroup.MarginLayoutParams
        params3.bottomMargin = bottom + (resources.getDimensionPixelSize(R.dimen.nav_bar))/2 + (resources.getDimensionPixelSize(R.dimen.title_bar_elevation))
        findViewById<FloatingActionButton>(R.id.more_btn).layoutParams = params3

        val params4 = findViewById<FrameLayout>(R.id.common_title_back_search).layoutParams as ViewGroup.LayoutParams
        params4.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_search).layoutParams = params4

        findViewById<RecyclerView>(R.id.element_recyclerview).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val navSide = findViewById<FrameLayout>(R.id.nav_content).layoutParams as ViewGroup.MarginLayoutParams
        navSide.rightMargin = right
        navSide.leftMargin = left
        findViewById<FrameLayout>(R.id.nav_content).layoutParams = navSide

        val barSide = findViewById<FrameLayout>(R.id.search_box).layoutParams as ViewGroup.MarginLayoutParams
        barSide.rightMargin = right
        barSide.leftMargin = left
        findViewById<FrameLayout>(R.id.search_box).layoutParams = barSide

        val leftScrollBar = findViewById<ScrollView>(R.id.leftBar).layoutParams as ViewGroup.MarginLayoutParams
        leftScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main) + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<ScrollView>(R.id.leftBar).layoutParams = leftScrollBar

        val topScrollBar = findViewById<HorizontalScrollView>(R.id.topBar).layoutParams as ViewGroup.MarginLayoutParams
        topScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<HorizontalScrollView>(R.id.topBar).layoutParams = topScrollBar

        val numb = findViewById<ScrollView>(R.id.leftBar).layoutParams as ViewGroup.LayoutParams
        numb.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<ScrollView>(R.id.leftBar).layoutParams = numb

        val cornerP = findViewById<TextView>(R.id.corner).layoutParams as ViewGroup.LayoutParams
        cornerP.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<TextView>(R.id.corner).layoutParams = cornerP

        val cornerM = findViewById<TextView>(R.id.corner).layoutParams as ViewGroup.MarginLayoutParams
        cornerM.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<TextView>(R.id.corner).layoutParams = cornerM

        val params5 = findViewById<ConstraintLayout>(R.id.hover_menu_include).layoutParams as ViewGroup.MarginLayoutParams
        params5.bottomMargin = bottom
        findViewById<ConstraintLayout>(R.id.hover_menu_include).layoutParams = params5

        val params6 = findViewById<ZoomLayout>(R.id.scrollView).layoutParams as ViewGroup.MarginLayoutParams
        params6.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main) + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<ZoomLayout>(R.id.scrollView).layoutParams = params6

        val params7 = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).layoutParams as ViewGroup.LayoutParams
        params7.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_view)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).layoutParams = params7

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box).layoutParams = searchEmptyImgPrm

        val params8 = findViewById<LinearLayout>(R.id.one).layoutParams as ViewGroup.MarginLayoutParams
        params8.marginStart = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<LinearLayout>(R.id.one).layoutParams = params8
    }

    private fun updateStats() {
        val statistics = java.util.ArrayList<Statistics>()
        StatisticsModel.getList(this, statistics)
        val stat3 = statistics.find { it.id == 3 } //search stat
        stat3?.incrementProgress(this, 1)
    }
    private fun updateStatAchievement() {
        val achievements = java.util.ArrayList<Achievement>()
        AchievementModel.getList(this, achievements)
        val achievement8 = achievements.find { it.id == 8 } //search stat
        achievement8?.incrementProgress(this, 1)
    }

    // Helper: returns true if any UI overlay that needs to intercept back is still visible/open.
    private fun anyOverlayOpen(): Boolean {
        val popupView = findViewById<ConstraintLayout>(R.id.pro_popup_include)
        val hoverBackground = findViewById<TextView>(R.id.hover_background)
        val hoverMenu = findViewById<ConstraintLayout>(R.id.hover_menu_include)
        val slidingLayout = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout)
        val navBackground = findViewById<TextView>(R.id.nav_background)
        val navMenuInclude = findViewById<FrameLayout>(R.id.nav_menu_include)
        val searchMenu = findViewById<FrameLayout>(R.id.search_menu_include)
        val searchBackground = findViewById<TextView>(R.id.background)
        val filterBox = findViewById<ConstraintLayout>(R.id.filter_box)

        if (popupView?.visibility == View.VISIBLE) return true
        if (hoverBackground?.visibility == View.VISIBLE) return true
        if (hoverMenu?.visibility == View.VISIBLE) return true
        if (slidingLayout != null && slidingLayout.panelState == PanelState.EXPANDED) return true
        if (navBackground?.visibility == View.VISIBLE) return true
        if (navMenuInclude?.visibility == View.VISIBLE) return true
        if (filterBox?.visibility == View.VISIBLE) return true
        if (searchBackground?.visibility == View.VISIBLE) return true
        if (searchMenu?.visibility == View.VISIBLE) return true
        return false
    }

    // Centralized enabling/disabling of back interception; also registers/unregisters platform callback on newer OS.
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        // Mirror handleOnBackPressed behaviour for platform back invocation
                        handleBack()
                        // keep registered only if overlays remain
                        if (!anyOverlayOpen()) {
                            try {
                                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                            } catch (_: Exception) { }
                            onBackInvokedCb = null
                            backCallback?.isEnabled = false
                        }
                    }
                    onBackInvokedDispatcher.registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        onBackInvokedCb!!
                    )
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

    override fun onDestroy() {
        super.onDestroy()
        // Remove callback
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