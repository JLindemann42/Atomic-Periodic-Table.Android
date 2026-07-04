package com.jlindemann.science.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
import com.jlindemann.science.activities.*
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tables.*
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.adapter.TableAdapter
import com.jlindemann.science.model.TableItem
import com.jlindemann.science.preferences.MostUsedPreference
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.TableOrderPreference

class TablesFragment : BaseFragment(), TableAdapter.OnTableItemClickListener {

    private lateinit var adapter: TableAdapter
    private lateinit var recyclerView: DragDropSwipeRecyclerView
    private lateinit var tableOrderPreference: TableOrderPreference
    private var isReorderMode = false
    private lateinit var reorderBtn: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tables, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tableOrderPreference = TableOrderPreference(requireContext())
        setupRecyclerView(view)
        
        reorderBtn = view.findViewById(R.id.reorder_btn)
        reorderBtn.setOnClickListener { toggleReorderMode() }
        
        mostUsedBar(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.tables_recycler_view)
        val proPrefValue = ProVersion(requireContext()).getValue()
        
        val tables = getTableItems(proPrefValue)
        
        adapter = TableAdapter(requireContext(), tables, this)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        recyclerView.dragListener = object : OnItemDragListener<TableItem> {
            override fun onItemDragged(previousPosition: Int, newPosition: Int, item: TableItem) {}
            override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: TableItem) { saveTableOrder() }
        }
        recyclerView.longPressToStartDragging = true
    }

    private fun getTableItems(proPrefValue: Int): List<TableItem> {
        val defaultTables = mutableListOf(
            TableItem("iso", R.string.isotopes_title, R.string.isotopes_table_description, false, 0),
            TableItem("phi", R.string.table_ph, R.string.table_ph_text, false, 1),
            TableItem("ele", R.string.table_electrochemical, R.string.table_electrochemical_text, false, 2),
            TableItem("eqe", R.string.table_equations, R.string.table_equations_text, false, 3),
            TableItem("ion", R.string.table_ionization, R.string.table_ionization_text, false, 4),
            TableItem("sol", R.string.table_solubility, R.string.table_solubility_text, false, 5),
            TableItem("poi", R.string.table_poisson, R.string.table_poisson_text, proPrefValue != 100, 6),
            TableItem("nuc", R.string.table_nuclide, R.string.table_nuclide_text, proPrefValue != 100, 7),
            TableItem("con", R.string.constants_tite, R.string.table_constants_text, proPrefValue != 100, 8),
            TableItem("geo", R.string.table_geology, R.string.table_geology_text, proPrefValue != 100, 9),
            TableItem("emi", R.string.emission_title, R.string.emission_text, proPrefValue != 100, 10)
        )

        val savedOrder = tableOrderPreference.getOrder()
        if (savedOrder.isNotEmpty()) {
            val ordered = savedOrder.mapNotNull { id -> defaultTables.find { it.id == id } }.toMutableList()
            defaultTables.forEach { if (it !in ordered) ordered.add(it) }
            return ordered
        }
        return defaultTables
    }

    private fun saveTableOrder() {
        val currentOrder = adapter.dataSet.map { it.id }
        tableOrderPreference.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        reorderBtn.setImageResource(if (isReorderMode) R.drawable.ic_check_2 else R.drawable.ic_edit)
    }

    override fun onTableItemClick(item: TableItem) {
        if (isReorderMode) return
        val proPrefValue = ProVersion(requireContext()).getValue()
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
        startActivity(Intent(requireContext(), activityClass))
    }

    private fun mostUsedBar(root: View) {
        val mostUsedPreference = MostUsedPreference(requireContext())
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val proPrefValue = ProVersion(requireContext()).getValue()

        val regex = Regex("(\\w{3})=(\\d\\.\\d)")
        val matches = regex.findAll(mostUsedPrefValue).map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }.toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val chipList = listOf<com.google.android.material.chip.Chip?>(
            root.findViewById(R.id.most_1), root.findViewById(R.id.most_2), root.findViewById(R.id.most_3),
            root.findViewById(R.id.most_4), root.findViewById(R.id.most_5), root.findViewById(R.id.most_6),
            root.findViewById(R.id.most_7), root.findViewById(R.id.most_8), root.findViewById(R.id.most_9),
            root.findViewById(R.id.most_10), root.findViewById(R.id.most_11)
        )

        sortedValues.forEachIndexed { index, pair ->
            if (index < chipList.size) {
                val chip = chipList[index] ?: return@forEachIndexed
                val text = when (pair.first) {
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

                if (text.isNotEmpty()) {
                    chip.text = text
                    chip.visibility = View.VISIBLE
                    chip.setOnClickListener {
                        val activityClass = when (pair.first) {
                            "iso" -> IsotopesActivityExperimental::class.java
                            "phi" -> phActivity::class.java
                            "eqe" -> EquationsActivity::class.java
                            "ion" -> IonActivity::class.java
                            "sol" -> SolubilityActivity::class.java
                            "ele" -> ElectrodeActivity::class.java
                            "poi" -> if (proPrefValue == 100) PoissonActivity::class.java else ProActivity::class.java
                            "nuc" -> if (proPrefValue == 100) NuclideActivity::class.java else ProActivity::class.java
                            "con" -> if (proPrefValue == 100) ConstantsActivity::class.java else ProActivity::class.java
                            "geo" -> if (proPrefValue == 100) GeologyActivity::class.java else ProActivity::class.java
                            "emi" -> if (proPrefValue == 100) EmissionActivity::class.java else ProActivity::class.java
                            else -> null
                        }
                        activityClass?.let { startActivity(Intent(requireContext(), it)) }
                    }
                }
            }
        }
    }
}
