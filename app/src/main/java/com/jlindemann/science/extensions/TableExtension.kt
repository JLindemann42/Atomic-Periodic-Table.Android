package com.jlindemann.science.extensions

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginStart
import com.jlindemann.science.R
import com.jlindemann.science.model.Element
import com.jlindemann.science.preferences.TemperatureUnits
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Pasteur
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

abstract class TableExtension : AppCompatActivity(), View.OnApplyWindowInsetsListener {
    companion object { private const val TAG = "BaseActivity" }

    private var systemUiConfigured = false

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }

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
    private var elementList = ArrayList<Element>()

    private fun closeHover() {
        Utils.fadeOutAnim(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
    }

    fun initName(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            closeHover()
            val extText = "_text"
            val eView = "$name$extText"
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resID = resources.getIdentifier(eView, "id", packageName)
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val text = findViewById<TextView>(resID)
            text.text = item.element.capitalize()
            val btn = findViewById<TextView>(resIDB)
            val themePreference = ThemePreference(this)
            val themePrefValue = themePreference.getValue()

            val params = text.layoutParams as ViewGroup.MarginLayoutParams
            params.leftMargin = 0
            params.rightMargin = 0
            params.bottomMargin = resources.getDimensionPixelSize(R.dimen.groups2b)
            text.layoutParams = params
            text.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            btn.elevation = (resources.getDimension(R.dimen.zero_elevation))
            findViewById<TextView>(R.id.lanthanoids_btn).elevation = (resources.getDimension(R.dimen.zero_elevation))
            findViewById<TextView>(R.id.actinoids_btn).elevation = (resources.getDimension(R.dimen.zero_elevation))


            if (themePrefValue == 100) {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {Configuration.UI_MODE_NIGHT_NO -> {
                        btn.background.setTint(resources.getColor(R.color.element_box_light))
                        findViewById<TextView>(R.id.lanthanoids_btn).background.setTint(resources.getColor(R.color.element_box_light))
                        findViewById<TextView>(R.id.actinoids_btn).background.setTint(resources.getColor(R.color.element_box_light))
                    }
                    Configuration.UI_MODE_NIGHT_YES -> {
                        btn.background.setTint(resources.getColor(R.color.element_box_dark))
                        findViewById<TextView>(R.id.lanthanoids_btn).background.setTint(resources.getColor(R.color.element_box_dark))
                        findViewById<TextView>(R.id.actinoids_btn).background.setTint(resources.getColor(R.color.element_box_dark)) }
                }
            }
            if (themePrefValue == 0) {
                    btn.background.setTint(resources.getColor(R.color.element_box_light))
                    findViewById<TextView>(R.id.lanthanoids_btn).background.setTint(resources.getColor(R.color.element_box_light))
                    findViewById<TextView>(R.id.actinoids_btn).background.setTint(resources.getColor(R.color.element_box_light)) }
            if (themePrefValue == 1) {
                    btn.background.setTint(resources.getColor(R.color.element_box_dark))
                    findViewById<TextView>(R.id.lanthanoids_btn).background.setTint(resources.getColor(R.color.element_box_dark))
                    findViewById<TextView>(R.id.actinoids_btn).background.setTint(resources.getColor(R.color.element_box_dark)) }
        }
    }

    fun initBoiling(list: ArrayList<Element>) {
        val delay = Handler()
        initName(elementList)
        closeHover()
        delay.postDelayed({
            for (item in list) {
                val name = item.element
                val extText = "_text"
                val eView = "$name$extText"
                val iText = findViewById<TextView>(resources.getIdentifier(eView, "id", packageName))
                var jsonString : String? = null
                try {
                    val ext = ".json"
                    val ElementJson: String? = "$name$ext"
                    val inputStream: InputStream = assets.open(ElementJson.toString())
                    jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                    val tempPreference = TemperatureUnits(this)
                    val tempPrefValue = tempPreference.getValue()
                    val elementAtomicWeight = jsonObject.optString("element_boiling_$tempPrefValue", "---")
                    iText.text = elementAtomicWeight
                } catch(e: IOException) { }
            } },10)
    }

    fun initMelting(list: ArrayList<Element>) {
        val delay = Handler()
        initName(elementList)
        closeHover()
        delay.postDelayed({
            for (item in list) {
                val name = item.element
                val extText = "_text"
                val eView = "$name$extText"
                val iText = findViewById<TextView>(resources.getIdentifier(eView, "id", packageName))
                var jsonString : String? = null
                try {
                    val ext = ".json"
                    val ElementJson: String? = "$name$ext"
                    val inputStream: InputStream = assets.open(ElementJson.toString())
                    jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                    val tempPreference = TemperatureUnits(this)
                    val tempPrefValue = tempPreference.getValue()
                    val elementAtomicWeight = jsonObject.optString("element_melting_$tempPrefValue", "---")
                    iText.text = elementAtomicWeight
                } catch(e: IOException) { }
            } },10)
    }

    fun initElectro(list: ArrayList<Element>) {
        val delay = Handler()
        initName(elementList)
        closeHover()
        delay.postDelayed({
            for (item in list) {
                val name = item.element
                val extText = "_text"
                val eView = "$name$extText"
                val extBtn = "_btn"
                val eViewBtn = "$name$extBtn"
                val resID = resources.getIdentifier(eView, "id", packageName)
                val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)
                if (resID == 0) {
                    ToastUtil.showToast(this, "Error on find IdView")
                } else {
                    if (item.electro == 0.0) {
                        val text = findViewById<TextView>(resID)
                        text.text = "---"
                    } else {
                        val text = findViewById<TextView>(resID)
                        text.text = (item.electro).toString()
                    }
                }
                if (resIDB == 0) {
                    ToastUtil.showToast(this, "Error on find IdView")
                } else {
                    if (item.electro == 0.0) {
                        val btn = findViewById<TextView>(resIDB)
                        val themePreference = ThemePreference(this)
                        val themePrefValue = themePreference.getValue()

                    } else {
                        if (item.electro > 1) {
                            val btn = findViewById<TextView>(resIDB)
                            btn.background.setTint(Color.argb(255, 255, 225.div(item.electro).toInt(), 0))
                        } else {
                            val btn = findViewById<TextView>(resIDB)
                            btn.background.setTint(Color.argb(255, 255, 214, 0))
                        }
                    }
                }
            }
        }, 10)
    }

    fun initGroups(list: ArrayList<Element>) {
        val delay = Handler()
        initName(list)
        delay.postDelayed({
            for (item in list) {
                closeHover()
                val name = item.element
                val extBtn = "_btn"
                val extText = "_text"
                val eViewBtn = "$name$extBtn"
                val eText = "$name$extText"
                val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)
                val resID = resources.getIdentifier(eText, "id", packageName)

                val iText = findViewById<TextView>(resID)
                var jsonstring : String? = null
                try {
                    val ext = ".json"
                    val ElementJson: String? = "$name$ext"
                    val inputStream: InputStream = assets.open(ElementJson.toString())
                    jsonstring = inputStream.bufferedReader().use { it.readText() }

                    val jsonArray = JSONArray(jsonstring)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                    val elementGroup = jsonObject.optString("element_group", "---")
                    iText.text = elementGroup
                    val params = iText.layoutParams as ViewGroup.MarginLayoutParams
                    params.leftMargin = resources.getDimensionPixelSize(R.dimen.groups)
                    params.rightMargin = resources.getDimensionPixelSize(R.dimen.groups)
                    params.bottomMargin = resources.getDimensionPixelSize(R.dimen.groupsb)
                    iText.layoutParams = params
                    iText.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    iText.requestLayout()
                }
                catch(e: IOException) { }

                val btn = findViewById<TextView>(resIDB)
                if ((item.number == 3) or (item.number == 11) or (item.number == 19) or (item.number == 37) or (item.number == 55) or (item.number == 87)) {
                    btn.background.setTint(Color.argb(255, 255, 102, 102))
                }
                if ((item.number == 4) or (item.number == 12) or (item.number == 20) or (item.number == 38) or (item.number == 56) or (item.number == 88)) {
                    btn.background.setTint(Color.argb(255, 255, 195, 112))
                }
                if ((item.number in 21..30) or (item.number in 39..48) or (item.number in 72..80) or (item.number in 104..112)) {
                    btn.background.setTint(Color.argb(255, 225, 168, 166))
                }
                if ((item.number == 5) or (item.number == 14) or (item.number in 32..33) or (item.number in 51..52) or (item.number == 85)) {
                    btn.background.setTint(Color.argb(255, 184, 184, 136))
                }
                if ((item.number == 13) or (item.number == 31) or (item.number in 49..50) or (item.number in 81..84) or (item.number in 113..118)) {
                    btn.background.setTint(Color.argb(255, 174, 174, 174))
                }
                if ((item.number == 53) or (item.number in 34..35) or (item.number in 15..17) or (item.number in 6..9) or (item.number == 1)) {
                    btn.background.setTint(Color.argb(255, 129, 199, 132))
                }
                if ((item.number == 2) or (item.number == 10) or (item.number == 18) or (item.number == 36) or (item.number == 54) or (item.number == 86)) {
                    btn.background.setTint(Color.argb(255, 97, 193, 193))
                }
            }
        }, 10)
    }

    //New functions for updating tables. Trying to minimize code
    fun initTableChange(list: ArrayList<Element>, jsonName: String) {
        initName(list)
        closeHover()
        val delay = Handler()
        delay.postDelayed({
            for (item in list) {
                val name = item.element
                val extText = "_text"
                val eView = "$name$extText"
                val resID = resources.getIdentifier(eView, "id", packageName)
                val iText = findViewById<TextView>(resID)

                var jsonstring : String? = null
                try {
                    val ext = ".json"
                    val ElementJson: String? = "$name$ext"
                    val inputStream: InputStream = assets.open(ElementJson.toString())
                    jsonstring = inputStream.bufferedReader().use { it.readText() }

                    val jsonArray = JSONArray(jsonstring)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                    val outputText = jsonObject.optString(jsonName, "---")
                    iText.text = outputText
                }
                catch(e: IOException) { }
            }
        }, 10)
    }

}