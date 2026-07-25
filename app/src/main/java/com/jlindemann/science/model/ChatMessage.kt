package com.jlindemann.science.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val isFromUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Tappable actions offered alongside the message, as a JSON array from
     * `ChatActionCodec` — normally the sources an answer was drawn from.
     *
     * A JSON string rather than a typed list so `@Parcelize` needs no custom writers, and
     * trailing-and-defaulted so every existing positional constructor call still compiles.
     * Not persisted: these are ephemeral UI affordances, and stored chats decode to null.
     */
    val actions: String? = null
) : Parcelable
