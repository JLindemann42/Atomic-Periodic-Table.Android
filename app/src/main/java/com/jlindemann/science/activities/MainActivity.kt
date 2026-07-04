package com.jlindemann.science.activities

import android.animation.ValueAnimator
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
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.ai.AIAgentManager
import com.jlindemann.science.ai.AIPersonality
import com.jlindemann.science.ai.ChatHistoryManager
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.*
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.*
import com.jlindemann.science.adapter.*
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.extensions.TableExtension
import com.jlindemann.science.fragments.FlashcardFragment
import com.jlindemann.science.fragments.HomeFragment
import com.jlindemann.science.fragments.ProFragment
import com.jlindemann.science.fragments.TablesFragment
import com.jlindemann.science.fragments.ToolsFragment
import com.otaliastudios.zoom.ZoomLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : TableExtension(), ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null

    // AI Panel
    private lateinit var aiAgentManager: AIAgentManager
    private var aiChatMessages = mutableListOf<ChatMessage>()
    private var currentChatSessionId: String? = null
    private var aiAdapter: ChatMessageAdapter? = null
    private val aiScope = CoroutineScope(Dispatchers.Main)
    private var aiGradientAnimator: ValueAnimator? = null

    private var currentFragmentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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
        setupAIPanel()
        setupKeyboardAnimation()
        
        findViewById<View>(R.id.ai_chat_fab).setOnClickListener {
            openAIPanel()
        }
        
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
        if (aiPanelRootVisible()) {
            closeAIPanel()
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

    private fun aiPanelRootVisible() = findViewById<View>(R.id.ai_panel_include)?.visibility == View.VISIBLE
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
            startActivity(Intent(this, ProActivity::class.java))
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
            fragment?.view?.let { fragment.initTableChange(it, elements, "element_electronegativty") } 
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
                    startActivity(Intent(this, ProActivity::class.java))
                }
            }
        }
        proClick(R.id.h_poisson_ratio_btn, "poisson_ratio")
        proClick(R.id.h_young_modulus_btn, "young_modulus")
        proClick(R.id.h_bulk_modulus_btn, "bulk_modulus")
        proClick(R.id.h_shear_modulus_btn, "shear_modulus")
        proClick(R.id.h_poisson_constant_btn, "poisson_ratio")
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
        if (intent.getBooleanExtra("show_flashcard_results", false)) {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_learning_games
            // The fragment might already be active or being created
            Handler(Looper.getMainLooper()).postDelayed({
                (supportFragmentManager.findFragmentByTag("flashcards") as? FlashcardFragment)?.handleGameResults(intent)
            }, 100)
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
        val userBtn = findViewById<ImageButton>(R.id.user_btn) ?: return
        if (currentUser?.photoUrl != null) {
            Glide.with(this).load(currentUser.photoUrl).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(userBtn)
        } else {
            userBtn.setImageResource(R.drawable.ic_account)
        }
    }

    private fun handleWidgetIntent() {
        intent.getStringExtra("widget_element_key")?.let { key ->
            ElementSendAndLoad(this).setValue(key)
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, ElementInfoActivity::class.java))
            }, 100)
        }
    }

    fun updateVersionBadge() {
        val versionBadge = findViewById<TextView>(R.id.version_badge) ?: return
        val proPref = ProVersion(this)
        val proPlusPref = ProPlusVersion(this)

        when {
            proPlusPref.getValue() == 100 -> {
                versionBadge.text = getString(R.string.pro_plus_badge)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorTertiary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnTertiary))
            }
            proPref.getValue() == 100 -> {
                versionBadge.text = getString(R.string.pro_title)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorPrimary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnPrimary))
            }
            else -> {
                versionBadge.text = getString(R.string.free)
                versionBadge.backgroundTintList = getColorStateListFromAttr(R.attr.colorSecondary)
                versionBadge.setTextColor(getColorFromAttr(R.attr.colorOnSecondary))
            }
        }
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun getColorStateListFromAttr(attr: Int): android.content.res.ColorStateList? {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColorStateList(this, typedValue.resourceId)
        } else {
            android.content.res.ColorStateList.valueOf(typedValue.data)
        }
    }

    private fun setupAIPanel() {
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
        val aiPanelContainerView = aiPanelRoot.findViewById<View>(R.id.ai_panel_container) ?: return
        val aiScrim = aiPanelRoot.findViewById<View>(R.id.ai_panel_scrim) ?: return
        val aiRecyclerView = aiPanelRoot.findViewById<RecyclerView>(R.id.ai_chat_recycler) ?: return
        val aiMessageInput = aiPanelRoot.findViewById<EditText>(R.id.ai_message_input) ?: return
        val aiSendBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_send_btn) ?: return
        val aiLoadingIndicator = aiPanelRoot.findViewById<ProgressBar>(R.id.ai_loading_indicator) ?: return
        val aiLanguageBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_language_btn) ?: return
        val aiInputContainer = aiPanelRoot.findViewById<View>(R.id.ai_input_container) ?: return
        val aiUserBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_user_btn) ?: return
        val aiHistoryBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_history_btn) ?: return
        val aiHistoryContainer = aiPanelRoot.findViewById<View>(R.id.ai_history_container) ?: return
        val aiHistoryRecycler = aiPanelRoot.findViewById<RecyclerView>(R.id.ai_history_recycler) ?: return
        val aiCloseHistoryBtn = aiPanelRoot.findViewById<ImageButton>(R.id.close_history_btn) ?: return
        val aiHistoryEmptyView = aiPanelRoot.findViewById<View>(R.id.history_empty_view) ?: return
        val aiNewChatBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_new_chat) ?: return

        aiAdapter = ChatMessageAdapter(aiChatMessages)
        aiRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        aiRecyclerView.adapter = aiAdapter

        aiAgentManager = AIAgentManager(this)
        aiScope.launch {
            aiAgentManager.initialize()
            if (aiChatMessages.isEmpty()) addAIGreeting()
        }

        aiLanguageBtn.setOnClickListener { showAILanguageMenu(aiLanguageBtn) }
        aiSendBtn.setOnClickListener {
            val text = aiMessageInput.text.toString().trim()
            if (text.isNotEmpty()) sendMessageToAI(text, aiMessageInput, aiLoadingIndicator, aiRecyclerView)
        }
        aiHistoryBtn.setOnClickListener {
            if (AuthManager.isSignedIn()) showChatHistory(aiHistoryContainer, aiHistoryRecycler, aiHistoryEmptyView)
            else Toast.makeText(this, "Please sign in to view chat history", Toast.LENGTH_SHORT).show()
        }
        aiCloseHistoryBtn.setOnClickListener { aiHistoryContainer.visibility = View.GONE }
        aiUserBtn.setOnClickListener { startActivity(Intent(this, UserActivity::class.java)) }
        aiNewChatBtn.setOnClickListener { startNewAIChat() }
        aiScrim.setOnClickListener { closeAIPanel() }

        ViewCompat.setOnApplyWindowInsetsListener(aiPanelRoot) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (aiInputContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottom + resources.getDimensionPixelSize(R.dimen.margin)
            insets
        }

        val behavior = BottomSheetBehavior.from(aiPanelContainerView)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    aiPanelRoot.visibility = View.GONE
                    setBackInterceptionEnabled(anyOverlayOpen())
                    stopAIGradientPulsation()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { aiScrim.alpha = slideOffset }
        })

        startAIInputAnimation(aiPanelRoot)
    }

    private fun startAIInputAnimation(root: View) {
        val inputBg = root.findViewById<View>(R.id.ai_input_gradient_bg) ?: return
        ValueAnimator.ofFloat(0.6f, 1.0f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { inputBg.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun setupKeyboardAnimation() {
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
        val aiInputContainer = aiPanelRoot.findViewById<View>(R.id.ai_input_container) ?: return
        val aiRecyclerView = aiPanelRoot.findViewById<RecyclerView>(R.id.ai_chat_recycler) ?: return
        ViewCompat.setWindowInsetsAnimationCallback(aiInputContainer, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                val diff = (ime - sys).coerceAtLeast(0)
                aiInputContainer.translationY = -diff.toFloat()
                aiRecyclerView.translationY = -diff.toFloat()
                return insets
            }
        })
    }

    private fun showAILanguageMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val languages = ElementDataLoader.getAvailableLanguages(assets)
        languages.forEachIndexed { index, lang -> popup.menu.add(0, index, index, lang.uppercase()) }
        popup.setOnMenuItemClickListener { item ->
            val selectedLang = languages[item.itemId]
            aiScope.launch {
                aiAgentManager.setLanguage(selectedLang)
                val msg = ChatMessage(UUID.randomUUID().toString(), getString(R.string.ai_lang_switched, selectedLang.uppercase()), false, System.currentTimeMillis())
                aiChatMessages.add(msg)
                aiAdapter?.notifyItemInserted(aiChatMessages.size - 1)
                findViewById<RecyclerView>(R.id.ai_chat_recycler)?.scrollToPosition(aiChatMessages.size - 1)
            }
            true
        }
        popup.show()
    }

    private fun openAIPanel() {
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
        val aiPanelContainerView = aiPanelRoot.findViewById<View>(R.id.ai_panel_container) ?: return
        val aiScrim = aiPanelRoot.findViewById<View>(R.id.ai_panel_scrim) ?: return
        aiPanelRoot.visibility = View.VISIBLE
        val behavior = BottomSheetBehavior.from(aiPanelContainerView)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        aiPanelContainerView.post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        aiScrim.alpha = 1f
        setBackInterceptionEnabled(true)
        startAIGradientPulsation(1500, 2)
    }

    private fun startAIGradientPulsation(duration: Long = 2000, repeatCount: Int = ValueAnimator.INFINITE) {
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
        val gradientView = aiPanelRoot.findViewById<View>(R.id.ai_panel_gradient_view) ?: return
        aiGradientAnimator?.cancel()
        aiGradientAnimator = ValueAnimator.ofFloat(0f, 0.6f).apply {
            this.duration = duration
            this.repeatCount = repeatCount
            this.repeatMode = ValueAnimator.REVERSE
            addUpdateListener { gradientView.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopAIGradientPulsation() {
        aiGradientAnimator?.let {
            it.cancel()
            findViewById<View>(R.id.ai_panel_include)?.findViewById<View>(R.id.ai_panel_gradient_view)?.animate()?.alpha(0f)?.setDuration(500)?.start()
        }
        aiGradientAnimator = null
    }

    private fun closeAIPanel() {
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
        val aiPanelContainerView = aiPanelRoot.findViewById<View>(R.id.ai_panel_container) ?: return
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(aiPanelRoot.windowToken, 0)
        BottomSheetBehavior.from(aiPanelContainerView).state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun startNewAIChat() {
        aiChatMessages.clear()
        currentChatSessionId = null
        aiAgentManager.clearConversation()
        aiAdapter?.notifyDataSetChanged()
        addAIGreeting()
    }

    private fun addAIGreeting() {
        val greeting = ChatMessage(UUID.randomUUID().toString(), AIPersonality.getGreeting(this, aiAgentManager.getActiveLanguage()), false, System.currentTimeMillis())
        aiChatMessages.add(greeting)
        aiAdapter?.notifyItemInserted(aiChatMessages.size - 1)
    }

    private fun sendMessageToAI(text: String, input: EditText, loader: ProgressBar, recycler: RecyclerView) {
        val userMsg = ChatMessage(UUID.randomUUID().toString(), text, true, System.currentTimeMillis())
        aiChatMessages.add(userMsg)
        aiAdapter?.notifyItemInserted(aiChatMessages.size - 1)
        recycler.scrollToPosition(aiChatMessages.size - 1)
        input.text.clear()
        loader.visibility = View.VISIBLE
        aiAgentManager.addToConversationHistory(userMsg)
        saveCurrentChatSession()
        startAIGradientPulsation(1000, ValueAnimator.INFINITE)
        aiScope.launch {
            val response = aiAgentManager.generateResponse(text)
            loader.visibility = View.GONE
            aiChatMessages.add(response)
            aiAdapter?.notifyItemInserted(aiChatMessages.size - 1)
            recycler.scrollToPosition(aiChatMessages.size - 1)
            aiAgentManager.addToConversationHistory(response)
            saveCurrentChatSession()
            stopAIGradientPulsation()
        }
    }

    private fun saveCurrentChatSession() {
        if (AuthManager.isSignedIn() && aiChatMessages.isNotEmpty()) {
            if (currentChatSessionId == null) currentChatSessionId = UUID.randomUUID().toString()
            val firstUserMessage = aiChatMessages.firstOrNull { it.isFromUser }?.text ?: "New Chat"
            val title = if (firstUserMessage.length > 40) firstUserMessage.take(37) + "..." else firstUserMessage
            val session = ChatSession(currentChatSessionId!!, title, System.currentTimeMillis(), aiChatMessages.toList())
            ChatHistoryManager.saveChatSession(session) { success, id -> if (success && currentChatSessionId == null) currentChatSessionId = id }
        }
    }

    private fun showChatHistory(container: View, recycler: RecyclerView, emptyView: View) {
        container.visibility = View.VISIBLE
        val loader = findViewById<ProgressBar>(R.id.ai_loading_indicator)
        loader?.visibility = View.VISIBLE
        ChatHistoryManager.loadChatHistory { sessions ->
            loader?.visibility = View.GONE
            if (sessions.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recycler.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                recycler.layoutManager = LinearLayoutManager(this)
                recycler.adapter = ChatHistoryAdapter(sessions) { loadChatSession(it, container) }
            }
        }
    }

    private fun loadChatSession(session: ChatSession, historyContainer: View) {
        aiChatMessages.clear()
        aiChatMessages.addAll(session.messages)
        currentChatSessionId = session.id
        aiAdapter?.notifyDataSetChanged()
        findViewById<RecyclerView>(R.id.ai_chat_recycler)?.scrollToPosition(aiChatMessages.size - 1)
        historyContainer.visibility = View.GONE
        aiAgentManager.setConversationHistory(session.messages)
    }

    override fun onDestroy() {
        super.onDestroy()
        aiScope.cancel()
        backCallback?.remove()
        backCallback = null
        if (onBackInvokedCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
            onBackInvokedCb = null
        }
    }
}
