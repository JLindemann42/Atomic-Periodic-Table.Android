package com.jlindemann.science.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.Element

class IsotopeAdapter(var elementList: ArrayList<Element>, var clickListener: OnElementClickListener, val context: Context) : RecyclerView.Adapter<IsotopeAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(elementList[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.isotope_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return elementList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewElement = itemView.findViewById(R.id.tv_iso_type) as TextView
        private val textViewShort = itemView.findViewById(R.id.ic_iso_type) as TextView
        private val textViewNumb = itemView.findViewById(R.id.tv_iso_numb) as TextView

        fun initialize(item: Element, action: OnElementClickListener, context: Context) {
            textViewElement.text = item.element
            textViewElement.text = item.element.capitalize()
            textViewShort.text = item.short
            textViewNumb.text = item.number.toString()

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true

            itemView.setOnClickListener {
                action.elementClickListener(item, adapterPosition)
            }
        }
    }

    interface OnElementClickListener {
        fun elementClickListener(item: Element, position: Int)
    }

    fun filterList(filteredList: ArrayList<Element>) {
        elementList = filteredList
        notifyDataSetChanged()
    }
}


