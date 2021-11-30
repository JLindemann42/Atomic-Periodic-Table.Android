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
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.R2
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
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.filter_view.*
import kotlinx.android.synthetic.main.hover_menu.*
import kotlinx.android.synthetic.main.nav_menu_view.*
import kotlinx.android.synthetic.main.search_layout.*
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
        edit_element.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) { filter(s.toString(), elements, recyclerView) }
        })

        setOnCLickListenerSetups(elements)
        scrollAdapter()
        setupNavListeners()
        onClickNav()
        searchListener()
        sliding_layout.panelState = PanelState.COLLAPSED
        searchFilter(elements, recyclerView)
        mediaListeners()
        hoverListeners(elements)
        initName(elements)
        more_btn.setOnClickListener { openHover() }
        hover_background.setOnClickListener { closeHover() }
        random_btn.setOnClickListener { getRandomItem() }
        view_main.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

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
                if (mScale > 1f)
                    mScale = 1f
                val scaleAnimation = ScaleAnimation(
                    1f / pScale,
                    1f / mScale,
                    1f / pScale,
                    1f / mScale,
                    detector.focusX,
                    detector.focusY
                )
                if (mScale > 1f) {
                    topBar.visibility = View.GONE
                    leftBar.visibility = View.GONE
                    corner.visibility = View.GONE
                }
                if (mScale == 1f) {
                    topBar.visibility = View.VISIBLE
                    leftBar.visibility = View.VISIBLE
                    corner.visibility = View.VISIBLE
                }
                scaleAnimation.duration = 0
                scaleAnimation.fillAfter = true
                val layout = scrollLin as LinearLayout
                layout.startAnimation(scaleAnimation)
                return true
            }
        })

        scrollView.getViewTreeObserver()
            .addOnScrollChangedListener(object : OnScrollChangedListener {
                var y = 0f
                override fun onScrollChanged() {
                    if (scrollView.getScrollY() > y) {
                        Utils.fadeOutAnim(nav_bar_main, 150)
                        Utils.fadeOutAnim(more_btn, 150)
                    } else {
                        Utils.fadeInAnim(nav_bar_main, 150)
                        Utils.fadeInAnim(more_btn, 150)
                    }
                    y = scrollView.getScrollY().toFloat()
                }
            })

        sliding_layout.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {}
            override fun onPanelStateChanged(panel: View?, previousState: PanelState, newState: PanelState) {
                if (sliding_layout.panelState === PanelState.COLLAPSED) {
                    nav_menu_include.visibility = View.GONE
                    Utils.fadeOutAnim(nav_background, 100)
                }
            }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
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
        scrollView.setOnScrollChangeListener { v: TwoWayNestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
            leftBar.scrollTo(0, scrollY)
            topBar.scrollTo(scrollX, 0)
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
        Utils.fadeInAnimBack(hover_background, 200)
        Utils.fadeInAnim(hover_menu_include, 300)
    }

    private fun closeHover() {
        Utils.fadeOutAnim(hover_background, 200)
        Utils.fadeOutAnim(hover_menu_include, 300)
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
                Anim.fadeIn(empty_search_box, 300)
            }
            else {
                empty_search_box.visibility = View.GONE
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
        if (nav_background.visibility == View.VISIBLE) {
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            Utils.fadeOutAnim(nav_background, 150)
            return
        }
        if (hover_background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(hover_background, 150)
            Utils.fadeOutAnim(hover_menu_include, 150)
            return
        }
        if (search_menu_include.visibility == View.VISIBLE) {
            Utils.fadeInAnim(nav_bar_main, 150)
            Utils.fadeOutAnim(nav_background, 150)
            Utils.fadeOutAnim(search_menu_include, 150)
            Utils.fadeInAnim(more_btn, 300)
            return
        }
        if (search_menu_include.visibility == View.VISIBLE && background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(background, 150)
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeInAnim(more_btn, 300)
            return
        } else {
            super.onBackPressed()
        }
    }

    private fun searchListener() {
        search_box.setOnClickListener {
            Utils.fadeInAnim(search_menu_include, 300)
            nav_bar_main.visibility = View.GONE
            Utils.fadeOutAnim(more_btn, 300)

            edit_element.requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.ime())
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(edit_element, InputMethodManager.SHOW_IMPLICIT)
            }
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
        }
        close_element_search.setOnClickListener {
            Utils.fadeOutAnim(search_menu_include, 300)
            Utils.fadeInAnim(more_btn, 300)
            nav_bar_main.visibility = View.VISIBLE

            val view = this.currentFocus
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view_main.doOnLayout { window.insetsController?.hide(WindowInsets.Type.ime()) }
                } else {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
    }

    private fun mediaListeners() {
        twitter_button.setOnClickListener {
            val uri = Uri.parse(getString(R.string.twitter))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        facebook_button.setOnClickListener {
            val uri = Uri.parse(getString(R.string.facebook))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        instagram_button.setOnClickListener {
            val uri = Uri.parse(getString(R.string.instagram))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        homepage_button.setOnClickListener {
            val uri = Uri.parse(getString(R.string.homepage))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    private fun onClickNav() {
        menu_btn.setOnClickListener {
            nav_menu_include.visibility = View.VISIBLE
            nav_background.visibility = View.VISIBLE
            Utils.fadeInAnimBack(nav_background, 200)
            sliding_layout.panelState = PanelState.EXPANDED
        }
        nav_background.setOnClickListener {
            search_menu_include.visibility = View.GONE
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            nav_bar_main.visibility = View.VISIBLE
            Utils.fadeOutAnim(nav_background, 100)
        }
        solubility_btn.setOnClickListener {
            val intent = Intent(this, TableActivity::class.java)
            startActivity(intent)
        }
        isotopes_btn.setOnClickListener {
            val intent = Intent(this, IsotopesActivityExperimental::class.java)
            startActivity(intent)
        }
        dictionary_btn.setOnClickListener {
            val intent = Intent(this, DictionaryActivity::class.java)
            startActivity(intent)
        }
        blog_btn.setOnClickListener{
            val packageManager = packageManager
            val blogURL = "https://www.jlindemann.se/homepage/blog"
            TabUtil.openCustomTab(blogURL, packageManager, this)
        }
    }

    private fun hoverListeners(elements: ArrayList<Element>) {
        h_name_btn.setOnClickListener { initName(elements) }
        h_group_btn.setOnClickListener { initGroups(elements) }
        h_electronegativity_btn.setOnClickListener { initElectro(elements) }
        atomic_weight_btn.setOnClickListener { initWeight(elements) }
        boiling_btn.setOnClickListener { initBoiling(elements) }
        melting_point.setOnClickListener { initMelting(elements) }
        h_phase_btn.setOnClickListener { initPhase(elements) }
        h_year_btn.setOnClickListener { initYear(elements) }
        h_fusion_btn.setOnClickListener { initHeat(elements) }
        h_specific_btn.setOnClickListener { initSpecific(elements) }
        h_vaporizaton_btn.setOnClickListener { initVape(elements) }
    }

    private fun setupNavListeners() {
        settings_btn.setOnClickListener {
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
        filter_box.visibility = View.GONE
        background.visibility = View.GONE

        filter_btn.setOnClickListener {
            Utils.fadeInAnim(filter_box, 150)
            Utils.fadeInAnim(background, 150)
        }
        background.setOnClickListener {
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
        }
        elmt_numb_btn2.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        electro_btn.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(1)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
        alphabet_btn.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(2)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
            Utils.fadeOutAnim(filter_box, 150)
            Utils.fadeOutAnim(background, 150)
            filtList.sortWith(Comparator { lhs, rhs ->
                if (lhs.element < rhs.element) -1 else if (lhs.element < rhs.element) 1 else 0
            })
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        navLin.setPadding(left, 0, right, 0)

        val params = common_title_back_main.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        common_title_back_main.layoutParams = params

        val params2 = nav_bar_main.layoutParams as ViewGroup.LayoutParams
        params2.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar)
        nav_bar_main.layoutParams = params2

        val params3 = more_btn.layoutParams as ViewGroup.MarginLayoutParams
        params3.bottomMargin = bottom + (resources.getDimensionPixelSize(R.dimen.nav_bar))/2 + (resources.getDimensionPixelSize(R.dimen.title_bar_elevation))
        more_btn.layoutParams = params3

        val params4 = common_title_back_search.layoutParams as ViewGroup.LayoutParams
        params4.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_search.layoutParams = params4

        element_recyclerview.setPadding(
            0,
            resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.margin_space) + top,
            0,
            resources.getDimensionPixelSize(R.dimen.title_bar))

        val navSide = nav_content.layoutParams as ViewGroup.MarginLayoutParams
        navSide.rightMargin = right
        navSide.leftMargin = left
        nav_content.layoutParams = navSide

        val barSide = search_box.layoutParams as ViewGroup.MarginLayoutParams
        barSide.rightMargin = right + resources.getDimensionPixelSize(R.dimen.search_margin_side)
        barSide.leftMargin = left + resources.getDimensionPixelSize(R.dimen.search_margin_side)
        search_box.layoutParams = barSide

        val leftScrollBar = leftBar.layoutParams as ViewGroup.MarginLayoutParams
        leftScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main) + resources.getDimensionPixelSize(R.dimen.left_bar)
        leftBar.layoutParams = leftScrollBar

        val topScrollBar = topBar.layoutParams as ViewGroup.MarginLayoutParams
        topScrollBar.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        topBar.layoutParams = topScrollBar

        val numb = leftBar.layoutParams as ViewGroup.LayoutParams
        numb.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        leftBar.layoutParams = numb

        val cornerP = corner.layoutParams as ViewGroup.LayoutParams
        cornerP.width = left + resources.getDimensionPixelSize(R.dimen.left_bar)
        corner.layoutParams = cornerP

        val cornerM = corner.layoutParams as ViewGroup.MarginLayoutParams
        cornerM.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        corner.layoutParams = cornerM

        val params5 = hover_menu_include.layoutParams as ViewGroup.MarginLayoutParams
        params5.bottomMargin = bottom
        hover_menu_include.layoutParams = params5

        val params6 = scrollView.layoutParams as ViewGroup.MarginLayoutParams
        params6.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar_main)
        scrollView.layoutParams = params6

        val params7 = sliding_layout.layoutParams as ViewGroup.LayoutParams
        params7.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_view)
        sliding_layout.layoutParams = params7

        val searchEmptyImgPrm = empty_search_box.layoutParams as ViewGroup.MarginLayoutParams
        searchEmptyImgPrm.topMargin = top + (resources.getDimensionPixelSize(R.dimen.title_bar))
        empty_search_box.layoutParams = searchEmptyImgPrm
    }
}