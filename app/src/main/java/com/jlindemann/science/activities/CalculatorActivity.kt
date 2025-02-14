package com.jlindemann.science.activities

import android.os.Bundle
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream


class CalculatorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_calculator) //Don't move down (Needs to be before we call our functions)

        findViewById<FloatingActionButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }

        val editText = findViewById<EditText>(R.id.weightEditText)

    }

    fun readJson() {
    }


}



