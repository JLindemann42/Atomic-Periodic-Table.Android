package com.jlindemann.science.model

data class TableItem(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val requiresPro: Boolean = false,
    var order: Int = 0
)
