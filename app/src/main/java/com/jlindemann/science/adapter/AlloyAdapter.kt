package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.AlloyActivity
import com.jlindemann.science.model.Alloy

class AlloyAdapter(var list: ArrayList<Alloy>, var clickListener: AlloyActivity, val context: Context) : RecyclerView.Adapter<AlloyAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.alloy_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName = itemView.findViewById(R.id.tv_alloy_name) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_alloy_short) as TextView
        private val textViewComposition = itemView.findViewById(R.id.tv_alloy_composition) as TextView
        private val textViewBase = itemView.findViewById(R.id.tv_alloy_base) as TextView

        fun initialize(item: Alloy, action: OnAlloyClickListener, context: Context) {
            textViewName.text = item.name
            textViewShort.text = item.name.take(2)
            textViewComposition.text = item.composition
            textViewBase.text = item.base.replaceFirstChar { it.uppercase() }

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setOnClickListener {
                action.alloyClickListener(item, adapterPosition)
            }
        }
    }

    fun filterList(filteredList: ArrayList<Alloy>) {
        list = filteredList
        notifyDataSetChanged()
    }

    interface OnAlloyClickListener {
        fun alloyClickListener(item: Alloy, position: Int)
    }

}
