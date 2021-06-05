package com.jlindemann.science.adapter

import android.content.Context
import android.content.res.Configuration
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.OrderActivity
import com.jlindemann.science.activities.tables.EquationsActivity
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.Order
import com.jlindemann.science.preferences.ThemePreference

class OrderAdapter(dataSet: List<String> = emptyList()) : DragDropSwipeAdapter<String, OrderAdapter.ViewHolder>(dataSet) {

    class ViewHolder(itemView: View) : DragDropSwipeAdapter.ViewHolder(itemView) {
        val itemText: TextView = itemView.findViewById(R.id.title_order)
        val dragIcon: ImageView = itemView.findViewById(R.id.handle_order)
    }

    override fun getViewHolder(itemLayout: View) = OrderAdapter.ViewHolder(itemLayout)

    override fun onBindViewHolder(item: String, viewHolder: OrderAdapter.ViewHolder, position: Int) {
        // Here we update the contents of the view holder's views to reflect the item's data
        viewHolder.itemText.text = item
    }

    override fun getViewToTouchToStartDraggingItem(item: String, viewHolder: OrderAdapter.ViewHolder, position: Int): View? {
        // We return the view holder's view on which the user has to touch to drag the item
        return viewHolder.dragIcon
    }
}


