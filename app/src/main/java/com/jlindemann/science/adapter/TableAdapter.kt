package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.jlindemann.science.R
import com.jlindemann.science.model.TableItem

class TableAdapter(
    private val context: Context,
    dataSet: List<TableItem> = emptyList(),
    private val clickListener: OnTableItemClickListener
) : DragDropSwipeAdapter<TableItem, TableAdapter.ViewHolder>(dataSet) {

    private var isReorderMode = false

    interface OnTableItemClickListener {
        fun onTableItemClick(item: TableItem)
    }

    fun setReorderMode(enabled: Boolean) {
        isReorderMode = enabled
        notifyDataSetChanged()
    }

    override fun getViewHolder(itemLayout: View): ViewHolder {
        return ViewHolder(itemLayout)
    }

    override fun onBindViewHolder(item: TableItem, viewHolder: ViewHolder, position: Int) {
        viewHolder.bind(item, clickListener, isReorderMode, context)
    }

    override fun getViewToTouchToStartDraggingItem(
        item: TableItem,
        viewHolder: ViewHolder,
        position: Int
    ): View? {
        return if (isReorderMode) viewHolder.itemView else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.table_list_item, parent, false)
        return ViewHolder(view)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.table_title)
        private val descriptionText: TextView = itemView.findViewById(R.id.table_description)
        private val openButton: TextView = itemView.findViewById(R.id.table_button)
        private val proBadge: TextView = itemView.findViewById(R.id.pro_badge)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(
            item: TableItem,
            clickListener: OnTableItemClickListener,
            isReorderMode: Boolean,
            context: Context
        ) {
            titleText.text = context.getString(item.titleResId)
            descriptionText.text = context.getString(item.descriptionResId)
            
            if (item.requiresPro) {
                proBadge.visibility = View.VISIBLE
            } else {
                proBadge.visibility = View.GONE
            }

            if (isReorderMode) {
                dragHandle.visibility = View.VISIBLE
                openButton.visibility = View.GONE
                itemView.alpha = 0.8f
            } else {
                dragHandle.visibility = View.GONE
                openButton.visibility = View.VISIBLE
                itemView.alpha = 1.0f
            }

            if (!isReorderMode) {
                itemView.setOnClickListener { clickListener.onTableItemClick(item) }
                openButton.setOnClickListener { clickListener.onTableItemClick(item) }
            } else {
                itemView.setOnClickListener(null)
                openButton.setOnClickListener(null)
            }
        }
    }
}
