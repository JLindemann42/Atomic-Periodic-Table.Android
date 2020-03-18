package com.jlindemann.science

import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.animation.ScaleAnimation
import android.widget.ScrollView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.SettingsActivity
import com.jlindemann.science.activities.SolubilityActivity
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import kotlinx.android.synthetic.main.activity_main.*
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
import kotlinx.android.synthetic.main.nav_menu_view.*


class MainActivity : AppCompatActivity(), View.OnClickListener {

    var mScale = 1f
    lateinit var mScaleDetector: ScaleGestureDetector
    lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> {
                    setTheme(R.style.AppTheme)
                    window.setNavigationBarColor(getColor(R.color.colorLightPrimary))
                    window.setStatusBarColor(getColor(R.color.colorLightPrimary))
                }
                Configuration.UI_MODE_NIGHT_YES -> {
                    setTheme(R.style.AppThemeDark)
                    window.setNavigationBarColor(getColor(R.color.colorDarkPrimary))
                    window.setStatusBarColor(getColor(R.color.colorDarkPrimary))
                }
            }
        }

        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
            window.setNavigationBarColor(getColor(R.color.colorLightPrimary))
            window.setStatusBarColor(getColor(R.color.colorLightPrimary))
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
            window.setNavigationBarColor(getColor(R.color.colorDarkPrimary))
            window.setStatusBarColor(getColor(R.color.colorDarkPrimary))
        }

        val ElementSendAndLoadPreference = ElementSendAndLoad(this)
        var ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()

        setContentView(R.layout.activity_main)
        setOnCLickListenerSetups()
        setupNavListeners()
        detailViewDisabled()
        onClickNav()
        sliding_layout.setPanelState(PanelState.COLLAPSED)

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

        //Currently does nothing as landscape mode is disabled in App manifest
        if (Build.VERSION.SDK_INT >= 28) {
            val attribute = window.attributes
            attribute.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

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
                    nav_background.visibility = View.GONE
                }
            }
        })
    }

    override fun onBackPressed() {
        if (nav_background.visibility == View.VISIBLE) {
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            return
        }
        if (search_menu_include.visibility == View.VISIBLE) {
            search_menu_include.visibility = View.GONE
            nav_bar.visibility = View.VISIBLE
            return
        }
        else {
            super.onBackPressed()
        }
    }

    private fun onClickNav() {
        menu_btn.setOnClickListener {
            nav_menu_include.visibility = View.VISIBLE
            nav_background.visibility = View.VISIBLE
            sliding_layout.setPanelState(PanelState.EXPANDED)
        }
        search_box.setOnClickListener {
            search_menu_include.visibility = View.VISIBLE
            Utils.fadeInAnim(search_menu_include, 300)
            nav_bar.visibility = View.GONE
        }
        solubility_btn.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
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

    private fun detailViewDisabled() {
        detail_btn_disabled.setOnClickListener {
            hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.non_metals), PorterDuff.Mode.MULTIPLY);
            detail_btn_disabled.visibility = View.GONE
            detail_btn_enabled.visibility = View.VISIBLE
        }
    }

    private fun detailViewEnabled() {
        detail_btn_enabled.setOnClickListener {
            val themePreference = ThemePreference(this)
            var themePrefValue = themePreference.getValue()
            if (themePrefValue == 0) {
                hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.colorLightPrimary), PorterDuff.Mode.MULTIPLY);
            }
            if (themePrefValue == 1) {
                hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.colorDarkPrimary), PorterDuff.Mode.MULTIPLY);
            }
            detail_btn_disabled.visibility = View.VISIBLE
            detail_btn_enabled.visibility = View.GONE
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
                ElementSend.setValue(1)
                startActivity(intent)
            }
            R.id.helium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(2)
                startActivity(intent)
            }
            R.id.lithium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(3)
                startActivity(intent)
            }
            R.id.beryllium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(4)
                startActivity(intent)
            }
            R.id.boron_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(5)
                startActivity(intent)
            }
            R.id.carbon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(6)
                startActivity(intent)
            }
            R.id.nitrogen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(7)
                startActivity(intent)
            }
            R.id.oxygen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(8)
                startActivity(intent)
            }
            R.id.fluorine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(9)
                startActivity(intent)
            }
            R.id.neon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(10)
                startActivity(intent)
            }

            R.id.sodium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(11)
                startActivity(intent)
            }
            R.id.magnesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(12)
                startActivity(intent)
            }
            R.id.aluminium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(13)
                startActivity(intent)
            }
            R.id.silicon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend = ElementSendAndLoad(this)
                ElementSend.setValue(14)
                startActivity(intent)
            }
            R.id.phosphorus_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(15)
                startActivity(intent)
            }
            R.id.sulfur_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(16)
                startActivity(intent)
            }
            R.id.chlorine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(17)
                startActivity(intent)
            }
            R.id.argon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(18)
                startActivity(intent)
            }
            R.id.potassium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(19)
                startActivity(intent)
            }
            R.id.calcium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(20)
                startActivity(intent)
            }
            R.id.scandium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(21)
                startActivity(intent)
            }
            R.id.titanium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(22)
                startActivity(intent)
            }
            R.id.vanadium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(23)
                startActivity(intent)
            }
            R.id.chromium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(24)
                startActivity(intent)
            }
            R.id.manganese_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(25)
                startActivity(intent)
            }
            R.id.iron_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(26)
                startActivity(intent)
            }
            R.id.cobalt_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(27)
                startActivity(intent)
            }
            R.id.nickel_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(28)
                startActivity(intent)
            }
            R.id.copper_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(29)
                startActivity(intent)
            }
            R.id.zinc_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(30)
                startActivity(intent)
            }
            R.id.gallium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(31)
                startActivity(intent)
            }
            R.id.germanium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(32)
                startActivity(intent)
            }
            R.id.arsenic_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(33)
                startActivity(intent)
            }
            R.id.selenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(34)
                startActivity(intent)
            }
            R.id.bromine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(35)
                startActivity(intent)
            }
            R.id.krypton_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(36)
                startActivity(intent)
            }
            R.id.rubidium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(37)
                startActivity(intent)
            }
            R.id.strontium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(38)
                startActivity(intent)
            }
            R.id.yttrium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(39)
                startActivity(intent)
            }
            R.id.zirconium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(40)
                startActivity(intent)
            }
            R.id.niobium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(41)
                startActivity(intent)
            }
            R.id.molybdenum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(42)
                startActivity(intent)
            }
            R.id.technetium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(43)
                startActivity(intent)
            }
            R.id.ruthenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(44)
                startActivity(intent)
            }
            R.id.rhodium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(45)
                startActivity(intent)
            }
            R.id.palladium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(46)
                startActivity(intent)
            }
            R.id.silver_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(47)
                startActivity(intent)
            }
            R.id.cadmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(48)
                startActivity(intent)
            }
            R.id.indium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(49)
                startActivity(intent)
            }
            R.id.tin_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(50)
                startActivity(intent)
            }
            R.id.antimony_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(51)
                startActivity(intent)
            }
            R.id.tellurium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(52)
                startActivity(intent)
            }
            R.id.iodine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(53)
                startActivity(intent)
            }
            R.id.xenon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(54)
                startActivity(intent)
            }
            R.id.caesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(55)
                startActivity(intent)
            }
            R.id.barium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(56)
                startActivity(intent)
            }
            R.id.lanthanum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(57)
                startActivity(intent)
            }
            R.id.cerium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(58)
                startActivity(intent)
            }
            R.id.praseodymium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(59)
                startActivity(intent)
            }
            R.id.neodymium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(60)
                startActivity(intent)
            }
            R.id.promethium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(61)
                startActivity(intent)
            }
            R.id.samarium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(62)
                startActivity(intent)
            }
            R.id.europium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(63)
                startActivity(intent)
            }
            R.id.gadolinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(64)
                startActivity(intent)
            }
            R.id.terbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(65)
                startActivity(intent)
            }
            R.id.dysprosium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(66)
                startActivity(intent)
            }
            R.id.holmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(67)
                startActivity(intent)
            }
            R.id.erbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(68)
                startActivity(intent)
            }
            R.id.thulium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(69)
                startActivity(intent)
            }
            R.id.ytterbium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(70)
                startActivity(intent)
            }
            R.id.lutetium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(71)
                startActivity(intent)
            }
            R.id.hafnium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(72)
                startActivity(intent)
            }
            R.id.tantalum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(73)
                startActivity(intent)
            }
            R.id.tungsten_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(74)
                startActivity(intent)
            }
            R.id.rhenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(75)
                startActivity(intent)
            }
            R.id.osmium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(76)
                startActivity(intent)
            }
            R.id.iridium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(77)
                startActivity(intent)
            }
            R.id.platinum_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(78)
                startActivity(intent)
            }
            R.id.gold_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(79)
                startActivity(intent)
            }
            R.id.mercury_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(80)
                startActivity(intent)
            }
            R.id.thallium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(81)
                startActivity(intent)
            }
            R.id.lead_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(82)
                startActivity(intent)
            }
            R.id.bismuth_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(83)
                startActivity(intent)
            }
            R.id.polonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(84)
                startActivity(intent)
            }
            R.id.astatine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(85)
                startActivity(intent)
            }
            R.id.radon_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(86)
                startActivity(intent)
            }
            R.id.francium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(87)
                startActivity(intent)
            }
            R.id.radium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(88)
                startActivity(intent)
            }
            R.id.actinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(89)
                startActivity(intent)
            }
            R.id.thorium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(90)
                startActivity(intent)
            }
            R.id.protactinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(91)
                startActivity(intent)
            }
            R.id.uranium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(92)
                startActivity(intent)
            }
            R.id.neptunium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(93)
                startActivity(intent)
            }
            R.id.plutonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(94)
                startActivity(intent)
            }
            R.id.americium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(95)
                startActivity(intent)
            }
            R.id.curium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(96)
                startActivity(intent)
            }
            R.id.berkelium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(97)
                startActivity(intent)
            }
            R.id.californium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(98)
                startActivity(intent)
            }
            R.id.einsteinium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(99)
                startActivity(intent)
            }
            R.id.fermium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(100)
                startActivity(intent)
            }
            R.id.mendelevium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(101)
                startActivity(intent)
            }
            R.id.nobelium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(102)
                startActivity(intent)
            }
            R.id.lawrencium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(103)
                startActivity(intent)
            }
            R.id.rutherfordium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(104)
                startActivity(intent)
            }
            R.id.dubnium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(105)
                startActivity(intent)
            }
            R.id.seaborgium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(106)
                startActivity(intent)
            }
            R.id.bohrium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(107)
                startActivity(intent)
            }
            R.id.hassium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(108)
                startActivity(intent)
            }
            R.id.meitnerium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(109)
                startActivity(intent)
            }
            R.id.darmstadtium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(110)
                startActivity(intent)
            }
            R.id.roentgenium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(111)
                startActivity(intent)
            }
            R.id.copernicium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(112)
                startActivity(intent)
            }
            R.id.nihonium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(113)
                startActivity(intent)
            }
            R.id.flerovium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(114)
                startActivity(intent)
            }
            R.id.moscovium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(115)
                startActivity(intent)
            }
            R.id.livermorium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(116)
                startActivity(intent)
            }
            R.id.tennessine_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(117)
                startActivity(intent)
            }
            R.id.oganesson_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(118)
                startActivity(intent)
            }

            else -> {
            }
        }
    } //RedirectToElementActivities

    fun showNavMenu() {

    }
}
