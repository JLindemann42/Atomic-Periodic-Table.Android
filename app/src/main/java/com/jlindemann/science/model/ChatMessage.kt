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
    val actions: String? = null,
    /**
     * The visual card attached to this answer, encoded by
     * [com.jlindemann.science.ai.cards.ChatCardCodec].
     *
     * A string for the same reasons as [actions]: `@Parcelize` needs no custom writer, the field is
     * trailing and defaulted so every positional constructor call still compiles, and an older build
     * — or a restored session — decodes an unknown card to null and simply shows the text.
     *
     * It holds a *reference* to what to draw, not the data, so a 42-isotope chart costs about thirty
     * bytes here rather than two kilobytes through a Parcel and into Firestore.
     */
    val cards: String? = null
) : Parcelable
