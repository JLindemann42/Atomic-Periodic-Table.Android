package com.jlindemann.science.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.jlindemann.science.R
import com.jlindemann.science.R2.id.bottom
import com.jlindemann.science.extensions.getStatusBarHeight
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.Pasteur
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.activity_element_info.back_btn
import kotlinx.android.synthetic.main.activity_element_info.element_title
import kotlinx.android.synthetic.main.activity_favorite_settings_page.*
import kotlinx.android.synthetic.main.atomic_view.*
import kotlinx.android.synthetic.main.favorite_bar.*
import kotlinx.android.synthetic.main.overview_view.*
import kotlinx.android.synthetic.main.overview_view.element_name
import kotlinx.android.synthetic.main.otherphysics.*
import kotlinx.android.synthetic.main.properties_view.*
import kotlinx.android.synthetic.main.shell_view.*
import kotlinx.android.synthetic.main.temperature_view.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import com.jlindemann.science.activities.BaseActivity.*

class ElementInfoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Setup theme
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        Utils.gestureSetup(window)

        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }

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
        onClickShell()
        onClickClose()

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
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = frame.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin += top
        frame.layoutParams = params

        val params2 = common_title_back.layoutParams as ViewGroup.LayoutParams
        params2.height += top
        common_title_back.layoutParams = params2
    }


    fun readJson() {

        var jsonstring : String? = null

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
            val elementElectrons = jsonObject.optString("element_electrons", "---")
            val elementShellElectrons = jsonObject.optString("element_shells_electrons", "---")
            val elementYear = jsonObject.optString("element_year", "---")
            val elementDiscoveredBy = jsonObject.optString("element_discovered_name", "---")
            val elementProtons = jsonObject.optString("element_protons", "---")
            val elementNeutronsCommon = jsonObject.optString("element_neutron_common", "---")
            val elementGroup = jsonObject.optString("element_group", "---")
            val elementElectronegativity = jsonObject.optString("element_electronegativty", "---")
            var wikipedia = jsonObject.optString("wikilink", "---")
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
            val atomicRadius = jsonObject.optString("element_atomic_radius", "---")
            val covalentRadius = jsonObject.optString("element_covalent_radius", "---")
            val vanDerWaalsRadius = jsonObject.optString("element_van_der_waals", "---")

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
            covalent_radius_text.text = covalentRadius
            van_der_waals_radius_text.text = vanDerWaalsRadius

            config_data.text = elementShellElectrons

            if (phaseText.toString() == "Solid") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.solid))
            }
            if (phaseText.toString() == "Gas") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.gas))
            }
            if (phaseText.toString() == "Liquid") {
                phase_icon.setImageDrawable(getDrawable(R.drawable.liquid))
            }

            //set element data for favorite bar
            molar_mass_f.text = elementAtomicWeight
            phase_f.text = phaseText
            electronegativity_f.text = elementElectronegativity
            density_f.text = elementDensity

            val degreePreference = DegreePreference(this)
            var degreePrefValue = degreePreference.getValue()

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

            specific_heat_f.text = specificHeatCapacity

            loadImage(url)
            loadModelView(elementModelUrl)
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

    private fun onClickShell() {
        electron_view.setOnClickListener {
            anim(shell)
        }
    }

    private fun onClickClose() {
        close_shell_btn.setOnClickListener {
            hideAnim(shell)
        }
    }

    private fun anim(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 0.0f
        view.animate().setDuration(300)
        view.animate().alpha(1.0f)
    }

    private fun hideAnim(view: View) {
        view.animate().setDuration(300)
        view.animate().alpha(0.0f)
        val handler = Handler()
        handler.postDelayed({
            view.visibility = View.GONE
        }, 200)

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

        try {
            Picasso.get().load(url.toString()).into(element_image)
        }

        catch(e: ConnectException) {
            offline_div.visibility = View.VISIBLE
            frame.visibility = View.GONE
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

            //Set appearance settings for CustomTab
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