package com.jlindemann.science.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.AnalyticsHelper
import com.jlindemann.science.utils.ElementDataLoader
import com.jlindemann.science.utils.Utils
import com.otaliastudios.zoom.ZoomLayout
import java.io.IOException

class HomeFragment : BaseFragment() {

    private var elementList = ArrayList<Element>()
    private var isScrollViewRaised = false
    private val scrollHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ElementModel.getList(elementList, requireContext())
        
        setupPeriodicTable(view)
        scrollAdapter(view)
        initName(view, elementList)
        view.findViewById<FloatingActionButton>(R.id.filterFab).setOnClickListener { (activity as MainActivity).openHover() }

        // Initial name refresh
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) initName(view, elementList)
        }, 250)
    }

    private fun setupPeriodicTable(view: View) {
        for (item in elementList) {
            val resID = resources.getIdentifier("${item.elementKey}_btn", "id", requireContext().packageName)
            view.findViewById<View>(resID)?.apply {
                foreground = ContextCompat.getDrawable(requireContext(), R.drawable.t_ripple)
                setOnClickListener {
                    ElementSendAndLoad(requireContext()).setValue(item.elementKey)
                    startActivity(Intent(requireContext(), ElementInfoActivity::class.java))
                }
            }
        }
    }

    private fun scrollAdapter(view: View) {
        val zoomLay = view.findViewById<ZoomLayout>(R.id.scrollView)
        val yScroll = view.findViewById<ScrollView>(R.id.leftBar)
        val xScroll = view.findViewById<HorizontalScrollView>(R.id.topBar)
        val raisePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()

        val runnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                
                if (zoomLay.zoom < 1) {
                    yScroll.visibility = View.INVISIBLE
                    xScroll.visibility = View.INVISIBLE
                    if (!isScrollViewRaised) {
                        val params = zoomLay.layoutParams as ViewGroup.MarginLayoutParams
                        params.topMargin = (params.topMargin - raisePx).coerceAtLeast(0)
                        zoomLay.layoutParams = params
                        isScrollViewRaised = true
                    }
                } else {
                    yScroll.visibility = View.VISIBLE
                    xScroll.visibility = View.VISIBLE
                    if (isScrollViewRaised) {
                        val params = zoomLay.layoutParams as ViewGroup.MarginLayoutParams
                        params.topMargin = params.topMargin + raisePx
                        zoomLay.layoutParams = params
                        isScrollViewRaised = false
                    }
                }
                yScroll.scrollTo(0, -zoomLay.panY.toInt())
                xScroll.scrollTo(-zoomLay.panX.toInt(), 0)
                scrollHandler.postDelayed(this, 16)
            }
        }
        scrollHandler.post(runnable)
    }

    fun initName(view: View, list: ArrayList<Element>) {
        val themePrefValue = ThemePreference(requireContext()).getValue()
        
        for (item in list) {
            val name = item.elementKey
            val textResId = resources.getIdentifier("${name}_text", "id", requireContext().packageName)
            val btnResId = resources.getIdentifier("${name}_btn", "id", requireContext().packageName)

            val textView = view.findViewById<TextView>(textResId) ?: continue
            val btnView = view.findViewById<TextView>(btnResId) ?: continue
            
            val jsonObject = ElementDataLoader.loadElementData(requireContext(), name)
            textView.text = jsonObject?.optString("element", "---") ?: "---"

            if (themePrefValue == 100) {
                if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    btnView.background.setTint(ContextCompat.getColor(requireContext(), R.color.element_box_dark))
                } else {
                    btnView.background.setTint(ContextCompat.getColor(requireContext(), R.color.element_box_light))
                }
            } else if (themePrefValue == 1) {
                btnView.background.setTint(ContextCompat.getColor(requireContext(), R.color.element_box_dark))
            } else {
                btnView.background.setTint(ContextCompat.getColor(requireContext(), R.color.element_box_light))
            }
        }
    }
    
    fun initTableChange(view: View, list: ArrayList<Element>, jsonName: String) {
        for (item in list) {
            val name = item.elementKey
            val resID = resources.getIdentifier("${name}_text", "id", requireContext().packageName)
            val iText = view.findViewById<TextView>(resID) ?: continue
            try {
                val jsonObject = ElementDataLoader.loadElementData(requireContext(), name)
                iText.text = jsonObject?.optString(jsonName, "---") ?: "---"
            } catch (e: Exception) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollHandler.removeCallbacksAndMessages(null)
    }
}
