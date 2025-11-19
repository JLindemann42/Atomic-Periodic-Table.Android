package com.jlindemann.science.model

data class ToolItem(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val requiresProPlus: Boolean = false,
    val showNewBadge: Boolean = false,
    var order: Int = 0
)
