package com.jlindemann.science.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.activity_settings.back_btn
import kotlinx.android.synthetic.main.activity_isotopes.*
import kotlinx.android.synthetic.main.activity_isotopes.element_title
import kotlinx.android.synthetic.main.isotope_panel.*
import kotlinx.android.synthetic.main.overview_view.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

class IsotopesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)

        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }

        setContentView(R.layout.activity_isotopes) //Don't move down (Needs to be before we call our functions)

        sliding_layout_i.setPanelState(PanelState.COLLAPSED)
        Utils.fadeOutAnim(sliding_layout_i, 300)

        setUpSlidingLayout()
        setUpClickEvents()

        back_btn.setOnClickListener { this.onBackPressed() }
    }

    override fun onBackPressed() {
        if (background_i.visibility == View.VISIBLE) {
            sliding_layout_i.setPanelState(PanelState.COLLAPSED)
            Utils.fadeOutAnim(sliding_layout_i, 300)
            Utils.fadeOutAnim(background_i, 100)
            return
        } else {
            super.onBackPressed()
        }
    }

    private fun setUpSlidingLayout() {
        sliding_layout_i.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                //Empty
            }

            override fun onPanelStateChanged(
                panel: View?,
                previousState: PanelState,
                newState: PanelState
            ) {
                if (sliding_layout_i.getPanelState() === PanelState.COLLAPSED) {
                    Utils.fadeOutAnim(background_i, 100)
                    Utils.fadeOutAnim(sliding_layout_i, 300)
                }
            }
        })
    }

    private fun drawCard() {
        var jsonstring : String? = null

        try {
            val ElementSendAndLoadPreference = ElementSendAndLoad(this)
            val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
            val name = ElementSendAndLoadValue
            val ext = ".json"
            val iext = " Isotopes"
            val ElementJson: String? = "$name$ext"
            val inputStream: InputStream = assets.open(ElementJson.toString())
            jsonstring = inputStream.bufferedReader().use { it.readText() }

            val jsonArray = JSONArray(jsonstring)
            val jsonObject: JSONObject = jsonArray.getJSONObject(0)

            val element= jsonObject.optString("element", "---")
            val elementProtons = jsonObject.optString("element_protons", "---")
            val elementNeutronsCommon = jsonObject.optString("element_neutron_common", "---")
            val elementElectrons = jsonObject.optString("element_electrons", "---")

            val element_iso_name_1 = jsonObject.optString("iso_1", "---")
            val element_z_1 = jsonObject.optString("iso_Z_1", "---")
            val element_n_1 = jsonObject.optString("iso_N_1", "---")
            val element_a_1 = jsonObject.optString("iso_A_1", "---")
            val element_half_1 = jsonObject.optString("iso_half_1", "---")
            val element_mass_1 = jsonObject.optString("iso_mass_1", "---")
            val element_iso_name_2 = jsonObject.optString("iso_2", "---")
            val element_z_2 = jsonObject.optString("iso_Z_2", "---")
            val element_n_2 = jsonObject.optString("iso_N_2", "---")
            val element_a_2 = jsonObject.optString("iso_A_2", "---")
            val element_half_2 = jsonObject.optString("iso_half_2", "---")
            val element_mass_2 = jsonObject.optString("iso_mass_2", "---")
            val element_iso_name_3 = jsonObject.optString("iso_3", "---")
            val element_z_3 = jsonObject.optString("iso_Z_3", "---")
            val element_n_3 = jsonObject.optString("iso_N_3", "---")
            val element_a_3 = jsonObject.optString("iso_A_3", "---")
            val element_half_3 = jsonObject.optString("iso_half_3", "---")
            val element_mass_3 = jsonObject.optString("iso_mass_3", "---")
            val element_iso_name_4 = jsonObject.optString("iso_4", "---")
            val element_z_4 = jsonObject.optString("iso_Z_4", "---")
            val element_n_4 = jsonObject.optString("iso_N_4", "---")
            val element_a_4 = jsonObject.optString("iso_A_4", "---")
            val element_half_4 = jsonObject.optString("iso_half_4", "---")
            val element_mass_4 = jsonObject.optString("iso_mass_4", "---")
            val element_iso_name_5 = jsonObject.optString("iso_5", "---")
            val element_z_5 = jsonObject.optString("iso_Z_5", "---")
            val element_n_5 = jsonObject.optString("iso_N_5", "---")
            val element_a_5 = jsonObject.optString("iso_A_5", "---")
            val element_half_5 = jsonObject.optString("iso_half_5", "---")
            val element_mass_5 = jsonObject.optString("iso_mass_5", "---")
            val element_iso_name_6 = jsonObject.optString("iso_6", "---")
            val element_z_6 = jsonObject.optString("iso_Z_6", "---")
            val element_n_6 = jsonObject.optString("iso_N_6", "---")
            val element_a_6 = jsonObject.optString("iso_A_6", "---")
            val element_half_6 = jsonObject.optString("iso_half_6", "---")
            val element_mass_6 = jsonObject.optString("iso_mass_6", "---")

            card_title.text = "$element$iext"
            element_electrons_i.text = elementElectrons
            element_protons_i.text = elementProtons
            element_neutrons_common_i.text = elementNeutronsCommon

            val half = "Halftime: "
            val half1 = "$half$element_half_1"
            val half2 = "$half$element_half_2"
            val half3 = "$half$element_half_3"
            val half4 = "$half$element_half_4"
            val half5 = "$half$element_half_5"
            val half6 = "$half$element_half_6"
            val Mass = "Mass: "
            val u = "u"
            val mass1 = "$Mass$element_mass_1$u"
            val mass2 = "$Mass$element_mass_2$u"
            val mass3 = "$Mass$element_mass_3$u"
            val mass4 = "$Mass$element_mass_4$u"
            val mass5 = "$Mass$element_mass_5$u"
            val mass6 = "$Mass$element_mass_6$u"

            iso_name_1.text = element_iso_name_1
            iso_z_1.text = element_z_1
            iso_n_1.text = element_n_1
            iso_a_1.text = element_a_1
            iso_half_1.text = half1
            iso_massa_1.text = mass1
            iso_name_2.text = element_iso_name_2
            iso_z_2.text = element_z_2
            iso_n_2.text = element_n_2
            iso_a_2.text = element_a_2
            iso_half_2.text = half2
            iso_massa_2.text = mass2
            iso_name_3.text = element_iso_name_3
            iso_z_3.text = element_z_3
            iso_n_3.text = element_n_3
            iso_a_3.text = element_a_3
            iso_half_3.text = half3
            iso_massa_3.text = mass3
            iso_name_4.text = element_iso_name_4
            iso_z_4.text = element_z_4
            iso_n_4.text = element_n_4
            iso_a_4.text = element_a_4
            iso_half_4.text = half4
            iso_massa_4.text = mass4
            iso_name_5.text = element_iso_name_5
            iso_z_5.text = element_z_5
            iso_n_5.text = element_n_5
            iso_a_5.text = element_a_5
            iso_half_5.text = half5
            iso_massa_5.text = mass5
            iso_name_6.text = element_iso_name_6
            iso_z_6.text = element_z_6
            iso_n_6.text = element_n_6
            iso_a_6.text = element_a_6
            iso_half_6.text = half6
            iso_massa_6.text = mass6

            if (element_iso_name_5 == "---") {
                box_5.visibility = View.GONE
            }
            if (element_iso_name_6 == "---") {
                box_6.visibility = View.GONE
            }

        }
        catch (e: IOException) {


            ToastUtil.showToast(this, "Couldn't load Data")
        }

    }

    private fun setUpClickEvents() {
        hyd_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(1)
            drawCard()
        }
        hel_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(2)
            drawCard()
        }
        lit_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(3)
            drawCard()
        }
        ber_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(4)
            drawCard()
        }
        bor_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(5)
            drawCard()
        }
        car_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(6)
            drawCard()
        }
        nit_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(7)
            drawCard()
        }
        oxy_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(8)
            drawCard()
        }
        flu_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(9)
            drawCard()
        }
        neo_iso.setOnClickListener {
            sliding_layout_i.panelState = PanelState.EXPANDED
            Utils.fadeInAnimCard(background_i, 100)
            Utils.fadeInAnim(sliding_layout_i, 100)
            val elementSend = ElementSendAndLoad(this)
            elementSend.setValue(10)
            drawCard()
        }

    }
}



