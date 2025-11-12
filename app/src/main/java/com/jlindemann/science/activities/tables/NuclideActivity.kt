package com.jlindemann.science.activities.tables

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.text.isDigitsOnly
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ThemePreference
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.activities.IsotopesActivityExperimental
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.sendIso
import com.otaliastudios.zoom.ZoomLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class NuclideActivity : BaseActivity() {
    private val elementLists = ArrayList<Element>()
    var mScale = 1f
    lateinit var mScaleDetector: ScaleGestureDetector
    lateinit var gestureDetector: GestureDetector

    // Unified back handling fields
    private var backCallback: OnBackPressedCallback? = null
    private var onBackInvokedCb: android.window.OnBackInvokedCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        setAppropriateTheme(themePrefValue)
        setContentView(R.layout.activity_nuclide)

        findViewById<ViewStub>(R.id.viewStub).inflate()

        // Setup centralized back handling (disabled by default)
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val consumed = handleBackPress()
                if (!consumed) {
                    // Not consumed by overlays -> fallback to default behaviour
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

        // Register platform callback on Android 14+ but start disabled
        setBackInterceptionEnabled(false)

        // Run initial UI setup
        runOnUiThread {
            findViewById<FrameLayout>(R.id.ldn_place).visibility = View.VISIBLE
            findViewById<ZoomLayout>(R.id.nuclideZoomView).visibility = View.GONE
            findViewById<FloatingActionButton>(R.id.nuc_info_fab).setOnClickListener {
                showInfoPanel()
            }
        }

        // Load JSON in the background and update UI in lifecycleScope
        lifecycleScope.launch {
            loadAndDisplayElements()
        }

        //Add value to most used:
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "nuc"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }

        setupGestureDetectors()
        setupSeekBar()
        setupBackButton()
        findViewById<FrameLayout>(R.id.view_nuc).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun setAppropriateTheme(themePrefValue: Int) {
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        } else {
            setTheme(if (themePrefValue == 0) R.style.AppTheme else R.style.AppThemeDark)
        }
    }

    private fun setupGestureDetectors() {
        gestureDetector = GestureDetector(this, GestureListener())
        mScaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    handleScaleGesture(detector)
                    return true
                }
            })
    }

    private fun handleScaleGesture(detector: ScaleGestureDetector) {
        val scale = 1 - detector.scaleFactor
        val pScale = mScale
        mScale += scale
        mScale = mScale.coerceIn(1f, 1f)
        val scaleAnimation = ScaleAnimation(
            1f / pScale,
            1f / mScale,
            1f / pScale,
            1f / mScale,
            detector.focusX,
            detector.focusY
        )
        scaleAnimation.duration = 0
        scaleAnimation.fillAfter = true
        findViewById<LinearLayout>(R.id.scrollNuc).startAnimation(scaleAnimation)
    }

    private fun setupSeekBar() {
        findViewById<SeekBar>(R.id.seekBarNuc).setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, currentValue: Int, p2: Boolean) {
                val scaleAnimation = ScaleAnimation(
                    1f / currentValue,
                    1f / currentValue,
                    1f / currentValue,
                    1f / currentValue
                )
                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                findViewById<LinearLayout>(R.id.scrollNuc).startAnimation(scaleAnimation)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.nuc_back_btn).setOnClickListener { this.onBackPressed() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        super.dispatchTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return mScaleDetector.onTouchEvent(event)
    }

    @SuppressLint("SetTextI18n") //suppresses as it's only showing number and therefore no attr needed
    private suspend fun loadAndDisplayElements() {
        withContext(Dispatchers.IO) {
            ElementModel.getList(elementLists)
        }
        initializeDefaultView()

        withContext(Dispatchers.IO) {
            for (item in elementLists) {
                loadElementData(item)?.let { jsonObject ->
                    withContext(Dispatchers.Main) {
                        addIsotopesToLayout(item, jsonObject)
                    }
                    runOnUiThread {
                        findViewById<TextView>(R.id.ldn_text).text = "${item.number} / 118"
                    }
                }
                if (item == elementLists.last()) {
                    runOnUiThread {
                        findViewById<ZoomLayout>(R.id.nuclideZoomView).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.ldn_place).visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun initializeDefaultView() {
        val dLayout = findViewById<FrameLayout>(R.id.nuc_view)
        val mLayout = layoutInflater.inflate(R.layout.item_nuclide, dLayout, false)
        val param = RelativeLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.item_nuclide),
            resources.getDimensionPixelSize(R.dimen.item_nuclide)
        ).apply {
            leftMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * 1
            topMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * 1
        }

        mLayout.findViewById<TextView>(R.id.nuclide_element).text = "n"
        mLayout.findViewById<TextView>(R.id.nuclide_number).text = "1"
        dLayout.addView(mLayout, param)
    }

    private suspend fun loadElementData(item: Element): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = assets.open("${item.element}.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                jsonArray.getJSONObject(0)
            } catch (e: IOException) {
                Log.e("NuclideActivity", "Failed to load element data for ${item.element}", e)
                null
            }
        }
    }

    //add isotopes to the parent layout
    private fun addIsotopesToLayout(item: Element, jsonObject: JSONObject) {
        for (i in 1..item.isotopes) {
            val n = jsonObject.optString("iso_N_$i", "-")
            val z = jsonObject.optString("iso_Z_$i", "-")
            val decayTypeResult = jsonObject.optString("decay_type_$i", "default")
            if (n.isDigitsOnly() && z.isDigitsOnly()) {
                addIsotopeView(item, n.toInt(), z.toInt(), decayTypeResult)
            }
        }
    }

    private fun addIsotopeView(item: Element, n: Int, z: Int, decayTypeResult: String) {
        val dLayout = findViewById<FrameLayout>(R.id.nuc_view)
        val myLayout = layoutInflater.inflate(R.layout.item_nuclide, dLayout, false)
        val params = RelativeLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.item_nuclide),
            resources.getDimensionPixelSize(R.dimen.item_nuclide)
        ).apply { //margin depending on where in the coordinate system
            leftMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * z
            topMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * n
        }

        val short = myLayout.findViewById<TextView>(R.id.nuclide_element)
        val top = myLayout.findViewById<TextView>(R.id.nuclide_number)
        val frame = myLayout.findViewById<FrameLayout>(R.id.item_nuclide_frame)
        val decay = myLayout.findViewById<TextView>(R.id.nuclide_decay)

        short.text = item.short
        top.text = (z + n).toString()

        val decayColors = mapOf(
            "stable" to Pair(Color.argb(255, 42, 50, 61), R.color.colorLightPrimary),
            "3p" to Pair(Color.argb(255, 137, 0, 7), R.color.colorLightPrimary),
            "2p" to Pair(Color.argb(255, 154, 0, 7), R.color.colorLightPrimary),
            "p" to Pair(Color.argb(255, 211, 47, 47), R.color.colorLightPrimary),
            "B+" to Pair(Color.argb(255, 211, 102, 89), R.color.colorDarkPrimary),
            "2B-" to Pair(Color.argb(255, 3, 155, 229), R.color.colorDarkPrimary),
            "B-" to Pair(Color.argb(255, 89, 204, 255), R.color.colorDarkPrimary),
            "n" to Pair(Color.argb(255, 78, 186, 170), R.color.colorDarkPrimary),
            "2n" to Pair(Color.argb(255, 0, 137, 123), R.color.colorDarkPrimary),
            "α" to Pair(Color.argb(255, 255, 235, 59), R.color.colorDarkPrimary),
            "alpha" to Pair(Color.argb(255, 255, 235, 59), R.color.colorDarkPrimary),
            "e- capture" to Pair(Color.argb(255, 176, 0, 78), R.color.colorDarkPrimary),
            "e-capture" to Pair(Color.argb(255, 176, 0, 78), R.color.colorDarkPrimary),
            "EC" to Pair(Color.argb(255, 176, 0, 78), R.color.colorDarkPrimary),
            "ε" to Pair(Color.argb(255, 176, 0, 78), R.color.colorDarkPrimary),
            "SF" to Pair(Color.argb(255, 87, 201, 98), R.color.colorDarkPrimary),
        )

        val (bgColor, textColorRes) = decayColors[decayTypeResult] ?: decayColors["stable"]!!

        frame.background.setTint(bgColor)
        short.setTextColor(resources.getColor(textColorRes))
        top.setTextColor(resources.getColor(textColorRes))
        decay.setTextColor(resources.getColor(textColorRes))

        frame.setOnClickListener {
            val isoPreference = ElementSendAndLoad(this)
            isoPreference.setValue(item.element.lowercase(Locale.ROOT)) //Send element number
            val isoSend = sendIso(this)
            isoSend.setValue("true") //Set flag for sent
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent) //Send intent
        }

        decay.text = when (decayTypeResult) {
            "observationally stable" -> "stable"
            "α", "alpha" -> "α"
            "B-" -> "β-"
            "B+" -> "β+"
            "2B-" -> "2β-"
            "e- capture", "e-capture", "EC", "ε" -> "ε-capture"
            else -> decayTypeResult
        }
        dLayout.addView(myLayout, params) //inflates view after data has been parsed from json
    }


    private class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean = true
    }

    private fun showInfoPanel() {
        Anim.fadeIn(findViewById<ConstraintLayout>(R.id.nuc_info_panel), 300)

        //setting up clickListeners that closes infoPanel
        findViewById<TextView>(R.id.nuc_panel_background).setOnClickListener {
            closeInfoPanel()
            // update interception after hiding
            setBackInterceptionEnabled(anyOverlayOpen())
        }
        findViewById<FloatingActionButton>(R.id.nuc_info_close_btn).setOnClickListener {
            closeInfoPanel()
            // update interception after hiding
            setBackInterceptionEnabled(anyOverlayOpen())
        }

        // Info panel shown -> enable back interception
        setBackInterceptionEnabled(true)
    }

    private fun closeInfoPanel() {
        Anim.fadeOutAnim(findViewById<ConstraintLayout>(R.id.nuc_info_panel), 300)
    }

    // Centralized back handling: prefer overlay-first closing
    override fun onBackPressed() {
        if (!handleBackPress()) {
            super.onBackPressed()
        }
    }

    // Centralized overlay detection
    private fun anyOverlayOpen(): Boolean {
        val infoVisible = findViewById<ConstraintLayout>(R.id.nuc_info_panel).visibility == View.VISIBLE
        return infoVisible
    }

    // Close overlays if visible; return true when consumed.
    private fun handleBackPress(): Boolean {
        val infoPanel = findViewById<ConstraintLayout>(R.id.nuc_info_panel)
        if (infoPanel.visibility == View.VISIBLE) {
            closeInfoPanel()
            // update interception state after hiding
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

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<FrameLayout>(R.id.scrollViewNuc).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val params2 = findViewById<FrameLayout>(R.id.common_title_back_nuc).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_nuc).layoutParams = params2

        val params3 = findViewById<FloatingActionButton>(R.id.nuc_info_fab).layoutParams as ViewGroup.MarginLayoutParams
        params3.setMargins(0, 0, resources.getDimensionPixelSize(R.dimen.margin), bottom + resources.getDimensionPixelSize(R.dimen.margin))
        findViewById<FloatingActionButton>(R.id.nuc_info_fab).layoutParams = params3

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