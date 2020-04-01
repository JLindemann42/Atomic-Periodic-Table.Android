package com.jlindemann.science.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jlindemann.science.R
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.RenderScriptBlur
import com.jlindemann.science.utils.RenderScriptProvider
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_settings.back_btn
import kotlinx.android.synthetic.main.activity_dictionary.*

class DictionaryActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_dictionary) //Don't move down (Needs to be before we call our functions)

        val renderScriptProvider = RenderScriptProvider(this)

        cosmonaut.clipToOutline = true
        cosmonautShadow.blurScript = RenderScriptBlur(renderScriptProvider)
        cosmonaut2.clipToOutline = true
        cosmonautShadow2.blurScript = RenderScriptBlur(renderScriptProvider)
        //onClickListeners() //Disabled as a result of conflicts between ACTION_DOWN and ScrollView

        back_btn.setOnClickListener {
            this.onBackPressed()
        }

    }
}



