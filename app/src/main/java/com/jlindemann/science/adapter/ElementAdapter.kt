package com.jlindemann.science.adapter

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.jlindemann.science.R

class ElementAdapter(private val context: Context,
                      private val list: Array<String>
) : RecyclerView.Adapter<ElementAdapter.CategoryViewHolder>() {
    companion object ResMap {
        val KEYWORDS: Array<String> = arrayOf("Hydrogen", "Helium", "Lithium")
    }


    var onClickItem: ((string: String) -> Unit)? = null

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(list[holder.adapterPosition])
    }

    override fun getItemCount(): Int = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        return CategoryViewHolder(LayoutInflater.from(context).inflate(R.layout.element_list, parent, false))
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        @BindView(R.id.title2)
        lateinit var categoryName: TextView

        private var category: String? = null

        init {
            ButterKnife.bind(this, itemView)
            itemView.setOnClickListener {
                category?.let {
                    onClickItem?.invoke(it)
                }
            }
        }

        fun bind(cate: String) {
            category = cate
            categoryName.text = cate
        }
    }
}