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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.browser.customtabs.CustomTabsIntent
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.github.mmin18.widget.RealtimeBlurView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.FavoritePageActivity
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.settings.SubmitActivity
import com.jlindemann.science.activities.tables.NuclideActivity
import com.jlindemann.science.extensions.InfoExtension
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.*
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
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
        Utils.fadeInAnim(findViewById<ScrollView>(R.id.scr_view), 300)

        readJson()
        findViewById<CardView>(R.id.shell).visibility = View.GONE
        findViewById<CardView>(R.id.detail_emission).visibility = View.GONE
        detailViews()
        offlineCheck()
        nextPrev()
        favoriteBarSetup()
        elementAnim(findViewById<FrameLayout>(R.id.overview_inc), findViewById<FrameLayout>(R.id.properties_inc))
        findViewById<ConstraintLayout>(R.id.view).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener { super.onBackPressed() }
        findViewById<FloatingActionButton>(R.id.edit_fav_btn).setOnClickListener {
            val intent = Intent(this, FavoritePageActivity::class.java)
            startActivity(intent)
        }
        findViewById<AppCompatButton>(R.id.i_btn).setOnClickListener {
            val intent = Intent(this, SubmitActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.get_pro_hardness_btn).setOnClickListener {
            val intent = Intent(this, ProActivity::class.java)
            startActivity(intent)
        }
        //Check if PRO version and if make changes:
        val proPref = ProVersion(this)
        var proPrefValue = proPref.getValue()
        if (proPrefValue==100) {
            proChanges()
        }
        else {
            findViewById<LinearLayout>(R.id.more_properties).visibility = View.INVISIBLE
            findViewById<LinearLayout>(R.id.hardness_properties).visibility = View.INVISIBLE

        }

    }

    private fun proChanges() {
        //more properties
        findViewById<FrameLayout>(R.id.pro_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.more_properties).visibility = View.VISIBLE

        //hardness properties
        findViewById<FrameLayout>(R.id.pro_hardness_box).visibility = View.GONE
        findViewById<LinearLayout>(R.id.hardness_properties).visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        if (findViewById<RealtimeBlurView>(R.id.shell_background).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.shell), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.shell_background), 300)
            return
        }
        if (findViewById<CardView>(R.id.detail_emission).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.detail_emission), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.detail_emission_background), 300)
            return
        }
        else { super.onBackPressed() }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.frame).layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.frame).layoutParams = params

            val paramsO = findViewById<Space>(R.id.offline_space).layoutParams as ViewGroup.MarginLayoutParams
            paramsO.topMargin += top
            findViewById<Space>(R.id.offline_space).layoutParams = paramsO

            val params2 = findViewById<FrameLayout>(R.id.common_title_back).layoutParams as ViewGroup.LayoutParams
            params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back).layoutParams = params2
    }

    private fun offlineCheck() {
        val offlinePreferences = offlinePreference(this)
        val offlinePrefValue = offlinePreferences.getValue()

        if (offlinePrefValue == 1) {
            findViewById<FrameLayout>(R.id.frame).visibility = View.GONE
            findViewById<Space>(R.id.offline_space).visibility = View.VISIBLE
            findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
            findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
            findViewById<TextView>(R.id.sp_offline).text = "Go online for emission lines"
        }
        else {
            findViewById<FrameLayout>(R.id.frame).visibility = View.VISIBLE
            findViewById<Space>(R.id.offline_space).visibility = View.GONE
            findViewById<ImageView>(R.id.sp_img).visibility = View.VISIBLE
            findViewById<TextView>(R.id.sp_offline).visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        favoriteBarSetup()
    }

    private fun detailViews() {
        findViewById<CardView>(R.id.electron_view).setOnClickListener {
            Utils.fadeInAnim(findViewById<CardView>(R.id.shell), 300)
            Utils.fadeInAnim(findViewById<RealtimeBlurView>(R.id.shell_background), 300)
        }
        findViewById<FloatingActionButton>(R.id.close_shell_btn).setOnClickListener {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.shell), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.shell_background), 300)
        }
        findViewById<RealtimeBlurView>(R.id.shell_background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.shell), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.shell_background), 300)
        }
        findViewById<ImageView>(R.id.sp_img).setOnClickListener {
            Utils.fadeInAnim(findViewById<CardView>(R.id.detail_emission), 300)
            Utils.fadeInAnim(findViewById<RealtimeBlurView>(R.id.detail_emission_background), 300)
        }
        findViewById<FloatingActionButton>(R.id.close_emission_btn).setOnClickListener {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.detail_emission), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.detail_emission_background), 300)
        }
        findViewById<RealtimeBlurView>(R.id.detail_emission_background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<CardView>(R.id.detail_emission), 300)
            Utils.fadeOutAnim(findViewById<RealtimeBlurView>(R.id.detail_emission_background), 300)
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
        findViewById<FloatingActionButton>(R.id.next_btn).setOnClickListener {
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
        findViewById<FloatingActionButton>(R.id.previous_btn).setOnClickListener {
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