package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.ElectrodeActivity
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.Series

class ElectrodeAdapter(var list: ArrayList<Series>, var clickListener: ElectrodeActivity, val context: Context) : RecyclerView.Adapter<ElectrodeAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.electrode_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName = itemView.findViewById(R.id.tv_name) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_short) as TextView
        private val textViewCharge = itemView.findViewById(R.id.tv_charge) as TextView
        private val textViewVoltage = itemView.findViewById(R.id.tv_voltage) as TextView

        fun initialize(item: Series, action: OnSeriesClickListener, context: Context) {
            textViewName.text = item.name
            textViewName.text = item.name.capitalize()
            textViewShort.text = item.short
            val voltage = item.voltage.toString()
            val shortVolt = " (Volt)"
            val textVolt = voltage + shortVolt
            textViewVoltage.text = textVolt
            textViewCharge.text = item.charge

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.c_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true

            itemView.setOnClickListener {
                action.seriesClickListener(item, adapterPosition)
            }
        }
    }

    interface OnSeriesClickListener {
        fun seriesClickListener(item: Series, position: Int)
    }

    fun filterList(filteredList: ArrayList<Series>) {
        list = filteredList
        notifyDataSetChanged()
    }

}


