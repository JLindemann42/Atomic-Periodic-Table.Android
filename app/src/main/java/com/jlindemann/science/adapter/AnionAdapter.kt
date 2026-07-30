package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

class AnionAdapter(private val anions: List<String>) :
    RecyclerView.Adapter<AnionAdapter.AnionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_solubility_anion, parent, false)
        return AnionViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnionViewHolder, position: Int) {
        holder.text.text = anions[position]
    }

    override fun getItemCount(): Int = anions.size

    class AnionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.anion_text)
    }
}
