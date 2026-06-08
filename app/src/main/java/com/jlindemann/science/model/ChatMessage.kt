package com.jlindemann.science.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val isFromUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable
