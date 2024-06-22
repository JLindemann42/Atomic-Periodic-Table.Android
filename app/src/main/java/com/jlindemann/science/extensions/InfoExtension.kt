package com.jlindemann.science.extensions

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.IsotopesActivityExperimental
import com.jlindemann.science.model.Element
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.Pasteur
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import kotlin.math.pow

abstract class InfoExtension : AppCompatActivity(), View.OnApplyWindowInsetsListener {
    companion object {
        private const val TAG = "BaseActivity"
    }

    private var systemUiConfigured = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onStart() {
        super.onStart()
        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener(this)

        if (!systemUiConfigured) {
            systemUiConfigured = true
        }
    }

    open fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) = Unit
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        Pasteur.info(TAG, "height: ${insets.systemWindowInsetBottom}")
        onApplySystemInsets(insets.systemWindowInsetTop, insets.systemWindowInsetBottom, insets.systemWindowInsetLeft, insets.systemWindowInsetRight)
        return insets.consumeSystemWindowInsets()
    }
    fun readJson() {
        var jsonstring : String? = null
        findViewById<FrameLayout>(R.id.ox_view).refreshDrawableState()

        try {
            //Setup json reader
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
            if (ElementSendAndLoadValue == "hydrogen") {
                findViewById<FloatingActionButton>(R.id.previous_btn).visibility = View.GONE
            }
            else {
                findViewById<FloatingActionButton>(R.id.previous_btn).visibility = View.VISIBLE
            }
            if (ElementSendAndLoadValue == "oganesson") {
                findViewById<FloatingActionButton>(R.id.next_btn).visibility = View.GONE
            }
            else {
                findViewById<FloatingActionButton>(R.id.next_btn).visibility = View.VISIBLE
            }
            val name = ElementSendAndLoadValue
            val ext = ".json"
            val ElementJson: String? = "$name$ext"

            //Read json
            val inputStream: InputStream = assets.open(ElementJson.toString())
            jsonstring = inputStream.bufferedReader().use { it.readText() }

            val jsonArray = JSONArray(jsonstring)
            val jsonObject: JSONObject = jsonArray.getJSONObject(0)

            //optStrings from jsonObject or fallback
            val element= jsonObject.optString("element", "---")
            val description = jsonObject.optString("description", "---")
            val url = jsonObject.optString("link", "---")
            val short = jsonObject.optString("short", "---")
            val elementElectrons = jsonObject.optString("element_electrons", "---")
            val elementShellElectrons = jsonObject.optString("element_shells_electrons", "---")
            val elementYear = jsonObject.optString("element_year", "---")
            val elementDiscoveredBy = jsonObject.optString("element_discovered_name", "---")
            val elementProtons = jsonObject.optString("element_protons", "---")
            val elementNeutronsCommon = jsonObject.optString("element_neutron_common", "---")
            val elementGroup = jsonObject.optString("element_group", "---")
            val elementElectronegativity = jsonObject.optString("element_electronegativty", "---")
            val wikipedia = jsonObject.optString("wikilink", "---")
            val elementBoilingKelvin = jsonObject.optString("element_boiling_kelvin", "---")
            val elementBoilingCelsius = jsonObject.optString("element_boiling_celsius", "---")
            val elementBoilingFahrenheit = jsonObject.optString("element_boiling_fahrenheit", "---")
            val elementMeltingKelvin = jsonObject.optString("element_melting_kelvin", "---")
            val elementMeltingCelsius = jsonObject.optString("element_melting_celsius", "---")
            val elementMeltingFahrenheit = jsonObject.optString("element_melting_fahrenheit", "---")
            val elementAtomicNumber = jsonObject.optString("element_atomic_number", "---")
            val elementAtomicWeight = jsonObject.optString("element_atomicmass", "---")
            val elementDensity = jsonObject.optString("element_density", "---")
            val elementModelUrl = jsonObject.optString("element_model", "---")
            val elementAppearance = jsonObject.optString("element_appearance", "---")
            val elementBlock = jsonObject.optString("element_block", "---")
            val elementCrystalStructure = jsonObject.optString("element_crystal_structure", "---")
            val fusionHeat = jsonObject.optString("element_fusion_heat", "---")
            val specificHeatCapacity = jsonObject.optString("element_specific_heat_capacity", "---")
            val vaporizationHeat = jsonObject.optString("element_vaporization_heat", "---")
            val phaseText = jsonObject.optString("element_phase", "---")

            //atomic view
            val electronConfig = jsonObject.optString("element_electron_config", "---")
            val ionCharge = jsonObject.optString("element_ion_charge", "---")
            val ionizationEnergies = jsonObject.optString("element_ionization_energy", "---")
            val atomicRadiusE = jsonObject.optString("element_atomic_radius_e", "---")
            val atomicRadius = jsonObject.optString("element_atomic_radius", "---")
            val covalentRadius = jsonObject.optString("element_covalent_radius", "---")
            val vanDerWaalsRadius = jsonObject.optString("element_van_der_waals", "---")
            val oxidationNeg1 = jsonObject.optString("oxidation_state_neg", "---")
            val oxidationPos1 = jsonObject.optString("oxidation_state_pos", "---")

            //Electromagnetic Properties
            val electricalType = jsonObject.optString("electrical_type", "---")
            val resistivity = jsonObject.optString("resistivity", "---")
            val rMultiplier = jsonObject.optString("resistivity_mult", "---")
            val magneticType = jsonObject.optString("magnetic_type", "---")
            val superconductingPoint = jsonObject.optString("superconducting_point", "---")

            //Nuclear Properties
            val isRadioactive = jsonObject.optString("radioactive", "---")
            val neutronCrossSection = jsonObject.optString("neutron_cross_sectional", "---")

            //More Properties
            val soundOfSpeedGas = jsonObject.optString("speed_of_sound_gas", "---")
            val soundOfSpeedLiquid = jsonObject.optString("speed_of_sound_liquid", "---")
            val soundOfSpeedSolid = jsonObject.optString("speed_of_sound_solid", "---")
            val youngModulus = jsonObject.optString("young_modulus", "---")
            val shearModulus = jsonObject.optString("shear_modulus", "---")
            val bulkModulus = jsonObject.optString("bulk_modulus", "---")
            val poissonRatio = jsonObject.optString("poisson_ratio", "---")

            if (rMultiplier == "---") {
                findViewById<TextView>(R.id.element_resistivity).text = "---"
            }
            else {
                val input = resistivity.toFloat() * rMultiplier.toFloat()
                val output = input.pow(-1).toString()
                findViewById<TextView>(R.id.element_resistivity).text = output.replace("E", "*10^") + " (S/m)"
            }

            findViewById<TextView>(R.id.description_name).setOnClickListener {
                findViewById<TextView>(R.id.description_name).maxLines = 100
                findViewById<TextView>(R.id.description_name).requestLayout()
                findViewById<TextView>(R.id.dsc_btn).text = "collapse"
            }
            findViewById<TextView>(R.id.dsc_btn).setOnClickListener {
                if (findViewById<TextView>(R.id.dsc_btn).text == "..more") {
                    findViewById<TextView>(R.id.description_name).maxLines = 100
                    findViewById<TextView>(R.id.description_name).requestLayout()
                    findViewById<TextView>(R.id.dsc_btn).text = "collapse"
                }
                else {
                    findViewById<TextView>(R.id.description_name).maxLines = 4
                    findViewById<TextView>(R.id.description_name).requestLayout()
                    findViewById<TextView>(R.id.dsc_btn).text = "..more"
                }
            }

            //set elements
            findViewById<TextView>(R.id.element_title).text = element
            findViewById<TextView>(R.id.description_name).text = description
            findViewById<TextView>(R.id.element_name).text = element
            findViewById<TextView>(R.id.electrons_el).text = elementElectrons
            findViewById<TextView>(R.id.element_year).text = elementYear
            findViewById<TextView>(R.id.element_shells_electrons).text = elementShellElectrons
            findViewById<TextView>(R.id.element_discovered_by).text = elementDiscoveredBy
            findViewById<TextView>(R.id.element_electrons).text = elementElectrons
            findViewById<TextView>(R.id.element_protons).text = elementProtons
            findViewById<TextView>(R.id.element_neutrons_common).text = elementNeutronsCommon
            findViewById<TextView>(R.id.element_group).text = elementGroup
            findViewById<TextView>(R.id.element_boiling_kelvin).text = elementBoilingKelvin
            findViewById<TextView>(R.id.element_boiling_celsius).text = elementBoilingCelsius
            findViewById<TextView>(R.id.element_boiling_fahrenheit).text = elementBoilingFahrenheit
            findViewById<TextView>(R.id.element_electronegativty).text = elementElectronegativity
            findViewById<TextView>(R.id.element_melting_kelvin).text = elementMeltingKelvin
            findViewById<TextView>(R.id.element_melting_celsius).text = elementMeltingCelsius
            findViewById<TextView>(R.id.element_melting_fahrenheit).text = elementMeltingFahrenheit
            findViewById<TextView>(R.id.element_atomic_number).text = elementAtomicNumber
            findViewById<TextView>(R.id.element_atomic_weight).text = elementAtomicWeight
            findViewById<TextView>(R.id.element_density).text = elementDensity
            findViewById<TextView>(R.id.element_block).text = elementBlock
            findViewById<TextView>(R.id.element_appearance).text = elementAppearance

            //Nuclear Properties
            findViewById<TextView>(R.id.radioactive_text).text = isRadioactive
            findViewById<TextView>(R.id.neutron_cross_sectional_text).text = neutronCrossSection
            findViewById<FrameLayout>(R.id.isotopes_frame).setOnClickListener {
                val isoPreference = ElementSendAndLoad(this)
                isoPreference.setValue(element.toLowerCase()) //Send element number
                val isoSend = sendIso(this)
                isoSend.setValue("true") //Set flag for sent
                val intent = Intent(this, IsotopesActivityExperimental::class.java)
                startActivity(intent) //Send intent
            }

            findViewById<TextView>(R.id.phase_text).text = phaseText
            findViewById<TextView>(R.id.fusion_heat_text).text = fusionHeat
            findViewById<TextView>(R.id.specific_heat_text).text = specificHeatCapacity
            findViewById<TextView>(R.id.vaporization_heat_text).text = vaporizationHeat

            findViewById<TextView>(R.id.electron_config_text).text = electronConfig
            findViewById<TextView>(R.id.ion_charge_text).text = ionCharge
            findViewById<TextView>(R.id.ionization_energies_text).text = ionizationEnergies
            findViewById<TextView>(R.id.atomic_radius_text).text = atomicRadius
            findViewById<TextView>(R.id.atomic_radius_e_text).text = atomicRadiusE
            findViewById<TextView>(R.id.covalent_radius_text).text = covalentRadius
            findViewById<TextView>(R.id.van_der_waals_radius_text).text = vanDerWaalsRadius

            //more items
            findViewById<TextView>(R.id.speed_sound_solid_text).text = "Solid: " + soundOfSpeedSolid
            findViewById<TextView>(R.id.speed_sound_gas_text).text = "Gas: " + soundOfSpeedGas
            findViewById<TextView>(R.id.speed_sound_liquid_text).text = "Liquid: " + soundOfSpeedLiquid
            findViewById<TextView>(R.id.bulk_modulus_text).text = "K: " + bulkModulus
            findViewById<TextView>(R.id.young_modulus_text).text = "E: " + youngModulus
            findViewById<TextView>(R.id.shear_modulus_text).text = "G: " + shearModulus

            if (soundOfSpeedSolid == "---") { findViewById<TextView>(R.id.speed_sound_solid_text).visibility = View.GONE } //Check if Solid speed and show if
            else { findViewById<TextView>(R.id.speed_sound_solid_text).visibility = View.VISIBLE}

            if (soundOfSpeedGas == "---") { findViewById<TextView>(R.id.speed_sound_gas_text).visibility = View.GONE } //Check if gas speed and show if
            else { findViewById<TextView>(R.id.speed_sound_gas_text).visibility = View.VISIBLE}

            if (soundOfSpeedLiquid == "---") { findViewById<TextView>(R.id.speed_sound_liquid_text).visibility = View.GONE } //Check if liquid speed and show if
            else { findViewById<TextView>(R.id.speed_sound_liquid_text).visibility = View.VISIBLE}

            //Shell View items
            findViewById<TextView>(R.id.config_data).text = elementShellElectrons
            findViewById<TextView>(R.id.e_config_data).text = electronConfig

            //Electromagnetic Properties Items
            findViewById<TextView>(R.id.element_electrical_type).text = electricalType
            findViewById<TextView>(R.id.element_magnetic_type).text = magneticType
            findViewById<TextView>(R.id.element_superconducting_point).text = superconductingPoint + " (K)"

            if (phaseText.toString() == "Solid") { findViewById<ImageView>(R.id.phase_icon).setImageDrawable(getDrawable(R.drawable.solid)) }
            if (phaseText.toString() == "Gas") { findViewById<ImageView>(R.id.phase_icon).setImageDrawable(getDrawable(R.drawable.gas)) }
            if (phaseText.toString() == "Liquid") { findViewById<ImageView>(R.id.phase_icon).setImageDrawable(getDrawable(R.drawable.liquid)) }

            if (oxidationNeg1.contains(0.toString())) { findViewById<TextView>(R.id.ox0).text = "0"
                findViewById<TextView>(R.id.ox0).background.setTint(getColor(R.color.non_metals)) }
            if (oxidationNeg1.contains(1.toString())) { findViewById<TextView>(R.id.m1ox).text = "-1"
                findViewById<TextView>(R.id.m1ox).background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(2.toString())) { findViewById<TextView>(R.id.m2ox).text = "-2"
                findViewById<TextView>(R.id.m2ox).background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(3.toString())) { findViewById<TextView>(R.id.m3ox).text = "-3"
                findViewById<TextView>(R.id.m3ox).background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(4.toString())) { findViewById<TextView>(R.id.m4ox).text = "-4"
                findViewById<TextView>(R.id.m4ox).background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(5.toString())) { findViewById<TextView>(R.id.m5ox).text = "-5"
                findViewById<TextView>(R.id.m5ox).background.setTint(getColor(R.color.noble_gas)) }

            if (oxidationPos1.contains(1.toString())) { findViewById<TextView>(R.id.p1ox).text = "+1"
                findViewById<TextView>(R.id.p1ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(2.toString())) { findViewById<TextView>(R.id.p2ox).text = "+2"
                findViewById<TextView>(R.id.p2ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(3.toString())) { findViewById<TextView>(R.id.p3ox).text = "+3"
                findViewById<TextView>(R.id.p3ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(4.toString())) { findViewById<TextView>(R.id.p4ox).text = "+4"
                findViewById<TextView>(R.id.p4ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(5.toString())) { findViewById<TextView>(R.id.p5ox).text = "+5"
                findViewById<TextView>(R.id.p5ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(6.toString())) { findViewById<TextView>(R.id.p6ox).text = "+6"
                findViewById<TextView>(R.id.p6ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(7.toString())) { findViewById<TextView>(R.id.p7ox).text = "+7"
                findViewById<TextView>(R.id.p7ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(8.toString())) { findViewById<TextView>(R.id.p8ox).text = "+8"
                findViewById<TextView>(R.id.p8ox).background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(9.toString())) { findViewById<TextView>(R.id.p9ox).text = "+9"
                findViewById<TextView>(R.id.p9ox).background.setTint(getColor(R.color.alkali_metals)) }

            //set element data for favorite bar
            findViewById<TextView>(R.id.molar_mass_f).text = elementAtomicWeight
            findViewById<TextView>(R.id.phase_f).text = phaseText
            findViewById<TextView>(R.id.electronegativity_f).text = elementElectronegativity
            findViewById<TextView>(R.id.density_f).text = elementDensity

            val degreePreference = DegreePreference(this)
            val degreePrefValue = degreePreference.getValue()

            if (degreePrefValue == 0) {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingKelvin
                findViewById<TextView>(R.id.melting_f).text = elementMeltingKelvin
            }
            if (degreePrefValue == 1) {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingCelsius
                findViewById<TextView>(R.id.melting_f).text = elementMeltingCelsius
            }
            if (degreePrefValue == 2) {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingFahrenheit
                findViewById<TextView>(R.id.melting_f).text = elementMeltingFahrenheit
            }

            if (url == "empty") {
                Utils.fadeInAnim(findViewById<TextView>(R.id.no_img), 150)
                findViewById<TextView>(R.id.pro_bar).visibility = View.GONE
            }
            else {
                Utils.fadeInAnim(findViewById<ProgressBar>(R.id.pro_bar), 150)
                findViewById<ProgressBar>(R.id.no_img).visibility = View.GONE
            }

            findViewById<TextView>(R.id.fusion_heat_f).text = fusionHeat
            findViewById<TextView>(R.id.specific_heat_f).text = specificHeatCapacity
            findViewById<TextView>(R.id.vaporization_heat_f).text = vaporizationHeat
            findViewById<TextView>(R.id.radioactive_f).text = isRadioactive
            findViewById<TextView>(R.id.resistivity_f).text = resistivity
            findViewById<TextView>(R.id.a_empirical_f).text = atomicRadiusE
            findViewById<TextView>(R.id.a_calculated_f).text = atomicRadius
            findViewById<TextView>(R.id.covalent_f).text = covalentRadius
            findViewById<TextView>(R.id.van_f).text = vanDerWaalsRadius

            val offlinePreferences = offlinePreference(this)
            val offlinePrefValue = offlinePreferences.getValue()
            if (offlinePrefValue == 0) {
                loadImage(url)
                loadModelView(elementModelUrl)
                loadSp(short)
            }
            wikiListener(wikipedia)
        }
        catch (e: IOException) {
            findViewById<TextView>(R.id.element_title).text = "Not able to load json"
            val stringText = "Couldn't load element:"
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
            val name = ElementSendAndLoadValue

            ToastUtil.showToast(this, "$stringText$name")
        }
    }

    private fun loadImage(url: String?) {
        try { Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.element_image)) }
        catch(e: ConnectException) {
            findViewById<Space>(R.id.offline_div).visibility = View.VISIBLE
            findViewById<FrameLayout>(R.id.frame).visibility = View.GONE
        }
    }

    private fun loadSp(url: String?) {
        val hUrl = "http://www.jlindemann.se/atomic/emission_lines/"
        val ext = ".gif"
        val fURL = hUrl + url + ext
        try {
            Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.sp_img))
            Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.sp_img_detail))
        }

        catch(e: ConnectException) {
            findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
            findViewById<TextView>(R.id.sp_offline).text = "No Data"
            findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
        }
    }

    private fun loadModelView(url: String?) {
        Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.model_view))
        Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.card_model_view))
    }

    fun wikiListener(url: String?) {
        findViewById<ImageButton>(R.id.wikipedia_btn).setOnClickListener {
            val PACKAGE_NAME = "com.android.chrome"
            val customTabBuilder = CustomTabsIntent.Builder()

            customTabBuilder.setToolbarColor(ContextCompat.getColor(this, R.color.colorLightPrimary))
            customTabBuilder.setSecondaryToolbarColor(ContextCompat.getColor(this, R.color.colorLightPrimary))
            customTabBuilder.setShowTitle(true)

            val CustomTab = customTabBuilder.build()
            val intent = CustomTab.intent
            intent.data = Uri.parse(url)

            val packageManager = packageManager
            val resolveInfoList = packageManager.queryIntentActivities(CustomTab.intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (TextUtils.equals(packageName, PACKAGE_NAME))
                    CustomTab.intent.setPackage(PACKAGE_NAME)
            }
            CustomTab.intent.data?.let { it1 -> CustomTab.launchUrl(this, it1) }
        }
    }

    fun favoriteBarSetup() {
        //Favorite Molar
        val molarPreference = FavoriteBarPreferences(this)
        var molarPrefValue = molarPreference.getValue()
        if (molarPrefValue == 1) { findViewById<TextView>(R.id.molar_mass_lay).visibility = View.VISIBLE }
        if (molarPrefValue == 0) { findViewById<TextView>(R.id.molar_mass_lay).visibility = View.GONE }

        //Favorite Phase
        val phasePreferences = FavoritePhase(this)
        var phasePrefValue = phasePreferences.getValue()
        if (phasePrefValue == 1) { findViewById<TextView>(R.id.phase_lay).visibility = View.VISIBLE }
        if (phasePrefValue == 0) { findViewById<TextView>(R.id.phase_lay).visibility = View.GONE }

        //Electronegativity Phase
        val electronegativityPreferences = ElectronegativityPreference(this)
        var electronegativityPrefValue = electronegativityPreferences.getValue()
        if (electronegativityPrefValue == 1) { findViewById<TextView>(R.id.electronegativity_lay).visibility = View.VISIBLE }
        if (electronegativityPrefValue == 0) { findViewById<TextView>(R.id.electronegativity_lay).visibility = View.GONE }

        //Density
        val densityPreference = DensityPreference(this)
        var densityPrefValue = densityPreference.getValue()
        if (densityPrefValue == 1) { findViewById<TextView>(R.id.density_lay).visibility = View.VISIBLE }
        if (densityPrefValue == 0) { findViewById<TextView>(R.id.density_lay).visibility = View.GONE }

        //Boiling
        val boilingPreference = BoilingPreference(this)
        var boilingPrefValue = boilingPreference.getValue()
        if (boilingPrefValue == 1) { findViewById<TextView>(R.id.boiling_lay).visibility = View.VISIBLE }
        if (boilingPrefValue == 0) { findViewById<TextView>(R.id.boiling_lay).visibility = View.GONE }

        //Melting
        val meltingPreference = MeltingPreference(this)
        val meltingPrefValue = meltingPreference.getValue()
        if (meltingPrefValue == 1) { findViewById<TextView>(R.id.melting_lay).visibility = View.VISIBLE }
        if (meltingPrefValue == 0) { findViewById<TextView>(R.id.melting_lay).visibility = View.GONE }

        //Empirical
        val empiricalPreference = AtomicRadiusEmpPreference(this)
        val empiricalPrefValue = empiricalPreference.getValue()
        if (empiricalPrefValue == 1) { findViewById<TextView>(R.id.a_empirical_lay).visibility = View.VISIBLE }
        if (empiricalPrefValue == 0) { findViewById<TextView>(R.id.a_empirical_lay).visibility = View.GONE }

        //Calculated
        val calculatedPreference = AtomicRadiusCalPreference(this)
        val calculatedPrefValue = calculatedPreference.getValue()
        if (calculatedPrefValue == 1) { findViewById<TextView>(R.id.a_calculated_lay).visibility = View.VISIBLE }
        if (calculatedPrefValue == 0) { findViewById<TextView>(R.id.a_calculated_lay).visibility = View.GONE }

        //Covalent
        val covalentPreference = AtomicCovalentPreference(this)
        val covalentPrefValue = covalentPreference.getValue()
        if (covalentPrefValue == 1) { findViewById<TextView>(R.id.covalent_lay).visibility = View.VISIBLE }
        if (covalentPrefValue == 0) { findViewById<TextView>(R.id.covalent_lay).visibility = View.GONE }

        //Van Der Waals
        val vanPreference = AtomicVanPreference(this)
        val vanPrefValue = vanPreference.getValue()
        if (vanPrefValue == 1) { findViewById<TextView>(R.id.van_lay).visibility = View.VISIBLE }
        if (vanPrefValue == 0) { findViewById<TextView>(R.id.van_lay).visibility = View.GONE }

        //Fusion Heat
        val fusionHeatPreference = FusionHeatPreference(this)
        var fusionHeatValue = fusionHeatPreference.getValue()
        if (fusionHeatValue == 1) { findViewById<TextView>(R.id.fusion_heat_lay).visibility = View.VISIBLE }
        if (fusionHeatValue == 0) { findViewById<TextView>(R.id.fusion_heat_lay).visibility = View.GONE }

        //Specific Heat
        val specificHeatPreference = SpecificHeatPreference(this)
        var specificHeatValue = specificHeatPreference.getValue()
        if (specificHeatValue == 1) { findViewById<TextView>(R.id.specific_heat_lay).visibility = View.VISIBLE }
        if (specificHeatValue == 0) { findViewById<TextView>(R.id.specific_heat_lay).visibility = View.GONE }

        //Vaporization Heat
        val vaporizationHeatPreference = VaporizationHeatPreference(this)
        var vaporizationHeatValue = vaporizationHeatPreference.getValue()
        if (vaporizationHeatValue == 1) { findViewById<TextView>(R.id.vaporization_heat_lay).visibility = View.VISIBLE }
        if (vaporizationHeatValue == 0) { findViewById<TextView>(R.id.vaporization_heat_lay).visibility = View.GONE }

        //Radioactive
        val radioactivePreference = RadioactivePreference(this)
        var radioactiveValue = radioactivePreference.getValue()
        if (radioactiveValue == 1) { findViewById<TextView>(R.id.phase_lay).visibility = View.VISIBLE }
        if (radioactiveValue == 0) { findViewById<TextView>(R.id.phase_lay).visibility = View.GONE }
    }
}