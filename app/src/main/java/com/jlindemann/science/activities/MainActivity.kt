package com.jlindemann.science.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.DictionaryActivity
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.animations.Anim
import com.jlindemann.science.extensions.TableExtension
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.SearchPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.TabUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import org.deejdev.twowaynestedscrollview.TwoWayNestedScrollView
import java.util.*
import java.util.logging.Handler
import kotlin.collections.ArrayList


class MainActivity : TableExtension(), ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

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
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.element_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val adapter = ElementAdapter(elements, this, this)
        recyclerView.adapter = adapter
        findViewById<EditText>(R.id.edit_element).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), elements, recyclerView) }
        })

        setOnCLickListenerSetups(elements)
        scrollAdapter()
        setupNavListeners()
        onClickNav()
        searchListener()
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).panelState = PanelState.COLLAPSED
        searchFilter(elements, recyclerView)
        mediaListeners()
        hoverListeners(elements)
        initName(elements)
        findViewById<FloatingActionButton>(R.id.more_btn).setOnClickListener { openHover() }
        findViewById<TextView>(R.id.hover_background).setOnClickListener { closeHover() }
        findViewById<Button>(R.id.random_btn).setOnClickListener { getRandomItem() }
        findViewById<ConstraintLayout>(R.id.view_main).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val handler = android.os.Handler()
        handler.postDelayed({
            initName(elements)
        }, 250)

        gestureDetector = GestureDetector(this, GestureListener())
        mScaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = 1 - detector.scaleFactor
                val pScale = mScale
                mScale += scale
                mScale += scale
                if (mScale < 1f)
                    mScale = 1f
                if (mScale > 12.5f)
                    mScale = 12.5f
                val scaleAnimation = ScaleAnimation(
                    1f / pScale,
                    1f / mScale,
                    1f / pScale,
                    1f / mScale,
                    detector.focusX,
                    detector.focusY
                )
                if (mScale > 1f) {
                    findViewById<HorizontalScrollView>(R.id.topBar).visibility = View.GONE
                    findViewById<ScrollView>(R.id.leftBar).visibility = View.GONE
                    findViewById<TextView>(R.id.corner).visibility = View.GONE
                }
                if (mScale == 1f) {
                    findViewById<HorizontalScrollView>(R.id.topBar).visibility = View.VISIBLE
                    findViewById<ScrollView>(R.id.leftBar).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.corner).visibility = View.VISIBLE
                }
                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                val layout = findViewById<LinearLayout>(R.id.scrollLin) as LinearLayout
                layout.startAnimation(scaleAnimation)
                return true
            }
        })

        findViewById<TwoWayNestedScrollView>(R.id.scrollView).getViewTreeObserver()
            .addOnScrollChangedListener(object : OnScrollChangedListener {
                var y = 0f
                override fun onScrollChanged() {
                    if (findViewById<TwoWayNestedScrollView>(R.id.scrollView).getScrollY() > y) {
                        Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.nav_bar_main), 150)
                        Utils.fadeOutAnim(findViewById<FloatingActionButton>(R.id.more_btn), 150)
                    } else {
                        Utils.fadeInAnim(findViewById<FrameLayout>(R.id.nav_bar_main), 150)
                        Utils.fadeInAnim(findViewById<FloatingActionButton>(R.id.more_btn), 150)
                    }
                    y = findViewById<TwoWayNestedScrollView>(R.id.scrollView).getScrollY().toFloat()
                }
            })

        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {}
            override fun onPanelStateChanged(panel: View?, previousState: PanelState, newState: PanelState) {
                if (findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).panelState === PanelState.COLLAPSED) {
                    findViewById<FrameLayout>(R.id.nav_menu_include).visibility = View.GONE
                    Utils.fadeOutAnim(findViewById<TextView>(R.id.nav_background), 100)
                }
            }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        super.dispatchTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    private class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { return true }
    }

    private fun scrollAdapter() {
        findViewById<TwoWayNestedScrollView>(R.id.scrollView).setOnScrollChangeListener { v: TwoWayNestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
            findViewById<ScrollView>(R.id.leftBar).scrollTo(0, scrollY)
            findViewById<HorizontalScrollView>(R.id.topBar).scrollTo(scrollX, 0)
        }
    }

    private fun getRandomItem() {
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)
        val randomNumber = (0..117).random()
        val item = elements[randomNumber]

        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)
        val intent = Intent(this, ElementInfoActivity::class.java)
        startActivity(intent)
    }

    private fun openHover() {
        Utils.fadeInAnimBack(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
    }

    private fun closeHover() {
        Utils.fadeOutAnim(findViewById<TextView>(R.id.hover_background), 200)
        Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 300)
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) {
            if (item.element.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        val searchPreference = SearchPreferences(this)
        val searchPrefValue = searchPreference.getValue()
        if (searchPrefValue == 2) {
            filteredList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element < rhs.element) 1 else 0
            })
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        val handler = android.os.Handler()
        handler.postDelayed({
            if (recyclerView.adapter!!.itemCount == 0) {
                Anim.fadeIn(findViewById<LinearLayout>(R.id.empty_search_box), 300)
            }
            else {
                findViewById<LinearLayout>(R.id.empty_search_box).visibility = View.GONE
            }
        }, 10)
        recyclerView.adapter = ElementAdapter(filteredList, this, this)
    }

    override fun elementClickListener2(item: Element, position: Int) {
        val elementSendAndLoad = ElementSendAndLoad(this)
        elementSendAndLoad.setValue(item.element)

        val intent = Intent(this, ElementInfoActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (findViewById<TextView>(R.id.nav_background).visibility == View.VISIBLE) {
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).setPanelState(PanelState.COLLAPSED)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.nav_background), 150)
            return
        }
        if (findViewById<TextView>(R.id.hover_background).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<TextView>(R.id.hover_background), 150)
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.hover_menu_include), 150)
            return
        }
        if (findViewById<FrameLayout>(R.id.search_menu_include).visibility == View.VISIBLE) {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.nav_bar_main), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.nav_background), 150)
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_menu_include), 150)
            Utils.fadeInAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)
            return
        }
        if (findViewById<FrameLayout>(R.id.search_menu_include).visibility == View.VISIBLE && findViewById<TextView>(R.id.background).visibility == View.VISIBLE) {
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeInAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)
            return
        } else { super.onBackPressed() }
    }

    private fun searchListener() {
        findViewById<FrameLayout>(R.id.search_box).setOnClickListener {
            Utils.fadeInAnim(findViewById<FrameLayout>(R.id.search_menu_include), 300)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.GONE
            Utils.fadeOutAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)

            findViewById<EditText>(R.id.edit_element).requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.ime())
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(findViewById<EditText>(R.id.edit_element), InputMethodManager.SHOW_IMPLICIT)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
        }
        findViewById<FloatingActionButton>(R.id.close_element_search).setOnClickListener {
            Utils.fadeOutAnim(findViewById<FrameLayout>(R.id.search_menu_include), 300)
            Utils.fadeInAnim(findViewById<FloatingActionButton>(R.id.more_btn), 300)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.VISIBLE

            val view = this.currentFocus
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    findViewById<ConstraintLayout>(R.id.view_main).doOnLayout { window.insetsController?.hide(WindowInsets.Type.ime()) }
                } else {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
    }

    private fun mediaListeners() {
        findViewById<FloatingActionButton>(R.id.twitter_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.twitter))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.facebook_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.facebook))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.instagram_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.instagram))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        findViewById<FloatingActionButton>(R.id.homepage_button).setOnClickListener {
            val uri = Uri.parse(getString(R.string.homepage))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    private fun onClickNav() {
        findViewById<FloatingActionButton>(R.id.menu_btn).setOnClickListener {
            findViewById<FrameLayout>(R.id.nav_menu_include).visibility = View.VISIBLE
            findViewById<TextView>(R.id.nav_background).visibility = View.VISIBLE
            Utils.fadeInAnimBack(findViewById<TextView>(R.id.nav_background), 200)
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).panelState = PanelState.EXPANDED
        }
        findViewById<TextView>(R.id.nav_background).setOnClickListener {
            findViewById<FrameLayout>(R.id.search_menu_include).visibility = View.GONE
            findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).setPanelState(PanelState.COLLAPSED)
            findViewById<FrameLayout>(R.id.nav_bar_main).visibility = View.VISIBLE
            Utils.fadeOutAnim(findViewById<TextView>(R.id.nav_background), 100)
        }
        findViewById<TextView>(R.id.solubility_btn).setOnClickListener {
            val intent = Intent(this, TableActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.isotopes_btn).setOnClickListener {
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.dictionary_btn).setOnClickListener {
            val intent = Intent(this, DictionaryActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.blog_btn).setOnClickListener{
            val packageManager = packageManager
            val blogURL = "https://www.jlindemann.se/homepage/blog"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
    }

    private fun hoverListeners(elements: ArrayList<Element>) {
        findViewById<TextView>(R.id.h_name_btn).setOnClickListener { initName(elements) }
        findViewById<TextView>(R.id.h_group_btn).setOnClickListener { initGroups(elements) }
        findViewById<TextView>(R.id.h_electronegativity_btn).setOnClickListener { initElectro(elements) }
        findViewById<TextView>(R.id.atomic_weight_btn).setOnClickListener { initWeight(elements) }
        findViewById<TextView>(R.id.boiling_btn).setOnClickListener { initBoiling(elements) }
        findViewById<TextView>(R.id.melting_point).setOnClickListener { initMelting(elements) }
        findViewById<TextView>(R.id.h_phase_btn).setOnClickListener { initPhase(elements) }
        findViewById<TextView>(R.id.h_year_btn).setOnClickListener { initYear(elements) }
        findViewById<TextView>(R.id.h_fusion_btn).setOnClickListener { initHeat(elements) }
        findViewById<TextView>(R.id.h_specific_btn).setOnClickListener { initSpecific(elements) }
        findViewById<TextView>(R.id.h_vaporizaton_btn).setOnClickListener { initVape(elements) }
    }

    private fun setupNavListeners() {
        findViewById<FloatingActionButton>(R.id.settings_btn).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setOnCLickListenerSetups(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val btn = findViewById<TextView>(resIDB)
            btn.foreground = ContextCompat.getDrawable(this,
                R.drawable.t_ripple
            );
            btn.isClickable = true
            btn.isFocusable = true
            btn.setOnClickListener {
                val intent = Intent(this, ElementInfoActivity::class.java)
                val ElementSend = ElementSendAndLoad(this)
                ElementSend.setValue(item.element)
                startActivity(intent)
            }
        }
    }

    private fun searchFilter(list: ArrayList<Element>, recyclerView: RecyclerView) {
        findViewById<ConstraintLayout>(R.id.filter_box).visibility = View.GONE
        findViewById<TextView>(R.id.background).visibility = View.GONE
        findViewById<FloatingActionButton>(R.id.filter_btn).setOnClickListener {
            Utils.fadeInAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeInAnim(findViewById<TextView>(R.id.background), 150)
        }
        findViewById<TextView>(R.id.background).setOnClickListener {
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
        }
        findViewById<TextView>(R.id.elmt_numb_btn2).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        findViewById<TextView>(R.id.electro_btn).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        findViewById<TextView>(R.id.alphabet_btn).setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(2)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(findViewById<ConstraintLayout>(R.id.filter_box), 150)
            Utils.fadeOutAnim(findViewById<TextView>(R.id.background), 150)
            filtList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element < rhs.element) 1 else 0
            })
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        findViewById<LinearLayout>(R.id.navLin).setPadding(left, 0, right, 0)

        val params = findViewById<FrameLayout>(R.id.common_title_back_main).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<FrameLayout>(R.id.common_title_back_main).layoutParams = params

        val params2 = findViewById<FrameLayout>(R.id.nav_bar_main).layoutParams as ViewGroup.LayoutParams
        params2.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar)
        findViewById<FrameLayout>(R.id.nav_bar_main).layoutParams = params2

        val params3 = findViewById<FloatingActionButton>(R.id.more_btn).layoutParams as ViewGroup.MarginLayoutParams
        params3.bottomMargin = bottom + (resources.getDimensionPixelSize(R.dimen.nav_bar))/2 + (resources.getDimensionPixelSize(R.dimen.title_bar_elevation))
        findViewById<FloatingActionButton>(R.id.more_btn).layoutParams = params3

        val params4 = findViewById<FrameLayout>(R.id.common_title_back_search).layoutParams as ViewGroup.LayoutParams
        params4.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_search).layoutParams = params4

        findViewById<RecyclerView>(R.id.element_recyclerview).setPadding(0, resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top, 0, resources.getDimensionPixelSize(R.dimen.title_bar))

        val navSide = findViewById<FrameLayout>(R.id.nav_content).layoutParams as ViewGroup.MarginLayoutParams
        navSide.rightMargin = right
        navSide.leftMargin = left
        findViewById<FrameLayout>(R.id.nav_content).layoutParams = navSide

        val barSide = findViewById<FrameLayout>(R.id.search_box).layoutParams as ViewGroup.MarginLayoutParams
        barSide.rightMargin = right + resources.getDimensionPixelSize(R.dimen.search_margin_side)
        barSide.leftMargin = left + resources.getDimensionPixelSize(R.dimen.search_margin_side)
        findViewById<FrameLayout>(R.id.search_box).layoutParams = barSide

        val leftScrollBar = findViewById<ScrollView>(R.id.leftBar).layoutParams as ViewGroup.MarginLayoutParams
        leftScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main) + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<ScrollView>(R.id.leftBar).layoutParams = leftScrollBar

        val topScrollBar = findViewById<HorizontalScrollView>(R.id.topBar).layoutParams as ViewGroup.MarginLayoutParams
        topScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<HorizontalScrollView>(R.id.topBar).layoutParams = topScrollBar

        val numb = findViewById<ScrollView>(R.id.leftBar).layoutParams as ViewGroup.LayoutParams
        numb.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<ScrollView>(R.id.leftBar).layoutParams = numb

        val cornerP = findViewById<TextView>(R.id.corner).layoutParams as ViewGroup.LayoutParams
        cornerP.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        findViewById<TextView>(R.id.corner).layoutParams = cornerP

        val cornerM = findViewById<TextView>(R.id.corner).layoutParams as ViewGroup.MarginLayoutParams
        cornerM.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<TextView>(R.id.corner).layoutParams = cornerM

        val params5 = findViewById<ConstraintLayout>(R.id.hover_menu_include).layoutParams as ViewGroup.MarginLayoutParams
        params5.bottomMargin = bottom
        findViewById<ConstraintLayout>(R.id.hover_menu_include).layoutParams = params5

        val params6 = findViewById<TwoWayNestedScrollView>(R.id.scrollView).layoutParams as ViewGroup.MarginLayoutParams
        params6.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        findViewById<TwoWayNestedScrollView>(R.id.scrollView).layoutParams = params6

        val params7 = findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).layoutParams as ViewGroup.LayoutParams
        params7.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_view)
        findViewById<SlidingUpPanelLayout>(R.id.sliding_layout).layoutParams = params7

        val searchEmptyImgPrm = findViewById<LinearLayout>(R.id.empty_search_box).layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        findViewById<LinearLayout>(R.id.empty_search_box).layoutParams = searchEmptyImgPrm
    }
}