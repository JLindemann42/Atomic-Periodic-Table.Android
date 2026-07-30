package com.jlindemann.science.activities.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.UnifiedTitleBarController

class FavoritePageActivity : BaseActivity() {
    private lateinit var titleBar: UnifiedTitleBarController

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
        setContentView(R.layout.activity_favorite_settings_page)

        val molarPreference = FavoriteBarPreferences(this)
        var molarPrefValue = molarPreference.getValue()
        if (molarPrefValue == 1) {
            findViewById<CheckBox>(R.id.molar_mass_check).isChecked = true
        }
        if (molarPrefValue == 0) {
            findViewById<CheckBox>(R.id.molar_mass_check).isChecked = false
        }

        val phasePreferences = FavoritePhase(this)
        var phasePrefValue = phasePreferences.getValue()
        if (phasePrefValue == 1) {
            findViewById<CheckBox>(R.id.phase_check).isChecked = true
        }
        if (phasePrefValue == 0) {
            findViewById<CheckBox>(R.id.phase_check).isChecked = false
        }

        val electronegativityPreferences = ElectronegativityPreference(this)
        var electronegativityPrefValue = electronegativityPreferences.getValue()
        if (electronegativityPrefValue == 1) {
            findViewById<CheckBox>(R.id.electronegativity_check).isChecked = true
        }
        if (electronegativityPrefValue == 0) {
            findViewById<CheckBox>(R.id.electronegativity_check).isChecked = false
        }

        //Density
        val densityPreference = DensityPreference(this)
        var densityPrefValue = densityPreference.getValue()
        if (densityPrefValue == 1) {
            findViewById<CheckBox>(R.id.density_check).isChecked = true
        }
        if (densityPrefValue == 0) {
            findViewById<CheckBox>(R.id.density_check).isChecked = false
        }

        //Degree
        val degreePreference = DegreePreference(this)
        val degreePrefValue = degreePreference.getValue()
        val tempUnitGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.temp_unit_group)
        when (degreePrefValue) {
            0 -> tempUnitGroup.check(R.id.kelvin_btn)
            1 -> tempUnitGroup.check(R.id.celsius_btn)
            2 -> tempUnitGroup.check(R.id.fahrenheit_btn)
        }

        //Boiling Point
        val boilingPreference = BoilingPreference(this)
        var boilingPrefValue = boilingPreference.getValue()
        if (boilingPrefValue == 1) {
            findViewById<CheckBox>(R.id.boiling_check).isChecked = true
        }
        if (boilingPrefValue == 0) {
            findViewById<CheckBox>(R.id.boiling_check).isChecked = false
        }

        //Melting Point
        val meltingPreference = MeltingPreference(this)
        val meltingPrefValue = meltingPreference.getValue()
        if (meltingPrefValue == 1) {
            findViewById<CheckBox>(R.id.melting_check).isChecked = true
        }
        if (meltingPrefValue == 0) {
            findViewById<CheckBox>(R.id.melting_check).isChecked = false
        }

        //Atomic Radius Emp Point
        val AtomicEmpPreference = AtomicRadiusEmpPreference(this)
        val AtomicEmpPrefValue = AtomicEmpPreference.getValue()
        if (AtomicEmpPrefValue == 1) { findViewById<CheckBox>(R.id.atomic_radius_empirical_check).isChecked = true }
        if (AtomicEmpPrefValue == 0) { findViewById<CheckBox>(R.id.atomic_radius_empirical_check).isChecked = false }

        //Atomic Radius Cal Point
        val AtomicCalPreference = AtomicRadiusCalPreference(this)
        val AtomicCalPrefValue = AtomicCalPreference.getValue()
        if (AtomicCalPrefValue == 1) { findViewById<CheckBox>(R.id.atomic_radius_calculated_check).isChecked = true }
        if (AtomicCalPrefValue == 0) { findViewById<CheckBox>(R.id.atomic_radius_calculated_check).isChecked = false }

        //Covalent Radius Point
        val CovalentPreference = AtomicCovalentPreference(this)
        val AtomicCovalentPrefValue = CovalentPreference.getValue()
        if (AtomicCovalentPrefValue == 1) { findViewById<CheckBox>(R.id.covalent_radius_check).isChecked = true }
        if (AtomicCovalentPrefValue == 0) { findViewById<CheckBox>(R.id.covalent_radius_check).isChecked = false }

        //Covalent Radius Point
        val VanPreference = AtomicVanPreference(this)
        val VanprefValue = VanPreference.getValue()
        if (VanprefValue == 1) { findViewById<CheckBox>(R.id.van_der_waals_radius_check).isChecked = true }
        if (VanprefValue == 0) { findViewById<CheckBox>(R.id.van_der_waals_radius_check).isChecked = false }

        //Specific Heat Capacity
        val specificHeatPreference = SpecificHeatPreference(this)
        var specificHeatValue = specificHeatPreference.getValue()
        if (specificHeatValue == 1) {
            findViewById<CheckBox>(R.id.specific_heat_check).isChecked = true
        }
        if (specificHeatValue == 0) {
            findViewById<CheckBox>(R.id.specific_heat_check).isChecked = false
        }

        //Fusion Heat
        val fusionheatPreference = FusionHeatPreference(this)
        var fusionHeatValue = fusionheatPreference.getValue()
        if (fusionHeatValue == 1) {
            findViewById<CheckBox>(R.id.fusion_heat_check).isChecked = true
        }
        if (fusionHeatValue == 0) {
            findViewById<CheckBox>(R.id.fusion_heat_check).isChecked = false
        }

        //Vaporization heat
        val vaporizationHeatPreference = VaporizationHeatPreference(this)
        var vaporizationHeatValue = vaporizationHeatPreference.getValue()
        if (vaporizationHeatValue == 1) {
            findViewById<CheckBox>(R.id.vaporization_heat_check).isChecked = true
        }
        if (vaporizationHeatValue == 0) {
            findViewById<CheckBox>(R.id.vaporization_heat_check).isChecked = false
        }

        //Radioactive heat
        val radioactivePreference = RadioactivePreference(this)
        var radioactiveHeatValue = radioactivePreference.getValue()
        if (radioactiveHeatValue == 1) {
            findViewById<CheckBox>(R.id.radioactive_check).isChecked = true
        }
        if (radioactiveHeatValue == 0) {
            findViewById<CheckBox>(R.id.radioactive_check).isChecked = false
        }

        //Radioactive heat
        val resistivityPreference = ResistivityPreference(this)
        var resistivityValue = resistivityPreference.getValue()
        if (resistivityValue == 1) {
            findViewById<CheckBox>(R.id.radioactive_check).isChecked = true
        }
        if (resistivityValue == 0) {
            findViewById<CheckBox>(R.id.radioactive_check).isChecked = false
        }
        onCheckboxClicked()
        findViewById<ConstraintLayout>(R.id.viewf).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.settings_favorite_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }

        // Title Controller with animated visibility
        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
        findViewById<ScrollView>(R.id.fav_set_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                private var isTitleVisible = false // Track animation state

                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.fav_set_scroll).scrollY
                    val threshold = 150

                    val titleColorBackground = titleSurface
                    val titleText = titleBar.titleView
                    val titleDownstateText = findViewById<TextView>(R.id.favorite_set_title_downstate)
                    val titleBackground = titleBar.container

                    if (scrollY > threshold) {
                        if (!isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                            TitleBarAnimator.animateVisibility(titleText, true)
                            TitleBarAnimator.animateVisibility(titleDownstateText, false)
                            titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                            isTitleVisible = true
                        }
                    } else {
                        if (isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, false)
                            TitleBarAnimator.animateVisibility(titleText, false)
                            TitleBarAnimator.animateVisibility(titleDownstateText, true)
                            titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                            isTitleVisible = false
                        }
                    }
                }
            })
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            titleBar.container.layoutParams = params

            val params2 = findViewById<TextView>(R.id.favorite_set_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<TextView>(R.id.favorite_set_title_downstate).layoutParams = params2
    }

    fun onCheckboxClicked() {
        //Molar Mass
        val molarPreference = FavoriteBarPreferences(this)
        var molarPrefValue = molarPreference.getValue()
        var checkBox:CheckBox = findViewById<CheckBox>(R.id.molar_mass_check)
        checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                molarPreference.setValue(1)
            }
            else {
                molarPreference.setValue(0)
            }
        }

        findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.temp_unit_group).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val degreePreference = DegreePreference(this)
                when (checkedId) {
                    R.id.kelvin_btn -> degreePreference.setValue(0)
                    R.id.celsius_btn -> degreePreference.setValue(1)
                    R.id.fahrenheit_btn -> degreePreference.setValue(2)
                }
            }
        }

        //STP Phase
        val phasePreference = FavoritePhase(this)
        var phasePrefValue = phasePreference.getValue()
        var phaseCheckBox:CheckBox = findViewById<CheckBox>(R.id.phase_check)
        phaseCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                phasePreference.setValue(1)
            }
            else {
                phasePreference.setValue(0)
            }
        }

        //Electronegativity
        val electronegativityPreference = ElectronegativityPreference(this)
        var electronegativityPrefValue = electronegativityPreference.getValue()
        var electronegativityCheckBox:CheckBox = findViewById<CheckBox>(R.id.electronegativity_check)
        electronegativityCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                electronegativityPreference.setValue(1)
            }
            else {
                electronegativityPreference.setValue(0)
            }
        }

        //Density
        val densityPreference = DensityPreference(this)
        var densityPrefValue = densityPreference.getValue()
        var densityCheckBox:CheckBox = findViewById<CheckBox>(R.id.density_check)
        densityCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                densityPreference.setValue(1)
            }
            else {
                densityPreference.setValue(0)
            }

        }

        //Boiling Point
        val boilingPreference = BoilingPreference(this)
        var boilingPrefValue = boilingPreference.getValue()
        var boilingCheckBox:CheckBox = findViewById<CheckBox>(R.id.boiling_check)
        boilingCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                boilingPreference.setValue(1)
            }
            else {
                boilingPreference.setValue(0)
            }

        }

        //Melting Point
        val meltingPreference = MeltingPreference(this)
        val meltingCheckBox:CheckBox = findViewById<CheckBox>(R.id.melting_check)
        meltingCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) { meltingPreference.setValue(1) }
            else { meltingPreference.setValue(0) }
        }

        //Atomic Radius Empirical Point
        val atomicEmpiricalPreference = AtomicRadiusEmpPreference(this)
        val atomicEmpiricalBox:CheckBox = findViewById<CheckBox>(R.id.atomic_radius_empirical_check)
        atomicEmpiricalBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) { atomicEmpiricalPreference.setValue(1) }
            else { atomicEmpiricalPreference.setValue(0) }
        }

        //Atomic Radius Calculated Point
        val atomicCalculatedPreference = AtomicRadiusCalPreference(this)
        val atomicCalculatedBox:CheckBox = findViewById<CheckBox>(R.id.atomic_radius_calculated_check)
        atomicCalculatedBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) { atomicCalculatedPreference.setValue(1) }
            else { atomicCalculatedPreference.setValue(0) }
        }

        //Covalent Radius Point
        val covalentPreference = AtomicCovalentPreference(this)
        val covalentCheckBox:CheckBox = findViewById<CheckBox>(R.id.covalent_radius_check)
        covalentCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) { covalentPreference.setValue(1) }
            else { covalentPreference.setValue(0) }
        }

        //Van Der Waals Radius Point
        val vanPreference = AtomicVanPreference(this)
        val vanCheckBox:CheckBox = findViewById<CheckBox>(R.id.van_der_waals_radius_check)
        vanCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) { vanPreference.setValue(1) }
            else { vanPreference.setValue(0) }
        }

        //Specific Heat Point
        val specificHeatPreference = SpecificHeatPreference(this)
        var specificHeatValue = specificHeatPreference.getValue()
        var specificCheckBox:CheckBox = findViewById<CheckBox>(R.id.specific_heat_check)
        specificCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                specificHeatPreference.setValue(1)
            }
            else {
                specificHeatPreference.setValue(0)
            }
        }

        //Fusion Heat
        val fusionHeatPreference = FusionHeatPreference(this)
        var fusionHeatValue = fusionHeatPreference.getValue()
        var fusionCheckBox:CheckBox = findViewById<CheckBox>(R.id.fusion_heat_check)
        fusionCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                fusionHeatPreference.setValue(1)
            }
            else {
                fusionHeatPreference.setValue(0)
            }
        }

        //Vapor Heat
        val vaporizationHeatPreference = VaporizationHeatPreference(this)
        var vaporizationHeatValue = vaporizationHeatPreference.getValue()
        var vaporizationCheckBox:CheckBox = findViewById<CheckBox>(R.id.vaporization_heat_check)
        vaporizationCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                vaporizationHeatPreference.setValue(1)
            }
            else {
                vaporizationHeatPreference.setValue(0)
            }
        }

        //Radioactive
        val radioactivePreference = RadioactivePreference(this)
        var radioactiveValue = radioactivePreference.getValue()
        var radioactiveCheckBox:CheckBox = findViewById<CheckBox>(R.id.radioactive_check)
        radioactiveCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                radioactivePreference.setValue(1)
            }
            else {
                radioactivePreference.setValue(0)
            }
        }

        //Radioactive
        val resistivityPreference = ResistivityPreference(this)
        var resitivityValue = resistivityPreference.getValue()
        var resitivityCheckBox:CheckBox = findViewById<CheckBox>(R.id.resitivity_check)
        resitivityCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                resistivityPreference.setValue(1)
            }
            else {
                resistivityPreference.setValue(0)
            }
        }
    }

}

