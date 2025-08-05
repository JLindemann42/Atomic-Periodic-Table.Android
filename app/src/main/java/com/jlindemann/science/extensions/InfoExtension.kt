package com.jlindemann.science.extensions

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.IsotopesActivityExperimental
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.Pasteur
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException

/**
 * Base Activity for element info pages. Handles element data loading, note-taking, achievements, and most UI logic.
 */
abstract class InfoExtension : AppCompatActivity(), View.OnApplyWindowInsetsListener {
    companion object {
        private const val TAG = "BaseActivity"
    }

    private var systemUiConfigured = false
    private val mainScope = MainScope()
    private var notesTextWatcher: TextWatcher? = null // Track watcher for note editing

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

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

    /**
     * Hook for subclasses to handle system window insets.
     */
    open fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) = Unit
    /**
     * Handles the application of window insets for immersive UI.
     */
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        Pasteur.info(TAG, "height: ${insets.systemWindowInsetBottom}")
        onApplySystemInsets(
            insets.systemWindowInsetTop,
            insets.systemWindowInsetBottom,
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetRight
        )
        return insets.consumeSystemWindowInsets()
    }

    /**
     * Reads element JSON data, updates UI, and handles navigation button visibility.
     */
    fun readJson() {
        findViewById<FrameLayout>(R.id.ox_view).refreshDrawableState()
        updateAchievementProgress(1)
        updateStats()

        mainScope.launch {
            try {
                val (elementKey, jsonObject) = withContext(Dispatchers.IO) {
                    val pref = ElementSendAndLoad(this@InfoExtension)
                    val value = pref.getValue()
                    val jsonFile = "$value.json"
                    val inputStream: InputStream = assets.open(jsonFile)
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val jsonObject = jsonArray.getJSONObject(0)
                    Pair(value, jsonObject)
                }

                // Previous/next button visibility
                findViewById<FloatingActionButton>(R.id.previous_btn).visibility =
                    if (elementKey == "hydrogen") View.GONE else View.VISIBLE
                findViewById<FloatingActionButton>(R.id.next_btn).visibility =
                    if (elementKey == "oganesson") View.GONE else View.VISIBLE

                updateElementUI(jsonObject)
            } catch (e: IOException) {
                findViewById<TextView>(R.id.element_title).text = "Not able to load json"
                val stringText = "Couldn't load element:"
                val prefValue = ElementSendAndLoad(this@InfoExtension).getValue()
                ToastUtil.showToast(this@InfoExtension, "$stringText$prefValue")
            }
        }
    }

    /**
     * Updates the UI with element data from the given JSON object.
     */
    private fun updateElementUI(jsonObject: JSONObject) {
        val elementCode = jsonObject.optString("element_code", "---")
        val element = jsonObject.optString("element", "---")
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
        val fusionHeat = jsonObject.optString("element_fusion_heat", "---")
        val specificHeatCapacity = jsonObject.optString("element_specific_heat_capacity", "---")
        val vaporizationHeat = jsonObject.optString("element_vaporization_heat", "---")
        val phaseText = jsonObject.optString("element_phase", "---")
        val electronConfig = jsonObject.optString("element_electron_config", "---")
        val ionCharge = jsonObject.optString("element_ion_charge", "---")
        val ionizationEnergies = jsonObject.optString("element_ionization_energy", "---")
        val atomicRadiusE = jsonObject.optString("element_atomic_radius_e", "---")
        val atomicRadius = jsonObject.optString("element_atomic_radius", "---")
        val covalentRadius = jsonObject.optString("element_covalent_radius", "---")
        val vanDerWaalsRadius = jsonObject.optString("element_van_der_waals", "---")
        val oxidationNeg1 = jsonObject.optString("oxidation_state_neg", "---")
        val oxidationPos1 = jsonObject.optString("oxidation_state_pos", "---")
        val electricalType = jsonObject.optString("electrical_type", "---")
        val resistivity = jsonObject.optString("resistivity", "---")
        val rMultiplier = jsonObject.optString("resistivity_mult", "---")
        val magneticType = jsonObject.optString("magnetic_type", "---")
        val superconductingPoint = jsonObject.optString("superconducting_point", "---")
        val isRadioactive = jsonObject.optString("radioactive", "---")
        val neutronCrossSection = jsonObject.optString("neutron_cross_sectional", "---")
        val mohsHardness = jsonObject.optString("mohs_hardness", "---")
        val vickersHardness = jsonObject.optString("vickers_hardness", "---")
        val brinellHardness = jsonObject.optString("brinell_hardness", "---")
        val soundOfSpeedGas = jsonObject.optString("speed_of_sound_gas", "---")
        val soundOfSpeedLiquid = jsonObject.optString("speed_of_sound_liquid", "---")
        val soundOfSpeedSolid = jsonObject.optString("speed_of_sound_solid", "---")
        val youngModulus = jsonObject.optString("young_modulus", "---")
        val shearModulus = jsonObject.optString("shear_modulus", "---")
        val bulkModulus = jsonObject.optString("bulk_modulus", "---")
        val poissonRatio = jsonObject.optString("poisson_ratio", "---")
        val abundanceEarthCrust = jsonObject.optString("earth_crust", "N/A") + " mg/kg (ppm)"
        val abundanceEarthSoil = jsonObject.optString("earth_soils", "N/A") + " mg/kg (ppm)"
        val abundanceUrbanSoil = jsonObject.optString("urban_soils", "N/A") + " mg/kg (ppm)"
        val abundanceCrustalRocks = jsonObject.optString("crustal_rocks", "N/A") + " mg/kg (ppm)"
        val abundanceSeaWater = jsonObject.optString("sea_water", "N/A") + " μg/l"
        val abundanceSun = jsonObject.optString("sun", "N/A") + " (atoms per 10^6 atoms of silicon)"
        val abundanceSolarSystem = jsonObject.optString("solar_system", "N/A") + " (atoms per 10^6 atoms of silicon)"
        val fireHazard = jsonObject.optString("flammability", "---")
        val healthHazard = jsonObject.optString("health", "---")
        val reactivityHazard = jsonObject.optString("instability", "---")
        val specificHazard = jsonObject.optString("special", "---")
        val casNumber = jsonObject.optString("cas_number", "---")
        val egNumber = jsonObject.optString("eg_number", "---")

        findViewById<TextView>(R.id.element_resistivity).text = resistivity

        findViewById<TextView>(R.id.description_name).setOnClickListener {
            it as TextView
            it.maxLines = 100
            it.requestLayout()
            findViewById<TextView>(R.id.dsc_btn).text = "collapse"
        }
        findViewById<TextView>(R.id.dsc_btn).setOnClickListener {
            val desc = findViewById<TextView>(R.id.description_name)
            val btn = findViewById<TextView>(R.id.dsc_btn)
            if (btn.text == "..more") {
                desc.maxLines = 100
                desc.requestLayout()
                btn.text = "collapse"
            } else {
                desc.maxLines = 4
                desc.requestLayout()
                btn.text = "..more"
            }
        }

        // Set elements in UI
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
        findViewById<TextView>(R.id.element_density).text = formatSuperscript(elementDensity)
        findViewById<TextView>(R.id.element_block).text = elementBlock
        findViewById<TextView>(R.id.element_appearance).text = elementAppearance

        // Notes setup
        val eText = findViewById<EditText>(R.id.notes_edit_text)
        val notesPref = NotesPreference(this)
        val notesPrefValue = notesPref.getValue()
        val str = notesPrefValue
        if (!str.contains("<$elementCode>")) {
            val newString = str + "<$elementCode>Take notes for the element:</$elementCode>"
            notesPref.setValue(newString)
            handleNotes(elementCode, eText)
        } else {
            handleNotes(elementCode, eText)
        }

        // Nuclear Properties
        findViewById<TextView>(R.id.radioactive_text).text = isRadioactive
        findViewById<TextView>(R.id.neutron_cross_sectional_text).text = neutronCrossSection
        findViewById<FrameLayout>(R.id.isotopes_frame).setOnClickListener {
            val isoPreference = ElementSendAndLoad(this)
            isoPreference.setValue(element.lowercase())
            val isoSend = sendIso(this)
            isoSend.setValue("true")
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.isotope_btn).setOnClickListener {
            val isoPreference = ElementSendAndLoad(this)
            isoPreference.setValue(element.lowercase())
            val isoSend = sendIso(this)
            isoSend.setValue("true")
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.phase_text).text = phaseText
        findViewById<TextView>(R.id.fusion_heat_text).text = fusionHeat
        findViewById<TextView>(R.id.specific_heat_text).text = specificHeatCapacity
        findViewById<TextView>(R.id.vaporization_heat_text).text = vaporizationHeat

        findViewById<TextView>(R.id.electron_config_text).text = formatSuperscript(electronConfig)
        findViewById<TextView>(R.id.ion_charge_text).text = formatSuperscript(ionCharge)
        findViewById<TextView>(R.id.ionization_energies_text).text = ionizationEnergies
        findViewById<TextView>(R.id.atomic_radius_text).text = atomicRadius
        findViewById<TextView>(R.id.atomic_radius_e_text).text = atomicRadiusE
        findViewById<TextView>(R.id.covalent_radius_text).text = covalentRadius
        findViewById<TextView>(R.id.van_der_waals_radius_text).text = vanDerWaalsRadius

        // Speed of sound and hardness
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        if (proPrefValue == 100) {
            findViewById<TextView>(R.id.speed_sound_solid_text).text = "Solid: $soundOfSpeedSolid"
            findViewById<TextView>(R.id.speed_sound_gas_text).text = "Gas: $soundOfSpeedGas"
            findViewById<TextView>(R.id.speed_sound_liquid_text).text = "Liquid: $soundOfSpeedLiquid"
            findViewById<TextView>(R.id.poisson_text).text = poissonRatio
            findViewById<TextView>(R.id.bulk_modulus_text).text = "K: $bulkModulus"
            findViewById<TextView>(R.id.young_modulus_text).text = "E: $youngModulus"
            findViewById<TextView>(R.id.shear_modulus_text).text = "G: $shearModulus"
            findViewById<TextView>(R.id.mohs_hardness_text).text = mohsHardness
            findViewById<TextView>(R.id.vickers_hardness_text).text = vickersHardness
            findViewById<TextView>(R.id.brinell_hardness_text).text = brinellHardness
        }
        findViewById<TextView>(R.id.speed_sound_solid_text).visibility =
            if (soundOfSpeedSolid == "---") View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.speed_sound_gas_text).visibility =
            if (soundOfSpeedGas == "---") View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.speed_sound_liquid_text).visibility =
            if (soundOfSpeedLiquid == "---") View.GONE else View.VISIBLE

        // Shell View items
        findViewById<TextView>(R.id.config_data).text = elementShellElectrons
        findViewById<TextView>(R.id.e_config_data).text = formatSuperscript(electronConfig)

        // Electromagnetic Properties
        findViewById<TextView>(R.id.element_electrical_type).text = electricalType
        findViewById<TextView>(R.id.element_magnetic_type).text = magneticType
        findViewById<TextView>(R.id.element_superconducting_point).text = "$superconductingPoint (K)"

        val phaseIconView = findViewById<ImageView>(R.id.phase_icon)
        when (phaseText) {
            "Solid" -> phaseIconView.setImageDrawable(getDrawable(R.drawable.solid))
            "Gas" -> phaseIconView.setImageDrawable(getDrawable(R.drawable.gas))
            "Liquid" -> phaseIconView.setImageDrawable(getDrawable(R.drawable.liquid))
        }

        // Oxidation states
        setOxidationStates(oxidationNeg1, oxidationPos1)

        // Favorite bar
        findViewById<TextView>(R.id.molar_mass_f).text = elementAtomicWeight
        findViewById<TextView>(R.id.phase_f).text = phaseText
        findViewById<TextView>(R.id.electronegativity_f).text = elementElectronegativity
        findViewById<TextView>(R.id.density_f).text = formatSuperscript(elementDensity)

        val degreePreference = DegreePreference(this)
        when (degreePreference.getValue()) {
            0 -> {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingKelvin
                findViewById<TextView>(R.id.melting_f).text = elementMeltingKelvin
            }
            1 -> {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingCelsius
                findViewById<TextView>(R.id.melting_f).text = elementMeltingCelsius
            }
            2 -> {
                findViewById<TextView>(R.id.boiling_f).text = elementBoilingFahrenheit
                findViewById<TextView>(R.id.melting_f).text = elementMeltingFahrenheit
            }
        }
        if (url == "empty") {
            Utils.fadeInAnim(findViewById<TextView>(R.id.no_img), 150)
            findViewById<ProgressBar>(R.id.pro_bar).visibility = View.GONE
        } else {
            Utils.fadeInAnim(findViewById<ProgressBar>(R.id.pro_bar), 150)
            findViewById<AppCompatTextView>(R.id.no_img).visibility = View.GONE
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

        // Abundance
        findViewById<TextView>(R.id.abundance_earth_crust_text).text = formatSuperscript(abundanceEarthCrust)
        findViewById<TextView>(R.id.abundance_earth_soil_text).text = formatSuperscript(abundanceEarthSoil)
        findViewById<TextView>(R.id.abundance_urban_soil_text).text = formatSuperscript(abundanceUrbanSoil)
        findViewById<TextView>(R.id.abundance_crustal_rocks_text).text = formatSuperscript(abundanceCrustalRocks)
        findViewById<TextView>(R.id.abundance_sea_water_text).text = formatSuperscript(abundanceSeaWater)
        findViewById<TextView>(R.id.abundance_sun_text).text = formatSuperscript(abundanceSun)
        findViewById<TextView>(R.id.abundance_solar_system_text).text = formatSuperscript(abundanceSolarSystem)

        // Grid Parameters:
        val crystalStructure = jsonObject.optString("crystal_structure", "null")
        val crystalView = findViewById<CrystalStructureView>(R.id.crystal_view)
        findViewById<TextView>(R.id.crystal_structure_text).text = crystalStructure
        if (crystalStructure == "null") {
            crystalView.visibility = View.GONE
        }
        crystalView.crystalSystem = crystalStructure // set type of crystal for 3D View

        // Lattice constants parsing and formatting
        val latticeConstants = jsonObject.optJSONObject("lattice_constants")
        val latticeString = buildString {
            latticeConstants?.let {
                val a = it.optString("a", "")
                val b = it.optString("b", "")
                val c = it.optString("c", "")
                if (a.isNotEmpty()) append("a: $a ")
                if (b.isNotEmpty()) append("b: $b ")
                if (c.isNotEmpty()) append("c: $c ")
            }
        }.trim()
        findViewById<TextView>(R.id.grid_parameters_text).text = if (latticeString.isNotEmpty()) latticeString else "---"

        // Debye temperature handling
        val debye = jsonObject.opt("debye_temperature")
        val debyeLowTextView = findViewById<TextView>(R.id.debye_low_text)
        val debyeRoomTextView = findViewById<TextView>(R.id.debye_room_text)
        when (debye) {
            is JSONObject -> {
                debyeLowTextView.text = debye.optString("low_temperature_limit", "---")
                debyeRoomTextView.text = debye.optString("room_temperature", "---")
                if (debyeRoomTextView.text.isBlank()) debyeRoomTextView.visibility = View.GONE else debyeRoomTextView.visibility = View.VISIBLE
            }
            is String -> {
                debyeLowTextView.text = debye
                debyeRoomTextView.text = "---"
            }
            else -> {
                debyeLowTextView.text = "---"
                debyeRoomTextView.text = "---"
            }
        }
        // Hazards
        setHazards(fireHazard, healthHazard, reactivityHazard, specificHazard, proPrefValue)

        // Others
        findViewById<TextView>(R.id.cas_text).text = casNumber
        findViewById<TextView>(R.id.eg_text).text = egNumber

        // Images and model
        val offlinePreferences = offlinePreference(this)
        if (offlinePreferences.getValue() == 0) {
            loadImage(url)
            loadModelView(elementModelUrl)
            loadSp(short)
        }
        wikiListener(wikipedia)
    }

    /**
     * Updates oxidation state UI elements.
     */
    private fun setOxidationStates(oxidationNeg1: String, oxidationPos1: String) {
        val negIds = listOf(R.id.ox0, R.id.m1ox, R.id.m2ox, R.id.m3ox, R.id.m4ox, R.id.m5ox)
        val posIds = listOf(
            R.id.p1ox, R.id.p2ox, R.id.p3ox, R.id.p4ox, R.id.p5ox, R.id.p6ox, R.id.p7ox, R.id.p8ox, R.id.p9ox
        )
        val negVals = listOf("0", "1", "2", "3", "4", "5")
        val posVals = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        val negColor = getColor(R.color.noble_gas)
        val posColor = getColor(R.color.alkali_metals)
        val zeroColor = getColor(R.color.non_metals)

        negVals.forEachIndexed { idx, value ->
            if (oxidationNeg1.contains(value)) {
                val tv = findViewById<TextView>(negIds[idx])
                tv.text = if (value == "0") "0" else "-$value"
                tv.background.setTint(if (value == "0") zeroColor else negColor)
            }
        }
        posVals.forEachIndexed { idx, value ->
            if (oxidationPos1.contains(value)) {
                val tv = findViewById<TextView>(posIds[idx])
                tv.text = "+$value"
                tv.background.setTint(posColor)
            }
        }
    }

    /**
     * Updates hazard labels and descriptions.
     */
    private fun setHazards(
        fireHazard: String,
        healthHazard: String,
        reactivityHazard: String,
        specificHazard: String,
        proPrefValue: Int
    ) {
        if (proPrefValue == 100) {
            findViewById<TextView>(R.id.fire_hazard_txt).text = fireHazard
            findViewById<TextView>(R.id.reactivity_hazard_txt).text = reactivityHazard
            findViewById<TextView>(R.id.health_hazard_txt).text = healthHazard
            findViewById<TextView>(R.id.fire_hazard_text).text = "---"
            findViewById<TextView>(R.id.health_hazard_text).text = "---"
            findViewById<TextView>(R.id.reactivity_hazard_text).text = "---"
            when (fireHazard) {
                "0" -> findViewById<TextView>(R.id.fire_hazard_text).text = "Will not burn"
                "1" -> findViewById<TextView>(R.id.fire_hazard_text).text = "Above 93.3°C"
                "2" -> findViewById<TextView>(R.id.fire_hazard_text).text = "Below 93.3°C"
                "3" -> findViewById<TextView>(R.id.fire_hazard_text).text = "Below 37.8°C"
                "4" -> findViewById<TextView>(R.id.fire_hazard_text).text = "Below 22.8°C"
            }
            when (healthHazard) {
                "0" -> findViewById<TextView>(R.id.health_hazard_text).text = "No health hazard"
                "1" -> findViewById<TextView>(R.id.health_hazard_text).text = "Minor health hazard"
                "2" -> findViewById<TextView>(R.id.health_hazard_text).text = "Health Hazard"
                "3" -> findViewById<TextView>(R.id.health_hazard_text).text = "Moderate health hazard"
                "4" -> findViewById<TextView>(R.id.health_hazard_text).text = "Major health hazard"
            }
            when (reactivityHazard) {
                "0" -> findViewById<TextView>(R.id.reactivity_hazard_text).text = "Stable"
                "1" -> findViewById<TextView>(R.id.reactivity_hazard_text).text = "Normally stable"
                "2" -> findViewById<TextView>(R.id.reactivity_hazard_text).text =
                    "Undergoes violent chemical change at elevated temperatures and pressures"
                "3" -> findViewById<TextView>(R.id.reactivity_hazard_text).text =
                    "Capable of detonation or explosive decomposition"
                "4" -> findViewById<TextView>(R.id.reactivity_hazard_text).text =
                    "Readily capable of detonation or explosive decomposition"
            }
            when (specificHazard) {
                "Simple asphyxiant" -> {
                    findViewById<TextView>(R.id.specific_hazard_text).text = "Simple asphyxiant"
                    findViewById<TextView>(R.id.specific_hazard_txt).text = "SA"
                }
                "W" -> {
                    findViewById<TextView>(R.id.specific_hazard_text).text = "Reacts with water"
                    findViewById<TextView>(R.id.specific_hazard_txt).text = "W"
                }
                "OX" -> {
                    findViewById<TextView>(R.id.specific_hazard_text).text = "Oxidizer"
                    findViewById<TextView>(R.id.specific_hazard_txt).text = "OX"
                }
                "", "---" -> {
                    findViewById<TextView>(R.id.specific_hazard_text).text = "---"
                    findViewById<TextView>(R.id.specific_hazard_txt).text = "-"
                }
            }
        }
    }


    /**
     * Handles loading and updating notes for the current element.
     *
     * Ensures only one TextWatcher is attached and always operates on the latest notes string.
     */
    private fun handleNotes(elementCode: String, eText: EditText) {
        val notesPref = NotesPreference(this)
        val firstDelim = "<$elementCode>"
        val lastDelim = "</$elementCode>"

        // Remove old watcher before updating text and attaching new one
        notesTextWatcher?.let { eText.removeTextChangedListener(it) }

        val notesPrefValue = notesPref.getValue()
        val p1 = notesPrefValue.indexOf(firstDelim)
        val p2 = notesPrefValue.indexOf(lastDelim, p1)
        val note = if (p1 != -1 && p2 != -1) {
            notesPrefValue.substring(p1 + firstDelim.length, p2)
        } else {
            "Take notes for the element:"
        }

        eText.setText(note)

        notesTextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Use latest value each time
                val currentNotes = notesPref.getValue()
                val start = currentNotes.indexOf(firstDelim)
                val end = currentNotes.indexOf(lastDelim, start)
                if (start != -1 && end != -1) {
                    val newNotes = currentNotes.substring(0, start + firstDelim.length) +
                            (s ?: "") +
                            currentNotes.substring(end)
                    notesPref.setValue(newNotes)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        eText.addTextChangedListener(notesTextWatcher)
    }

    /**
     * Loads the element image from the given URL into the appropriate ImageView.
     */
    private fun loadImage(url: String?) {
        try {
            Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.element_image))
        } catch (e: ConnectException) {
            findViewById<Space>(R.id.offline_div).visibility = View.VISIBLE
            findViewById<FrameLayout>(R.id.frame).visibility = View.GONE
        }
    }

    /**
     * Loads the spectral emission lines image for the element.
     */
    private fun loadSp(url: String?) {
        val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
        val ext = ".gif"
        val fURL = hUrl + url + ext
        try {
            Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.sp_img))
            Picasso.get().load(fURL).into(findViewById<ImageView>(R.id.sp_img_detail))
        } catch (e: ConnectException) {
            findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
            findViewById<TextView>(R.id.sp_offline).text = "No Data"
            findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
        }
    }

    /**
     * Extension function to convert a String to an Editable.
     */
    fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)
    /**
     * Loads the atom model image into the UI.
     */
    private fun loadModelView(url: String?) {
        Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.model_view))
        Picasso.get().load(url.toString()).into(findViewById<ImageView>(R.id.card_model_view))
    }

    /**
     * Sets up Wikipedia button to open a custom tab with the provided URL.
     */
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
            val resolveInfoList = packageManager.queryIntentActivities(
                CustomTab.intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (TextUtils.equals(packageName, PACKAGE_NAME))
                    CustomTab.intent.setPackage(PACKAGE_NAME)
            }
            try {
                CustomTab.intent.data?.let { it1 -> CustomTab.launchUrl(this, it1) }
            } catch (e: IOException) {
                ToastUtil.showToast(this, "Error Code: 11001")
            }
        }
    }

    /**
     * Sets up favorite bar visibility based on user preferences.
     */
    fun favoriteBarSetup() {
        val molarPreference = FavoriteBarPreferences(this)
        val molarPrefValue = molarPreference.getValue()
        findViewById<LinearLayout>(R.id.molar_mass_lay).visibility =
            if (molarPrefValue == 1) View.VISIBLE else View.GONE

        val phasePreferences = FavoritePhase(this)
        val phasePrefValue = phasePreferences.getValue()
        findViewById<LinearLayout>(R.id.phase_lay).visibility =
            if (phasePrefValue == 1) View.VISIBLE else View.GONE

        val electronegativityPreferences = ElectronegativityPreference(this)
        val electronegativityPrefValue = electronegativityPreferences.getValue()
        findViewById<LinearLayout>(R.id.electronegativity_lay).visibility =
            if (electronegativityPrefValue == 1) View.VISIBLE else View.GONE

        val densityPreference = DensityPreference(this)
        val densityPrefValue = densityPreference.getValue()
        findViewById<LinearLayout>(R.id.density_lay).visibility =
            if (densityPrefValue == 1) View.VISIBLE else View.GONE

        val boilingPreference = BoilingPreference(this)
        val boilingPrefValue = boilingPreference.getValue()
        findViewById<LinearLayout>(R.id.boiling_lay).visibility =
            if (boilingPrefValue == 1) View.VISIBLE else View.GONE

        val meltingPreference = MeltingPreference(this)
        val meltingPrefValue = meltingPreference.getValue()
        findViewById<LinearLayout>(R.id.melting_lay).visibility =
            if (meltingPrefValue == 1) View.VISIBLE else View.GONE

        val empiricalPreference = AtomicRadiusEmpPreference(this)
        val empiricalPrefValue = empiricalPreference.getValue()
        findViewById<LinearLayout>(R.id.a_empirical_lay).visibility =
            if (empiricalPrefValue == 1) View.VISIBLE else View.GONE

        val calculatedPreference = AtomicRadiusCalPreference(this)
        val calculatedPrefValue = calculatedPreference.getValue()
        findViewById<LinearLayout>(R.id.a_calculated_lay).visibility =
            if (calculatedPrefValue == 1) View.VISIBLE else View.GONE

        val covalentPreference = AtomicCovalentPreference(this)
        val covalentPrefValue = covalentPreference.getValue()
        findViewById<LinearLayout>(R.id.covalent_lay).visibility =
            if (covalentPrefValue == 1) View.VISIBLE else View.GONE

        val vanPreference = AtomicVanPreference(this)
        val vanPrefValue = vanPreference.getValue()
        findViewById<LinearLayout>(R.id.van_lay).visibility =
            if (vanPrefValue == 1) View.VISIBLE else View.GONE

        val fusionHeatPreference = FusionHeatPreference(this)
        val fusionHeatValue = fusionHeatPreference.getValue()
        findViewById<LinearLayout>(R.id.fusion_heat_lay).visibility =
            if (fusionHeatValue == 1) View.VISIBLE else View.GONE

        val specificHeatPreference = SpecificHeatPreference(this)
        val specificHeatValue = specificHeatPreference.getValue()
        findViewById<LinearLayout>(R.id.specific_heat_lay).visibility =
            if (specificHeatValue == 1) View.VISIBLE else View.GONE

        val vaporizationHeatPreference = VaporizationHeatPreference(this)
        val vaporizationHeatValue = vaporizationHeatPreference.getValue()
        findViewById<LinearLayout>(R.id.vaporization_heat_lay).visibility =
            if (vaporizationHeatValue == 1) View.VISIBLE else View.GONE

        val radioactivePreference = RadioactivePreference(this)
        val radioactiveValue = radioactivePreference.getValue()
        findViewById<LinearLayout>(R.id.phase_lay).visibility =
            if (radioactiveValue == 1) View.VISIBLE else View.GONE
    }

    /**
     * Formats a string with superscript Unicode characters for display.
     */
    private fun formatSuperscript(text: String): String {
        val superscriptMap = mapOf(
            '0' to '\u2070',
            '1' to '\u00B9',
            '2' to '\u00B2',
            '3' to '\u00B3',
            '4' to '\u2074',
            '5' to '\u2075',
            '6' to '\u2076',
            '7' to '\u2077',
            '8' to '\u2078',
            '9' to '\u2079',
            '+' to '\u207A',
            '-' to '\u207B'
        )
        val regex = Regex("""\^(\d?)(\+|\-)?""")
        return regex.replace(text) {
            val numberPart = it.groupValues[1]
            val signPart = it.groupValues[2]
            val superscriptNumber =
                numberPart.map { char -> superscriptMap[char] ?: char }.joinToString("")
            val superscriptSign = superscriptMap[signPart.firstOrNull()] ?: ""
            superscriptNumber + superscriptSign
        }
    }

    /**
     * Increments achievement progress for the user.
     */
    private fun updateAchievementProgress(increment: Int) {
        val achievements = ArrayList<Achievement>()
        AchievementModel.getList(this, achievements)
        val ids = listOf(1, 2, 3, 4)
        ids.forEach { id ->
            achievements.find { it.id == id }?.incrementProgress(this, increment)
        }
    }

    /**
     * Increments stats for elements viewed.
     */
    private fun updateStats() {
        val statistics = java.util.ArrayList<Statistics>()
        StatisticsModel.getList(this, statistics)
        statistics.find { it.id == 1 }?.incrementProgress(this, 1)
    }
}