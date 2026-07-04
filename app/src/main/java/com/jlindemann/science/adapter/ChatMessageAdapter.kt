package com.jlindemann.science.adapter

import android.animation.ValueAnimator
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
        private val aiRoot: View = itemView.findViewById(R.id.aiMessageRoot)
        private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
        private val aiMascotIcon: android.widget.ImageView = itemView.findViewById(R.id.aiMascotIcon)
        private val aiGlow: View = itemView.findViewById(R.id.aiMessageGlow)
        
        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                userContainer.visibility = View.VISIBLE
                aiRoot.visibility = View.GONE
                aiMascotIcon.visibility = View.GONE
                userMessageText.text = message.text
            } else {
                userContainer.visibility = View.GONE
                aiRoot.visibility = View.VISIBLE
                aiMascotIcon.visibility = View.VISIBLE
                aiMessageText.text = message.text
                
                // Entrance animation
                animateEntrance(aiRoot, aiMascotIcon)
                
                // Start pulsating glow effect
                startGlowAnimation(aiGlow)
                // Start mascot pulse
                startMascotAnimation(aiMascotIcon)
            }
        }

        private fun animateEntrance(root: View, mascot: View) {
            root.alpha = 0f
            root.translationX = -50f
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            mascot.alpha = 0f
            mascot.scaleX = 0f
            mascot.scaleY = 0f
            mascot.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }

        private fun startGlowAnimation(view: View) {
            val animator = ValueAnimator.ofFloat(0.1f, 1.0f)
            animator.duration = 1000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.REVERSE
            animator.addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                view.alpha = value
                val scale = 1.0f + (value * 0.2f)
                view.scaleX = scale
                view.scaleY = scale
            }
            animator.start()
        }

        private fun startMascotAnimation(view: View) {
            view.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .rotation(15f)
                .setDuration(1000)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .rotation(-15f)
                        .setDuration(1000)
                        .withEndAction {
                            startMascotAnimation(view)
                        }
                        .start()
                }
                .start()
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
