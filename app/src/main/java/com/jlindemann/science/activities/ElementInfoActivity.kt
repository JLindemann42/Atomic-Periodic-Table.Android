package com.jlindemann.science.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.transition.TransitionManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mmin18.widget.RealtimeBlurView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.activities.tables.IonActivity
import com.jlindemann.science.activities.tables.NuclideActivity
import com.jlindemann.science.adapter.AchievementAdapter
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.extensions.CrystalStructureView
import com.jlindemann.science.extensions.InfoExtension
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.offlinePreference
import com.jlindemann.science.preferences.sendIso
import com.jlindemann.science.utils.AiChatPanelController
import com.jlindemann.science.utils.ElementDataLoader
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.jlindemann.science.adapter.ChatMessageAdapter
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.model.ChatSession
import com.jlindemann.science.adapter.ChatHistoryAdapter
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.*
import androidx.appcompat.widget.AppCompatImageView
import java.util.UUID
import com.squareup.picasso.Picasso
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import kotlin.math.pow

class ElementInfoActivity : InfoExtension() {

    // Lifecycle-aware callback references
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    
    // Comparison mode tracking
    private var isCompareMode = false
    private var compareElementKey: String? = null
    private var mainElementName: String? = null
    private var compareElementName: String? = null
    // Same two elements, spelled in the app language, for display in the title bar
    private var mainElementDisplayName: String? = null
    private var compareElementDisplayName: String? = null

    // AI Panel. All of its state and view wiring lives in the controller.
    private val aiScope = CoroutineScope(Dispatchers.Main)
    private var aiPanel: AiChatPanelController? = null

    // Isotope Panel
    private var bottomSheetBehaviorIso: BottomSheetBehavior<View>? = null

    // Emission Panel
    private var bottomSheetBehaviorEmission: BottomSheetBehavior<View>? = null

    // Shell Panel
    private var bottomSheetBehaviorShell: BottomSheetBehavior<View>? = null

    // Ionization Panel
    private var bottomSheetBehaviorIon: BottomSheetBehavior<View>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        val ElementSendAndLoadPreference = ElementSendAndLoad(this)
        var ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
        setContentView(R.layout.activity_element_info)
        Utils.fadeInAnim(findViewById<ScrollView>(R.id.scr_view), 300)

        // readJson() will call updateElementUI which now handles overlay listeners
        readJson()
        setupStaticDetailListeners()
        offlineCheck()
        nextPrev()
        favoriteBarSetup()
        elementAnim(findViewById<FrameLayout>(R.id.overview_inc), findViewById<FrameLayout>(R.id.properties_inc))
        findViewById<View>(R.id.ai_panel_include)?.let { panelRoot ->
            aiPanel = AiChatPanelController(
                activity = this,
                panelRoot = panelRoot,
                scope = aiScope,
                // Answers on this screen are grounded in the element being viewed.
                contextElement = { mainElementName },
                onPanelOpened = {
                    updateAIUserProfileImage()
                    setBackInterceptionEnabled(true)
                },
                // This screen has other overlays, so back-interception stays on if any is open.
                onPanelHidden = { setBackInterceptionEnabled(anyOverlayOpen()) }
            ).also { it.setup() }
        }
        setupIsotopePanel()
        setupEmissionPanel()
        setupShellPanel()
        setupIonizationPanel()
        findViewById<ConstraintLayout>(R.id.view).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<MaterialButton>(R.id.back_btn).setOnClickListener { super.onBackPressed() }

        findViewById<View>(R.id.close_compare_btn).setOnClickListener {
            exitCompareMode()
        }
        findViewById<ImageButton>(R.id.edit_fav_btn).setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.ai_chat_fab).setOnClickListener {
            aiPanel?.open()
        }
        findViewById<MaterialButton>(R.id.i_btn).setOnClickListener {
            val intent = Intent(this, SubmitActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_btn).setOnClickListener {
            goToProPage()
        }
        findViewById<TextView>(R.id.get_pro_hardness_btn).setOnClickListener {
            goToProPage()
        }
        findViewById<TextView>(R.id.get_pro_hazard_btn).setOnClickListener {
            goToProPage()
        }
        findViewById<AppCompatImageView>(R.id.notes_sync_status).setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            startActivity(intent)
        }
        findViewById<MaterialButton>(R.id.wikipedia_btn).setOnClickListener {
            // Wikipedia logic is in InfoExtension.wikiListener
            // We can trigger it by finding the Wikipedia URL and calling wikiListener or just let the button handle it.
            // However, the button in activity_element_info is outside the content that updateElementUI handles.
            // We need the wikilink for the current element.
            val elementSendAndLoadValue = ElementSendAndLoad(this).getValue()
            val jsonObject = ElementDataLoader.loadElementData(this, elementSendAndLoadValue ?: "hydrogen")
            val wikipedia = jsonObject?.optString("wikilink", "")
            if (wikipedia?.isNotEmpty() == true) {
                super.wikiListener(wikipedia, findViewById(android.R.id.content))
            }
        }
        findViewById<MaterialButton>(R.id.isotope_btn).setOnClickListener {
            openIsotopePanel()
        }
        //Check if PRO version and if make changes:
        val proPref = ProVersion(this)
        var proPrefValue = proPref.getValue()
        if (proPrefValue==100) {
            proChanges()
        }
        else {
            findViewById<LinearLayout>(R.id.more_properties).visibility = View.VISIBLE //Changed as implementing new PRO dialog
            findViewById<LinearLayout>(R.id.hardness_properties).visibility = View.VISIBLE //Changed as implementing new PRO dialog
        }
        //Check ProPlus
        val proPlusPref = ProPlusVersion(this)
        var proPlusPrefValue = proPlusPref.getValue()
        if (proPlusPrefValue==100) {
            findViewById<MaterialButton>(R.id.compare_btn).setOnClickListener {
                toggleCompareMode()
            }
        }
        else {
            findViewById<MaterialButton>(R.id.compare_btn).setOnClickListener {
                goToProPage()
            }
        }


        // Restore comparison mode state if needed
        if (savedInstanceState != null) {
            isCompareMode = savedInstanceState.getBoolean("isCompareMode", false)
            compareElementKey = savedInstanceState.getString("compareElementKey")
            
            if (isCompareMode && compareElementKey != null) {
                // Register callback as enabled early so it's ready before enterCompareMode
                // which might depend on it via setBackInterceptionEnabled
                findViewById<ScrollView>(R.id.scr_view).post {
                    enterCompareMode(compareElementKey!!)
                }
            }
        }

        // Register lifecycle-aware OnBackPressedCallback in DISABLED state, enables when overlays are visible.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Try to close overlays first
                val consumed = handleBackPress()
                if (!consumed) {
                    // Nothing to close -> forward to system/back behaviour.
                    backCallback?.isEnabled = false
                    try {
                        if (isTaskRoot) {
                            moveTaskToBack(true)
                        } else {
                            finish()
                        }
                    } catch (e: Exception) {
                        // Fallback to default
                        super@ElementInfoActivity.onBackPressed()
                    }
                } else {
                    // Keep interception enabled only while overlays remain
                    backCallback?.isEnabled = anyOverlayOpen()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)

        // We will dynamically register the platform OnBackInvokedCallback when interception is enabled
        // via setBackInterceptionEnabled(enabled) below.
        setBackInterceptionEnabled(anyOverlayOpen())
    }

    private fun proChanges() {
        //more properties
        findViewById<FrameLayout>(R.id.pro_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.more_properties).visibility = View.VISIBLE

        //hardness properties
        findViewById<FrameLayout>(R.id.pro_hardness_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.hardness_properties).visibility = View.VISIBLE

        //hazard properties
        findViewById<FrameLayout>(R.id.pro_hazard_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.hazard_properties).visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        // Fallback for devices < API 33: handleBackPress() will close overlays; otherwise default behavior.
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    // Helper to determine whether any overlay is currently open and requires interception
    private fun anyOverlayOpen(): Boolean {
        val overlayBg = findViewById<View?>(R.id.overlay_background)
        val aiPanelRoot = findViewById<View?>(R.id.ai_panel_include)
        val isotopePanelVisible = bottomSheetBehaviorIso?.state != BottomSheetBehavior.STATE_HIDDEN
        val emissionPanelVisible = bottomSheetBehaviorEmission?.state != BottomSheetBehavior.STATE_HIDDEN
        val shellPanelVisible = bottomSheetBehaviorShell?.state != BottomSheetBehavior.STATE_HIDDEN
        val ionPanelVisible = bottomSheetBehaviorIon?.state != BottomSheetBehavior.STATE_HIDDEN

        return isCompareMode ||
                (overlayBg?.visibility == View.VISIBLE) ||
                (aiPanelRoot?.visibility == View.VISIBLE) ||
                isotopePanelVisible ||
                emissionPanelVisible ||
                shellPanelVisible ||
                ionPanelVisible
    }

    // Centralized enabling/disabling of back interception; also registers/unregisters platform callback on newer OS.
    private fun setBackInterceptionEnabled(enabled: Boolean) {
        backCallback?.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                if (onBackInvokedCb == null) {
                    onBackInvokedCb = android.window.OnBackInvokedCallback {
                        // Mirror OnBackPressedCallback behavior
                        val consumed = handleBackPress()
                        if (!anyOverlayOpen()) {
                            try {
                                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCb!!)
                            } catch (_: Exception) { }
                            onBackInvokedCb = null
                            backCallback?.isEnabled = false
                            // If nothing was consumed by handleBackPress, forward to system behavior
                            if (!consumed) {
                                try {
                                    if (isTaskRoot) moveTaskToBack(true) else finish()
                                } catch (_: Exception) { /* ignore */ }
                            }
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

    // Close overlays (shell or detail emission) if visible; return true if consumed.
    private fun handleBackPress(): Boolean {
        val overlayBg = findViewById<View>(R.id.overlay_background)
        val aiPanelRoot = findViewById<View>(R.id.ai_panel_include)

        if (aiPanel?.isOpen() == true) {
            aiPanel?.close()
            return true
        } else if (bottomSheetBehaviorIso?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehaviorIso?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        } else if (bottomSheetBehaviorEmission?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehaviorEmission?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        } else if (bottomSheetBehaviorShell?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehaviorShell?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        } else if (bottomSheetBehaviorIon?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehaviorIon?.state = BottomSheetBehavior.STATE_HIDDEN
            return true
        } else if (overlayBg.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(overlayBg, 300)
            setBackInterceptionEnabled(isCompareMode || anyOverlayOpen())
            return true
        } else if (isCompareMode) {
            exitCompareMode()
            setBackInterceptionEnabled(anyOverlayOpen())
            return true
        }
        return false
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val commonTitleBack = findViewById<FrameLayout>(R.id.common_title_back)
        if (commonTitleBack != null) {
            val params2 = commonTitleBack.layoutParams as ViewGroup.LayoutParams
            params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            commonTitleBack.layoutParams = params2
        }
        val params3 = findViewById<FloatingActionButton>(R.id.ai_chat_fab).layoutParams as ViewGroup.MarginLayoutParams
        params3.bottomMargin = bottom
        findViewById<FloatingActionButton>(R.id.ai_chat_fab).layoutParams = params3


        // Handle AI Panel insets handled by its own listener now
        
        // Handle Isotope Panel insets
        findViewById<View>(R.id.frame_iso)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
        
        // Handle Emission Panel insets
        findViewById<View>(R.id.scroll_emission)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
        
        // Handle Shell Panel insets
        findViewById<View>(R.id.scroll_shell)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
        
        // Handle Ion Panel insets
        findViewById<View>(R.id.scroll_ion)?.setPadding(0, 0, 0, bottom + resources.getDimensionPixelSize(R.dimen.default_padding))
    }

    private fun offlineCheck() {
        val offlinePreferences = offlinePreference(this)
        val offlinePrefValue = offlinePreferences.getValue()

        fun applyOffline(root: View) {
            if (offlinePrefValue == 1) {
                root.findViewById<FrameLayout>(R.id.frame).visibility = View.GONE
                // Only show offline_space if not in compare mode (where notes_frame is hidden)
                root.findViewById<Space>(R.id.offline_space).visibility = if (isCompareMode) View.GONE else View.VISIBLE
                root.findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
                root.findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
                root.findViewById<TextView>(R.id.sp_offline).text = getString(R.string.go_online_for_emission)
            } else {
                root.findViewById<FrameLayout>(R.id.frame).visibility = View.VISIBLE
                root.findViewById<Space>(R.id.offline_space).visibility = View.GONE
                root.findViewById<ImageView>(R.id.sp_img).visibility = View.VISIBLE
                root.findViewById<TextView>(R.id.sp_offline).visibility = View.GONE
            }
        }

        applyOffline(findViewById(android.R.id.content))
        if (isCompareMode) {
            applyOffline(findViewById(R.id.compare_element_content))
        }
    }

    override fun onResume() {
        super.onResume()
        favoriteBarSetup()
    }

    override fun updateElementUI(jsonObject: JSONObject, englishName: String, rootView: View, elementKey: String) {
        super.updateElementUI(jsonObject, englishName, rootView, elementKey)
        
        val localizedName = jsonObject.optString("element", englishName).ifBlank { englishName }
        if (rootView == findViewById<View>(android.R.id.content)) {
            mainElementName = englishName
            mainElementDisplayName = localizedName
        } else if (rootView.id == R.id.compare_element_content) {
            compareElementName = englishName
            compareElementDisplayName = localizedName
        }

        if (isCompareMode && mainElementDisplayName != null && compareElementDisplayName != null) {
            findViewById<TextView>(R.id.element_title).text = getString(R.string.comparison_title_format, mainElementDisplayName, compareElementDisplayName)
        }

        // Hide specific property icons in comparison mode
        val propertyIcons = listOf(
            R.id.phase_icon, R.id.wikipedia_description, R.id.open_btn, R.id.open_btn2,
            R.id.isotopes_icon, R.id.ionization_button, R.id.dsc_btn
        )
        propertyIcons.forEach { iconId ->
            rootView.findViewById<View>(iconId)?.visibility = if (isCompareMode) View.GONE else View.VISIBLE
        }

        // Hide header sections on the comparison side to reduce clutter
        if (isCompareMode && rootView.id == R.id.compare_element_content) {
            val headerContainers = listOf(
                R.id.overview_inc, R.id.properties_inc, R.id.temperatures_inc,
                R.id.atomic_inc, R.id.electromagnetic_inc, R.id.addition_physics,
                R.id.nuclear_inc, R.id.hardness_inc, R.id.more_inc,
                R.id.abundance_inc, R.id.grid_inc, R.id.hazards_inc, R.id.other_inc
            )
            headerContainers.forEach { containerId ->
                rootView.findViewById<View>(containerId)?.findViewById<View>(R.id.header_container)?.visibility = View.INVISIBLE
            }
        } else {
            // Ensure headers are visible on the main side or when not in compare mode
            val headerContainers = listOf(
                R.id.overview_inc, R.id.properties_inc, R.id.temperatures_inc,
                R.id.atomic_inc, R.id.electromagnetic_inc, R.id.addition_physics,
                R.id.nuclear_inc, R.id.hardness_inc, R.id.more_inc,
                R.id.abundance_inc, R.id.grid_inc, R.id.hazards_inc, R.id.other_inc
            )
            headerContainers.forEach { containerId ->
                rootView.findViewById<View>(containerId)?.findViewById<View>(R.id.header_container)?.visibility = View.VISIBLE
            }
        }

        // Hook up interactive overlays for the specific rootView (main or compare side)
        rootView.findViewById<View>(R.id.electron_view)?.setOnClickListener {
            updateShellWithData(jsonObject)
            openShellPanel()
        }

        rootView.findViewById<ImageView>(R.id.sp_img)?.setOnClickListener {
            updateEmissionWithData(jsonObject)
            openEmissionPanel()
        }

        rootView.findViewById<TextView>(R.id.ion_charge_view_all_text)?.setOnClickListener {
            openIonizationPanel(elementKey)
        }
        rootView.findViewById<View>(R.id.ionization_button)?.setOnClickListener {
            openIonizationPanel(elementKey)
        }
    }

    private fun updateShellWithData(jsonObject: JSONObject) {
        val shellPanel = findViewById<View>(R.id.shell_panel_include)
        val elementShellElectrons = jsonObject.optString("element_shells_electrons", "---")
        val electronConfig = jsonObject.optString("element_electron_config", "---")
        val short = jsonObject.optString("short", "")
        
        shellPanel.findViewById<TextView>(R.id.config_data)?.text = elementShellElectrons
        shellPanel.findViewById<TextView>(R.id.e_config_data)?.text = formatSuperscript(electronConfig)
        
        shellPanel.findViewById<com.jlindemann.science.views.ElectronShellView>(R.id.shell_model_view)?.let {
            it.setShellData(elementShellElectrons, short)
        }
    }

    private fun updateEmissionWithData(jsonObject: JSONObject) {
        val isotopePanel = findViewById<View>(R.id.emission_panel_include)
        val short = jsonObject.optString("short", "---")
        val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
        val ext = ".gif"
        val fURL = hUrl + short + ext
        
        isotopePanel.findViewById<ImageView>(R.id.sp_img_detail)?.let {
            Picasso.get().load(fURL).into(it)
        }
    }

    private fun elementAnim(view: View, view2: View) {
        view.alpha = 0.0f
        view.animate().setDuration(150)
        view.animate().alpha(1.0f)
        val delay = Handler()
        delay.postDelayed({
            view2.alpha = 0.0f
            view2.animate().setDuration(150)
            view2.animate().alpha(1.0f)
        }, 150)
    }

    private fun nextPrev() {
        findViewById<MaterialButton>(R.id.next_btn).setOnClickListener {
            try {
                val ElementSendAndLoadPreference = ElementSendAndLoad(this)
                val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
                val jsonObject = ElementDataLoader.loadElementData(this, ElementSendAndLoadValue ?: "hydrogen")
                if (jsonObject != null) {
                    val currentNumb = jsonObject.optString("element_atomic_number", "---")
                    val elements = ArrayList<Element>()
                    ElementModel.getList(elements, this)
                    val item = elements[currentNumb.toInt()]
                    val elementSendAndLoad = ElementSendAndLoad(this)
                    elementSendAndLoad.setValue(item.elementKey)
                    readJson()
                }
            }
            catch (e: IOException) {}
        }
        findViewById<MaterialButton>(R.id.previous_btn).setOnClickListener {
            try {
                val ElementSendAndLoadPreference = ElementSendAndLoad(this)
                val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
                val jsonObject = ElementDataLoader.loadElementData(this, ElementSendAndLoadValue ?: "hydrogen")
                if (jsonObject != null) {
                    val currentNumb = jsonObject.optString("element_atomic_number", "---")
                    val elements = ArrayList<Element>()
                    ElementModel.getList(elements, this)
                    val item = elements[currentNumb.toInt()-2]
                    val elementSendAndLoad = ElementSendAndLoad(this)
                    elementSendAndLoad.setValue(item.elementKey)
                    readJson()
                }
            }
            catch (e: IOException) {}
        }
    }

    private fun toggleCompareMode() {
        if (isCompareMode) {
            exitCompareMode()
        } else {
            showElementSelector()
        }
    }

    private fun showElementSelector() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_element_selector, null)
        bottomSheetDialog.setContentView(view)

        // Make the bottom sheet background transparent so our custom background is visible
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.background = null
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        // Handle navigation bar insets to ensure the floating sheet stays above the navigation bar area
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, 0, 0, insets.bottom)
            windowInsets
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.elements_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val elements = ArrayList<Element>()
        ElementModel.getList(elements, this)

        val adapter = ElementAdapter(elements, object : ElementAdapter.OnElementClickListener2 {
            override fun elementClickListener2(item: Element, position: Int) {
                bottomSheetDialog.dismiss()
                enterCompareMode(item.elementKey)
            }
        }, this)

        recyclerView.adapter = adapter
        bottomSheetDialog.show()
    }

    private fun enterCompareMode(elementKey: String) {
        val root = findViewById<ViewGroup>(R.id.view)
        TransitionManager.beginDelayedTransition(root)

        isCompareMode = true
        compareElementKey = elementKey
        mainElementName = null
        compareElementName = null
        mainElementDisplayName = null
        compareElementDisplayName = null

        // Show comparison layout
        val compareRoot = findViewById<View>(R.id.compare_element_content)
        val divider = findViewById<View>(R.id.divider)
        
        compareRoot.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE

        // Hide navigation buttons for main view
        findViewById<MaterialButton>(R.id.previous_btn).visibility = View.GONE
        findViewById<MaterialButton>(R.id.next_btn).visibility = View.GONE
        
        // Hide navigation buttons on the comparison side as well
        compareRoot.findViewById<MaterialButton>(R.id.previous_btn).visibility = View.GONE
        compareRoot.findViewById<MaterialButton>(R.id.next_btn).visibility = View.GONE

        // Hide isotope, wikipedia and compare buttons in title bar
        findViewById<MaterialButton>(R.id.wikipedia_btn).visibility = View.GONE
        findViewById<MaterialButton>(R.id.isotope_btn).visibility = View.GONE
        findViewById<MaterialButton>(R.id.compare_btn).visibility = View.GONE

        // Show close comparison button
        findViewById<View>(R.id.close_compare_btn).visibility = View.VISIBLE

        // The title is constrained to button_container, which collapses on its own once the
        // buttons above are hidden, so no manual margin adjustment is needed.

        // Hide notes section in comparison mode as it's not needed and takes space
        findViewById<FrameLayout>(R.id.notes_frame).visibility = View.GONE
        compareRoot.findViewById<FrameLayout>(R.id.notes_frame).visibility = View.GONE
        
        // Update offline UI state for comparison mode
        offlineCheck()

        // Hide "submit data issue" button in comparison mode
        findViewById<MaterialButton>(R.id.i_btn).visibility = View.GONE
        compareRoot.findViewById<MaterialButton>(R.id.i_btn).visibility = View.GONE

        // Hide bottom space to save vertical space in split screen
        findViewById<View>(R.id.bottom_spacer)?.visibility = View.GONE
        compareRoot.findViewById<View>(R.id.bottom_spacer)?.visibility = View.GONE

        // Hide favorite bar in comparison mode as it takes too much vertical space
        findViewById<View>(R.id.favorite_bar).visibility = View.GONE
        compareRoot.findViewById<View>(R.id.favorite_bar).visibility = View.GONE

        // Enable back interception for compare mode
        setBackInterceptionEnabled(true)
        
        // Reload main view to apply UI changes
        readJson()
        // Load comparison element data using the new readJson signature
        readJson(compareRoot, elementKey)

        // Adjust orientation for side-by-side sections in compare mode
        updateSectionOrientations(true)
    }

    private fun updateSectionOrientations(isCompare: Boolean) {
        val orientation = if (isCompare) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        
        // Find containers in both main and compare views
        val mainContent = findViewById<View>(R.id.main_element_content)
        val compareContent = findViewById<View>(R.id.compare_element_content)

        listOf(mainContent, compareContent).forEach { root ->
            root?.findViewById<LinearLayout>(R.id.grid_content_wrapper)?.orientation = orientation
            root?.findViewById<LinearLayout>(R.id.hazard_content_wrapper)?.orientation = orientation

            // For visualization containers, adjust top margin and height when stacked vertically
            val gridViz = root?.findViewById<View>(R.id.grid_visualization_container)
            val hazardViz = root?.findViewById<View>(R.id.hazard_visualization_container)

            val margin = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt() else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            val innerMargin = if (isCompare) 0 else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52f, resources.displayMetrics).toInt()
            val fixedHeight = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics).toInt() else LinearLayout.LayoutParams.MATCH_PARENT
            
            listOf(gridViz, hazardViz).forEach { viz ->
                (viz?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.topMargin = margin
                    params.height = fixedHeight
                    if (isCompare) {
                        params.width = LinearLayout.LayoutParams.MATCH_PARENT
                        params.weight = 0f
                        params.marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                        params.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                    } else {
                        params.width = 0
                        params.weight = 0.28f
                        params.marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                        params.marginStart = 0
                    }
                    viz.layoutParams = params
                }
            }

            // Adjust inner containers (crystal/hazard card parents) to remove the alignment margin in compare mode
            root?.findViewById<View>(R.id.crystal_container)?.let { v ->
                (v.layoutParams as? LinearLayout.LayoutParams)?.let { p ->
                    p.topMargin = innerMargin
                    v.layoutParams = p
                }
            }
            root?.findViewById<View>(R.id.hazard_visualization_container)?.let { v ->
                (v.layoutParams as? LinearLayout.LayoutParams)?.let { p ->
                    p.topMargin = innerMargin
                    v.layoutParams = p
                }
            }

            // Also adjust data containers weights
            val gridData = root?.findViewById<View>(R.id.grid_data_container)
            val hazardData = root?.findViewById<View>(R.id.hazard_data_container)

            listOf(gridData, hazardData).forEach { data ->
                (data?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    if (isCompare) {
                        params.width = LinearLayout.LayoutParams.MATCH_PARENT
                        params.weight = 0f
                    } else {
                        params.width = 0
                        params.weight = 0.72f
                    }
                    data.layoutParams = params
                }
            }

            // Adjust electron view height and layout in compare mode
            root?.findViewById<View>(R.id.electron_view)?.let { ev ->
                (ev.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.height = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics).toInt() 
                                   else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 140f, resources.displayMetrics).toInt()
                    ev.layoutParams = params
                }
                
                // Adjust text container margin to avoid overlap with image
                ev.findViewById<View>(R.id.electron_text_container)?.let { tc ->
                    (tc.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.marginEnd = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics).toInt()
                                          else 0
                        tc.layoutParams = params
                    }
                }

                // Adjust model image size
                ev.findViewById<View>(R.id.shell_model_view)?.let { mv ->
                    (mv.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.width = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics).toInt()
                                       else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 140f, resources.displayMetrics).toInt()
                        mv.layoutParams = params
                    }
                }
            }
        }

        if (isCompare) {
            syncAllRowHeights()
        }
    }

    private fun syncAllRowHeights() {
        val mainContent = findViewById<View>(R.id.main_element_content) ?: return
        val compareContent = findViewById<View>(R.id.compare_element_content) ?: return

        val containerIds = listOf(
            // Overview
            R.id.description_container, R.id.english_name_container, R.id.year_discovered_container,
            R.id.discovered_by_container, R.id.group_container, R.id.appearance_container,
            R.id.electrons_container, R.id.isotope_container,
            // Properties
            R.id.atomic_number_container, R.id.atomic_weight_container, R.id.density_container,
            R.id.electronegativity_container, R.id.electronegativity_allen_container,
            R.id.block_container, R.id.emission_spectrum_container,
            // Atomic
            R.id.oxidation_states_container, R.id.electron_configuration_container,
            R.id.ion_charge_container, R.id.ionization_energies_container,
            R.id.atomic_radius_e_container, R.id.atomic_radius_container,
            R.id.covalent_radius_container, R.id.van_der_waals_radius_container,
            // Thermodynamic
            R.id.phase_container, R.id.fusion_heat_container, R.id.specific_heat_container,
            R.id.vaporization_heat_container, R.id.thermal_conductivity_container,
            R.id.thermal_expansion_container, R.id.molar_heat_capacity_container,
            R.id.molar_volume_container, R.id.electron_affinity_container,
            // Temperatures
            R.id.boiling_kelvin_container, R.id.boiling_celsius_container, R.id.boiling_fahrenheit_container,
            R.id.melting_kelvin_container, R.id.melting_celsius_container, R.id.melting_fahrenheit_container,
            // Nuclear
            R.id.radioactive_container, R.id.isotopes_container, R.id.neutron_cross_section_container,
            // Hardness
            R.id.mohs_hardness_container, R.id.vickers_hardness_container, R.id.brinell_hardness_container,
            // Abundance
            R.id.abundance_earth_crust_container, R.id.abundance_earth_soil_container,
            R.id.abundance_urban_soil_container, R.id.abundance_crustal_rocks_container,
            R.id.abundance_sea_water_container, R.id.abundance_sun_container,
            R.id.abundance_solar_system_container, R.id.abundance_meteorites_container,
            R.id.abundance_human_body_container,
            // Electromagnetic
            R.id.electrical_type_container, R.id.resistivity_container,
            R.id.work_function_container, R.id.magnetic_type_container,
            R.id.curie_point_container, R.id.neel_point_container,
            R.id.superconducting_point_container,
            // Hazards
            R.id.fire_hazard_container, R.id.health_hazard_container,
            R.id.reactivity_hazard_container, R.id.specific_hazard_container,
            // Grid
            R.id.crystal_structure_container, R.id.grid_parameters_container,
            R.id.debye_low_container, R.id.debye_room_container,
            R.id.space_group_name_container, R.id.space_group_number_container,
            R.id.refractive_index_container,
            // More
            R.id.speed_sound_container, R.id.poisson_ratio_container,
            R.id.young_modulus_container, R.id.bulk_modulus_container,
            R.id.shear_modulus_container
        )

        containerIds.forEach { id ->
            val mainRow = mainContent.findViewById<View>(id)
            val compareRow = compareContent.findViewById<View>(id)

            if (mainRow != null && compareRow != null) {
                // Reset heights to wrap_content to measure natural height
                mainRow.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                compareRow.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

                mainRow.post {
                    val maxHeight = Math.max(mainRow.height, compareRow.height)
                    if (maxHeight > 0) {
                        mainRow.layoutParams.height = maxHeight
                        compareRow.layoutParams.height = maxHeight
                        mainRow.requestLayout()
                        compareRow.requestLayout()
                    }
                }
            }
        }
    }


    private fun exitCompareMode() {
        val root = findViewById<ViewGroup>(R.id.view)
        TransitionManager.beginDelayedTransition(root)

        isCompareMode = false
        compareElementKey = null
        mainElementName = null
        compareElementName = null
        mainElementDisplayName = null
        compareElementDisplayName = null

        // Restore title (the reload below replaces this with the element's localized name)
        findViewById<TextView>(R.id.element_title).text = getString(R.string.element_info_title)

        // Hide comparison layout
        val compareRoot = findViewById<View>(R.id.compare_element_content)
        val divider = findViewById<View>(R.id.divider)
        
        compareRoot.visibility = View.GONE
        divider.visibility = View.GONE

        // Restore title bar buttons
        findViewById<MaterialButton>(R.id.wikipedia_btn).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.isotope_btn).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.compare_btn).visibility = View.VISIBLE

        // Show navigation buttons
        findViewById<MaterialButton>(R.id.previous_btn).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.next_btn).visibility = View.VISIBLE

        // Show notes and other hidden elements
        findViewById<FrameLayout>(R.id.notes_frame).visibility = View.VISIBLE
        offlineCheck()
        findViewById<MaterialButton>(R.id.i_btn).visibility = View.VISIBLE
        findViewById<View>(R.id.favorite_bar).visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_spacer)?.visibility = View.VISIBLE

        // Disable back interception
        setBackInterceptionEnabled(false)

        // Reload to restore original UI state
        readJson()

        // Restore orientation for side-by-side sections
        updateSectionOrientations(false)

        // Hide close comparison button
        findViewById<View>(R.id.close_compare_btn).visibility = View.GONE
    }






private fun setupStaticDetailListeners() {
    val shellBg = findViewById<View>(R.id.overlay_background)

    shellBg?.setOnClickListener {
        Utils.fadeOutAnim(shellBg, 300)
        setBackInterceptionEnabled(anyOverlayOpen())
    }
}








    private fun updateAIUserProfileImage() {
        try {
            val aiPanelRoot = findViewById<View>(R.id.ai_panel_include) ?: return
            val aiUserBtn = aiPanelRoot.findViewById<ImageButton>(R.id.ai_user_btn) ?: return
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            
            if (currentUser != null && currentUser.photoUrl != null) {
                com.bumptech.glide.Glide.with(this)
                    .load(currentUser.photoUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.ic_account)
                    .error(R.drawable.ic_account)
                    .into(aiUserBtn)
                aiUserBtn.imageTintList = null
            } else {
                aiUserBtn.setImageResource(R.drawable.ic_account)
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                aiUserBtn.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }




    private fun setupIonizationPanel() {
        val ionPanelRoot = findViewById<View>(R.id.ion_panel_include) ?: return
        val background = findViewById<TextView>(R.id.background_iso) ?: return
        
        bottomSheetBehaviorIon = BottomSheetBehavior.from(ionPanelRoot)
        bottomSheetBehaviorIon?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehaviorIon?.skipCollapsed = true
        
        bottomSheetBehaviorIon?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (bottomSheetBehaviorIso?.state == BottomSheetBehavior.STATE_HIDDEN && 
                        bottomSheetBehaviorEmission?.state == BottomSheetBehavior.STATE_HIDDEN &&
                        bottomSheetBehaviorShell?.state == BottomSheetBehavior.STATE_HIDDEN) {
                        background.visibility = View.GONE
                        background.alpha = 0f
                    }
                } else {
                    background.visibility = View.VISIBLE
                }
                setBackInterceptionEnabled(anyOverlayOpen())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (bottomSheetBehaviorIso?.state != BottomSheetBehavior.STATE_EXPANDED &&
                    bottomSheetBehaviorEmission?.state != BottomSheetBehavior.STATE_EXPANDED &&
                    bottomSheetBehaviorShell?.state != BottomSheetBehavior.STATE_EXPANDED) {
                    background.visibility = View.VISIBLE
                    background.alpha = slideOffset.coerceAtLeast(0f) * 0.6f
                }
            }
        })
        
        ionPanelRoot.findViewById<View>(R.id.drag_frame_ion)?.setOnClickListener {
            bottomSheetBehaviorIon?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun openIonizationPanel(elementKey: String) {
        drawIonizationCard(elementKey)
        bottomSheetBehaviorIon?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    private fun drawIonizationCard(elementKey: String) {
        val ionPanel = findViewById<View>(R.id.ion_panel_include)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements, this)
        val elementItem = elements.find { it.elementKey == elementKey.lowercase() }
        
        ionPanel.findViewById<TextView>(R.id.ion_detail_title).text = 
            "${elementKey.replaceFirstChar { it.uppercase() }} ionization"
            
        try {
            val jsonObject = ElementDataLoader.loadElementData(this, elementKey)
            if (jsonObject != null) {
                val maxIons = 30 // Based on the layout
                for (i in 1..maxIons) {
                    val final = "element_ionization_energy$i"
                    val ionization = jsonObject.optString(final, "")
                    val nameId = "ion_text_$i"
                    val iText = ionPanel.findViewById<TextView>(resources.getIdentifier(nameId, "id", packageName))
                    
                    if (ionization.isNotEmpty() && ionization != "---") {
                        iText.text = "$i. $ionization"
                        iText.visibility = View.VISIBLE
                    } else {
                        iText.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupShellPanel() {
        val shellPanelRoot = findViewById<View>(R.id.shell_panel_include) ?: return
        val background = findViewById<TextView>(R.id.background_iso) ?: return
        
        bottomSheetBehaviorShell = BottomSheetBehavior.from(shellPanelRoot)
        bottomSheetBehaviorShell?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehaviorShell?.skipCollapsed = true
        
        bottomSheetBehaviorShell?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (bottomSheetBehaviorIso?.state == BottomSheetBehavior.STATE_HIDDEN && 
                        bottomSheetBehaviorEmission?.state == BottomSheetBehavior.STATE_HIDDEN) {
                        background.visibility = View.GONE
                        background.alpha = 0f
                    }
                } else {
                    background.visibility = View.VISIBLE
                }
                setBackInterceptionEnabled(anyOverlayOpen())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (bottomSheetBehaviorIso?.state != BottomSheetBehavior.STATE_EXPANDED &&
                    bottomSheetBehaviorEmission?.state != BottomSheetBehavior.STATE_EXPANDED) {
                    background.visibility = View.VISIBLE
                    background.alpha = slideOffset.coerceAtLeast(0f) * 0.6f
                }
            }
        })
        
        shellPanelRoot.findViewById<View>(R.id.drag_frame_shell)?.setOnClickListener {
            bottomSheetBehaviorShell?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun openShellPanel() {
        bottomSheetBehaviorShell?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    private fun setupEmissionPanel() {
        val emissionPanelRoot = findViewById<View>(R.id.emission_panel_include) ?: return
        val background = findViewById<TextView>(R.id.background_iso) ?: return
        
        bottomSheetBehaviorEmission = BottomSheetBehavior.from(emissionPanelRoot)
        bottomSheetBehaviorEmission?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehaviorEmission?.skipCollapsed = true
        
        bottomSheetBehaviorEmission?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    // Only hide background if Isotope panel is ALSO hidden
                    if (bottomSheetBehaviorIso?.state == BottomSheetBehavior.STATE_HIDDEN) {
                        background.visibility = View.GONE
                        background.alpha = 0f
                    }
                } else {
                    background.visibility = View.VISIBLE
                }
                setBackInterceptionEnabled(anyOverlayOpen())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // If this sheet is sliding, update background alpha unless the OTHER sheet is already expanded
                if (bottomSheetBehaviorIso?.state != BottomSheetBehavior.STATE_EXPANDED) {
                    background.visibility = View.VISIBLE
                    background.alpha = slideOffset.coerceAtLeast(0f) * 0.6f
                }
            }
        })
        
        emissionPanelRoot.findViewById<View>(R.id.drag_frame_emission)?.setOnClickListener {
            bottomSheetBehaviorEmission?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun openEmissionPanel() {
        bottomSheetBehaviorEmission?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    private fun setupIsotopePanel() {
        val isotopePanelRoot = findViewById<View>(R.id.isotope_panel_include) ?: return
        val background = findViewById<TextView>(R.id.background_iso) ?: return
        
        // Ensure background is invisible initially
        background.visibility = View.GONE
        background.alpha = 0f
        
        bottomSheetBehaviorIso = BottomSheetBehavior.from(isotopePanelRoot)
        bottomSheetBehaviorIso?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehaviorIso?.skipCollapsed = true
        
        bottomSheetBehaviorIso?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    background.visibility = View.GONE
                    background.alpha = 0f
                } else {
                    background.visibility = View.VISIBLE
                }
                setBackInterceptionEnabled(anyOverlayOpen())
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                background.visibility = View.VISIBLE
                background.alpha = slideOffset.coerceAtLeast(0f) * 0.6f
            }
        })
        
        background.setOnClickListener {
            bottomSheetBehaviorIso?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun openIsotopePanel() {
        drawIsotopeCard()
        bottomSheetBehaviorIso?.state = BottomSheetBehavior.STATE_EXPANDED
        setBackInterceptionEnabled(true)
    }

    private fun drawIsotopeCard() {
        val elementKey = ElementSendAndLoad(this).getValue() ?: "hydrogen"
        val elements = ArrayList<Element>()
        ElementModel.getList(elements, this)
        val item = elements.find { it.elementKey == elementKey.lowercase() } ?: return

        try {
            val jsonObject = ElementDataLoader.loadElementData(this, elementKey)
            if (jsonObject != null) {
                val aLayout = findViewById<LinearLayout>(R.id.frame_iso) ?: return
                aLayout.removeAllViews()

                val inflater = layoutInflater
                val fLayout: View = inflater.inflate(R.layout.row_iso_panel_title_item, aLayout, false)

                val iTitle = fLayout.findViewById(R.id.iso_title) as TextView
                val iExt = " Isotopes"
                iTitle.text = "${elementKey.replaceFirstChar { it.uppercase() }}$iExt"

                aLayout.addView(fLayout)

                for (i in 1..item.isotopes) {
                    val myLayout: View = inflater.inflate(R.layout.row_iso_panel_item, aLayout, false)
                    val name = "iso_"
                    val z = "iso_Z_"
                    val n = "iso_N_"
                    val a = "iso_A_"
                    val half = "iso_half_"
                    val mass = "iso_mass_"
                    val halfText = "Half-Time: "
                    val massText = "Mass: "

                    val isoName = jsonObject.optString("$name$i", "")
                    if (isoName.isEmpty() || isoName == "---") continue

                    val isoZ = jsonObject.optString("$z$i", "---")
                    val isoN = jsonObject.optString("$n$i", "---")
                    val isoA = jsonObject.optString("$a$i", "---")
                    val isoHalf = jsonObject.optString("$half$i", "---")
                    val isoMass = jsonObject.optString("$mass$i", "---")

                    val iName = myLayout.findViewById(R.id.i_name) as TextView
                    val iZ = myLayout.findViewById(R.id.i_z) as TextView
                    val iN = myLayout.findViewById(R.id.i_n) as TextView
                    val iA = myLayout.findViewById(R.id.i_a) as TextView
                    val iHalf = myLayout.findViewById(R.id.i_half) as TextView
                    val iMass = myLayout.findViewById(R.id.i_mass) as TextView

                    iName.text = isoName
                    iZ.text = isoZ
                    iN.text = isoN
                    iA.text = isoA
                    iHalf.text = "$halfText$isoHalf"
                    iMass.text = "$massText$isoMass"

                    aLayout.addView(myLayout)
                }
            }
        } catch (e: Exception) {
            ToastUtil.showToast(this, "Couldn't load Data")
        }
    }









override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("isCompareMode", isCompareMode)
    outState.putString("compareElementKey", compareElementKey)
}

override fun onDestroy() {
    super.onDestroy()
    aiPanel?.onDestroy()
    aiScope.cancel()
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