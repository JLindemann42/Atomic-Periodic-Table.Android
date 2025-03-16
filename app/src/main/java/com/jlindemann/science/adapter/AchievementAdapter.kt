package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.UserActivity
import com.jlindemann.science.model.Achievement

class AchievementAdapter(var list: ArrayList<Achievement>, var clickListener: UserActivity, val context: Context) : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_item_achievement, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.achievement_title)
        private val description: TextView = itemView.findViewById(R.id.achievement_description)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.achievement_progress)

        fun initialize(achievement: Achievement, action: OnAchievementClickListener, context: Context) {
            title.text = achievement.title
            description.text = achievement.description
            progressBar.max = achievement.maxProgress
            progressBar.progress = achievement.progress

            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setOnClickListener {
                action.achievementClickListener(achievement, adapterPosition)
            }
        }
    }

    fun filterList(filteredList: ArrayList<Achievement>) {
        list = filteredList
        notifyDataSetChanged()
    }

    interface OnAchievementClickListener {
        fun achievementClickListener(item: Achievement, position: Int)
    }

}


