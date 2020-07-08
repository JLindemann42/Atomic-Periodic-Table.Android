package com.jlindemann.science

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Insets
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.activities.*
import com.jlindemann.science.adapter.ElementAdapter
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.preferences.SearchPreferences
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.Utils
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.filter_view.*
import kotlinx.android.synthetic.main.hover_menu.*
import kotlinx.android.synthetic.main.nav_menu_view.*
import kotlinx.android.synthetic.main.search_layout.*
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity(), ElementAdapter.OnElementClickListener2 {
    private var elementList = ArrayList<Element>()
    var mAdapter = ElementAdapter(elementList, this, this)

    var mScale = 1f
    lateinit var mScaleDetector: ScaleGestureDetector
    lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> {
                    setTheme(R.style.AppTheme)
                }
                Configuration.UI_MODE_NIGHT_YES -> {
                    setTheme(R.style.AppThemeDark)
                }
            }
        }
        if (themePrefValue == 0) {
            setTheme(R.style.AppTheme)
        }
        if (themePrefValue == 1) {
            setTheme(R.style.AppThemeDark)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.element_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val elements = ArrayList<Element>()
        ElementModel.getList(elements)

        val adapter = ElementAdapter(elements, this, this)
        recyclerView.adapter = adapter
        edit_element.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {
            }
            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ) {
            }
            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), elements, recyclerView)
            }
        })

        setOnCLickListenerSetups(elements)
        setupNavListeners()
        detailViewDisabled(elements)
        detailViewEnabled(elements)
        onClickNav()
        searchListener()
        sliding_layout.setPanelState(PanelState.COLLAPSED)
        searchFilter(elements, recyclerView)
        electroView(elements)
        nameView(elements)
        mediaListeners()

        more_btn.setOnClickListener { openHover() }
        hover_background.setOnClickListener { closeHover() }
        random_btn.setOnClickListener { getRandomItem() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            view_main.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        sliding_layout.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                //Empty
            }

            override fun onPanelStateChanged(
                panel: View?,
                previousState: PanelState,
                newState: PanelState
            ) {
                if (sliding_layout.getPanelState() === PanelState.COLLAPSED) {
                    nav_menu_include.visibility = View.GONE
                    Utils.fadeOutAnim(nav_background, 100)
                }
            }
        })
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

    override fun onApplySystemInsets(top: Int, bottom: Int) {

            val params = common_title_back_main.layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_main.layoutParams = params

            val params2 = nav_bar_main.layoutParams as ViewGroup.LayoutParams
            params2.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar)
            nav_bar_main.layoutParams = params2

            val params3 = more_btn.layoutParams as ViewGroup.MarginLayoutParams
            params3.bottomMargin = bottom + (resources.getDimensionPixelSize(R.dimen.nav_bar))/2
            more_btn.layoutParams = params3

            val params4 = common_title_back_search.layoutParams as ViewGroup.LayoutParams
            params4.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            common_title_back_search.layoutParams = params4

            val params5 = hover_menu_include.layoutParams as ViewGroup.MarginLayoutParams
            params5.bottomMargin = bottom + resources.getDimensionPixelSize(R.dimen.nav_bar)
            hover_menu_include.layoutParams = params5

            val params6 = scrollView.layoutParams as ViewGroup.MarginLayoutParams
            params6.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            scrollView.layoutParams = params6

            val params7 = sliding_layout.layoutParams as ViewGroup.LayoutParams
            params7.height = bottom + resources.getDimensionPixelSize(R.dimen.nav_view)
            sliding_layout.layoutParams = params7

    }

    private fun openHover() {
        Utils.fadeInAnim(hover_background, 150)
        Utils.fadeInAnim(hover_menu_include, 150)
    }

    private fun closeHover() {
        Utils.fadeOutAnim(hover_background, 150)
        Utils.fadeOutAnim(hover_menu_include, 150)
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
        elmt_numb_btn.setOnClickListener {
            val searchPreference = SearchPreferences(this)
            searchPreference.setValue(0)

            val filtList: ArrayList<Element> = ArrayList()
            for (item in list) {
                filtList.add(item)
            }
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
            mAdapter.filterList(filtList)
            mAdapter.notifyDataSetChanged()
            recyclerView.adapter = ElementAdapter(filtList, this, this)
        }
    }

    private fun filter(text: String, list: ArrayList<Element>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Element> = ArrayList()
        for (item in list) {
            if (item.element.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
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
            return
        }
        if (search_menu_include.visibility == View.VISIBLE && background.visibility == View.VISIBLE) {
            Utils.fadeOutAnim(background, 150)
            Utils.fadeOutAnim(filter_box, 150)
            return
        } else {
            super.onBackPressed()
        }
    }

    private fun searchListener() {
        search_box.setOnClickListener {
            Utils.fadeInAnim(search_menu_include, 300)
            nav_bar_main.visibility = View.GONE

            edit_element.requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view_main.doOnLayout {
                    window.insetsController?.show(WindowInsets.Type.ime())
                }
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
            nav_bar_main.visibility = View.VISIBLE

            val view = this.currentFocus
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view_main.doOnLayout {
                        window.insetsController?.hide(WindowInsets.Type.ime())
                    }
                }
                else {
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
            Utils.fadeInAnim(nav_background, 200)
            sliding_layout.setPanelState(PanelState.EXPANDED)
        }
        nav_background.setOnClickListener {
            search_menu_include.visibility = View.GONE
            sliding_layout.setPanelState(PanelState.COLLAPSED)
            nav_bar_main.visibility = View.VISIBLE
            Utils.fadeOutAnim(nav_background, 100)
        }
        solubility_btn.setOnClickListener {
            val intent = Intent(this, SolubilityActivity::class.java)
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
    }

    private fun setupNavListeners() {
        settings_btn.setOnClickListener() {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun electroView(list: ArrayList<Element>) {
        electron_btn.setOnClickListener {
            closeHover()
            initElectro(list)

            Utils.fadeOutAnim(electron_btn_frame, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(electron_btn_frame_hide, 150)
            }, 151)
        }
    }

    private fun nameView(list: ArrayList<Element>) {
        electron_btn_hide.setOnClickListener {
            closeHover()
            initName(list)

            Utils.fadeOutAnim(electron_btn_frame_hide, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(electron_btn_frame, 150)
            }, 151)
        }
    }

    private fun initElectro(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extText = "_text"
            val eView = "$name$extText"
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resID = resources.getIdentifier(eView, "id", packageName)
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            if (resID == 0) {
                ToastUtil.showToast(this, "Error (electro)")
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
                ToastUtil.showToast(this, "Error (electro back)")
            } else {
                if (item.electro == 0.0) {
                    val btn = findViewById<Button>(resIDB)
                    val themePreference = ThemePreference(this)
                    val themePrefValue = themePreference.getValue()

                    if (themePrefValue == 100) {
                        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                            Configuration.UI_MODE_NIGHT_NO -> {
                                btn.background.setTint(Color.argb(255, 254, 254, 254))
                            }
                            Configuration.UI_MODE_NIGHT_YES -> {
                                btn.background.setTint(Color.argb(255, 18, 18, 18))
                            }
                        }
                    }
                    if (themePrefValue == 0) {
                        btn.background.setTint(Color.argb(255, 254, 254, 254))
                    }
                    if (themePrefValue == 1) {
                        btn.background.setTint(Color.argb(255, 18, 18, 18))
                    }
                } else {
                    if (item.electro > 1) {
                        val btn = findViewById<Button>(resIDB)
                        btn.background.setTint(
                            Color.argb(
                                255,
                                255,
                                225.div(item.electro).toInt(),
                                0
                            )
                        )
                    } else {
                        val btn = findViewById<Button>(resIDB)
                        btn.background.setTint(Color.argb(255, 255, 214, 0))
                    }
                }
            }
        }
    }

    private fun initName(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extText = "_text"
            val eView = "$name$extText"
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resID = resources.getIdentifier(eView, "id", packageName)
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val text = findViewById<TextView>(resID)
            text.text = (item.element)
            val btn = findViewById<Button>(resIDB)
            val themePreference = ThemePreference(this)
            val themePrefValue = themePreference.getValue()

            if (themePrefValue == 100) {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_NO -> {
                        btn.background.setTint(Color.argb(255, 254, 254, 254))
                    }
                    Configuration.UI_MODE_NIGHT_YES -> {
                        btn.background.setTint(Color.argb(255, 18, 18, 18))
                    }
                }
            }
            if (themePrefValue == 0) {
                btn.background.setTint(Color.argb(255, 254, 254, 254))
            }
            if (themePrefValue == 1) {
                btn.background.setTint(Color.argb(255, 18, 18, 18))
            }
        }
    }

    private fun detailViewDisabled(list: ArrayList<Element>) {
        detail_btn.setOnClickListener {
            closeHover()
            for (item in list) {
                val name = item.element
                val extBtn = "_btn"
                val eViewBtn = "$name$extBtn"
                val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

                val btn = findViewById<Button>(resIDB)
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

                Utils.fadeOutAnim(detail_btn_frame, 150)
                val delay = Handler()
                delay.postDelayed({
                    Utils.fadeInAnim(detail_btn_frame_close, 150)
                }, 151)
            }
        }
    }

    private fun detailViewEnabled(list: ArrayList<Element>) {
        detail_btn_close.setOnClickListener {
            closeHover()
            initName(list)

            Utils.fadeOutAnim(detail_btn_frame_close, 150)
            val delay = Handler()
            delay.postDelayed({
                Utils.fadeInAnim(detail_btn_frame, 150)
            }, 151)
        }
    }

    private fun setOnCLickListenerSetups(list: ArrayList<Element>) {
        for (item in list) {
            val name = item.element
            val extBtn = "_btn"
            val eViewBtn = "$name$extBtn"
            val resIDB = resources.getIdentifier(eViewBtn, "id", packageName)

            val btn = findViewById<Button>(resIDB)
            btn.foreground = ContextCompat.getDrawable(this, R.drawable.c_ripple);
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
}