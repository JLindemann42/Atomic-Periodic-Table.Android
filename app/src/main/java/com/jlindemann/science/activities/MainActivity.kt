package com.jlindemann.science.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.graphics.Typeface
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.jlindemann.science.R
import com.jlindemann.science.model.*
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.*
import com.jlindemann.science.adapter.*
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.extensions.TableExtension
import com.jlindemann.science.fragments.FlashcardFragment
import com.jlindemann.science.fragments.HomeFragment
import com.jlindemann.science.activities.tools.CalculatorActivity
import com.jlindemann.science.activities.tools.LearningGamesActivity
import com.jlindemann.science.activities.tools.FlashCardActivity
import com.jlindemann.science.activities.TableActivity
import com.jlindemann.science.activities.ToolsActivity
import com.jlindemann.science.activities.UserActivity
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.IntroductionActivity
import com.jlindemann.science.activities.SplashActivity
import com.jlindemann.science.activities.SolubilityActivity
import com.jlindemann.science.activities.tables.IonActivity
import com.jlindemann.science.activities.tables.DictionaryActivity
import com.jlindemann.science.activities.tables.ElectrodeActivity
import com.jlindemann.science.activities.tables.EquationsActivity
import com.jlindemann.science.activities.tables.EmissionActivity
import com.jlindemann.science.activities.tables.phActivity
import com.jlindemann.science.activities.tables.PoissonActivity
import com.jlindemann.science.activities.tables.GeologyActivity
import com.jlindemann.science.activities.tables.NuclideActivity
import com.jlindemann.science.activities.tables.ConstantsActivity
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.OrderActivity
import com.jlindemann.science.activities.settings.AboutActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.settings.LicensesActivity
import com.jlindemann.science.activities.settings.CreditsActivity
import com.jlindemann.science.activities.settings.SourcesActivity
import com.jlindemann.science.activities.settings.UnitActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jlindemann.science.fragments.ProFragment
import com.jlindemann.science.fragments.TablesFragment
import com.jlindemann.science.fragments.ToolsFragment
import com.otaliastudios.zoom.ZoomLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : TableExtension(), ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null

    // AI Panel. All of its state and view wiring lives in the controller.
    private val aiScope = CoroutineScope(Dispatchers.Main)
    private var aiPanel: AiChatPanelController? = null

    private var currentFragmentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val themePreference = ThemePreference(this)
        when (themePreference.getValue()) {
            100 -> {
                if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                    setTheme(R.style.AppThemeDark)
                } else {
                    setTheme(R.style.AppTheme)
                }
            }
            0 -> setTheme(R.style.AppTheme)
            1 -> setTheme(R.style.AppThemeDark)
        }
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)

        ElementModel.getList(elementList, this)
        
        val recyclerView = findViewById<RecyclerView>(R.id.element_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        mAdapter = ElementAdapter(elementList, this, this)
        recyclerView.adapter = mAdapter

        findViewById<EditText>(R.id.edit_element).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), elementList, recyclerView)
                updateStats()
                updateStatAchievement()
            }
        })

        setupBottomNavigation()
        findViewById<View>(R.id.ai_panel_include)?.let { panelRoot ->
            aiPanel = AiChatPanelController(
                activity = this,
                panelRoot = panelRoot,
                scope = aiScope,
                // Hooked here rather than at each call site so no way of opening the panel can
                // forget to take over the back gesture.
                onPanelOpened = { setBackInterceptionEnabled(true) },
                onPanelHidden = { setBackInterceptionEnabled(anyOverlayOpen()) }
            ).also { it.setup() }
        }

        findViewById<View>(R.id.ai_chat_fab).setOnClickListener { aiPanel?.open() }
        
        findViewById<View>(R.id.filterFab_main).setOnClickListener {
            openHover()
        }

        searchListener()
        searchFilter(elementList, recyclerView)
        mediaListeners()
        checkSale()
        maybeShowProPopup()
        updateVersionBadge()

        // Initial fragment
        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), "home")
        }

        // Modern Top Bar actions
        findViewById<ImageButton>(R.id.user_btn).setOnClickListener {
            startActivity(Intent(this, UserActivity::class.java))
        }
        findViewById<ImageButton>(R.id.settings_btn_top).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<TextView>(R.id.hover_background).setOnClickListener { closeHover() }

        // Load profile
        loadUserProfileImage()
        handleWidgetIntent()
        handleProIntent(intent)

        // Back button interception setup
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
        
        // Initial setup for hover menu listeners
        hoverListeners(elementList, ProVersion(this).getValue())
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(HomeFragment(), "home")
                    true
                }
                R.id.nav_tables -> {
                    switchFragment(TablesFragment(), "tables")
                    true
                }
                R.id.nav_tools -> {
                    switchFragment(ToolsFragment(), "tools")
                    true
                }
                R.id.nav_learning_games -> {
                    switchFragment(FlashcardFragment(), "flashcards")
                    true
                }
                R.id.nav_pro -> {
                    switchFragment(ProFragment(), "pro")
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        if (currentFragmentTag == tag) return
        
        val transaction = supportFragmentManager.beginTransaction()
        
        transaction.setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        
        transaction.replace(R.id.fragment_container, fragment, tag)
        transaction.commit()
        
        handleFilterFabVisibility(tag)
        currentFragmentTag = tag
        updateTopBarForFragment(tag)
    }


    private fun handleFilterFabVisibility(tag: String) {
        val filterFab = findViewById<View>(R.id.filterFab_main) ?: return
        if (tag == "home") {
            if (filterFab.visibility != View.VISIBLE) {
                filterFab.visibility = View.VISIBLE
                filterFab.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
            }
        } else {
            if (filterFab.visibility == View.VISIBLE) {
                filterFab.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
                    filterFab.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun updateTopBarForFragment(tag: String) {
        val displayModeBtn = findViewById<View>(R.id.display_mode_btn)

    }

    private fun handleBack() {
        if (aiPanel?.isOpen() == true) {
            aiPanel?.close()
            return
        }
        if (popupVisible()) {
            hidePopup()
            return
        }
        if (hoverVisible()) {
            closeHover()
            return
        }
        if (searchVisible()) {
            closeSearch()
            return
        }
        if (currentFragmentTag != "home") {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_home
            return
        }

        setBackInterceptionEnabled(false)
        if (isTaskRoot) moveTaskToBack(true) else super.onBackPressed()
    }

    private fun aiPanelRootVisible() = aiPanel?.isOpen() == true
    private fun popupVisible() = findViewById<View>(R.id.pro_popup_include)?.visibility == View.VISIBLE
    private fun hoverVisible() = findViewById<View>(R.id.hover_menu_include)?.visibility == View.VISIBLE
    private fun searchVisible() = findViewById<View>(R.id.search_menu_include)?.visibility == View.VISIBLE

    private fun hidePopup() {
        val popupView = findViewById<ConstraintLayout>(R.id.pro_popup_include)
        Anim.fadeOutAnim(popupView, 300)
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    private fun closeSearch() {
        val searchMenu = findViewById<FrameLayout>(R.id.search_menu_include)
        Utils.fadeOutAnim(searchMenu, 150)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    private fun anyOverlayOpen(): Boolean {
        return aiPanelRootVisible() || popupVisible() || hoverVisible() || searchVisible()
    }

    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled && onBackInvokedCb == null) {
                onBackInvokedCb = android.window.OnBackInvokedCallback { handleBack() }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCb!!)
            } else if (!enabled && onBackInvokedCb != null) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                onBackInvokedCb = null
            }
        }
    }

    private fun maybeShowProPopup() {
        val proPref = ProVersion(this)
        if (proPref.getValue() == 100) return
        val prefs = getSharedPreferences("popup_prefs", Context.MODE_PRIVATE)
        val starts = prefs.getInt("app_starts", 0) + 1
        prefs.edit().putInt("app_starts", starts).apply()
        if (starts <= 10) return
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("popup_suppressed_until", 0L)) return
        if ((0..9).random() != 0) return
        val popupView = findViewById<ConstraintLayout>(R.id.pro_popup_include) ?: return
        Anim.fadeIn(popupView, 300)
        setBackInterceptionEnabled(true)
        findViewById<Button>(R.id.popup_action_button)?.setOnClickListener {
            Anim.fadeOutAnim(popupView, 300)
            goToProPage()
            setBackInterceptionEnabled(false)
        }
        val suppressPopup: () -> Unit = {
            Anim.fadeOutAnim(popupView, 300)
            prefs.edit().putLong("popup_suppressed_until", now + 30 * 24 * 60 * 60 * 1000L).apply()
            setBackInterceptionEnabled(false)
        }
        findViewById<Button>(R.id.popup_secondary_button)?.setOnClickListener { suppressPopup() }
        findViewById<ConstraintLayout>(R.id.background_popup_pro)?.setOnClickListener { suppressPopup() }
    }

    private fun checkSale() {
        try {
            val saleStartDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(getString(R.string.next_sale_start))
            val saleEndDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(getString(R.string.next_sale_end))
            val now = Date()
            val proText = findViewById<TextView>(R.id.pro_btn) ?: return
            if (now > saleStartDate && now < saleEndDate) {
                proText.text = getString(R.string.go_pro_school_start_sale)
                proText.setTextColor(ContextCompat.getColor(this, R.color.orange))
            }
        } catch (e: Exception) {}
    }

    fun openHover() {
        Utils.fadeInAnimBack(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
        setBackInterceptionEnabled(true)
    }

    fun closeHover() {
        Utils.fadeOutAnim(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
        setBackInterceptionEnabled(false)
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList = ArrayList<Element>()
        for (item in list) {
            if (item.element.lowercase().contains(text.lowercase())) filteredList.add(item)
        }
        if (SearchPreferences(this).getValue() == 2) {
            filteredList.sortBy { it.element }
        }
        mAdapter.filterList(filteredList)
        recyclerView.adapter = ElementAdapter(filteredList, this, this)
        if (filteredList.isEmpty()) {
            findViewById<View>(R.id.empty_search_box).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.empty_search_box).visibility = View.GONE
        }
    }

    override fun elementClickListener2(item: Element, position: Int) {
        ElementSendAndLoad(this).setValue(item.elementKey)
        startActivity(Intent(this, ElementInfoActivity::class.java))
    }

    private fun searchListener() {
        findViewById<ImageButton>(R.id.search_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_menu_include), 300)
            setBackInterceptionEnabled(true)
            findViewById<EditText>(R.id.edit_element).requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(findViewById<EditText>(R.id.edit_element), InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<View>(R.id.close_element_search).setOnClickListener {
            closeSearch()
        }
    }

    private fun mediaListeners() {
        val openUrl = { url: String -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        findViewById<View>(R.id.bluesky_button)?.setOnClickListener { openUrl(getString(R.string.bluesky)) }
        findViewById<View>(R.id.facebook_button)?.setOnClickListener { openUrl(getString(R.string.facebook)) }
        findViewById<View>(R.id.instagram_button)?.setOnClickListener { openUrl(getString(R.string.instagram)) }
        findViewById<View>(R.id.homepage_button)?.setOnClickListener { openUrl(getString(R.string.homepage)) }
    }

    private fun hoverListeners(elements: ArrayList<Element>, proValue: Int) {
        val getHome = { supportFragmentManager.findFragmentByTag("home") as? HomeFragment }
        
        findViewById<View>(R.id.h_name_btn).setOnClickListener { 
            val fragment = getHome()
            fragment?.view?.let { fragment.initName(it, elements) } 
            closeHover()
        }
        
        val click = { btn: Int, key: String ->
            findViewById<View>(btn).setOnClickListener {
                val fragment = getHome()
                fragment?.view?.let { fragment.initTableChange(it, elements, key) }
                closeHover()
            }
        }
        
        findViewById<View>(R.id.h_group_btn).setOnClickListener { 
            val fragment = getHome()
            fragment?.view?.let { fragment.initTableChange(it, elements, "element_group") } 
            closeHover()
        }
        
        findViewById<View>(R.id.h_electronegativity_btn).setOnClickListener { 
            val fragment = getHome()
            fragment?.view?.let { fragment.initElectro(it, elements, "element_electronegativty") }
            closeHover()
        }
        
        click(R.id.atomic_weight_btn, "element_atomicmass")
        click(R.id.boiling_btn, "element_boiling_celsius")
        click(R.id.melting_point, "element_melting_celsius")
        click(R.id.h_phase_btn, "element_phase")
        click(R.id.h_year_btn, "element_year")
        click(R.id.h_fusion_btn, "element_fusion_heat")
        click(R.id.h_specific_btn, "element_specific_heat_capacity")
        click(R.id.h_vaporizaton_btn, "element_vaporization_heat")
        click(R.id.h_electrical_type_btn, "electrical_type")
        click(R.id.h_superconducting_point_btn, "superconducting_point")
        click(R.id.h_magnetic_type_btn, "magnetic_type")
        click(R.id.h_electrical_resistivity_btn, "resistivity")
        click(R.id.h_cas_number_btn, "cas_number")
        click(R.id.h_eg_number_btn, "eg_number")

        val proClick = { btn: Int, key: String ->
            findViewById<View>(btn).setOnClickListener {
                if (proValue == 100) {
                    val fragment = getHome()
                    fragment?.view?.let { fragment.initTableChange(it, elements, key) }
                    closeHover()
                } else {
                    goToProPage()
                }
            }
        }
        proClick(R.id.h_poisson_ratio_btn, "poisson_ratio")
        proClick(R.id.h_young_modulus_btn, "young_modulus")
        proClick(R.id.h_bulk_modulus_btn, "bulk_modulus")
        proClick(R.id.h_shear_modulus_btn, "shear_modulus")
        proClick(R.id.h_poisson_constant_btn, "poisson_ratio")

        findViewById<View>(R.id.random_btn).setOnClickListener {
            if (elements.isNotEmpty()) {
                val item = elements.random()

                // Achievement 7: Element of Surprise!
                val achievements = ArrayList<Achievement>()
                AchievementModel.getList(this, achievements)
                achievements.find { it.id == 7 }?.incrementProgress(this, 1)

                ElementSendAndLoad(this).setValue(item.elementKey)
                startActivity(Intent(this, ElementInfoActivity::class.java))
                closeHover()
            }
        }
    }

    private fun setOnCLickListenerSetups(list: ArrayList<Element>) { }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        findViewById<View>(R.id.filter_btn).setOnClickListener {
            Anim.fadeIn(findViewById(R.id.filter_box), 150)
            Anim.fadeIn(findViewById(R.id.background), 150)
            setBackInterceptionEnabled(true)
        }
        findViewById<View>(R.id.background).setOnClickListener {
            Anim.fadeOutAnim(findViewById(R.id.filter_box), 150)
            Anim.fadeOutAnim(findViewById(R.id.background), 150)
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        val applySort = { type: Int ->
            SearchPreferences(this).setValue(type)
            val filtList = if (type == 2) ArrayList(list).apply { sortBy { it.element } } else list
            Anim.fadeOutAnim(findViewById(R.id.filter_box), 150)
            Anim.fadeOutAnim(findViewById(R.id.background), 150)
            setBackInterceptionEnabled(anyOverlayOpen())
            mAdapter.filterList(filtList)
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        findViewById<View>(R.id.elmt_numb_btn2).setOnClickListener { applySort(0) }
        findViewById<View>(R.id.electro_btn).setOnClickListener { applySort(1) }
        findViewById<View>(R.id.alphabet_btn).setOnClickListener { applySort(2) }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfileImage()
        updateVersionBadge()

        aiScope.launch { aiPanel?.refreshLanguage() }

        intent?.let {
            if (it.getBooleanExtra("show_flashcard_results", false)) {
                it.removeExtra("show_flashcard_results")
                findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_learning_games
                // Give it a tiny bit of time for the fragment to be swapped in if needed
                Handler(Looper.getMainLooper()).postDelayed({
                    (supportFragmentManager.findFragmentByTag("flashcards") as? FlashcardFragment)?.handleGameResults(it)
                }, 100)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleProIntent(intent)
        if (intent.getBooleanExtra("show_flashcard_results", false)) {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_learning_games
            // The fragment might already be active or being created
            Handler(Looper.getMainLooper()).postDelayed({
                (supportFragmentManager.findFragmentByTag("flashcards") as? FlashcardFragment)?.handleGameResults(intent)
            }, 100)
        }
    }

    private fun handleProIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("show_pro", false) == true) {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_pro
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val topBar = findViewById<View>(R.id.common_title_back_main)
        val mainBarHeight = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        topBar?.layoutParams?.height = mainBarHeight
        topBar?.requestLayout()
        
        val bottomNav = findViewById<View>(R.id.bottom_nav)
        bottomNav?.setPadding(0, 0, 0, bottom)
        findViewById<View>(R.id.logo_area)?.setPadding(left, 0, 0, 0)

        // Search Layout Insets
        val searchTopBar = findViewById<View>(R.id.common_title_back_search)
        searchTopBar?.layoutParams?.height = mainBarHeight
        searchTopBar?.requestLayout()

        val searchRecycler = findViewById<View>(R.id.element_recyclerview)
        searchRecycler?.setPadding(0, mainBarHeight, 0, bottom)

        val emptySearchBox = findViewById<View>(R.id.empty_search_box)
        (emptySearchBox?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = mainBarHeight
        emptySearchBox?.requestLayout()

        val filterBox = findViewById<View>(R.id.filter_box)
        (filterBox?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = mainBarHeight + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        filterBox?.requestLayout()

        supportFragmentManager.fragments.forEach {
            if (it is com.jlindemann.science.fragments.BaseFragment) {
                it.onApplySystemInsets(top, bottom, left, right)
            }
        }
    }

    private fun updateStats() { }
    private fun updateStatAchievement() { }

    private fun loadUserProfileImage() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userBtn = findViewById<ImageButton>(R.id.user_btn)
        val aiUserBtn = findViewById<ImageButton>(R.id.ai_user_btn)

        if (currentUser?.photoUrl != null) {
            userBtn?.let {
                it.imageTintList = null
                Glide.with(this).load(currentUser.photoUrl).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(it)
            }
            aiUserBtn?.let {
                it.imageTintList = null
                Glide.with(this).load(currentUser.photoUrl).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(it)
            }
        } else {
            val defaultColor = getColorStateListFromAttr(R.attr.colorOnSurfaceVariant)
            userBtn?.let {
                it.imageTintList = defaultColor
                it.setImageResource(R.drawable.ic_account)
            }
            aiUserBtn?.let {
                it.imageTintList = defaultColor
                it.setImageResource(R.drawable.ic_account)
            }
        }
    }

    private fun handleWidgetIntent() {
        val action = intent.action
        
        when (action) {
            "OPEN_AI_CHAT" -> {
                aiPanel?.open()
                // The controller queues this behind its own initialisation, so a widget query
                // can no longer reach an agent that has not finished loading.
                intent.getStringExtra("initial_query")?.let { aiPanel?.send(it) }
            }
            "OPEN_TABLE" -> {
                findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_home
            }
            "OPEN_QUIZ" -> {
                startActivity(Intent(this, LearningGamesActivity::class.java))
            }
            "OPEN_MOLAR_MASS" -> {
                startActivity(Intent(this, CalculatorActivity::class.java))
            }
            else -> {
                intent.getStringExtra("widget_element_key")?.let { key ->
                    ElementSendAndLoad(this).setValue(key)
                    Handler(Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this, ElementInfoActivity::class.java))
                    }, 100)
                }
            }
        }
    }

    fun updateVersionBadge() {
        val versionBadge = findViewById<TextView>(R.id.version_badge) ?: return
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        val proPref = com.jlindemann.science.preferences.ProVersion(this)
        val proPlusPref = com.jlindemann.science.preferences.ProPlusVersion(this)

        val isProPlus = proPlusPref.getValue() == 100
        val isPro = proPref.getValue() == 100

        when {
            isProPlus -> {
                versionBadge.text = getString(R.string.pro_plus_badge)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorTertiary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnTertiary))
                bottomNav?.menu?.findItem(R.id.nav_pro)?.title = getString(R.string.get_pro_short)
            }
            isPro -> {
                versionBadge.text = getString(R.string.pro_title)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorPrimary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnPrimary))
                bottomNav?.menu?.findItem(R.id.nav_pro)?.title = getString(R.string.get_pro_short)
            }
            else -> {
                versionBadge.text = getString(R.string.free)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorSecondary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnSecondary))
                bottomNav?.menu?.findItem(R.id.nav_pro)?.title = getString(R.string.get_pro)
            }
        }
    }


















    override fun onDestroy() {
        super.onDestroy()
        aiPanel?.onDestroy()
        aiScope.cancel()
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            onBackInvokedCb = null
        }
    }
}
