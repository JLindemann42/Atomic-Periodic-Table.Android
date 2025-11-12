package com.jlindemann.science.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.Element
import com.jlindemann.science.preferences.SearchPreferences

class ElementAdapter(var elementList: ArrayList<Element>, var clickListener: OnElementClickListener2, val con: Context) : RecyclerView.Adapter<ElementAdapter.ViewHolder>() {

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // get preference value here (context is guaranteed to be valid by the time RecyclerView binds)
        val searchPrefValue = SearchPreferences(con).getValue()
        holder.initialize(elementList[position], clickListener, con, searchPrefValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.isotope_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return elementList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardHolder = itemView.findViewById<FrameLayout>(R.id.rCard)
        private val elementCard = itemView.findViewById<FrameLayout>(R.id.elemntCard)
        private val textViewElement = itemView.findViewById<TextView>(R.id.tv_iso_type)
        private val textViewShort = itemView.findViewById<TextView>(R.id.ic_iso_type)
        private val textViewNumb = itemView.findViewById<TextView>(R.id.tv_iso_numb)

        fun initialize(item: Element, action: OnElementClickListener2, con: Context, searchPrefValue: Int) {

            textViewElement.text = item.element.replaceFirstChar { it.uppercase() }
            textViewShort.text = item.short

            itemView.foreground = ContextCompat.getDrawable(con, R.drawable.toast_card_outline_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true

            if (searchPrefValue == 0) {
                textViewNumb.text = item.number.toString()
            }
            if (searchPrefValue == 1) {
                textViewNumb.text = item.electro.toString()
                textViewShort.setTextColor(Color.argb(255, 18, 18, 18))
                if (item.electro == 0.0) {
                    elementCard.background.setTint(Color.argb(255, 180, 180, 180))
                    textViewNumb.text = "---"
                }
                else {
                    if (item.electro > 1) {
                        elementCard.background.setTint(Color.argb(255, 255, 225.div(item.electro).toInt(), 0))
                    }
                    else {
                        elementCard.background.setTint(Color.argb(255, 255, 255, 0))
                    }
                }
            }
            if (searchPrefValue == 2) {
                textViewNumb.text = item.number.toString()
            }

            itemView.setOnClickListener {
                action.elementClickListener2(item, adapterPosition)
            }
        }
    }

    interface OnElementClickListener2 {
        fun elementClickListener2(item: Element, position: Int)
    }

    fun filterList(filteredList: ArrayList<Element>) {
        elementList = filteredList
        notifyDataSetChanged()
    }
}