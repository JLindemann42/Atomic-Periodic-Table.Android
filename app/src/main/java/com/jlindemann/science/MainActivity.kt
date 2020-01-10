package com.jlindemann.science

import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import butterknife.BindView
import butterknife.OnClick
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.SettingsActivity
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_element_info.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.group_1.*
import kotlinx.android.synthetic.main.group_2.*
import kotlinx.android.synthetic.main.group_3.*
import kotlinx.android.synthetic.main.nav_menu.*
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity(), View.OnClickListener {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themePreference = ThemePreference(this)
        var themePrefValue = themePreference.getValue()

        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
            window.setNavigationBarColor(getColor(R.color.colorLightPrimary))
            window.setStatusBarColor(getColor(R.color.colorLightPrimary))
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
            window.setNavigationBarColor(getColor(R.color.colorDarkPrimary))
            window.setStatusBarColor(getColor(R.color.colorDarkPrimary))
        }

        val ElementSendAndLoadPreference = ElementSendAndLoad(this)
        var ElementSendAndLoadValue = ElementSendAndLoadPreference.getValue()

        setContentView(R.layout.activity_main)

        setOnCLickListenerSetups()
        setupNavListeners()
        detailViewDisabled()
        detailViewEnabled()

        if (Build.VERSION.SDK_INT >= 27) {
            val attribute = window.attributes
            attribute.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

    }

    private fun setOnCLickListenerSetups() {
        hydrogen_btn.setOnClickListener { onClick(hydrogen_btn) }
        lithium_btn.setOnClickListener { onClick(lithium_btn) }
        sodium_btn.setOnClickListener { onClick(sodium_btn) }
        rubidium_btn.setOnClickListener { onClick(rubidium_btn) }
        potassium_btn.setOnClickListener { onClick(potassium_btn) }
        caesium_btn.setOnClickListener { onClick(caesium_btn) }
        francium_btn.setOnClickListener { onClick(francium_btn) }
        beryllium_btn.setOnClickListener { onClick(beryllium_btn) }
        magnesium_btn.setOnClickListener { onClick(magnesium_btn) }
        calcium_btn.setOnClickListener { onClick(calcium_btn) }
        strontium_btn.setOnClickListener { onClick(strontium_btn) }
        barium_btn.setOnClickListener { onClick(barium_btn) }
        radium_btn.setOnClickListener { onClick(radium_btn) }
        scandium_btn.setOnClickListener { onClick(scandium_btn) }
        yttrium_btn.setOnClickListener { onClick(yttrium_btn) }
    }

    private fun setupNavListeners() {
        menu_btn.setOnClickListener() {
            nav_menu_include.visibility = View.VISIBLE
        }

        settings_btn.setOnClickListener() {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun detailViewDisabled() {
        detail_btn_disabled.setOnClickListener {
            hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.non_metals), PorterDuff.Mode.MULTIPLY);

            detail_btn_disabled.visibility = View.GONE
            detail_btn_enabled.visibility = View.VISIBLE
        }
    }

    private fun detailViewEnabled() {
        detail_btn_enabled.setOnClickListener {
            val themePreference = ThemePreference(this)
            var themePrefValue = themePreference.getValue()

            if (themePrefValue == 0) {
                hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.colorLightPrimary), PorterDuff.Mode.MULTIPLY);
            }

            if (themePrefValue == 1) {
                hydrogen_btn.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.colorDarkPrimary), PorterDuff.Mode.MULTIPLY);
            }

            detail_btn_disabled.visibility = View.VISIBLE
            detail_btn_enabled.visibility = View.GONE
        }
    }

    override fun onClick(v: View) {

        when (v.id) {

            R.id.hydrogen_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(1)
                startActivity(intent)
            }
            R.id.lithium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(3)
                startActivity(intent)
            }

            R.id.sodium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(11)
                startActivity(intent)
            }

            R.id.potassium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(19)
                startActivity(intent)
            }

            R.id.rubidium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(37)
                startActivity(intent)
            }

            R.id.caesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(55)
                startActivity(intent)
            }

            R.id.francium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(87)
                startActivity(intent)
            }

            R.id.beryllium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(4)
                startActivity(intent)
            }

            R.id.magnesium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(12)
                startActivity(intent)
            }

            R.id.calcium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(20)
                startActivity(intent)
            }

            R.id.strontium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(38)
                startActivity(intent)
            }

            R.id.barium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(56)
                startActivity(intent)
            }

            R.id.radium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(88)
                startActivity(intent)
            }

            R.id.scandium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(21)
                startActivity(intent)
            }

            R.id.yttrium_btn -> {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend= ElementSendAndLoad(this)
                ElementSend.setValue(39)
                startActivity(intent)
            }

            else -> {
            }
        }
    } //RedirectToElementActivities

    fun showNavMenu() {

    }
}
