package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemDragListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tools.*
import com.jlindemann.science.adapter.ToolAdapter
import com.jlindemann.science.model.ToolItem
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.ToolOrderPreference
import com.jlindemann.science.utils.ProPlusTimeUtil

class ToolsActivity : BaseActivity(), ToolAdapter.OnToolItemClickListener {

    private lateinit var adapter: ToolAdapter
    private lateinit var recyclerView: DragDropSwipeRecyclerView
    private lateinit var toolOrderPref: ToolOrderPreference
    private var isReorderMode = false
    private lateinit var reorderBtn: ImageButton
    private var headerView: View? = null
    private var lastTopInset = 0

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
        setContentView(R.layout.activity_tools)

        findViewById<FrameLayout>(R.id.view_tools).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        toolOrderPref = ToolOrderPreference(this)
        setupRecyclerView()
        setupTitleBar()

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }

        reorderBtn = findViewById(R.id.reorder_btn)
        reorderBtn.setOnClickListener {
            toggleReorderMode()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.tools_recycler_view)
        
        val proPlusPref = ProPlusVersion(this)
        val proPlusPrefValue = proPlusPref.getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()
        
        val tools = getToolItems(proPlusPrefValue, isBeforeDeadline)
        val toolsWithHeader = mutableListOf(ToolItem("header", 0, 0))
        toolsWithHeader.addAll(tools)
        
        adapter = ToolAdapter(this, toolsWithHeader, this)
        adapter.setHeaderBindingAction { view ->
            headerView = view
            applyHeaderInsets(view, lastTopInset)
            mostUsedBar(view)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        recyclerView.dragListener = onItemDragListener
        recyclerView.longPressToStartDragging = true 
    }
    
    private val onItemDragListener = object : OnItemDragListener<ToolItem> {
        override fun onItemDragged(previousPosition: Int, newPosition: Int, item: ToolItem) {
        }

        override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: ToolItem) {
            saveToolOrder()
        }
    }

    private fun getToolItems(proPlusPrefValue: Int, isBeforeDeadline: Boolean): List<ToolItem> {
        val defaultTools = mutableListOf(
            ToolItem("fla", R.string.flashcards_title, R.string.flashcards_info, false, true, 0),
            ToolItem("cal", R.string.calculator_title, R.string.calculator_text, false, false, 1),
            ToolItem("uni", R.string.unit_title, R.string.unit_description, false, false, 2),
            ToolItem("gas", R.string.ideal_gas_calculator_title, R.string.ideal_gas_text, proPlusPrefValue != 100 && isBeforeDeadline, false, 3)
        )

        val savedOrder = toolOrderPref.getOrder()
        if (savedOrder.isNotEmpty()) {
            val orderedList = savedOrder.mapNotNull { id ->
                defaultTools.find { it.id == id }
            }.toMutableList()
            
            // Add any new tools that aren't in the saved order
            defaultTools.forEach { tool ->
                if (orderedList.none { it.id == tool.id }) {
                    orderedList.add(tool)
                }
            }
            return orderedList
        }

        return defaultTools
    }

    private fun saveToolOrder() {
        val currentOrder = adapter.dataSet.filter { it.id != "header" }.map { it.id }
        toolOrderPref.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        
        if (isReorderMode) {
            reorderBtn.setImageResource(R.drawable.ic_check_2)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
            reorderBtn.setColorFilter(typedValue.data)
            reorderBtn.alpha = 1.0f
        } else {
            reorderBtn.setImageResource(R.drawable.ic_edit)
            reorderBtn.clearColorFilter()
            reorderBtn.alpha = 1.0f
            saveToolOrder()
        }
    }

    override fun onToolItemClick(item: ToolItem) {
        if (isReorderMode) return

        val proPlusPref = ProPlusVersion(this)
        val proPlusPrefValue = proPlusPref.getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()

        val activityClass = when (item.id) {
            "cal" -> CalculatorActivity::class.java
            "uni" -> UnitConversionActivity::class.java
            "fla" -> FlashCardActivity::class.java
            "gas" -> {
                if (proPlusPrefValue != 100 && isBeforeDeadline) {
                    ProActivity::class.java
                } else {
                    IdealGasCalculatorActivity::class.java
                }
            }
            else -> return
        }

        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    private fun setupTitleBar() {
        findViewById<FrameLayout>(R.id.common_title_tool_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.tools_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
        
        recyclerView.scrollListener = object : OnListScrollListener {
            private var isTitleVisible = false
            private var totalScrolledY = 0

            override fun onListScrollStateChanged(scrollState: OnListScrollListener.ScrollState) {}

            override fun onListScrolled(scrollDirection: OnListScrollListener.ScrollDirection, distance: Int) {
                totalScrolledY += distance
                val threshold = 150

                val titleColorBackground = findViewById<FrameLayout>(R.id.common_title_tool_color)
                val titleText = findViewById<TextView>(R.id.tools_title)
                val titleDownstateText = headerView?.findViewById<TextView>(R.id.tools_title_downstate)
                val titleBackground = findViewById<FrameLayout>(R.id.common_title_back_tab)

                if (totalScrolledY > threshold) {
                    if (!isTitleVisible) {
                        TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                        TitleBarAnimator.animateVisibility(titleText, true)
                        titleDownstateText?.let { TitleBarAnimator.animateVisibility(it, false) }
                        titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                        isTitleVisible = true
                    }
                } else {
                    if (isTitleVisible) {
                        TitleBarAnimator.animateVisibility(titleColorBackground, false)
                        TitleBarAnimator.animateVisibility(titleText, false)
                        titleDownstateText?.let { TitleBarAnimator.animateVisibility(it, true) }
                        titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                        isTitleVisible = false
                    }
                }
            }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        lastTopInset = top
        val params = findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams = params

        headerView?.let {
            applyHeaderInsets(it, top)
        }
    }

    private fun applyHeaderInsets(view: View, top: Int) {
        val titleDownstate = view.findViewById<TextView>(R.id.tools_title_downstate)
        val params = titleDownstate.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        titleDownstate.layoutParams = params
    }

    private fun mostUsedBar(rootView: View) {
        val mostUsedToolPreference = MostUsedToolPreference(this)
        val proPlusPref = ProPlusVersion(this)
        val proPlusPrefValue = proPlusPref.getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()
        
        val regex = Regex("(\\w{3})=(\\d\\.\\d)")
        val matches = regex.findAll(mostUsedToolPreference.getValue())
            .map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }
            .toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textView1: TextView = rootView.findViewById(R.id.mostT_1)
        val textView2: TextView = rootView.findViewById(R.id.mostT_2)
        val textView3: TextView = rootView.findViewById(R.id.mostT_3)
        val textView4: TextView = rootView.findViewById(R.id.mostT_4)


        val textViewList = listOf(textView1, textView2, textView3, textView4)

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                when (pair.first) {
                    "cal" -> textViewList[index].text = getString(R.string.cal)
                    "uni" -> textViewList[index].text = getString(R.string.uni)
                    "fla" -> textViewList[index].text = getString(R.string.fla)
                    "gas" -> textViewList[index].text = getString(R.string.gas)
                }

                textViewList[index].setOnClickListener {
                    if (pair.first == "gas" && proPlusPrefValue != 100 && isBeforeDeadline) {
                        val intent = Intent(this, ProActivity::class.java)
                        startActivity(intent)
                    } else {
                        val activity = when (pair.first) {
                            "cal" -> CalculatorActivity::class.java
                            "uni" -> UnitConversionActivity::class.java
                            "fla" -> FlashCardActivity::class.java
                            "gas" -> IdealGasCalculatorActivity::class.java
                            else -> null
                        }
                        activity?.let {
                            val intent = Intent(this, it)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}
