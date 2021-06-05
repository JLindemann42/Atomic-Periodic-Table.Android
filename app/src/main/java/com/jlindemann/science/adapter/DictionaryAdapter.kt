package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.DictionaryActivity
import com.jlindemann.science.model.Dictionary

class DictionaryAdapter(var dictionaryList: ArrayList<Dictionary>, var clickListener: DictionaryActivity, val con: Context) : RecyclerView.Adapter<DictionaryAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.initialize(dictionaryList[position], clickListener, con)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.text_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return dictionaryList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val heading = itemView.findViewById(R.id.tv_title) as TextView
        private val text = itemView.findViewById(R.id.tv_text) as TextView
        private val wikiBtn = itemView.findViewById(R.id.wiki_btn) as Button
        private val frame = itemView.findViewById(R.id.rCard) as FrameLayout

        fun initialize(item: Dictionary, action: OnDictionaryClickListener, con: Context) {

            heading.text = item.heading
            text.text = item.text
            val url = item.wiki

            wikiBtn.foreground = ContextCompat.getDrawable(con, R.drawable.c_ripple)
            wikiBtn.isClickable = true
            wikiBtn.isFocusable = true

            wikiBtn.setOnClickListener {
                action.dictionaryClickListener(item, wikiBtn, url, adapterPosition)
            }
        }
    }

    interface OnDictionaryClickListener {
        fun dictionaryClickListener(item: Dictionary, wiki: Button, url: String, position: Int)
    }

    fun filterList(filteredList: ArrayList<Dictionary>) {
        dictionaryList = filteredList
        notifyDataSetChanged()
    }

}