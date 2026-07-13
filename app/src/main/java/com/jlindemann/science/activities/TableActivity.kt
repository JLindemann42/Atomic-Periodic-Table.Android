package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemDragListener
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnListScrollListener
import com.google.android.material.button.MaterialButton
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.*
import com.jlindemann.science.adapter.TableAdapter
import com.jlindemann.science.model.TableItem
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.TableOrderPreference
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.UnifiedTitleBarController

class TableActivity : BaseActivity(), TableAdapter.OnTableItemClickListener {

    private lateinit var adapter: TableAdapter
    private lateinit var recyclerView: DragDropSwipeRecyclerView
    private lateinit var tableOrderPref: TableOrderPreference
    private var isReorderMode = false
    private lateinit var reorderBtn: MaterialButton
    private lateinit var titleBar: UnifiedTitleBarController

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
        setContentView(R.layout.activity_tables)

        findViewById<FrameLayout>(R.id.view_sub).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        tableOrderPref = TableOrderPreference(this)
        setupRecyclerView()
        setupTitleBar()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.tables_recycler_view)
        
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        
        val tables = getTableItems(proPrefValue)
        
        adapter = TableAdapter(this, tables, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        recyclerView.dragListener = onItemDragListener
        recyclerView.longPressToStartDragging = true // Require long press to start drag, preventing conflicts with scrolling
    }
    
    private val onItemDragListener = object : OnItemDragListener<TableItem> {
        override fun onItemDragged(previousPosition: Int, newPosition: Int, item: TableItem) {
            // Item is being dragged
        }

        override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: TableItem) {
            // Item has been dropped, save the new order
            // Adjust for header if necessary, but library might handle dataSet directly
            saveTableOrder()
        }
    }

    private fun getTableItems(proPrefValue: Int): List<TableItem> {
        val defaultTables = mutableListOf(
            TableItem("iso", R.string.isotopes_title, R.string.isotopes_table_description, false, 0),
            TableItem("phi", R.string.table_ph, R.string.table_ph_text, false, 1),
            TableItem("ele", R.string.table_electrochemical, R.string.table_electrochemical_text, false, 2),
            TableItem("eqe", R.string.table_equations, R.string.table_equations_text, false, 3),
            TableItem("ion", R.string.table_ionization, R.string.table_ionization_text, false, 4),
            TableItem("sol", R.string.table_solubility, R.string.table_solubility_text, false, 5),
            TableItem("poi", R.string.table_poisson, R.string.table_poisson_text, proPrefValue == 1, 6),
            TableItem("nuc", R.string.table_nuclide, R.string.table_nuclide_text, proPrefValue == 1, 7),
            TableItem("con", R.string.constants_tite, R.string.table_constants_text, proPrefValue == 1, 8),
            TableItem("geo", R.string.table_geology, R.string.table_geology_text, proPrefValue == 1, 9),
            TableItem("emi", R.string.emission_title, R.string.emission_text, proPrefValue == 1, 10)
        )

        val savedOrder = tableOrderPref.getOrder()
        if (savedOrder.isNotEmpty()) {
            val orderedTables = savedOrder.mapNotNull { id ->
                defaultTables.find { it.id == id }
            }
            val missingTables = defaultTables.filter { table ->
                orderedTables.none { it.id == table.id }
            }
            return orderedTables + missingTables
        }

        return defaultTables
    }

    private fun saveTableOrder() {
        val currentOrder = adapter.dataSet.map { it.id }
        tableOrderPref.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        
        if (isReorderMode) {
            reorderBtn.setIconResource(R.drawable.ic_check_2)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
            reorderBtn.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
            reorderBtn.alpha = 1.0f
        } else {
            reorderBtn.setIconResource(R.drawable.ic_edit)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true)
            reorderBtn.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
            reorderBtn.alpha = 1.0f
            saveTableOrder()
        }
    }

    override fun onTableItemClick(item: TableItem) {
        if (isReorderMode) return

        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()

        if (item.requiresPro && proPrefValue != 100) {
            goToProPage()
            return
        }

        val activityClass = when (item.id) {
            "iso" -> IsotopesActivityExperimental::class.java
            "phi" -> phActivity::class.java
            "ele" -> ElectrodeActivity::class.java
            "eqe" -> EquationsActivity::class.java
            "ion" -> IonActivity::class.java
            "sol" -> SolubilityActivity::class.java
            "poi" -> PoissonActivity::class.java
            "nuc" -> NuclideActivity::class.java
            "con" -> ConstantsActivity::class.java
            "geo" -> GeologyActivity::class.java
            "emi" -> EmissionActivity::class.java
            else -> return
        }

        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    private fun setupTitleBar() {
        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.activity_table_title)
        titleBar.backButton.setOnClickListener { onBackPressed() }
        titleBar.setAction(R.drawable.ic_edit) { toggleReorderMode() }
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
        reorderBtn = titleBar.actionButton
        
        var totalScrollY = 0
        var isTitleVisible = false // Track animation state

        recyclerView.scrollListener = object : OnListScrollListener {
            override fun onListScrollStateChanged(scrollState: OnListScrollListener.ScrollState) {}

            override fun onListScrolled(scrollDirection: OnListScrollListener.ScrollDirection, distance: Int) {
                if (scrollDirection == OnListScrollListener.ScrollDirection.DOWN) {
                    totalScrollY += distance
                } else if (scrollDirection == OnListScrollListener.ScrollDirection.UP) {
                    totalScrollY -= distance
                }

            }
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        lastTopInset = top
        val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

        headerView?.let {
            applyHeaderInsets(it, top)
        }
    }

    private fun applyHeaderInsets(view: View, top: Int) {
    }

    private fun mostUsedBar(rootView: View) {
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()

        val regex = Regex("(\\w{3})=(\\d\\.\\d)")
        val matches = regex.findAll(mostUsedPrefValue).map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }.toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textViewList = listOf<TextView>(
            rootView.findViewById(R.id.most_1),
            rootView.findViewById(R.id.most_2),
            rootView.findViewById(R.id.most_3),
            rootView.findViewById(R.id.most_4),
            rootView.findViewById(R.id.most_5),
            rootView.findViewById(R.id.most_6),
            rootView.findViewById(R.id.most_7),
            rootView.findViewById(R.id.most_8),
            rootView.findViewById(R.id.most_9),
            rootView.findViewById(R.id.most_10),
            rootView.findViewById(R.id.most_11)
        )

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                val textView = textViewList[index]
                //Setup TextViews
                textView.text = when (pair.first) {
                    "geo" -> getString(R.string.geo)
                    "phi" -> getString(R.string.phi)
                    "eqe" -> getString(R.string.eqe)
                    "ion" -> getString(R.string.ion)
                    "sol" -> getString(R.string.sol)
                    "poi" -> getString(R.string.poi)
                    "nuc" -> getString(R.string.nuc)
                    "con" -> getString(R.string.con)
                    "ele" -> getString(R.string.ele)
                    "iso" -> getString(R.string.iso)
                    "emi" -> getString(R.string.emi)
                    else -> ""
                }

                textView.setOnClickListener {
                    if (proPrefValue != 100 && listOf("poi", "nuc", "con", "geo", "emi").contains(pair.first)) {
                        goToProPage()
                    } else {
                        val activityClass = when (pair.first) {
                            "iso" -> IsotopesActivityExperimental::class.java
                            "phi" -> phActivity::class.java
                            "eqe" -> EquationsActivity::class.java
                            "ion" -> IonActivity::class.java
                            "sol" -> SolubilityActivity::class.java
                            "ele" -> ElectrodeActivity::class.java
                            "poi" -> PoissonActivity::class.java
                            "nuc" -> NuclideActivity::class.java
                            "con" -> ConstantsActivity::class.java
                            "geo" -> GeologyActivity::class.java
                            "emi" -> EmissionActivity::class.java
                            else -> null
                        }
                        activityClass?.let {
                            val intent = Intent(this, it)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

}
