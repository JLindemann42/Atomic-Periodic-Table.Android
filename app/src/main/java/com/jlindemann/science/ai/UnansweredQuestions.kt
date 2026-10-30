package com.jlindemann.science.ai

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jlindemann.science.BuildConfig

/**
 * Sends a question the assistant could not answer to the developer — only when the user asks to,
 * question by question (roadmap 0.7).
 *
 * Until now a user's screenshot was the only way to learn what goes unanswered. The FAQ promises
 * that nothing typed in the chat leaves the device, so this is opt-in per question, behind a
 * dialog that says exactly what is sent, and the FAQ says so too.
 *
 * What is sent, and nothing else: the question as typed, the chat language, the app version and
 * the decline reason. No user id, no device id, no other message. The collection is create-only;
 * the security rule is in `docs/development/persistence-and-sync.md`.
 */
object UnansweredQuestions {

    const val COLLECTION = "unanswered_questions"

    /** Longest question kept. The security rule rejects anything longer. */
    const val MAX_CHARS = 500

    /**
     * Trimmed, capped, and without the three control characters the chat-action codec uses as
     * delimiters — the question travels inside a chip's args before it is sent.
     */
    fun clean(question: String): String =
        question.filterNot { it in '\u001D'..'\u001F' }.trim().take(MAX_CHARS).trimEnd()

    fun send(text: String, language: String, reason: String?, done: (Boolean) -> Unit) {
        val question = clean(text)
        if (question.isEmpty()) {
            done(false)
            return
        }
        val doc = hashMapOf(
            "text" to question,
            "language" to language,
            "reason" to (reason ?: "unknown"),
            "appVersion" to BuildConfig.VERSION_NAME,
            "platform" to "android",
            "createdAt" to FieldValue.serverTimestamp()
        )
        runCatching {
            FirebaseFirestore.getInstance().collection(COLLECTION).add(doc)
                .addOnSuccessListener { done(true) }
                .addOnFailureListener { done(false) }
        }.onFailure { done(false) }
    }
}
