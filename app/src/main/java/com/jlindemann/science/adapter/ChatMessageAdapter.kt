package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.model.ChatMessage

class ChatMessageAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.ChatViewHolder>() {
    
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: CardView = itemView.findViewById(R.id.userMessageContainer)
        private val aiContainer: CardView = itemView.findViewById(R.id.aiMessageContainer)
        private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val aiMascotIcon: android.widget.ImageView = itemView.findViewById(R.id.aiMascotIcon)
        
        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                userContainer.visibility = View.VISIBLE
                aiContainer.visibility = View.GONE
                aiMascotIcon.visibility = View.GONE
                userMessageText.text = message.text
            } else {
                userContainer.visibility = View.GONE
                aiContainer.visibility = View.VISIBLE
                aiMascotIcon.visibility = View.VISIBLE
                aiMessageText.text = message.text
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount() = messages.size
}
