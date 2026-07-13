package com.jlindemann.science.ai

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.model.ChatSession
import java.util.UUID

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
            "language" to session.language,
            "messages" to session.messages.map {
                hashMapOf("id" to it.id, "text" to it.text, "isFromUser" to it.isFromUser, "timestamp" to it.timestamp)
            }
        )
        val docRef = if (session.id.isNotEmpty()) getChatCollection(uid).document(session.id)
                     else getChatCollection(uid).document()
        docRef.set(chatData)
            .addOnSuccessListener { onComplete(true, docRef.id) }
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
                onLoaded(result.map { doc -> documentToSession(doc.id, doc.data) })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading chat history", e)
                onLoaded(emptyList())
            }
    }

    fun loadLatestChatSession(onLoaded: (ChatSession?) -> Unit) {
        val uid = AuthManager.getUid() ?: return onLoaded(null)
        getChatCollection(uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val doc = result.documents.firstOrNull()
                onLoaded(doc?.let { documentToSession(it.id, it.data ?: emptyMap()) })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading latest chat session", e)
                onLoaded(null)
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun documentToSession(id: String, data: Map<String, Any?>): ChatSession {
        val messages = (data["messages"] as? List<Map<String, Any>>)?.map {
            ChatMessage(
                id = it["id"] as? String ?: UUID.randomUUID().toString(),
                text = it["text"] as? String ?: "",
                isFromUser = it["isFromUser"] as? Boolean ?: true,
                timestamp = it["timestamp"] as? Long ?: 0L
            )
        } ?: emptyList()
        return ChatSession(
            id = id,
            title = data["title"] as? String ?: "",
            timestamp = data["timestamp"] as? Long ?: 0L,
            messages = messages,
            language = data["language"] as? String ?: "en"
        )
    }
}
