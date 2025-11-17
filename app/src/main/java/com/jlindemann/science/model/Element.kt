package com.jlindemann.science.model

data class Element(
    val element: String,      // Localized display name (e.g., "Hydrogen", "Wasserstoff")
    val short: String,        // Chemical symbol (e.g., "H", "He")
    val number: Int,          // Atomic number
    val electro: Double,      // Electronegativity
    val isotopes: Int,        // Number of isotopes
    val elementKey: String = element.lowercase()  // Lowercase English key for JSON lookup (e.g., "hydrogen")
)

