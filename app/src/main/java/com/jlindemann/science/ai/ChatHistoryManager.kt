package com.jlindemann.science.ai

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.ChatMessage
import com.jlindemann.science.model.ChatSession
import java.util.UUID

object ChatHistoryManager {
    private const val TAG = "ChatHistoryManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun getUserDocument(uid: String) =
        db.collection("users").document(uid)

    fun saveChatSession(session: ChatSession, onComplete: (Boolean, String?) -> Unit) {
        val uid = AuthManager.getUid() ?: return onComplete(false, null)
        val docRef = getUserDocument(uid)

        // Convert current session to a map
        val chatData = hashMapOf(
            "id" to if (session.id.isEmpty()) UUID.randomUUID().toString() else session.id,
            "title" to session.title,
            "timestamp" to session.timestamp,
            "language" to session.language,
            "messages" to session.messages.map {
                hashMapOf("id" to it.id, "text" to it.text, "isFromUser" to it.isFromUser, "timestamp" to it.timestamp)
            }
        )

        val sessionId = chatData["id"] as String

        // Load existing chats, update/append the current one, and save back
        docRef.get().addOnSuccessListener { snap ->
            @Suppress("UNCHECKED_CAST")
            val existingChats = (snap.get("chats") as? List<Map<String, Any?>>)?.toMutableList() ?: mutableListOf()
            
            // Find if this session already exists and replace it, otherwise append
            val index = existingChats.indexOfFirst { it["id"] == sessionId }
            if (index >= 0) {
                existingChats[index] = chatData
            } else {
                existingChats.add(chatData)
            }

            // Keep only latest 20 chats to prevent document size limits (optional but recommended)
            val trimmedChats = existingChats.sortedByDescending { it["timestamp"] as? Long ?: 0L }.take(20)

            docRef.update("chats", trimmedChats)
                .addOnSuccessListener { onComplete(true, sessionId) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating chats in user doc", e)
                    onComplete(false, null)
                }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error fetching user doc for chat save", e)
            onComplete(false, null)
        }
    }

    fun loadChatHistory(onLoaded: (List<ChatSession>) -> Unit) {
        val uid = AuthManager.getUid() ?: return onLoaded(emptyList())
        getUserDocument(uid).get()
            .addOnSuccessListener { snap ->
                val chats = (snap.get("chats") as? List<Map<String, Any?>>)?.map { doc ->
                    documentToSession(doc["id"] as? String ?: "", doc)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                onLoaded(chats)
            }
            .addOnFailureListener { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w(TAG, "Permission denied loading chat history")
                } else {
                    Log.e(TAG, "Error loading chat history", e)
                }
                onLoaded(emptyList())
            }
    }

    fun loadLatestChatSession(onLoaded: (ChatSession?) -> Unit) {
        loadChatHistory { history ->
            onLoaded(history.firstOrNull())
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
