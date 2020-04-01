package com.jlindemann.science.activities

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_calculator.*
import kotlinx.android.synthetic.main.activity_settings.back_btn
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream


class CalculatorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)

        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_calculator) //Don't move down (Needs to be before we call our functions)

        back_btn.setOnClickListener {
            this.onBackPressed()
        }

        initUi()

    }

    private fun initUi() {
        edit_element_1.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                onClickSearch()
                return@setOnKeyListener true
            }
            false
        }
    }

    fun onClickSearch() {

        val mArray = resources.getStringArray(R.array.calcArray)
        val text = edit_element_1.text.toString()

        for (i in 0 until 2) {
            var jsonstring : String? = null
            if (text == mArray.get(i).toString()) {

                if (text == "H") {
                    val ext = ".json"
                    val ElementJson: String? = "1$ext"

                    val inputStream: InputStream = assets.open(ElementJson.toString())
                    jsonstring = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonstring)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)
                    val elementAtomicWeight1 = jsonObject.optString("element_atomicmass", "---")

                    val final = elementAtomicWeight1.toInt()*(edit_number_1.text.toString().toInt())

                    Toast.makeText(this, final, Toast.LENGTH_SHORT).show()
                }

            }
        }
    }
}



