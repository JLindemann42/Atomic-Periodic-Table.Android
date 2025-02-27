package com.jlindemann.science.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

class IncludedElementsAdapter : RecyclerView.Adapter<IncludedElementsAdapter.ElementViewHolder>() {

    private val elements = mutableListOf<Quadruple<String, Double, Double, Double, String>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ElementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_included_element, parent, false)
        return ElementViewHolder(view)
    }

    override fun onBindViewHolder(holder: ElementViewHolder, position: Int) {
        val (element, quantity, atomicWeight, percentage, fullName) = elements[position]
        holder.elementName.text = element
        holder.atomicWeight.text = (quantity * atomicWeight).toString()
        holder.elementPercentage.text = String.format("%.2f", percentage) + "%"
        holder.elementFullName.text = fullName
    }

    override fun getItemCount(): Int = elements.size

    fun updateElements(newElements: List<Quadruple<String, Double, Double, Double, String>>) {
        elements.clear()
        elements.addAll(mergeElements(newElements))
        notifyDataSetChanged()
    }

    private fun mergeElements(elements: List<Quadruple<String, Double, Double, Double, String>>): List<Quadruple<String, Double, Double, Double, String>> {
        val mergedElements = mutableMapOf<String, Quadruple<String, Double, Double, Double, String>>()

        for ((element, quantity, atomicWeight, percentage, fullName) in elements) {
            val existing = mergedElements[element]
            if (existing == null) {
                mergedElements[element] = Quadruple(element, quantity, atomicWeight, percentage, fullName)
            } else {
                val newQuantity = existing.second + quantity
                val newPercentage = existing.fourth + percentage
                mergedElements[element] = Quadruple(element, newQuantity, atomicWeight, newPercentage, fullName)
            }
        }

        return mergedElements.values.toList()
    }

    class ElementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val elementName: TextView = itemView.findViewById(R.id.ic_element)
        val atomicWeight: TextView = itemView.findViewById(R.id.element_molar_weight)
        val elementPercentage: TextView = itemView.findViewById(R.id.percent_element)
        val elementFullName: TextView = itemView.findViewById(R.id.element_name_cal)
    }
}