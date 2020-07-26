package com.jlindemann.science.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Insets
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColor
import com.jlindemann.science.R
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_dictionary.*
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.activity_element_info.back_btn
import kotlinx.android.synthetic.main.activity_element_info.element_title
import kotlinx.android.synthetic.main.d_atomic.*
import kotlinx.android.synthetic.main.d_overview.*
import kotlinx.android.synthetic.main.d_properties.*
import kotlinx.android.synthetic.main.d_temperatures.*
import kotlinx.android.synthetic.main.d_thermodynamic.*
import kotlinx.android.synthetic.main.detail_emission.*
import kotlinx.android.synthetic.main.favorite_bar.*
import kotlinx.android.synthetic.main.shell_view.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import kotlinx.android.synthetic.main.loading_view.*
import kotlinx.android.synthetic.main.oxidiation_states.*
import kotlinx.android.synthetic.main.shell_view.card_model_view

class ElementInfoActivity : BaseActivity() {

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
        Utils.fadeInAnim(scr_view, 300)
        readJson()

        //Setup depending on PRO version
        val proPreference = ProVersion(this)
        val proPrefValue = proPreference.getValue()

        if (proPrefValue == 1) {
            //Show pro elements here
        }
        if (proPrefValue == 0) {
            //Hide pro elements here
        }
        
        shell.visibility = View.GONE
        detail_emission.visibility = View.GONE
        detailViews()
        offlineCheck()

        favoriteBarSetup()
        elementAnim(overview_inc, properties_inc)

        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        back_btn.setOnClickListener {
            super.onBackPressed()
        }
        edit_fav_btn.setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        i_btn.setOnClickListener {
            val intent = Intent(this, SubmitActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (shell_background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(shell, 300)
            Utils.fadeOutAnim(shell_background, 300)
            return
        }
        if (detail_emission.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(detail_emission, 300)
            Utils.fadeOutAnim(detail_emission_background, 300)
            return
        }
        else { super.onBackPressed() }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
            val params = frame.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            frame.layoutParams = params

            val paramsO = offline_space.layoutParams as ViewGroup.MarginLayoutParams
            paramsO.topMargin += top
            offline_space.layoutParams = paramsO

            val params2 = common_title_back.layoutParams as ViewGroup.LayoutParams
            params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back.layoutParams = params2

    }

    private fun offlineCheck() {
        val offlinePreferences = offlinePreference(this)
        val offlinePrefValue = offlinePreferences.getValue()

        if (offlinePrefValue == 1) {
            frame.visibility = View.GONE
            offline_space.visibility = View.VISIBLE
            sp_img.visibility = View.GONE
            sp_offline.visibility = View.VISIBLE
            sp_offline.text = "Go online for emission lines"
        }
        else {
            frame.visibility = View.VISIBLE
            offline_space.visibility = View.GONE
            sp_img.visibility = View.VISIBLE
            sp_offline.visibility = View.GONE
        }
    }

    fun readJson() {

        var jsonstring : String? = null
        ox_view.refreshDrawableState()

        try {
            //Setup json reader
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
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

            //set elements
            element_title.text = element
            element_name.text = element
            electrons_el.text = elementElectrons
            element_year.text = elementYear
            element_shells_electrons.text = elementShellElectrons
            element_discovered_by.text = elementDiscoveredBy
            element_electrons.text = elementElectrons
            element_protons.text = elementProtons
            element_neutrons_common.text = elementNeutronsCommon
            element_group.text = elementGroup
            element_boiling_kelvin.text = elementBoilingKelvin
            element_boiling_celsius.text = elementBoilingCelsius
            element_boiling_fahrenheit.text = elementBoilingFahrenheit
            element_electronegativty.text = elementElectronegativity
            element_melting_kelvin.text = elementMeltingKelvin
            element_melting_celsius.text = elementMeltingCelsius
            element_melting_fahrenheit.text = elementMeltingFahrenheit
            element_atomic_number.text = elementAtomicNumber
            element_atomic_weight.text = elementAtomicWeight
            element_density.text = elementDensity
            element_block.text = elementBlock
            element_appearance.text = elementAppearance

            phase_text.text = phaseText
            fusion_heat_text.text = fusionHeat
            specific_heat_text.text = specificHeatCapacity
            vaporization_heat_text.text = vaporizationHeat

            electron_config_text.text = electronConfig
            ion_charge_text.text = ionCharge
            ionization_energies_text.text = ionizationEnergies
            atomic_radius_text.text = atomicRadius
            atomic_radius_e_text.text = atomicRadiusE
            covalent_radius_text.text = covalentRadius
            van_der_waals_radius_text.text = vanDerWaalsRadius

            //Shell View items
            config_data.text = elementShellElectrons
            e_config_data.text = electronConfig

            if (phaseText.toString() == "Solid") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.solid))
            }
            if (phaseText.toString() == "Gas") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.gas))
            }
            if (phaseText.toString() == "Liquid") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.liquid))
            }


            if (oxidationNeg1.contains(0.toString())) { ox0.text = "0"
                ox0.background.setTint(getColor(R.color.non_metals)) }
            if (oxidationNeg1.contains(1.toString())) { m1ox.text = "-1"
                m1ox.background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(2.toString())) { m2ox.text = "-2"
                m2ox.background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(3.toString())) { m3ox.text = "-3"
                m3ox.background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(4.toString())) { m4ox.text = "-4"
                m4ox.background.setTint(getColor(R.color.noble_gas)) }
            if (oxidationNeg1.contains(5.toString())) { m5ox.text = "-5"
                m5ox.background.setTint(getColor(R.color.noble_gas)) }


            if (oxidationPos1.contains(1.toString())) { p1ox.text = "+1"
                p1ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(2.toString())) { p2ox.text = "+2"
                p2ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(3.toString())) { p3ox.text = "+3"
                p3ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(4.toString())) { p4ox.text = "+4"
                p4ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(5.toString())) { p5ox.text = "+5"
                p5ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(6.toString())) { p6ox.text = "+6"
                p6ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(7.toString())) { p7ox.text = "+7"
                p7ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(8.toString())) { p8ox.text = "+8"
                p8ox.background.setTint(getColor(R.color.alkali_metals)) }
            if (oxidationPos1.contains(9.toString())) { p9ox.text = "+9"
                p9ox.background.setTint(getColor(R.color.alkali_metals)) }

            //set element data for favorite bar
            molar_mass_f.text = elementAtomicWeight
            phase_f.text = phaseText
            electronegativity_f.text = elementElectronegativity
            density_f.text = elementDensity

            val degreePreference = DegreePreference(this)
            val degreePrefValue = degreePreference.getValue()

            if (degreePrefValue == 0) {
                boiling_f.text = elementBoilingKelvin
                melting_f.text = elementMeltingKelvin
            }
            if (degreePrefValue == 1) {
                boiling_f.text = elementBoilingCelsius
                melting_f.text = elementMeltingCelsius
            }
            if (degreePrefValue == 2) {
                boiling_f.text = elementBoilingFahrenheit
                melting_f.text = elementMeltingFahrenheit
            }

            if (url == "empty") {
                Utils.fadeInAnim(no_img, 150)
                pro_bar.visibility = View.GONE
            }
            else {
                Utils.fadeInAnim(pro_bar, 150)
                no_img.visibility = View.GONE
            }

            specific_heat_f.text = specificHeatCapacity

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
            element_title.text = "Not able to load json"
            val stringText = "Couldn't load element:"
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
            val name = ElementSendAndLoadValue

            ToastUtil.showToast(this, "$stringText$name")
        }

    }

    fun favoriteBarSetup() {
        //Favorite Molar
        val molarPreference = FavoriteBarPreferences(this)
        var molarPrefValue = molarPreference.getValue()
        if (molarPrefValue == 1) {
            molar_mass_lay.visibility = View.VISIBLE
        }
        if (molarPrefValue == 0) {
            molar_mass_lay.visibility = View.GONE
        }

        //Favorite Phase
        val phasePreferences = FavoritePhase(this)
        var phasePrefValue = phasePreferences.getValue()
        if (phasePrefValue == 1) {
            phase_lay.visibility = View.VISIBLE
        }
        if (phasePrefValue == 0) {
            phase_lay.visibility = View.GONE
        }

        //Electronegativity Phase
        val electronegativityPreferences = ElectronegativityPreference(this)
        var electronegativityPrefValue = electronegativityPreferences.getValue()
        if (electronegativityPrefValue == 1) {
            electronegativity_lay.visibility = View.VISIBLE
        }
        if (electronegativityPrefValue == 0) {
            electronegativity_lay.visibility = View.GONE
        }

        //Density
        val densityPreference = DensityPreference(this)
        var densityPrefValue = densityPreference.getValue()
        if (densityPrefValue == 1) {
            density_lay.visibility = View.VISIBLE
        }
        if (densityPrefValue == 0) {
            density_lay.visibility = View.GONE
        }

        //Boiling
        val boilingPreference = BoilingPreference(this)
        var boilingPrefValue = boilingPreference.getValue()
        if (boilingPrefValue == 1) {
            boiling_lay.visibility = View.VISIBLE
        }
        if (boilingPrefValue == 0) {
            boiling_lay.visibility = View.GONE
        }

        //Melting
        val meltingPreference = MeltingPreference(this)
        var meltingPrefValue = meltingPreference.getValue()
        if (meltingPrefValue == 1) {
            melting_lay.visibility = View.VISIBLE
        }
        if (meltingPrefValue == 0) {
            melting_lay.visibility = View.GONE
        }

        //Fusion Heat
        val fusionHeatPreference = FusionHeatPreference(this)
        var fusionHeatValue = fusionHeatPreference.getValue()
        if (fusionHeatValue == 1) {
            fusion_heat_lay.visibility = View.VISIBLE
        }
        if (fusionHeatValue == 0) {
            fusion_heat_lay.visibility = View.GONE
        }

        //Specific Heat
        val specificHeatPreference = SpecificHeatPreference(this)
        var specificHeatValue = specificHeatPreference.getValue()
        if (specificHeatValue == 1) {
            specific_heat_lay.visibility = View.VISIBLE
        }
        if (specificHeatValue == 0) {
            specific_heat_lay.visibility = View.GONE
        }

        //Vaporization Heat
        val vaporizationHeatPreference = VaporizationHeatPreference(this)
        var vaporizationHeatValue = vaporizationHeatPreference.getValue()
        if (vaporizationHeatValue == 1) {
            vaporization_heat_lay.visibility = View.VISIBLE
        }
        if (vaporizationHeatValue == 0) {
            vaporization_heat_lay.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        favoriteBarSetup()
    }

    private fun detailViews() {
        electron_view.setOnClickListener {
            Utils.fadeInAnim(shell, 300)
            Utils.fadeInAnim(shell_background, 300)
        }
        close_shell_btn.setOnClickListener {
            Utils.fadeOutAnim(shell, 300)
            Utils.fadeOutAnim(shell_background, 300)
        }
        shell_background.setOnClickListener {
            Utils.fadeOutAnim(shell, 300)
            Utils.fadeOutAnim(shell_background, 300)
        }

        sp_img.setOnClickListener {
            Utils.fadeInAnim(detail_emission, 300)
            Utils.fadeInAnim(detail_emission_background, 300)
        }
        close_emission_btn.setOnClickListener {
            Utils.fadeOutAnim(detail_emission, 300)
            Utils.fadeOutAnim(detail_emission_background, 300)
        }
        detail_emission_background.setOnClickListener {
            Utils.fadeOutAnim(detail_emission, 300)
            Utils.fadeOutAnim(detail_emission_background, 300)
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

    private fun loadImage(url: String?) {
        try { Picasso.get().load(url.toString()).into(element_image) }
        catch(e: ConnectException) {
            offline_div.visibility = View.VISIBLE
            frame.visibility = View.GONE
        }
    }

    private fun loadSp(url: String?) {
        val hUrl = "http://www.jlindemann.se/atomic/emission_lines/"
        val ext = ".gif"
        val fURL = hUrl + url + ext
        try {
            Picasso.get().load(fURL).into(sp_img)
            Picasso.get().load(fURL).into(sp_img_detail)
        }

        catch(e: ConnectException) {
            sp_img.visibility = View.GONE
            sp_offline.text = "No Data"
            sp_offline.visibility = View.VISIBLE
        }
    }

    private fun loadModelView(url: String?) {
        Picasso.get().load(url.toString()).into(model_view)
        Picasso.get().load(url.toString()).into(card_model_view)
    }

    fun wikiListener(url: String?) {
        wikipedia_btn.setOnClickListener {
            val PACKAGE_NAME = "com.android.chrome"
            val customTabBuilder = CustomTabsIntent.Builder()

            customTabBuilder.setToolbarColor(ContextCompat.getColor(this@ElementInfoActivity,R.color.colorLightPrimary))
            customTabBuilder.setSecondaryToolbarColor(ContextCompat.getColor(this@ElementInfoActivity,R.color.colorLightPrimary))
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


}