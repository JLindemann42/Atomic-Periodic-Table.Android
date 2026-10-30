package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryAdapter(
    private val sessions: List<ChatSession>,
    private val onItemClicked: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.history_title)
        private val timestamp: TextView = itemView.findViewById(R.id.history_timestamp)

        fun bind(session: ChatSession) {
            title.text = if (session.title.isNotEmpty()) session.title else "Untitled Chat"
            timestamp.text = dateFormat.format(Date(session.timestamp))
            itemView.setOnClickListener { onItemClicked(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size
}
