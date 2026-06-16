package com.jlindemann.science.ai

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.model.ChatSession

object ChatHistoryManager {
    private const val TAG = "ChatHistoryManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun getChatCollection(uid: String) = 
        db.collection("users").document(uid).collection("chats")

    fun saveChatSession(session: ChatSession, onComplete: (Boolean, String?) -> Unit) {
        val uid = AuthManager.getUid() ?: return onComplete(false, null)
        
        val chatData = hashMapOf(
            "title" to session.title,
            "timestamp" to session.timestamp,
            "messages" to session.messages.map { 
                hashMapOf(
                    "text" to it.text,
                    "isFromUser" to it.isFromUser,
                    "timestamp" to it.timestamp
                )
            }
        )

        val docRef = if (session.id.isNotEmpty()) {
            getChatCollection(uid).document(session.id)
        } else {
            getChatCollection(uid).document()
        }

        docRef.set(chatData)
            .addOnSuccessListener {
                onComplete(true, docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving chat session", e)
                onComplete(false, null)
            }
    }

    fun loadChatHistory(onLoaded: (List<ChatSession>) -> Unit) {
        val uid = AuthManager.getUid() ?: return onLoaded(emptyList())

        getChatCollection(uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val sessions = result.map { doc ->
                    val messagesList = (doc.get("messages") as? List<Map<String, Any>>)?.map {
                        ChatMessage(
                            text = it["text"] as? String ?: "",
                            isFromUser = it["isFromUser"] as? Boolean ?: true,
                            timestamp = it["timestamp"] as? Long ?: 0L
                        )
                    } ?: emptyList()

                    ChatSession(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        messages = messagesList
                    )
                }
                onLoaded(sessions)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading chat history", e)
                onLoaded(emptyList())
            }
    }
}
