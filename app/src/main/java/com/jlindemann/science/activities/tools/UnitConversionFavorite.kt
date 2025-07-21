package com.jlindemann.science.activities.tools

data class UnitConversionFavorite(
    val category: String,
    val fromUnit: String,
    val toUnit: String,
    val inputValue: Double,
    val convertedValue: Double
)