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
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tables.*
import com.jlindemann.science.activities.tools.TitleBarAnimator
import com.jlindemann.science.adapter.TableAdapter
import com.jlindemann.science.model.TableItem
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.TableOrderPreference
import com.jlindemann.science.preferences.ThemePreference

class TableActivity : BaseActivity(), TableAdapter.OnTableItemClickListener {

    private lateinit var adapter: TableAdapter
    private lateinit var recyclerView: DragDropSwipeRecyclerView
    private lateinit var tableOrderPref: TableOrderPreference
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
        setContentView(R.layout.activity_tables)

        findViewById<FrameLayout>(R.id.view_sub).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        tableOrderPref = TableOrderPreference(this)
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
        recyclerView = findViewById(R.id.tables_recycler_view)
        
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()
        
        val tables = getTableItems(proPrefValue)
        
        adapter = TableAdapter(this, tables, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        
        adapter.setDataChangeListener(object : DragDropSwipeRecyclerView.DataChangeListener {
            override fun onDatasetChanged() {
                saveTableOrder()
            }
        })
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
            return savedOrder.mapNotNull { id ->
                defaultTables.find { it.id == id }
            }
        }

        return defaultTables
    }

    private fun saveTableOrder() {
        val currentOrder = (0 until adapter.itemCount).map { position ->
            adapter.dataSet[position].id
        }
        tableOrderPref.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        
        if (isReorderMode) {
            reorderBtn.alpha = 1.0f
        } else {
            reorderBtn.alpha = 0.6f
            saveTableOrder()
        }
    }

    override fun onTableItemClick(item: TableItem) {
        if (isReorderMode) return

        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()

        val activityClass = when (item.id) {
            "iso" -> IsotopesActivityExperimental::class.java
            "phi" -> phActivity::class.java
            "ele" -> ElectrodeActivity::class.java
            "eqe" -> EquationsActivity::class.java
            "ion" -> IonActivity::class.java
            "sol" -> SolubilityActivity::class.java
            "poi" -> if (proPrefValue == 100) PoissonActivity::class.java else ProActivity::class.java
            "nuc" -> if (proPrefValue == 100) NuclideActivity::class.java else ProActivity::class.java
            "con" -> if (proPrefValue == 100) ConstantsActivity::class.java else ProActivity::class.java
            "geo" -> if (proPrefValue == 100) GeologyActivity::class.java else ProActivity::class.java
            "emi" -> if (proPrefValue == 100) EmissionActivity::class.java else ProActivity::class.java
            else -> return
        }

        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    private fun setupTitleBar() {
        /// Title Controller with animated visibility
        findViewById<FrameLayout>(R.id.common_title_table_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.tables_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.table_scroll).viewTreeObserver
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                private var isTitleVisible = false // Track animation state

                override fun onScrollChanged() {
                    val scrollY = findViewById<ScrollView>(R.id.table_scroll).scrollY
                    val threshold = 150

                    val titleColorBackground = findViewById<FrameLayout>(R.id.common_title_table_color)
                    val titleText = findViewById<TextView>(R.id.tables_title)
                    val titleDownstateText = findViewById<TextView>(R.id.tables_title_downstate)
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

            val params2 = findViewById<TextView>(R.id.tables_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.tables_title_downstate).layoutParams = params2
    }

    private fun mostUsedBar() {
        val mostUsedPreference = MostUsedPreference(this)
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val proPref = ProVersion(this)
        val proPrefValue = proPref.getValue()

        val regex = Regex("(\\w{3})=(\\d.\\d)")
        val matches = regex.findAll(mostUsedPrefValue).map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }.toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textView1: TextView = findViewById(R.id.most_1)
        val textView2: TextView = findViewById(R.id.most_2)
        val textView3: TextView = findViewById(R.id.most_3)
        val textView4: TextView = findViewById(R.id.most_4)
        val textView5: TextView = findViewById(R.id.most_5)
        val textView6: TextView = findViewById(R.id.most_6)
        val textView7: TextView = findViewById(R.id.most_7)
        val textView8: TextView = findViewById(R.id.most_8)
        val textView9: TextView = findViewById(R.id.most_9)
        val textView10: TextView = findViewById(R.id.most_10)
        val textView11: TextView = findViewById(R.id.most_11)


        val textViewList = listOf(textView1, textView2, textView3, textView4, textView5, textView6, textView7, textView8, textView9, textView10, textView11)

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                //Setup TextViews
                if (pair.first == "geo") {textViewList[index].text = getString(R.string.geo)}
                if (pair.first == "phi") {textViewList[index].text = getString(R.string.phi)}
                if (pair.first == "eqe") {textViewList[index].text = getString(R.string.eqe)}
                if (pair.first == "ion") {textViewList[index].text = getString(R.string.ion)}
                if (pair.first == "sol") {textViewList[index].text = getString(R.string.sol)}
                if (pair.first == "poi") {textViewList[index].text = getString(R.string.poi)}
                if (pair.first == "nuc") {textViewList[index].text = getString(R.string.nuc)}
                if (pair.first == "con") {textViewList[index].text = getString(R.string.con)}
                if (pair.first == "ele") {textViewList[index].text = getString(R.string.ele)}
                if (pair.first == "iso") {textViewList[index].text = getString(R.string.iso)}
                if (pair.first == "emi") {textViewList[index].text = getString(R.string.emi)}

                //Setup clickListener for non-pro
                if (proPrefValue==1) {
                    textViewList[index].setOnClickListener {
                        if (pair.first == "iso") {
                            val activity = IsotopesActivityExperimental::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "phi") {
                            val activity = phActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "eqe") {
                            val activity = EquationsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ion") {
                            val activity = IonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "sol") {
                            val activity = SolubilityActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ele") {
                            val activity = ElectrodeActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "poi") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "nuc") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "con") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "geo") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "emi") {
                            val activity = ProActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                    }
                }
                //Setup clickListener for pro
                if (proPrefValue==100) {
                    textViewList[index].setOnClickListener {
                        if (pair.first == "iso") {
                            val activity = IsotopesActivityExperimental::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "phi") {
                            val activity = phActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "eqe") {
                            val activity = EquationsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ion") {
                            val activity = IonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "sol") {
                            val activity = SolubilityActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "ele") {
                            val activity = ElectrodeActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "poi") {
                            val activity = PoissonActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "nuc") {
                            val activity = NuclideActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "con") {
                            val activity = ConstantsActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "geo") {
                            val activity = GeologyActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                        if (pair.first == "emi") {
                            val activity = EmissionActivity::class.java
                            val intent = Intent(this, activity)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

}



