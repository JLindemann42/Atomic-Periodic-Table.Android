package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.PoissonActivity
import com.jlindemann.science.model.Poisson
import com.jlindemann.science.model.Series

class PoissonAdapter(var list: ArrayList<Poisson>, var clickListener: PoissonActivity, val context: Context) : RecyclerView.Adapter<PoissonAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.poisson_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName = itemView.findViewById(R.id.tv_poi_name) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_poi_short) as TextView
        private val textViewStart = itemView.findViewById(R.id.tv_poi_1) as TextView
        private val textViewEnd = itemView.findViewById(R.id.tv_poi_2) as TextView
        private val textViewBetween = itemView.findViewById(R.id.tv_poi_between) as TextView
        private val textViewType = itemView.findViewById(R.id.tv_poi_type) as TextView

        fun initialize(item: Poisson, context: Context) {
            textViewName.text = item.name
            textViewName.text = item.name.capitalize()
            textViewShort.text = item.name.substring(0,2)
            textViewType.text = item.type
            val startValue = item.start.toString()
            val endValue = item.end.toString()

            if (startValue == endValue) {
                textViewEnd.visibility = View.GONE
                textViewBetween.visibility = View.GONE
                textViewStart.text = startValue
            }
            else {
                textViewEnd.visibility = View.VISIBLE
                textViewBetween.visibility = View.VISIBLE
                textViewStart.text = startValue
                textViewEnd.text = endValue
            }

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true

        }
    }

    fun filterList(filteredList: ArrayList<Poisson>) {
        list = filteredList
        notifyDataSetChanged()
    }

}


