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
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tools.*
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.adapter.ToolAdapter
import com.jlindemann.science.model.ToolItem
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ToolOrderPreference
import com.jlindemann.science.utils.ProPlusTimeUtil

class ToolsFragment : BaseFragment(), ToolAdapter.OnToolItemClickListener {

    private lateinit var adapter: ToolAdapter
    private lateinit var recyclerView: DragDropSwipeRecyclerView
    private lateinit var toolOrderPreference: ToolOrderPreference
    private var isReorderMode = false
    private lateinit var reorderBtn: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolOrderPreference = ToolOrderPreference(requireContext())
        setupRecyclerView(view)
        
        reorderBtn = view.findViewById(R.id.reorder_btn)
        reorderBtn.setOnClickListener { toggleReorderMode() }
        
        mostUsedBar(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.tools_recycler_view)
        val proPlusValue = ProPlusVersion(requireContext()).getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()
        
        val tools = getToolItems(proPlusValue, isBeforeDeadline)
        
        adapter = ToolAdapter(requireContext(), tools, this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.orientation = DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
        recyclerView.dragListener = object : OnItemDragListener<ToolItem> {
            override fun onItemDragged(previousPosition: Int, newPosition: Int, item: ToolItem) {}
            override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: ToolItem) { saveToolOrder() }
        }
        recyclerView.longPressToStartDragging = true
    }

    private fun getToolItems(proPlusValue: Int, isBeforeDeadline: Boolean): List<ToolItem> {
        val defaultTools = mutableListOf(
            ToolItem("cal", R.string.calculator_title, R.string.calculator_text, false, false, 0),
            ToolItem("uni", R.string.unit_title, R.string.unit_description, false, false, 1),
            ToolItem("gas", R.string.ideal_gas_calculator_title, R.string.ideal_gas_text, proPlusValue != 100 && isBeforeDeadline, false, 2)
        )

        val savedOrder = toolOrderPreference.getOrder()
        if (savedOrder.isNotEmpty()) {
            val ordered = savedOrder.mapNotNull { id -> defaultTools.find { it.id == id } }.toMutableList()
            defaultTools.forEach { if (it !in ordered) ordered.add(it) }
            return ordered
        }
        return defaultTools
    }

    private fun saveToolOrder() {
        val currentOrder = adapter.dataSet.map { it.id }
        toolOrderPreference.saveOrder(currentOrder)
    }

    private fun toggleReorderMode() {
        isReorderMode = !isReorderMode
        adapter.setReorderMode(isReorderMode)
        reorderBtn.setImageResource(if (isReorderMode) R.drawable.ic_check_2 else R.drawable.ic_edit)
    }

    override fun onToolItemClick(item: ToolItem) {
        if (isReorderMode) return
        val proPlusValue = ProPlusVersion(requireContext()).getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()

        val activityClass = when (item.id) {
            "cal" -> CalculatorActivity::class.java
            "uni" -> UnitConversionActivity::class.java
            "gas" -> if (proPlusValue != 100 && isBeforeDeadline) ProActivity::class.java else IdealGasCalculatorActivity::class.java
            else -> return
        }
        startActivity(Intent(requireContext(), activityClass))
    }

    private fun mostUsedBar(root: View) {
        val mostUsedToolPreference = MostUsedToolPreference(requireContext())
        val proPlusPrefValue = ProPlusVersion(requireContext()).getValue()
        val isBeforeDeadline = ProPlusTimeUtil.isBeforeJanuary2026()
        
        val regex = Regex("(\\w{3})=(\\d\\.\\d)")
        val matches = regex.findAll(mostUsedToolPreference.getValue())
            .map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }
            .toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val chipList = listOf<com.google.android.material.chip.Chip?>(
            root.findViewById(R.id.mostT_1), root.findViewById(R.id.mostT_2),
            root.findViewById(R.id.mostT_3), root.findViewById(R.id.mostT_4)
        )

        sortedValues.forEachIndexed { index, pair ->
            if (index < chipList.size) {
                val chip = chipList[index] ?: return@forEachIndexed
                val text = when (pair.first) {
                    "cal" -> getString(R.string.cal)
                    "uni" -> getString(R.string.uni)
                    "fla" -> getString(R.string.fla)
                    "gas" -> getString(R.string.gas)
                    else -> ""
                }
                
                if (text.isNotEmpty()) {
                    chip.text = text
                    chip.visibility = View.VISIBLE
                    chip.setOnClickListener {
                        if (pair.first == "gas" && proPlusPrefValue != 100 && isBeforeDeadline) {
                            startActivity(Intent(requireContext(), ProActivity::class.java))
                        } else {
                            val activity = when (pair.first) {
                                "cal" -> CalculatorActivity::class.java
                                "uni" -> UnitConversionActivity::class.java
                                "gas" -> IdealGasCalculatorActivity::class.java
                                else -> null
                            }
                            activity?.let { startActivity(Intent(requireContext(), it)) }
                        }
                    }
                }
            }
        }
    }
}
