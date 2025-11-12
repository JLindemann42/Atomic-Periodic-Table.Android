package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.ConstantsActivity
import com.jlindemann.science.model.Constants
import com.jlindemann.science.model.Poisson

class ConstantsAdapter(var list: ArrayList<Constants>, var clickListener: ConstantsActivity, val context: Context) : RecyclerView.Adapter<ConstantsAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.constants_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName = itemView.findViewById(R.id.tv_poi_name) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_poi_short) as TextView
        private val textViewStart = itemView.findViewById(R.id.tv_poi_1) as TextView
        private val textViewUnit = itemView.findViewById(R.id.tv_poi_unit) as TextView
        private val textViewValue = itemView.findViewById(R.id.tv_poi_type) as TextView

        fun initialize(item: Constants, action: OnConstantsClickListener, context: Context) {
            textViewName.text = item.name
            textViewName.text = item.name.capitalize()
            textViewShort.text = item.name.substring(0,2)
            textViewValue.text = item.value.toString()
            textViewUnit.text = item.unit
            textViewStart.text = item.info

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setOnClickListener {
                action.constantsClickListener(item, adapterPosition)
            }
        }
    }

    fun filterList(filteredList: ArrayList<Constants>) {
        list = filteredList
        notifyDataSetChanged()
    }

    interface OnConstantsClickListener {
        fun constantsClickListener(item: Constants, position: Int)
    }

}


