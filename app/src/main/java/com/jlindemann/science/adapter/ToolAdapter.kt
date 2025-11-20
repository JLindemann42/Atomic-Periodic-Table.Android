package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.jlindemann.science.R
import com.jlindemann.science.model.ToolItem

class ToolAdapter(
    private val context: Context,
    dataSet: List<ToolItem> = emptyList(),
    private val clickListener: OnToolItemClickListener
) : DragDropSwipeAdapter<ToolItem, ToolAdapter.ViewHolder>(dataSet) {

    private var isReorderMode = false

    interface OnToolItemClickListener {
        fun onToolItemClick(item: ToolItem)
    }

    fun setReorderMode(enabled: Boolean) {
        isReorderMode = enabled
        notifyDataSetChanged()
    }

    override fun getViewHolder(itemLayout: View): ViewHolder {
        return ViewHolder(itemLayout)
    }

    override fun onBindViewHolder(item: ToolItem, viewHolder: ViewHolder, position: Int) {
        viewHolder.bind(item, clickListener, isReorderMode, context)
    }

    override fun getViewToTouchToStartDraggingItem(
        item: ToolItem,
        viewHolder: ViewHolder,
        position: Int
    ): View? {
        return if (isReorderMode) viewHolder.itemView else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tool_list_item, parent, false)
        return ViewHolder(view)
    }

    class ViewHolder(itemView: View) : DragDropSwipeAdapter.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.tool_title)
        private val descriptionText: TextView = itemView.findViewById(R.id.tool_description)
        private val openButton: TextView = itemView.findViewById(R.id.tool_button)
        private val proPlusBadge: TextView? = itemView.findViewById(R.id.pro_plus_badge)
        private val newBadge: TextView? = itemView.findViewById(R.id.new_badge)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(
            item: ToolItem,
            clickListener: OnToolItemClickListener,
            isReorderMode: Boolean,
            context: Context
        ) {
            titleText.text = context.getString(item.titleResId)
            descriptionText.text = context.getString(item.descriptionResId)
            
            proPlusBadge?.visibility = if (item.requiresProPlus) View.VISIBLE else View.GONE
            newBadge?.visibility = if (item.showNewBadge && !isReorderMode) View.VISIBLE else View.GONE

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
                itemView.setOnClickListener { clickListener.onToolItemClick(item) }
                openButton.setOnClickListener { clickListener.onToolItemClick(item) }
            } else {
                itemView.setOnClickListener(null)
                openButton.setOnClickListener(null)
            }
        }
    }
}
