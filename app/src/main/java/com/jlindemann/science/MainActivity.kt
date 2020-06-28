package com.jlindemann.science

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.util.rangeTo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.activities.*
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.SearchPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.filter_view.*
import kotlinx.android.synthetic.main.group_1.*
import kotlinx.android.synthetic.main.group_10.*
import kotlinx.android.synthetic.main.group_11.*
import kotlinx.android.synthetic.main.group_12.*
import kotlinx.android.synthetic.main.group_13.*
import kotlinx.android.synthetic.main.group_14.*
import kotlinx.android.synthetic.main.group_15.*
import kotlinx.android.synthetic.main.group_16.*
import kotlinx.android.synthetic.main.group_17.*
import kotlinx.android.synthetic.main.group_18.*
import kotlinx.android.synthetic.main.group_2.*
import kotlinx.android.synthetic.main.group_3.*
import kotlinx.android.synthetic.main.group_4.*
import kotlinx.android.synthetic.main.group_5.*
import kotlinx.android.synthetic.main.group_6.*
import kotlinx.android.synthetic.main.group_7.*
import kotlinx.android.synthetic.main.group_8.*
import kotlinx.android.synthetic.main.group_9.*
import kotlinx.android.synthetic.main.hover_menu.*
import kotlinx.android.synthetic.main.nav_menu_view.*
import kotlinx.android.synthetic.main.search_layout.*
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity(), View.OnClickListener, ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

    var mScale = 1f
    lateinit var mScaleDetector: ScaleGestureDetector
    lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)
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

        edit_element.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {}
            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ) {}

            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), elements, recyclerView)
            }
        })

        setOnCLickListenerSetups()
        setupNavListeners()
        detailViewDisabled(elements)
        detailViewEnabled(elements)
        onClickNav()
        searchListener()
        sliding_layout.setPanelState(PanelState.COLLAPSED)
        searchFilter(elements, recyclerView)
        electroView(elements)
        nameView(elements)

        gestureDetector = GestureDetector(this, GestureListener())
        //Currently Disabled (Change min and max scale to enable zoom)
        mScaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = 1 - detector.scaleFactor
                val prevScale = mScale
                mScale += scale
                if (mScale < 1f) // Minimum scale
                    mScale = 1f
                if (mScale > 1) // Maximum scale
                    mScale = 1f
                val scaleAnimation = ScaleAnimation(
                    1f / prevScale,
                    1f / mScale,
                    1f / prevScale,
                    1f / mScale,
                    detector.focusX,
                    detector.focusY
                )

                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                val layout =
                    scrollViewZoom as ScrollView
                layout.startAnimation(scaleAnimation)
                return true

            }
        })
        more_btn.setOnClickListener { openHover() }
        hover_background.setOnClickListener { closeHover() }
        random_btn.setOnClickListener { getRandomItem() }

        if (Build.VERSION.SDK_INT >= 28) {
            val attribute = window.attributes
            attribute.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        view_main.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        sliding_layout.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                //Empty
            }
            override fun onPanelStateChanged(
                panel: View?,
                previousState: PanelState,
                newState: PanelState
            ) {
                if (sliding_layout.getPanelState() === PanelState.COLLAPSED) {
                    nav_menu_include.visibility = View.GONE
                    Utils.fadeOutAnim(nav_background, 100)
                }
            }
        })
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

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = common_title_back_main.layoutParams as ViewGroup.LayoutParams
        params.height += top
        common_title_back_main.layoutParams = params

        val params2 = nav_bar_main.layoutParams as ViewGroup.LayoutParams
        params2.height += bottom
        nav_bar_main.layoutParams = params2

        val params3 = more_btn.layoutParams as ViewGroup.MarginLayoutParams
        params3.bottomMargin += bottom
        more_btn.layoutParams = params3

        val params4 = common_title_back_search.layoutParams as ViewGroup.LayoutParams
        params4.height += top
        common_title_back_search.layoutParams = params4

        val params5 = hover_menu_include.layoutParams as ViewGroup.MarginLayoutParams
        params5.bottomMargin += bottom
        hover_menu_include.layoutParams = params5

        val params6 = scrollView.layoutParams as ViewGroup.MarginLayoutParams
        params6.topMargin += top
        scrollView.layoutParams = params6

        val params7 = sliding_layout.layoutParams as ViewGroup.LayoutParams
        params7.height += bottom
        sliding_layout.layoutParams = params7
    }

    private fun openHover() {
        Utils.fadeInAnim(hover_background, 150)
        Utils.fadeInAnim(hover_menu_include, 150)
    }

    private fun closeHover() {
        Utils.fadeOutAnim(hover_background, 150)
        Utils.fadeOutAnim(hover_menu_include, 150)
    }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        filter_box.visibility = View.GONE
        background.visibility = View.GONE

        filter_btn.setOnClickListener {
            Utils.fadeInAnim(filter_box, 150)
            Utils.fadeInAnim(background, 150)
        }
        background.setOnClickListener {
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
        }

        elmt_numb_btn.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        electro_btn.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) {
            if (item.element.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = ElementAdapter(filteredList, this, this)
    }

    override fun elementClickListener2(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)

        val intent = Intent(this, ElementInfoActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (nav_background.visibility == View.VISIBLE) {
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            Utils.fadeOutAnim(nav_background, 150)
            return
        }
        if (hover_background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(hover_background, 150)
            Utils.fadeOutAnim(hover_menu_include, 150)
            return
        }
        if (search_menu_include.visibility == View.VISIBLE) {
            Utils.fadeInAnim(nav_bar_main, 150)
            Utils.fadeOutAnim(nav_background, 150)
            Utils.fadeOutAnim(search_menu_include, 150)
            return
        }
        if (search_menu_include.visibility == View.VISIBLE && background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(background, 150)
            Utils.fadeOutAnim(filter_box, 150)
            return
        }
        else {
            super.onBackPressed()
        }
    }

    private fun searchListener() {
        search_box.setOnClickListener {
            Utils.fadeInAnim(search_menu_include, 300)
            nav_bar_main.visibility = View.GONE

            edit_element.requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_element, InputMethodManager.SHOW_IMPLICIT)
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
        }
        close_element_search.setOnClickListener {
            Utils.fadeOutAnim(search_menu_include, 300)
            nav_bar_main.visibility = View.VISIBLE

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun onClickNav() {
        menu_btn.setOnClickListener {
            nav_menu_include.visibility = View.VISIBLE
            nav_background.visibility = View.VISIBLE
            Utils.fadeInAnim(nav_background, 200)
            window.setStatusBarColor(getColor(R.color.background_status))
            sliding_layout.setPanelState(PanelState.EXPANDED)
        }
        nav_background.setOnClickListener {
            search_menu_include.visibility = View.GONE
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            nav_bar_main.visibility = View.VISIBLE
            Utils.fadeOutAnim(nav_background, 100)
        }
        solubility_btn.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
            startActivity(intent)
        }
        isotopes_btn.setOnClickListener {
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent)
        }
        dictionary_btn.setOnClickListener {
            val intent = Intent(this, DictionaryActivity::class.java)
            startActivity(intent)
        }
    }

    //dispatchTouchEvent() (scrollZoom)
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        super.dispatchTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    //GestureListener (ScrollZoom)
    private class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        // event when double tap occurs
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // double tap fired.
            return true
        }
    }

    private fun setupNavListeners() {

        settings_btn.setOnClickListener() {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun electroView(list: ArrayList<Element>) {
        electron_btn.setOnClickListener {
            closeHover()
            initElectro(list)

            Utils.fadeOutAnim(electron_btn_frame, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(electron_btn_frame_hide, 150)
            }, 151)
        }
    }

    private fun nameView(list: ArrayList<Element>) {
        electron_btn_hide.setOnClickListener {
            closeHover()
            initName(list)

            Utils.fadeOutAnim(electron_btn_frame_hide, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(electron_btn_frame, 150)
            }, 151)
        }
    }

    private fun initElectro(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extText = "_text"
            val eView = "$name$extText"
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resID = resources.getIdentifier(eView, "id", packageName)
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            if (resID == 0) {
                ToastUtil.showToast(this, "Error (electro)")
            }
            else {
                if (item.electro == 0.0) {
                    val text = findViewById<TextView>(resID)
                    text.text = "---"
                }
                else {
                    val text = findViewById<TextView>(resID)
                    text.text = (item.electro).toString()
                }
            }
            if (resIDB == 0) {
                ToastUtil.showToast(this, "Error (electro back)")
            }
            else {
                if (item.electro == 0.0) {
                    val btn = findViewById<Button>(resIDB)
                    val themePreference = ThemePreference(this)
                    val themePrefValue = themePreference.getValue()

                    if (themePrefValue == 100) {
                        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                            Configuration.UI_MODE_NIGHT_NO -> { btn.background.setTint(Color.argb(255, 254, 254, 254)) }
                            Configuration.UI_MODE_NIGHT_YES -> { btn.background.setTint(Color.argb(255, 18, 18, 18)) }
                        }
                    }
                    if (themePrefValue == 0) { btn.background.setTint(Color.argb(255, 254, 254, 254)) }
                    if (themePrefValue == 1) { btn.background.setTint(Color.argb(255, 18, 18, 18)) }
                }
                else {
                    if (item.electro > 1) {
                        val btn = findViewById<Button>(resIDB)
                        btn.background.setTint(Color.argb(255, 255, 225.div(item.electro).toInt(), 0))
                    }
                    else {
                        val btn = findViewById<Button>(resIDB)
                        btn.background.setTint(Color.argb(255, 255, 214, 0))
                    }
                }
            }
        }
    }

    private fun initName(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extText = "_text"
            val eView = "$name$extText"
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resID = resources.getIdentifier(eView, "id", packageName)
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val text = findViewById<TextView>(resID)
            text.text = (item.element)
            val btn = findViewById<Button>(resIDB)
            val themePreference = ThemePreference(this)
            val themePrefValue = themePreference.getValue()

            if (themePrefValue == 100) {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_NO -> { btn.background.setTint(Color.argb(255, 254, 254, 254)) }
                    Configuration.UI_MODE_NIGHT_YES -> { btn.background.setTint(Color.argb(255, 18, 18, 18)) }
                }
            }
            if (themePrefValue == 0) { btn.background.setTint(Color.argb(255, 254, 254, 254)) }
            if (themePrefValue == 1) { btn.background.setTint(Color.argb(255, 18, 18, 18)) }
        }
    }

    private fun detailViewDisabled(list: ArrayList<Element>) {
        detail_btn.setOnClickListener {
            closeHover()
            for (item in list) {
                val name = item.element
                val extBtn = "_btn"
                val eViewBtn = "$name$extBtn"
                val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

                val btn = findViewById<Button>(resIDB)
                if ((item.number == 3) or (item.number == 11) or (item.number == 19) or (item.number == 37) or (item.number == 55) or (item.number == 87)) {
                    btn.background.setTint(Color.argb(255, 255, 102, 102))
                }
                if ((item.number == 4) or (item.number == 12) or (item.number == 20) or (item.number == 38) or (item.number == 56) or (item.number == 88)) {
                    btn.background.setTint(Color.argb(255, 255, 195, 112))
                }
                if ((item.number in 21..30) or (item.number in 39..48) or (item.number in 72..80) or (item.number in 104..112)) {
                    btn.background.setTint(Color.argb(255, 225, 168, 166))
                }
                if ((item.number == 5) or (item.number == 14) or (item.number in 32..33) or (item.number in 51..52) or (item.number == 85)) {
                    btn.background.setTint(Color.argb(255, 184, 184, 136))
                }
                if ((item.number == 13) or (item.number == 31) or (item.number in 49..50) or (item.number in 81..84) or (item.number in 113..118)) {
                    btn.background.setTint(Color.argb(255, 174, 174, 174))
                }
                if ((item.number == 53) or (item.number in 34..35) or (item.number in 15..17) or (item.number in 6..9) or (item.number == 1)) {
                    btn.background.setTint(Color.argb(255, 129, 199, 132))
                }
                if ((item.number == 2) or (item.number == 10) or (item.number == 18) or (item.number == 36) or (item.number == 54) or (item.number == 86)) {
                    btn.background.setTint(Color.argb(255, 97, 193, 193))
                }

                Utils.fadeOutAnim(detail_btn_frame, 150)
                val delay = Handler()
                delay.postDelayed({
                    Utils.fadeInAnim(detail_btn_frame_close, 150)
                }, 151)
            }
        }
    }

    private fun detailViewEnabled(list: ArrayList<Element>) {
        detail_btn_close.setOnClickListener {
            closeHover()

            initName(list)

            Utils.fadeOutAnim(detail_btn_frame_close, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(detail_btn_frame, 150)
            }, 151)
        }
    }

    private fun setOnCLickListenerSetups() {
        //Row 1
        hydrogen_btn.setOnClickListener { onClick(hydrogen_btn) }
        helium_btn.setOnClickListener { onClick(helium_btn) }

        //Row 2
        lithium_btn.setOnClickListener { onClick(lithium_btn) }
        beryllium_btn.setOnClickListener { onClick(beryllium_btn) }
        boron_btn.setOnClickListener { onClick(boron_btn) }
        carbon_btn.setOnClickListener { onClick(carbon_btn) }
        nitrogen_btn.setOnClickListener { onClick(nitrogen_btn) }
        oxygen_btn.setOnClickListener { onClick(oxygen_btn) }
        fluorine_btn.setOnClickListener { onClick(fluorine_btn) }
        neon_btn.setOnClickListener { onClick(neon_btn) }

        //Row 3
        sodium_btn.setOnClickListener { onClick(sodium_btn) }
        magnesium_btn.setOnClickListener { onClick(magnesium_btn) }
        aluminium_btn.setOnClickListener { onClick(aluminium_btn) }
        silicon_btn.setOnClickListener { onClick(silicon_btn) }
        phosphorus_btn.setOnClickListener { onClick(phosphorus_btn) }
        sulfur_btn.setOnClickListener { onClick(sulfur_btn) }
        chlorine_btn.setOnClickListener { onClick(chlorine_btn) }
        argon_btn.setOnClickListener { onClick(argon_btn) }

        //Row 4
        potassium_btn.setOnClickListener { onClick(potassium_btn) }
        calcium_btn.setOnClickListener { onClick(calcium_btn) }
        scandium_btn.setOnClickListener { onClick(scandium_btn) }
        titanium_btn.setOnClickListener { onClick(titanium_btn) }
        vanadium_btn.setOnClickListener { onClick(vanadium_btn) }
        chromium_btn.setOnClickListener { onClick(chromium_btn) }
        manganese_btn.setOnClickListener { onClick(manganese_btn) }
        iron_btn.setOnClickListener { onClick(iron_btn) }
        cobalt_btn.setOnClickListener { onClick(cobalt_btn) }
        nickel_btn.setOnClickListener { onClick(nickel_btn) }
        copper_btn.setOnClickListener { onClick(copper_btn) }
        zinc_btn.setOnClickListener { onClick(zinc_btn) }
        gallium_btn.setOnClickListener { onClick(gallium_btn) }
        germanium_btn.setOnClickListener { onClick(germanium_btn) }
        arsenic_btn.setOnClickListener { onClick(arsenic_btn) }
        selenium_btn.setOnClickListener { onClick(selenium_btn) }
        bromine_btn.setOnClickListener { onClick(bromine_btn) }
        krypton_btn.setOnClickListener { onClick(krypton_btn) }

        //Row 5
        rubidium_btn.setOnClickListener { onClick(rubidium_btn) }
        strontium_btn.setOnClickListener { onClick(strontium_btn) }
        yttrium_btn.setOnClickListener { onClick(yttrium_btn) }
        zirconium_btn.setOnClickListener { onClick(zirconium_btn) }
        niobium_btn.setOnClickListener { onClick(niobium_btn) }
        molybdenum_btn.setOnClickListener { onClick(molybdenum_btn) }
        technetium_btn.setOnClickListener { onClick(technetium_btn) }
        ruthenium_btn.setOnClickListener { onClick(ruthenium_btn) }
        rhodium_btn.setOnClickListener { onClick(rhodium_btn) }
        palladium_btn.setOnClickListener { onClick(palladium_btn) }
        silver_btn.setOnClickListener { onClick(silver_btn) }
        cadmium_btn.setOnClickListener { onClick(cadmium_btn) }
        indium_btn.setOnClickListener { onClick(indium_btn) }
        tin_btn.setOnClickListener { onClick(tin_btn) }
        antimony_btn.setOnClickListener { onClick(antimony_btn) }
        tellurium_btn.setOnClickListener { onClick(tellurium_btn) }
        iodine_btn.setOnClickListener { onClick(iodine_btn) }
        xenon_btn.setOnClickListener { onClick(xenon_btn) }

        //Row 6
        caesium_btn.setOnClickListener { onClick(caesium_btn) }
        barium_btn.setOnClickListener { onClick(barium_btn) }
        //lanthanoids
        hafnium_btn.setOnClickListener { onClick(hafnium_btn) }
        tantalum_btn.setOnClickListener { onClick(tantalum_btn) }
        tungsten_btn.setOnClickListener { onClick(tungsten_btn) }
        rhenium_btn.setOnClickListener { onClick(rhenium_btn) }
        osmium_btn.setOnClickListener { onClick(osmium_btn) }
        iridium_btn.setOnClickListener { onClick(iridium_btn) }
        platinum_btn.setOnClickListener { onClick(platinum_btn) }
        gold_btn.setOnClickListener { onClick(gold_btn) }
        mercury_btn.setOnClickListener { onClick(mercury_btn) }
        thallium_btn.setOnClickListener { onClick(thallium_btn) }
        lead_btn.setOnClickListener { onClick(lead_btn) }
        bismuth_btn.setOnClickListener { onClick(bismuth_btn) }
        polonium_btn.setOnClickListener { onClick(polonium_btn) }
        astatine_btn.setOnClickListener { onClick(astatine_btn) }
        radon_btn.setOnClickListener { onClick(radon_btn) }

        //Row 7
        francium_btn.setOnClickListener { onClick(francium_btn) }
        radium_btn.setOnClickListener { onClick(radium_btn) }
        //lanthanoids
        rutherfordium_btn.setOnClickListener { onClick(rutherfordium_btn) }
        dubnium_btn.setOnClickListener { onClick(dubnium_btn) }
        seaborgium_btn.setOnClickListener { onClick(seaborgium_btn) }
        bohrium_btn.setOnClickListener { onClick(bohrium_btn) }
        hassium_btn.setOnClickListener { onClick(hassium_btn) }
        meitnerium_btn.setOnClickListener { onClick(meitnerium_btn) }
        darmstadtium_btn.setOnClickListener { onClick(darmstadtium_btn) }
        roentgenium_btn.setOnClickListener { onClick(roentgenium_btn) }
        copernicium_btn.setOnClickListener { onClick(copernicium_btn) }
        nihonium_btn.setOnClickListener { onClick(nihonium_btn) }
        flerovium_btn.setOnClickListener { onClick(flerovium_btn) }
        moscovium_btn.setOnClickListener { onClick(moscovium_btn) }
        livermorium_btn.setOnClickListener { onClick(livermorium_btn) }
        tennessine_btn.setOnClickListener { onClick(tennessine_btn) }
        oganesson_btn.setOnClickListener { onClick(oganesson_btn) }

        //Row 8
        lanthanum_btn.setOnClickListener { onClick(lanthanum_btn) }
        cerium_btn.setOnClickListener { onClick(cerium_btn) }
        praseodymium_btn.setOnClickListener { onClick(praseodymium_btn) }
        neodymium_btn.setOnClickListener { onClick(neodymium_btn) }
        promethium_btn.setOnClickListener { onClick(promethium_btn) }
        samarium_btn.setOnClickListener { onClick(samarium_btn) }
        europium_btn.setOnClickListener { onClick(europium_btn) }
        gadolinium_btn.setOnClickListener { onClick(gadolinium_btn) }
        terbium_btn.setOnClickListener { onClick(terbium_btn) }
        dysprosium_btn.setOnClickListener { onClick(dysprosium_btn) }
        holmium_btn.setOnClickListener { onClick(holmium_btn) }
        erbium_btn.setOnClickListener { onClick(erbium_btn) }
        thulium_btn.setOnClickListener { onClick(thulium_btn) }
        ytterbium_btn.setOnClickListener { onClick(ytterbium_btn) }
        lutetium_btn.setOnClickListener { onClick(lutetium_btn) }

        //Row 9
        actinium_btn.setOnClickListener { onClick(actinium_btn) }
        thorium_btn.setOnClickListener { onClick(thorium_btn) }
        protactinium_btn.setOnClickListener { onClick(protactinium_btn) }
        uranium_btn.setOnClickListener { onClick(uranium_btn) }
        neptunium_btn.setOnClickListener { onClick(neptunium_btn) }
        plutonium_btn.setOnClickListener { onClick(plutonium_btn) }
        americium_btn.setOnClickListener { onClick(americium_btn) }
        curium_btn.setOnClickListener { onClick(curium_btn) }
        berkelium_btn.setOnClickListener { onClick(berkelium_btn) }
        californium_btn.setOnClickListener { onClick(californium_btn) }
        einsteinium_btn.setOnClickListener { onClick(einsteinium_btn) }
        fermium_btn.setOnClickListener { onClick(fermium_btn) }
        mendelevium_btn.setOnClickListener { onClick(mendelevium_btn) }
        nobelium_btn.setOnClickListener { onClick(nobelium_btn) }
        lawrencium_btn.setOnClickListener { onClick(lawrencium_btn) }

    }

    override fun onClick(v: View) {

        when (v.id) {

            R.id.hydrogen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("hydrogen")
                startActivity(intent)
            }
            R.id.helium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("helium")
                startActivity(intent)
            }
            R.id.lithium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("lithium")
                startActivity(intent)
            }
            R.id.beryllium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("beryllium")
                startActivity(intent)
            }
            R.id.boron_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("boron")
                startActivity(intent)
            }
            R.id.carbon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("carbon")
                startActivity(intent)
            }
            R.id.nitrogen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("nitrogen")
                startActivity(intent)
            }
            R.id.oxygen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("oxygen")
                startActivity(intent)
            }
            R.id.fluorine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("fluorine")
                startActivity(intent)
            }
            R.id.neon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("neon")
                startActivity(intent)
            }

            R.id.sodium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("sodium")
                startActivity(intent)
            }
            R.id.magnesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("magnesium")
                startActivity(intent)
            }
            R.id.aluminium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("aluminium")
                startActivity(intent)
            }
            R.id.silicon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend = ElementSendAndLoad(this)
                ElementSend.setValue("silicon")
                startActivity(intent)
            }
            R.id.phosphorus_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("phosphorus")
                startActivity(intent)
            }
            R.id.sulfur_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("sulfur")
                startActivity(intent)
            }
            R.id.chlorine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("chlorine")
                startActivity(intent)
            }
            R.id.argon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("argon")
                startActivity(intent)
            }
            R.id.potassium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("potassium")
                startActivity(intent)
            }
            R.id.calcium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("calcium")
                startActivity(intent)
            }
            R.id.scandium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("scandium")
                startActivity(intent)
            }
            R.id.titanium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("titanium")
                startActivity(intent)
            }
            R.id.vanadium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("vanadium")
                startActivity(intent)
            }
            R.id.chromium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("chromium")
                startActivity(intent)
            }
            R.id.manganese_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("manganese")
                startActivity(intent)
            }
            R.id.iron_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("iron")
                startActivity(intent)
            }
            R.id.cobalt_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("cobalt")
                startActivity(intent)
            }
            R.id.nickel_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("nickel")
                startActivity(intent)
            }
            R.id.copper_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("copper")
                startActivity(intent)
            }
            R.id.zinc_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("zinc")
                startActivity(intent)
            }
            R.id.gallium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("gallium")
                startActivity(intent)
            }
            R.id.germanium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("germanium")
                startActivity(intent)
            }
            R.id.arsenic_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("arsenic")
                startActivity(intent)
            }
            R.id.selenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("selenium")
                startActivity(intent)
            }
            R.id.bromine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("bromine")
                startActivity(intent)
            }
            R.id.krypton_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("krypton")
                startActivity(intent)
            }
            R.id.rubidium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("rubidium")
                startActivity(intent)
            }
            R.id.strontium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("strontium")
                startActivity(intent)
            }
            R.id.yttrium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("yttrium")
                startActivity(intent)
            }
            R.id.zirconium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("zirconium")
                startActivity(intent)
            }
            R.id.niobium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("niobium")
                startActivity(intent)
            }
            R.id.molybdenum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("molybdenum")
                startActivity(intent)
            }
            R.id.technetium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("technetium")
                startActivity(intent)
            }
            R.id.ruthenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("ruthenium")
                startActivity(intent)
            }
            R.id.rhodium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("rhodium")
                startActivity(intent)
            }
            R.id.palladium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("palladium")
                startActivity(intent)
            }
            R.id.silver_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("silver")
                startActivity(intent)
            }
            R.id.cadmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("cadmium")
                startActivity(intent)
            }
            R.id.indium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("indium")
                startActivity(intent)
            }
            R.id.tin_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("tin")
                startActivity(intent)
            }
            R.id.antimony_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("antimony")
                startActivity(intent)
            }
            R.id.tellurium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("tellurium")
                startActivity(intent)
            }
            R.id.iodine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("iodine")
                startActivity(intent)
            }
            R.id.xenon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("xenon")
                startActivity(intent)
            }
            R.id.caesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("caesium")
                startActivity(intent)
            }
            R.id.barium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("barium")
                startActivity(intent)
            }
            R.id.lanthanum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("lanthanum")
                startActivity(intent)
            }
            R.id.cerium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("cerium")
                startActivity(intent)
            }
            R.id.praseodymium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("praseodymium")
                startActivity(intent)
            }
            R.id.neodymium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("neodymium")
                startActivity(intent)
            }
            R.id.promethium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("promethium")
                startActivity(intent)
            }
            R.id.samarium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("samarium")
                startActivity(intent)
            }
            R.id.europium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("europium")
                startActivity(intent)
            }
            R.id.gadolinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("gadolinium")
                startActivity(intent)
            }
            R.id.terbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("terbium")
                startActivity(intent)
            }
            R.id.dysprosium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("dysprosium")
                startActivity(intent)
            }
            R.id.holmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("holmium")
                startActivity(intent)
            }
            R.id.erbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("erbium")
                startActivity(intent)
            }
            R.id.thulium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("thulium")
                startActivity(intent)
            }
            R.id.ytterbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("ytterbium")
                startActivity(intent)
            }
            R.id.lutetium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("lutetium")
                startActivity(intent)
            }
            R.id.hafnium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("hafnium")
                startActivity(intent)
            }
            R.id.tantalum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("tantalum")
                startActivity(intent)
            }
            R.id.tungsten_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("tungsten")
                startActivity(intent)
            }
            R.id.rhenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("rhenium")
                startActivity(intent)
            }
            R.id.osmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("osmium")
                startActivity(intent)
            }
            R.id.iridium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("iridium")
                startActivity(intent)
            }
            R.id.platinum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("platinum")
                startActivity(intent)
            }
            R.id.gold_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("gold")
                startActivity(intent)
            }
            R.id.mercury_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("mercury")
                startActivity(intent)
            }
            R.id.thallium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("thallium")
                startActivity(intent)
            }
            R.id.lead_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("lead")
                startActivity(intent)
            }
            R.id.bismuth_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("bismuth")
                startActivity(intent)
            }
            R.id.polonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("polonium")
                startActivity(intent)
            }
            R.id.astatine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("astatine")
                startActivity(intent)
            }
            R.id.radon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("radon")
                startActivity(intent)
            }
            R.id.francium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("francium")
                startActivity(intent)
            }
            R.id.radium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("radium")
                startActivity(intent)
            }
            R.id.actinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("actinium")
                startActivity(intent)
            }
            R.id.thorium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("thorium")
                startActivity(intent)
            }
            R.id.protactinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("protactinium")
                startActivity(intent)
            }
            R.id.uranium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("uranium")
                startActivity(intent)
            }
            R.id.neptunium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("neptunium")
                startActivity(intent)
            }
            R.id.plutonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("plutonium")
                startActivity(intent)
            }
            R.id.americium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("americium")
                startActivity(intent)
            }
            R.id.curium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("curium")
                startActivity(intent)
            }
            R.id.berkelium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("berkelium")
                startActivity(intent)
            }
            R.id.californium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("californium")
                startActivity(intent)
            }
            R.id.einsteinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("einsteinium")
                startActivity(intent)
            }
            R.id.fermium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("fermium")
                startActivity(intent)
            }
            R.id.mendelevium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("mendelevium")
                startActivity(intent)
            }
            R.id.nobelium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("nobelium")
                startActivity(intent)
            }
            R.id.lawrencium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("lawrencium")
                startActivity(intent)
            }
            R.id.rutherfordium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("rutherfordium")
                startActivity(intent)
            }
            R.id.dubnium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("dubnium")
                startActivity(intent)
            }
            R.id.seaborgium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("seaborgium")
                startActivity(intent)
            }
            R.id.bohrium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("bohrium")
                startActivity(intent)
            }
            R.id.hassium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("hassium")
                startActivity(intent)
            }
            R.id.meitnerium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("meitnerium")
                startActivity(intent)
            }
            R.id.darmstadtium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("darmstadtium")
                startActivity(intent)
            }
            R.id.roentgenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("roentgenium")
                startActivity(intent)
            }
            R.id.copernicium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("copernicium")
                startActivity(intent)
            }
            R.id.nihonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("nihonium")
                startActivity(intent)
            }
            R.id.flerovium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("flerovium")
                startActivity(intent)
            }
            R.id.moscovium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("moscovium")
                startActivity(intent)
            }
            R.id.livermorium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("livermorium")
                startActivity(intent)
            }
            R.id.tennessine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("tennessine")
                startActivity(intent)
            }
            R.id.oganesson_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue("oganesson")
                startActivity(intent)
            }


            else -> {
            }
        }
    } //RedirectToElementActivities

    fun showNavMenu() {

    }
}
