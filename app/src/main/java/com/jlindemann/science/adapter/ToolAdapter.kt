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
) : DragDropSwipeAdapter<ToolItem, ToolAdapter.BaseViewHolder>(dataSet) {

    private var isReorderMode = false

    interface OnToolItemClickListener {
        fun onToolItemClick(item: ToolItem)
    }

    fun setHeaderBindingAction(action: (View) -> Unit) {
        // Legacy support - headers are now handled in Fragment layouts
    }

    fun setReorderMode(enabled: Boolean) {
        isReorderMode = enabled
        notifyDataSetChanged()
    }

    override fun getViewHolder(itemView: View): BaseViewHolder {
        return BaseViewHolder(itemView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.tool_list_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(item: ToolItem, viewHolder: BaseViewHolder, position: Int) {
        if (viewHolder is ItemViewHolder) {
            viewHolder.bind(item, clickListener, isReorderMode, context)
        }
    }

    override fun canBeDragged(item: ToolItem, viewHolder: BaseViewHolder, position: Int): Boolean {
        return isReorderMode
    }

    override fun canBeDroppedOver(item: ToolItem, viewHolder: BaseViewHolder, position: Int): Boolean {
        return true
    }

    override fun canBeSwiped(item: ToolItem, viewHolder: BaseViewHolder, position: Int): Boolean {
        return false
    }

    override fun getViewToTouchToStartDraggingItem(item: ToolItem, viewHolder: BaseViewHolder, position: Int): View? {
        return if (isReorderMode && viewHolder is ItemViewHolder) {
            viewHolder.itemView.findViewById(R.id.drag_handle)
        } else null
    }

    open class BaseViewHolder(itemView: View) : ViewHolder(itemView)

    class ItemViewHolder(itemView: View) : BaseViewHolder(itemView) {
        private val cardView: View = itemView.findViewById(R.id.tool_card)
        private val titleText: TextView = itemView.findViewById(R.id.tool_title)
        private val descriptionText: TextView = itemView.findViewById(R.id.tool_description)
        private val openButton: TextView = itemView.findViewById(R.id.tool_button)
        private val proPlusBadge: TextView? = itemView.findViewById(R.id.pro_plus_badge)
        private val newBadge: TextView? = itemView.findViewById(R.id.new_badge)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        private val space: TextView = itemView.findViewById(R.id.space_drag)

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
                descriptionText.visibility = View.GONE
                space.visibility = View.VISIBLE
                itemView.alpha = 0.8f
                itemView.setOnClickListener(null)
                cardView.setOnClickListener(null)
                openButton.setOnClickListener(null)
            } else {
                dragHandle.visibility = View.GONE
                openButton.visibility = View.VISIBLE
                descriptionText.visibility = View.VISIBLE
                space.visibility = View.GONE
                itemView.alpha = 1.0f
                
                val listener = View.OnClickListener { clickListener.onToolItemClick(item) }
                itemView.setOnClickListener(listener)
                cardView.setOnClickListener(listener)
                openButton.setOnClickListener(listener)
            }
        }
    }
}
