package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.SolubilityColumn

class SolubilityAdapter(private val columns: List<SolubilityColumn>) :
    RecyclerView.Adapter<SolubilityAdapter.ColumnViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColumnViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_solubility_column, parent, false)
        return ColumnViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColumnViewHolder, position: Int) {
        holder.bind(columns[position])
    }

    override fun getItemCount(): Int = columns.size

    class ColumnViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val header: TextView = itemView.findViewById(R.id.cation_header)
        private val cellsContainer: LinearLayout = itemView.findViewById(R.id.cells_container)

        fun bind(column: SolubilityColumn) {
            header.text = column.cation
            cellsContainer.removeAllViews()
            
            val inflater = LayoutInflater.from(itemView.context)
            for (value in column.values) {
                val cellView = inflater.inflate(R.layout.item_solubility_cell, cellsContainer, false) as TextView
                cellView.text = value
                applyCellStyle(cellView, value)
                cellsContainer.addView(cellView)
            }
        }

        private fun applyCellStyle(view: TextView, value: String) {
            val context = view.context
            when (value) {
                "S" -> {
                    view.setTextColor(getColorFromAttr(context, R.attr.colorOnPrimaryContainer))
                    view.backgroundTintList = ContextCompat.getColorStateList(context, getColorResFromAttr(context, R.attr.colorPrimaryContainer))
                }
                "I" -> {
                    view.setTextColor(getColorFromAttr(context, R.attr.colorOnErrorContainer))
                    view.backgroundTintList = ContextCompat.getColorStateList(context, getColorResFromAttr(context, R.attr.colorErrorContainer))
                }
                "Ss" -> {
                    view.setTextColor(getColorFromAttr(context, R.attr.colorOnTertiaryContainer))
                    view.backgroundTintList = ContextCompat.getColorStateList(context, getColorResFromAttr(context, R.attr.colorTertiaryContainer))
                }
                "---" -> {
                    view.setTextColor(getColorFromAttr(context, R.attr.colorOnSurfaceVariant))
                    view.backgroundTintList = ContextCompat.getColorStateList(context, getColorResFromAttr(context, R.attr.colorSurfaceVariant))
                    view.alpha = 0.66f
                }
                else -> {
                    view.alpha = 1.0f
                }
            }
        }

        private fun getColorFromAttr(context: android.content.Context, attr: Int): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }

        private fun getColorResFromAttr(context: android.content.Context, attr: Int): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return typedValue.resourceId
        }
    }
}
