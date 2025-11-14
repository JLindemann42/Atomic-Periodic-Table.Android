package com.jlindemann.science.model

data class Series(
    val name: String,           // Localized display name
    val voltage: Double,        // Standard electrode potential
    val charge: String,         // Ion charge (e.g., "+", "2+")
    val short: String,          // Chemical symbol
    val nameKey: String = name.lowercase()  // Lowercase English key for JSON lookup
)

