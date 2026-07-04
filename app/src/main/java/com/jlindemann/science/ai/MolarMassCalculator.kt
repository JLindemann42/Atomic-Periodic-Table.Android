package com.jlindemann.science.ai

import org.json.JSONObject

/**
 * Calculator for molar mass of chemical formulas.
 * Optimized and improved to handle various formats and lowercase inputs.
 * Distinguishes between N+H (Nitrogen + Hydrogen) and Nh (Nihonium) by respecting casing.
 */
class MolarMassCalculator(private val elementData: JSONObject?) {

    private val symbolMap = mutableMapOf<String, Double>()
    private val lowerSymbolMap = mutableMapOf<String, Double>()
    private val nameMap = mutableMapOf<String, String>()
    private val fullNameMap = mutableMapOf<String, String>() // Symbol to Full Name

    init {
        initializeMaps()
    }

    private fun initializeMaps() {
        val keys = elementData?.keys() ?: return
        while (keys.hasNext()) {
            val key = keys.next()
            val element = elementData.optJSONObject(key) ?: continue
            val symbol = element.optString("short", "")
            val name = element.optString("element", "").lowercase()
            val massStr = element.optString("element_atomicmass", "")
            val mass = massStr.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
            
            if (symbol.isNotEmpty()) {
                symbolMap[symbol] = mass
                fullNameMap[symbol] = element.optString("element", "Unknown")
                if (!lowerSymbolMap.containsKey(symbol.lowercase())) {
                    lowerSymbolMap[symbol.lowercase()] = mass
                }
            }
            if (name.isNotEmpty()) {
                nameMap[name] = symbol
            }
        }
    }

    /**
     * Calculate molar mass for a given formula string.
     * Returns the mass in g/mol or null if parsing fails.
     */
    fun calculate(formula: String): Double? {
        if (elementData == null || formula.isBlank()) return null
        
        val cleanFormula = formula.trim().replace(" ", "")
        
        // 1. Check if it's a full element name
        val symbolFromName = nameMap[cleanFormula.lowercase()]
        if (symbolFromName != null) {
            return symbolMap[symbolFromName]
        }

        // 2. Try to parse as formula
        return try {
            val elementCounts = getElementCounts(cleanFormula)
            if (elementCounts.isEmpty()) return null
            
            var totalMass = 0.0
            for ((symbol, count) in elementCounts) {
                val mass = symbolMap[symbol] ?: lowerSymbolMap[symbol.lowercase()] ?: 0.0
                totalMass += mass * count
            }
            if (totalMass == 0.0) null else totalMass
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns a map of element symbols and their quantities in the formula.
     */
    fun getElementCounts(formula: String): Map<String, Double> {
        val counts = mutableMapOf<String, Double>()
        try {
            val cleanFormula = formula.trim().replace(" ", "")
            parseRecursive(cleanFormula, 1.0, counts)
        } catch (e: Exception) { }
        return counts
    }

    private fun parseRecursive(formula: String, multiplier: Double, counts: MutableMap<String, Double>) {
        var processed = formula
        val bracketRegex = Regex("\\(([^()]*)\\)(\\d*\\.?\\d*)")
        
        // Find top-level brackets
        val match = bracketRegex.find(processed)
        if (match != null) {
            val subFormula = match.groupValues[1]
            val subMultiplier = match.groupValues[2].toDoubleOrNull() ?: 1.0
            
            // Parse everything before the brackets
            val before = processed.substring(0, match.range.first)
            if (before.isNotEmpty()) parseSimplePart(before, multiplier, counts)
            
            // Parse inside brackets
            parseRecursive(subFormula, multiplier * subMultiplier, counts)
            
            // Parse everything after the brackets
            val after = processed.substring(match.range.last + 1)
            if (after.isNotEmpty()) parseRecursive(after, multiplier, counts)
        } else {
            parseSimplePart(processed, multiplier, counts)
        }
    }

    private fun parseSimplePart(formula: String, multiplier: Double, counts: MutableMap<String, Double>) {
        val hasUpperCase = formula.any { it.isUpperCase() }
        if (hasUpperCase) {
            val regex = Regex("([A-Z][a-z]*)(\\d*\\.?\\d*)")
            regex.findAll(formula).forEach { match ->
                val symbol = match.groupValues[1]
                val count = (match.groupValues[2].toDoubleOrNull() ?: 1.0) * multiplier
                counts[symbol] = counts.getOrDefault(symbol, 0.0) + count
            }
        } else {
            // Lowercase logic
            var i = 0
            while (i < formula.length) {
                var foundLen = 0
                var symbolFound = ""
                for (len in 2 downTo 1) {
                    if (i + len <= formula.length) {
                        val part = formula.substring(i, i + len)
                        if (lowerSymbolMap.containsKey(part)) {
                            // Heuristic split
                            if (len == 2) {
                                val first = part.substring(0, 1)
                                val second = part.substring(1, 2)
                                if (lowerSymbolMap.containsKey(first) && lowerSymbolMap.containsKey(second)) {
                                    val commonClashes = listOf("nh", "co", "no", "hf", "sc", "ni")
                                    if (commonClashes.contains(part)) continue
                                }
                            }
                            symbolFound = part
                            foundLen = len
                            break
                        }
                    }
                }
                if (foundLen > 0) {
                    i += foundLen
                    var countStr = ""
                    while (i < formula.length && (formula[i].isDigit() || formula[i] == '.')) {
                        countStr += formula[i]
                        i++
                    }
                    val count = (if (countStr.isEmpty()) 1.0 else countStr.toDoubleOrNull() ?: 1.0) * multiplier
                    // Normalize symbol to actual map key
                    val actualSymbol = nameMap.entries.find { it.value.lowercase() == symbolFound.lowercase() }?.value 
                        ?: symbolFound.replaceFirstChar { it.uppercase() }
                    counts[actualSymbol] = counts.getOrDefault(actualSymbol, 0.0) + count
                } else i++
            }
        }
    }

    fun getElementName(symbol: String): String = fullNameMap[symbol] ?: symbol
}
