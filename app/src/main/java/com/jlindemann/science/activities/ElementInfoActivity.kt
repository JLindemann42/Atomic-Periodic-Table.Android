package com.jlindemann.science.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.extensions.InfoExtension
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.activity_element_info.back_btn
import kotlinx.android.synthetic.main.activity_element_info.element_title
import kotlinx.android.synthetic.main.d_atomic.*
import kotlinx.android.synthetic.main.d_electromagnetic.*
import kotlinx.android.synthetic.main.d_nuclear.*
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
import kotlin.math.pow

class ElementInfoActivity : InfoExtension() {

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
        nextPrev()
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

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
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

    private fun nextPrev() {
        next_btn.setOnClickListener {
            var jsonString : String? = null
            try {
                val ElementSendAndLoadPreference = ElementSendAndLoad(this)
                val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
                val name = ElementSendAndLoadValue
                val ext = ".json"
                val ElementJson: String? = "$name$ext"
                val inputStream: InputStream = assets.open(ElementJson.toString())
                jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                val currentNumb = jsonObject.optString("element_atomic_number", "---")
                val elements = ArrayList<Element>()
                ElementModel.getList(elements)
                val item = elements[currentNumb.toInt()]
                val elementSendAndLoad = ElementSendAndLoad(this)

                elementSendAndLoad.setValue(item.element)
                readJson()
            }
            catch (e: IOException) {}
        }
        previous_btn.setOnClickListener {
            var jsonString : String? = null
            try {
                val ElementSendAndLoadPreference = ElementSendAndLoad(this)
                val ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()
                val name = ElementSendAndLoadValue
                val ext = ".json"
                val ElementJson: String? = "$name$ext"
                val inputStream: InputStream = assets.open(ElementJson.toString())
                jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                val currentNumb = jsonObject.optString("element_atomic_number", "---")
                val elements = ArrayList<Element>()
                ElementModel.getList(elements)
                val item = elements[currentNumb.toInt()-2]
                val elementSendAndLoad = ElementSendAndLoad(this)

                elementSendAndLoad.setValue(item.element)
                readJson()
            }
            catch (e: IOException) {}
        }
    }
}