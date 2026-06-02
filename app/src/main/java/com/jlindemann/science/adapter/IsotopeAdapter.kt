package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.Element

class IsotopeAdapter(
    var elementList: ArrayList<Element>,
    var clickListener: OnElementClickListener,
    val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var headerBindingAction: ((View) -> Unit)? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    interface OnElementClickListener {
        fun elementClickListener(item: Element, position: Int)
    }

    fun setHeaderBindingAction(action: (View) -> Unit) {
        headerBindingAction = action
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.isotope_list_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.isotope_list_item, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            headerBindingAction?.invoke(holder.itemView)
        } else {
            val item = elementList[position - 1]
            if (holder is ItemViewHolder) {
                holder.bind(item, clickListener, context)
            }
        }
    }

    override fun getItemCount(): Int {
        return elementList.size + 1
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewElement = itemView.findViewById(R.id.tv_iso_type) as TextView
        private val textViewShort = itemView.findViewById(R.id.ic_iso_type) as TextView
        private val textViewNumb = itemView.findViewById(R.id.tv_iso_numb) as TextView

        fun bind(item: Element, action: OnElementClickListener, context: Context) {
            textViewElement.text = item.element.replaceFirstChar { it.uppercase() }
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

    fun filterList(filteredList: ArrayList<Element>) {
        elementList = filteredList
        notifyDataSetChanged()
    }
}
