package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemDragListener
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tools.CalculatorActivity
import com.jlindemann.science.activities.tools.FlashCardActivity
import com.jlindemann.science.activities.tools.IdealGasCalculatorActivity
import com.jlindemann.science.activities.tools.TitleBarAnimator
import com.jlindemann.science.activities.tools.UnitConversionActivity
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
        mostUsedBar()

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
        
        adapter = ToolAdapter(this, tools, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        recyclerView.dragListener = onItemDragListener
        recyclerView.longPressToStartDragging = true // Require long press to start drag, preventing conflicts with scrolling
    }
    
    private val onItemDragListener = object : OnItemDragListener<ToolItem> {
        override fun onItemDragged(previousPosition: Int, newPosition: Int, item: ToolItem) {
            // Item is being dragged
        }

        override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: ToolItem) {
            // Item has been dropped, save the new order
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
            return savedOrder.mapNotNull { id ->
                defaultTools.find { it.id == id }
            }
        }

        return defaultTools
    }

    private fun saveToolOrder() {
        val currentOrder = (0 until adapter.itemCount).map { position ->
            adapter.dataSet[position].id
        }
        toolOrderPref.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        
        if (isReorderMode) {
            reorderBtn.alpha = 1.0f
        } else {
            reorderBtn.alpha = 0.6f
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
        // Title Controller with animated visibility
        findViewById<FrameLayout>(R.id.common_title_tool_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.tools_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.tools_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                private var isTitleVisible = false // Track animation state

                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.tools_scroll).scrollY
                    val threshold = 150

                    val titleColorBackground = findViewById<FrameLayout>(R.id.common_title_tool_color)
                    val titleText = findViewById<TextView>(R.id.tools_title)
                    val titleDownstateText = findViewById<TextView>(R.id.tools_title_downstate)
                    val titleBackground = findViewById<FrameLayout>(R.id.common_title_back_tab)

                    if (scrollY > threshold) {
                        if (!isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                            TitleBarAnimator.animateVisibility(titleText, true)
                            TitleBarAnimator.animateVisibility(titleDownstateText, false)
                            titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                            isTitleVisible = true
                        }
                    } else {
                        if (isTitleVisible) {
                            TitleBarAnimator.animateVisibility(titleColorBackground, false)
                            TitleBarAnimator.animateVisibility(titleText, false)
                            TitleBarAnimator.animateVisibility(titleDownstateText, true)
                            titleBackground.elevation = resources.getDimension(R.dimen.zero_elevation)
                            isTitleVisible = false
                        }
                    }
                }
            })
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams = params

            val params2 = findViewById<TextView>(R.id.tools_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.tools_title_downstate).layoutParams = params2
    }

    private fun mostUsedBar() {
        val mostUsedToolPreference = MostUsedToolPreference(this)
        val proPlusPref = ProPlusVersion(this)
        val proPlusPrefValue = proPlusPref.getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()
        
        val regex = Regex("(\\w{3})=(\\d\\.\\d)") // Corrected regex pattern
        val matches = regex.findAll(mostUsedToolPreference.getValue())
            .map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }
            .toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textView1: TextView = findViewById(R.id.mostT_1)
        val textView2: TextView = findViewById(R.id.mostT_2)
        val textView3: TextView = findViewById(R.id.mostT_3)

        val textViewList = listOf(textView1, textView2, textView3)

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                // Setup TextViews
                when (pair.first) {
                    "cal" -> textViewList[index].text = getString(R.string.cal)
                    "uni" -> textViewList[index].text = getString(R.string.uni)
                    "fla" -> textViewList[index].text = getString(R.string.fla)
                    "gas" -> textViewList[index].text = getString(R.string.gas)
                }

                textViewList[index].setOnClickListener {
                    // Check if gas calculator requires PRO+
                    if (pair.first == "gas" && proPlusPrefValue != 100 && isBeforeDeadline) {
                        // Open ProActivity if user doesn't have PRO+
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



