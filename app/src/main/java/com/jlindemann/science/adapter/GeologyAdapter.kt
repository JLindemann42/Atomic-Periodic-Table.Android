package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.GeologyActivity
import com.jlindemann.science.model.Geology

class GeologyAdapter(var list: ArrayList<Geology>, var clickListener: GeologyActivity, val context: Context) : RecyclerView.Adapter<GeologyAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.geology_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName = itemView.findViewById(R.id.tv_geo_name) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_geo_short) as TextView
        private val textViewGroup = itemView.findViewById(R.id.tv_geo_group) as TextView
        private val textViewType = itemView.findViewById(R.id.tv_geo_type) as TextView

        fun initialize(item: Geology, action: OnGeologyClickListener, context: Context) {
            textViewName.text = item.name
            textViewName.text = item.name.capitalize()
            textViewShort.text = item.name.substring(0,2)
            textViewType.text = item.type
            textViewGroup.text = item.group


            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setOnClickListener {
                action.geologyClickListener(item, adapterPosition)
            }
        }
    }

    fun filterList(filteredList: ArrayList<Geology>) {
        list = filteredList
        notifyDataSetChanged()
    }

    interface OnGeologyClickListener {
        fun geologyClickListener(item: Geology, position: Int)
    }

}


