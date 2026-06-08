package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.databinding.ItemChatMessageBinding
import com.jlindemann.science.model.ChatMessage

class ChatMessageAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.ChatViewHolder>() {
    
    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.messageText.text = message.text
            
            // Show/hide user or AI message containers
            if (message.isFromUser) {
                binding.userMessageContainer.visibility = android.view.View.VISIBLE
                binding.aiMessageContainer.visibility = android.view.View.GONE
            } else {
                binding.userMessageContainer.visibility = android.view.View.GONE
                binding.aiMessageContainer.visibility = android.view.View.VISIBLE
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount() = messages.size
}
