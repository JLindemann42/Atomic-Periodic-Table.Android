package com.jlindemann.science.activities.tables

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.core.text.isDigitsOnly
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ThemePreference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.*

class NuclideActivity : BaseActivity() {
    private val elementLists = ArrayList<Element>()
    var mScale = 1f
    lateinit var mScaleDetector: ScaleGestureDetector
    lateinit var gestureDetector: GestureDetector

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
        setContentView(R.layout.activity_nuclide) //REMEMBER: Never move any function calls above this

        findViewById<ViewStub>(R.id.viewStub).inflate()

        runOnUiThread {
            findViewById<FrameLayout>(R.id.ldn_place).visibility = View.VISIBLE
        }

        val handler = Handler()
        handler.postDelayed({
                addViews(elementLists)
        }, 100)

        gestureDetector = GestureDetector(this, NuclideActivity.GestureListener())
        mScaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = 1 - detector.scaleFactor
                val pScale = mScale
                mScale += scale
                mScale += scale
                if (mScale < 1f)
                    mScale = 1f
                if (mScale > 1f)
                    mScale = 1f
                val scaleAnimation = ScaleAnimation(1f / pScale, 1f / mScale, 1f / pScale, 1f / mScale, detector.focusX, detector.focusY)
                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                scaleAnimation.willChangeBounds()
                scaleAnimation.willChangeTransformationMatrix()
                val layout = findViewById<LinearLayout>(R.id.scrollNuc) as LinearLayout
                layout.startAnimation(scaleAnimation)

                return true
            }
        })

        findViewById<SeekBar>(R.id.seekBarNuc).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, currentValue: Int, p2: Boolean) {
                val scaleAnimation = ScaleAnimation(1f / currentValue, 1f / currentValue, 1f / currentValue, 1f / currentValue)
                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                val layout = findViewById<LinearLayout>(R.id.scrollNuc) as LinearLayout
                layout.startAnimation(scaleAnimation)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        findViewById<FrameLayout>(R.id.view_nuc).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        findViewById<ImageButton>(R.id.nuc_back_btn).setOnClickListener { this.onBackPressed() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        super.dispatchTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return mScaleDetector.onTouchEvent(event)
    }

    private class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { return true }
    }

    fun addViews(list: ArrayList<Element>)  {
            ElementModel.getList(list)
            val dLayout = findViewById<FrameLayout>(R.id.nuc_view)
            val inflate = layoutInflater
            val mLayout: View = inflate.inflate(R.layout.item_nuclide, dLayout, false)
            val param = RelativeLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.item_nuclide),
                resources.getDimensionPixelSize(R.dimen.item_nuclide)
            )
            param.leftMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * 0
            param.topMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * 1
            val s = mLayout.findViewById(R.id.nuclide_element) as TextView
            val t = mLayout.findViewById(R.id.nuclide_number) as TextView
            s.text = "n"
            t.text = "1"
            dLayout.addView(mLayout, param)
            findViewById<FrameLayout>(R.id.ldn_place).visibility = View.GONE

            for (item in list) {
                var jsonString: String? = null
                try {
                    val hyd = item.element
                    val ext = ".json"
                    val elementJson: String? = "$hyd$ext"
                    val inputStream: InputStream = assets.open(elementJson.toString())
                    jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    val jsonObject: JSONObject = jsonArray.getJSONObject(0)

                    for (i in 1..item.isotopes) {
                        val isoN = "iso_N_"
                        val isoZ = "iso_Z_"
                        val isoHalf = "iso_half_"
                        val decayType = "decay_type_"
                        val number = i.toString()
                        val nJson: String = "$isoN$number"
                        val zJson: String = "$isoZ$number"
                        val halfJson: String = "$isoHalf$number"
                        val decayTypeString = "$decayType$number"
                        val n = jsonObject.optString(nJson, "-")
                        val z = jsonObject.optString(zJson, "-")
                        val half = jsonObject.optString(halfJson, "-")

                        val decayTypeResult = jsonObject.optString(decayTypeString, "default")
                        val mainLayout = findViewById<FrameLayout>(R.id.nuc_view)
                        val inflater = layoutInflater
                        val myLayout: View = inflater.inflate(R.layout.item_nuclide, mainLayout, false)
                        val params = RelativeLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.item_nuclide), resources.getDimensionPixelSize(R.dimen.item_nuclide))

                        if (n.isDigitsOnly() && z.isDigitsOnly()) {
                            params.leftMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * (z.toInt())
                            params.topMargin = resources.getDimensionPixelSize(R.dimen.item_nuclide) * (n.toInt())
                            val short = myLayout.findViewById(R.id.nuclide_element) as TextView
                            val top = myLayout.findViewById(R.id.nuclide_number) as TextView
                            val frame = myLayout.findViewById(R.id.item_nuclide_frame) as FrameLayout
                            val decay = myLayout.findViewById(R.id.nuclide_decay) as TextView

                            short.text = item.short
                            top.text = (z.toInt() + n.toInt()).toString()
                            decay.text = decayTypeResult
                            if (decayTypeResult == "stable") {
                                frame.background.setTint(Color.argb(255, 42, 50, 61))
                                short.setTextColor(resources.getColor(R.color.colorLightPrimary))
                                top.setTextColor(resources.getColor(R.color.colorLightPrimary))
                            }
                            if (decayTypeResult == "3p") {
                                frame.background.setTint(Color.argb(255, 137, 0, 7))
                                short.setTextColor(resources.getColor(R.color.colorLightPrimary))
                                top.setTextColor(resources.getColor(R.color.colorLightPrimary))
                            }
                            if (decayTypeResult == "2p") {
                                frame.background.setTint(Color.argb(255, 154, 0, 7))
                                short.setTextColor(resources.getColor(R.color.colorLightPrimary))
                                top.setTextColor(resources.getColor(R.color.colorLightPrimary))
                            }
                            if (decayTypeResult == "p") {
                                frame.background.setTint(Color.argb(255, 211, 47, 47))
                                short.setTextColor(resources.getColor(R.color.colorLightPrimary))
                                top.setTextColor(resources.getColor(R.color.colorLightPrimary))
                            }
                            if (decayTypeResult == "B+") {
                                frame.background.setTint(Color.argb(255, 211, 102, 89))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "2B-") {
                                frame.background.setTint(Color.argb(255, 3, 155, 229))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "B-") {
                                frame.background.setTint(Color.argb(255, 89, 204, 255))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "n") {
                                frame.background.setTint(Color.argb(255, 78, 186, 170))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "2n") {
                                frame.background.setTint(Color.argb(255, 0, 137, 123))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "a") {
                                frame.background.setTint(Color.argb(255, 255, 235, 59))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            if (decayTypeResult == "e- capture") {
                                frame.background.setTint(Color.argb(255, 176, 0, 78))
                                short.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                                top.setTextColor(resources.getColor(R.color.colorDarkPrimary))
                            }
                            mainLayout.addView(myLayout, params)
                        }
                    }
                } catch (e: IOException) {
                }
            }

    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<FrameLayout>(R.id.scrollViewNuc).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val params2 = findViewById<FrameLayout>(R.id.common_title_back_nuc).layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_nuc).layoutParams = params2
    }

}



