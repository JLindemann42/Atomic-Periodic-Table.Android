package com.jlindemann.science.activities

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.extensions.getStatusBarHeight
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.atomic_view.*
import kotlinx.android.synthetic.main.overview_view.*
import kotlinx.android.synthetic.main.overview_view.element_name
import kotlinx.android.synthetic.main.gridparameters.*
import kotlinx.android.synthetic.main.otherphysics.*
import kotlinx.android.synthetic.main.properties_view.*
import kotlinx.android.synthetic.main.shell_view.*
import kotlinx.android.synthetic.main.temperature_view.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

class ElementInfoActivity : AppCompatActivity() {



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



        elementAnim(overview_inc, properties_inc)

        back_btn.setOnClickListener {
            super.onBackPressed()
        }
    }

    fun layoutParams() {

        val layout: LinearLayout = findViewById(R.id.commom_title_back)
        val layout2: LinearLayout = findViewById(R.id.status_place)

        val params: ViewGroup.LayoutParams = layout.layoutParams
        val params2: ViewGroup.LayoutParams = layout2.layoutParams
        params.height = (80 + getStatusBarHeight())
        params2.height = (getStatusBarHeight())
        layout.layoutParams = params
        layout2.layoutParams = params2


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
            val specificHeatCapacity = jsonObject.optString("element_specific_heat_capacity", "---")

            //atomic view
            val electronConfig = jsonObject.optString("element_electron_config", "---")
            val ionCharge = jsonObject.optString("element_ion_charge", "---")
            val ionizationEnergies = jsonObject.optString("element_ionization_energy", "---")
            val atomicRadius = jsonObject.optString("element_atomic_radius", "---")
            val covalentRadius = jsonObject.optString("element_covalent_radius", "---")
            val vanDerWaalsRadius = jsonObject.optString("element_van_der_waals", "---")



            //set elements
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
            specific_heat_text.text = specificHeatCapacity

            electron_config_text.text = electronConfig
            ion_charge_text.text = ionCharge
            ionization_energies_text.text = ionizationEnergies
            atomic_radius_text.text = atomicRadius
            covalent_radius_text.text = covalentRadius
            van_der_waals_radius_text.text = vanDerWaalsRadius


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
            showToast("$stringText$name")
        }

    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun onClickShell() {
        electron_view.setOnClickListener {
            showAnim(shell)
        }
    }

    private fun onClickClose() {
        close_shell_btn.setOnClickListener {
            hideAnim(shell)
        }
    }

    private fun showAnim(view: View) {
        model_view.setOnClickListener {
            view.visibility = View.VISIBLE
            view.alpha = 0.0f
            view.animate().setDuration(300)
            view.animate().alpha(1.0f)
        }
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
        Picasso.get().load(url.toString()).into(element_image)
    }

    private fun loadModelView(url: String?) {
        Picasso.get().load(url.toString()).into(model_view)
    }

    fun wikiListener(url: String?) { //Wikipedia webView
        wikipedia_btn.setOnClickListener {
            val PACKAGE_NAME = "com.android.chrome"


            val customTabBuilder = CustomTabsIntent.Builder()

            //Set settings for CustomTab
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
            CustomTab.launchUrl(this, CustomTab.intent.data)
        }
    }
}