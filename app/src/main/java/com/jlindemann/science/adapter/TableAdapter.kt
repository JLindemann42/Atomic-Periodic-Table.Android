package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.RecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.jlindemann.science.R
import com.jlindemann.science.model.TableItem
import org.apache.commons.math3.geometry.Space

class TableAdapter(
    private val context: Context,
    dataSet: List<TableItem> = emptyList(),
    private val clickListener: OnTableItemClickListener
) : DragDropSwipeAdapter<TableItem, TableAdapter.BaseViewHolder>(dataSet) {

    private var isReorderMode = false
    private var headerBindingAction: ((View) -> Unit)? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    interface OnTableItemClickListener {
        fun onTableItemClick(item: TableItem)
    }

    fun setHeaderBindingAction(action: (View) -> Unit) {
        headerBindingAction = action
    }

    fun setReorderMode(enabled: Boolean) {
        isReorderMode = enabled
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun getViewHolder(itemLayout: View): BaseViewHolder {
        return ItemViewHolder(itemLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.table_list_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.table_list_item, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(item: TableItem, viewHolder: BaseViewHolder, position: Int) {
        // This is called for items by the library, but we handle it in our own onBindViewHolder(holder, position)
        // to manage the header offset.
    }

    override fun getItemCount(): Int {
        return dataSet.size + 1
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            headerBindingAction?.invoke(holder.itemView)
            setInternalViewHolderFields(holder, canDrag = false, canDrop = false, canSwipe = false)
        } else {
            val item = dataSet[position - 1]
            if (holder is ItemViewHolder) {
                holder.bind(item, clickListener, isReorderMode, context)
                
                // Manually trigger the library's internal setup logic via reflection
                setInternalViewHolderFields(
                    holder,
                    canDrag = canBeDragged(item, holder, position),
                    canDrop = canBeDroppedOver(item, holder, position),
                    canSwipe = false
                )
                
                // Using reflection to call the protected setViewForDragging method
                setupDragHandle(item, holder, position)
            }
        }
    }

    private fun setInternalViewHolderFields(holder: BaseViewHolder, canDrag: Boolean, canDrop: Boolean, canSwipe: Boolean) {
        try {
            val viewHolderClass = DragDropSwipeAdapter.ViewHolder::class.java
            
            val canBeDraggedField = viewHolderClass.getDeclaredField("canBeDragged")
            canBeDraggedField.isAccessible = true
            canBeDraggedField.set(holder, { canDrag })

            val canBeDroppedOverField = viewHolderClass.getDeclaredField("canBeDroppedOver")
            canBeDroppedOverField.isAccessible = true
            canBeDroppedOverField.set(holder, { canDrop })

            val canBeSwipedField = viewHolderClass.getDeclaredField("canBeSwiped")
            canBeSwipedField.isAccessible = true
            canBeSwipedField.set(holder, { canSwipe })
            
            holder.itemView.alpha = if (isReorderMode && canDrag) 0.8f else 1.0f
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDragHandle(item: TableItem, holder: BaseViewHolder, position: Int) {
        try {
            val setViewForDraggingMethod = DragDropSwipeAdapter::class.java.getDeclaredMethods().find { it.name == "setViewForDragging" }
            setViewForDraggingMethod?.let {
                it.isAccessible = true
                it.invoke(this, item, holder, position)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun canBeDragged(item: TableItem, viewHolder: BaseViewHolder, position: Int): Boolean {
        return position > 0 && isReorderMode
    }

    override fun canBeDroppedOver(
        item: TableItem,
        viewHolder: BaseViewHolder,
        position: Int
    ): Boolean {
        return position > 0
    }

    override fun getViewToTouchToStartDraggingItem(
        item: TableItem,
        viewHolder: BaseViewHolder,
        position: Int
    ): View? {
        return if (isReorderMode && viewHolder is ItemViewHolder) {
            viewHolder.itemView.findViewById(R.id.drag_handle)
        } else null
    }

    open class BaseViewHolder(itemView: View) : DragDropSwipeAdapter.ViewHolder(itemView)

    class HeaderViewHolder(itemView: View) : BaseViewHolder(itemView)

    class ItemViewHolder(itemView: View) : BaseViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.table_title)
        private val descriptionText: TextView = itemView.findViewById(R.id.table_description)
        private val openButton: TextView = itemView.findViewById(R.id.table_button)
        private val proBadge: TextView = itemView.findViewById(R.id.pro_badge)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        private val space: TextView = itemView.findViewById(R.id.space_drag)

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
                descriptionText.visibility = View.GONE
                space.visibility = View.VISIBLE
                proBadge.visibility = View.GONE
                itemView.alpha = 0.8f
            } else {
                dragHandle.visibility = View.GONE
                openButton.visibility = View.VISIBLE
                descriptionText.visibility = View.VISIBLE
                space.visibility = View.GONE
                if (item.requiresPro) {
                    proBadge.visibility = View.VISIBLE
                } else {
                    proBadge.visibility = View.GONE
                }
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
