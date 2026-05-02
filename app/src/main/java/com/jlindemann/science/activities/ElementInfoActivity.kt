package com.jlindemann.science.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
import androidx.appcompat.widget.AppCompatButton
import androidx.browser.customtabs.CustomTabsIntent
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mmin18.widget.RealtimeBlurView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.activities.tables.NuclideActivity
import com.jlindemann.science.adapter.AchievementAdapter
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.extensions.InfoExtension
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ElementDataLoader
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Text
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
        val ElementSendAndLoadPreference = ElementSendAndLoad(this)
        var ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
        setContentView(R.layout.activity_element_info)
        Utils.fadeInAnim(findViewById<ScrollView>(R.id.scr_view), 300)

        // readJson() will call updateElementUI which now handles overlay listeners
        readJson()
        findViewById<CardView>(R.id.shell).visibility = View.GONE
        findViewById<CardView>(R.id.detail_emission).visibility = View.GONE
        setupStaticDetailListeners()
        offlineCheck()
        nextPrev()
        favoriteBarSetup()
        elementAnim(findViewById<FrameLayout>(R.id.overview_inc), findViewById<FrameLayout>(R.id.properties_inc))
        findViewById<ConstraintLayout>(R.id.view).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener { super.onBackPressed() }

        findViewById<View>(R.id.close_compare_btn).setOnClickListener {
            exitCompareMode()
        }
        findViewById<FloatingActionButton>(R.id.edit_fav_btn).setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        findViewById<AppCompatButton>(R.id.i_btn).setOnClickListener {
            val intent = Intent(this, SubmitActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_hardness_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_hazard_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.notes_sync_status).setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            startActivity(intent)
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
            findViewById<ImageButton>(R.id.compare_btn).setOnClickListener {
                toggleCompareMode()
            }
        }
        else {
            findViewById<ImageButton>(R.id.compare_btn).setOnClickListener {
                //val intent = Intent(this, ProActivity::class.java)
                //startActivity(intent)
                toggleCompareMode()
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
        val shell = findViewById<CardView?>(R.id.shell)
        val shellBg = findViewById<RealtimeBlurView?>(R.id.shell_background)
        val detail = findViewById<CardView?>(R.id.detail_emission)
        val detailBg = findViewById<RealtimeBlurView?>(R.id.detail_emission_background)

        return isCompareMode ||
                (shell?.visibility == View.VISIBLE) ||
                (shellBg?.visibility == View.VISIBLE) ||
                (detail?.visibility == View.VISIBLE) ||
                (detailBg?.visibility == View.VISIBLE)
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
        val shell = findViewById<CardView>(R.id.shell)
        val shellBg = findViewById<RealtimeBlurView>(R.id.shell_background)
        val detail = findViewById<CardView>(R.id.detail_emission)
        val detailBg = findViewById<RealtimeBlurView>(R.id.detail_emission_background)

        if (shellBg.visibility == View.VISIBLE || shell.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(shell, 300)
            Utils.fadeOutAnim(shellBg, 300)
            setBackInterceptionEnabled(isCompareMode || anyOverlayOpen())
            return true
        } else if (detail.visibility == View.VISIBLE || detailBg.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(detail, 300)
            Utils.fadeOutAnim(detailBg, 300)
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
        val params2 = findViewById<FrameLayout>(R.id.common_title_back).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back).layoutParams = params2
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
        
        if (rootView == findViewById<View>(android.R.id.content)) {
            mainElementName = englishName
        } else if (rootView.id == R.id.compare_element_content) {
            compareElementName = englishName
        }

        if (isCompareMode && mainElementName != null && compareElementName != null) {
            findViewById<TextView>(R.id.element_title).text = getString(R.string.comparison_title_format, mainElementName, compareElementName)
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
        rootView.findViewById<CardView>(R.id.electron_view)?.setOnClickListener {
            updateShellWithData(jsonObject)
            Utils.fadeInAnim(findViewById<CardView>(R.id.shell), 300)
            Utils.fadeInAnim(findViewById<RealtimeBlurView>(R.id.shell_background), 300)
            setBackInterceptionEnabled(true)
        }

        rootView.findViewById<ImageView>(R.id.sp_img)?.setOnClickListener {
            updateEmissionWithData(jsonObject)
            Utils.fadeInAnim(findViewById<CardView>(R.id.detail_emission), 300)
            Utils.fadeInAnim(findViewById<RealtimeBlurView>(R.id.detail_emission_background), 300)
            setBackInterceptionEnabled(true)
        }
    }

    private fun updateShellWithData(jsonObject: JSONObject) {
        val shell = findViewById<CardView>(R.id.shell)
        val elementShellElectrons = jsonObject.optString("element_shells_electrons", "---")
        val electronConfig = jsonObject.optString("element_electron_config", "---")
        
        shell.findViewById<TextView>(R.id.config_data)?.text = elementShellElectrons
        shell.findViewById<TextView>(R.id.e_config_data)?.text = formatSuperscript(electronConfig)
    }

    private fun updateEmissionWithData(jsonObject: JSONObject) {
        val detail = findViewById<CardView>(R.id.detail_emission)
        val short = jsonObject.optString("short", "---")
        val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
        val ext = ".gif"
        val fURL = hUrl + short + ext
        
        detail.findViewById<ImageView>(R.id.sp_img_detail)?.let {
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
        findViewById<ImageButton>(R.id.next_btn).setOnClickListener {
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
        findViewById<ImageButton>(R.id.previous_btn).setOnClickListener {
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

        // Show comparison layout
        val compareRoot = findViewById<View>(R.id.compare_element_content)
        val divider = findViewById<View>(R.id.divider)
        
        compareRoot.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE

        // Hide navigation buttons for main view
        findViewById<ImageButton>(R.id.previous_btn).visibility = View.GONE
        findViewById<ImageButton>(R.id.next_btn).visibility = View.GONE
        
        // Hide navigation buttons on the comparison side as well
        compareRoot.findViewById<ImageButton>(R.id.previous_btn).visibility = View.GONE
        compareRoot.findViewById<ImageButton>(R.id.next_btn).visibility = View.GONE

        // Hide isotope, wikipedia and compare buttons in title bar
        findViewById<ImageButton>(R.id.wikipedia_btn).visibility = View.GONE
        findViewById<ImageButton>(R.id.isotope_btn).visibility = View.GONE
        findViewById<ImageButton>(R.id.compare_btn).visibility = View.GONE

        // Show close comparison button
        findViewById<View>(R.id.close_compare_btn).visibility = View.VISIBLE

        // Adjust title margins to use full width when buttons are hidden
        val titleText = findViewById<TextView>(R.id.element_title)
        val titleParams = titleText.layoutParams as FrameLayout.LayoutParams
        titleParams.marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        titleText.layoutParams = titleParams

        // Hide notes section in comparison mode as it's not needed and takes space
        findViewById<FrameLayout>(R.id.notes_frame).visibility = View.GONE
        compareRoot.findViewById<FrameLayout>(R.id.notes_frame).visibility = View.GONE
        
        // Update offline UI state for comparison mode
        offlineCheck()

        // Hide "submit data issue" button in comparison mode
        findViewById<AppCompatButton>(R.id.i_btn).visibility = View.GONE
        compareRoot.findViewById<AppCompatButton>(R.id.i_btn).visibility = View.GONE

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
            root?.findViewById<View>(R.id.hazard_container)?.let { v ->
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
                ev.findViewById<View>(R.id.model_view)?.let { mv ->
                    (mv.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.width = if (isCompare) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics).toInt()
                                       else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 140f, resources.displayMetrics).toInt()
                        mv.layoutParams = params
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

        // Restore title
        findViewById<TextView>(R.id.element_title).text = getString(R.string.element_info_title)

        // Hide comparison layout
        val compareRoot = findViewById<View>(R.id.compare_element_content)
        val divider = findViewById<View>(R.id.divider)
        
        compareRoot.visibility = View.GONE
        divider.visibility = View.GONE

        // Restore title bar buttons
        findViewById<ImageButton>(R.id.wikipedia_btn).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.isotope_btn).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.compare_btn).visibility = View.VISIBLE

        // Reset title margins
        val titleText = findViewById<TextView>(R.id.element_title)
        val titleParams = titleText.layoutParams as FrameLayout.LayoutParams
        titleParams.marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 168f, resources.displayMetrics).toInt()
        titleText.layoutParams = titleParams

        // Show navigation buttons
        findViewById<ImageButton>(R.id.previous_btn).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.next_btn).visibility = View.VISIBLE

        // Show notes and other hidden elements
        findViewById<FrameLayout>(R.id.notes_frame).visibility = View.VISIBLE
        offlineCheck()
        findViewById<AppCompatButton>(R.id.i_btn).visibility = View.VISIBLE
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
    val shell = findViewById<CardView>(R.id.shell)
    val shellBg = findViewById<RealtimeBlurView>(R.id.shell_background)
    val detail = findViewById<CardView>(R.id.detail_emission)
    val detailBg = findViewById<RealtimeBlurView>(R.id.detail_emission_background)

    shellBg.setOnClickListener {
        Utils.fadeOutAnim(shell, 300)
        Utils.fadeOutAnim(shellBg, 300)
        setBackInterceptionEnabled(anyOverlayOpen())
    }
    detailBg.setOnClickListener {
        Utils.fadeOutAnim(detail, 300)
        Utils.fadeOutAnim(detailBg, 300)
        setBackInterceptionEnabled(anyOverlayOpen())
    }
}


override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("isCompareMode", isCompareMode)
    outState.putString("compareElementKey", compareElementKey)
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