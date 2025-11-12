package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.IonActivity
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.Ion
import com.jlindemann.science.utils.ElementDataLoader
import java.io.IOException


class IonAdapter(var list: ArrayList<Ion>, var clickListener: IonActivity, val context: Context) : RecyclerView.Adapter<IonAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_ions_list, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int { return list.size }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView.findViewById(R.id.ionCard) as FrameLayout
        private val textViewName = itemView.findViewById(R.id.tv_name_d) as TextView
        private val textViewShort = itemView.findViewById(R.id.tv_short_d) as TextView
        private val textViewCharge = itemView.findViewById(R.id.tv_end) as TextView
        private val textViewVoltage = itemView.findViewById(R.id.tv_ionization) as TextView

        fun initialize(item: Ion, action: OnIonClickListener, context: Context) {
            try {
                val element = item.name
                val jsonObject = ElementDataLoader.loadElementData(context, element)
                val ionization1 = jsonObject?.optString("element_ionization_energy1", "---") ?: "---"
                textViewVoltage.text = ionization1
            }
            catch (e: IOException) { }
            textViewName.text = item.name
            textViewName.text = item.name.capitalize()
            textViewShort.text = item.short
            textViewCharge.text = "View all" + " " + item.count.toString()

            cardView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            cardView.isClickable = true
            cardView.isFocusable = true
            cardView.setOnClickListener {
                action.ionClickListener(item, adapterPosition)
            }

        }
    }

    fun filterList(filteredList: ArrayList<Ion>) {
        list = filteredList
        notifyDataSetChanged()

    }
    interface OnIonClickListener {
        fun ionClickListener(item: Ion, position: Int)
    }
}


