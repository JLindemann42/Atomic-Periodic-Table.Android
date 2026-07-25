package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.Filter
import com.jlindemann.science.ai.core.Op
import com.jlindemann.science.ai.data.Quantity
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.data.UnitConverter
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * Everything the extractor found in one query.
 *
 * [hasOperatorEvidence] is what the planner uses to decide whether a query is a computation at
 * all. Queries with no operator evidence are plain lookups and are left to the existing handlers.
 */
data class Operators(
    val comparators: List<Pair<Op, Quantity>> = emptyList(),
    val subsetFilters: List<Filter> = emptyList(),
    val aggregation: Aggregation = Aggregation.NONE,
    val superlativeDescending: Boolean? = null,
    val topN: Int? = null,
    val targetUnit: String? = null,
    val ordinal: Int? = null,
    val isListQuestion: Boolean = false
) {
    val hasOperatorEvidence: Boolean
        get() = comparators.isNotEmpty() || subsetFilters.isNotEmpty() ||
                aggregation != Aggregation.NONE || superlativeDescending != null ||
                topN != null || targetUnit != null
}

/**
 * Pulls comparators, subsets, aggregations and unit requests out of a query.
 *
 * This is what makes multi-hop questions expressible. "Which transition metals melt above
 * 2000 °C" yields a series filter plus a greater-than comparator, which together drive a scan
 * over all 118 elements — a shape the previous keyword router had no way to represent.
 */
object OperatorExtractor {

    /** A number optionally followed by a unit, e.g. "2000 °C", "19.3 g/cm3", "5000". */
    private val NUMBER_WITH_UNIT =
        Regex("""(-?\d+(?:[.,]\d+)?)\s*(°?[a-zA-Zµμ°/³²Ω]+(?:/[a-zA-Z·()]+)?)?""")

    private val TOP_N = Regex("""\b(?:top|first|best|de|die|los|les|i|as)?\s*(\d{1,3})\s*(?=\p{L})""")

    fun extract(rawQuery: String): Operators {
        val query = TextMatching.normalizeForLookup(rawQuery)

        val comparators = ArrayList<Pair<Op, Quantity>>()
        val subsets = ArrayList<Filter>()

        // ---- Comparators ------------------------------------------------------------------
        // The longest phrase wins, so "at least" is not read as a bare "least" superlative.
        val greater = longestMatch(query, Lexicon.GREATER)
        val less = longestMatch(query, Lexicon.LESS)
        greater?.let { phrase ->
            numberAfter(query, phrase)?.let { comparators.add(Op.GT to it) }
        }
        less?.let { phrase ->
            numberAfter(query, phrase)?.let { comparators.add(Op.LT to it) }
        }

        // ---- Subsets ----------------------------------------------------------------------
        val series = LinkedHashSet<SeriesId>()
        // Family names are consumed here so their words cannot be read again below: "noble
        // gases" names a group, and treating its "gases" as a phase filter as well would
        // silently exclude oganesson, which is predicted to be solid.
        var remaining = query
        for ((word, ids) in Lexicon.SERIES_WORDS.entries.sortedByDescending { it.key.length }) {
            if (mentions(remaining, word)) {
                series.addAll(ids)
                remaining = remaining.replace(word, " ")
            }
        }
        if (series.isNotEmpty()) subsets.add(Filter.InSeries(series))
        else if (Lexicon.METAL_WORDS.any { mentions(query, it) }) subsets.add(Filter.IsMetal(true))

        for ((word, block) in Lexicon.BLOCK_WORDS) {
            if (mentions(query, word)) { subsets.add(Filter.InBlock(block)); break }
        }
        for ((word, phase) in Lexicon.PHASE_WORDS) {
            if (mentions(remaining, word)) { subsets.add(Filter.InPhase(phase)); break }
        }
        if (Lexicon.RADIOACTIVE_WORDS.any { mentions(query, it) }) {
            subsets.add(Filter.IsRadioactive(true))
        }
        // "synthetic" is checked first: "non-synthetic" contains both, and the natural reading
        // of a query mentioning synthetic at all is the synthetic set unless negated.
        when {
            Lexicon.NATURAL_WORDS.any { mentions(query, it) } -> subsets.add(Filter.IsSynthetic(false))
            Lexicon.SYNTHETIC_WORDS.any { mentions(query, it) } -> subsets.add(Filter.IsSynthetic(true))
        }
        periodOrGroup(query)?.let { subsets.add(it) }

        // ---- Aggregation ------------------------------------------------------------------
        val aggregation = when {
            Lexicon.AVERAGE.any { mentions(query, it) } -> Aggregation.AVG
            Lexicon.MEDIAN.any { mentions(query, it) } -> Aggregation.MEDIAN
            Lexicon.SUM.any { mentions(query, it) } -> Aggregation.SUM
            Lexicon.COUNT.any { mentions(query, it) } -> Aggregation.COUNT
            else -> Aggregation.NONE
        }

        // ---- Superlative direction --------------------------------------------------------
        // Kept as two distinct sets so "least dense" is not read as "densest".
        val most = Lexicon.MOST.any { mentions(query, it) }
        val least = Lexicon.LEAST.any { mentions(query, it) }
        val descending = when {
            least && !most -> false
            most && !least -> true
            most && least -> !query.indexOf(firstMention(query, Lexicon.LEAST)!!).let { leastAt ->
                leastAt < query.indexOf(firstMention(query, Lexicon.MOST)!!)
            }
            else -> null
        }

        return Operators(
            comparators = comparators,
            subsetFilters = subsets,
            aggregation = aggregation,
            superlativeDescending = descending,
            topN = topN(query),
            targetUnit = targetUnit(query),
            ordinal = ordinal(query),
            isListQuestion = Lexicon.WHICH.any { query.startsWith("$it ") || mentions(query, it) }
        )
    }

    /** The number following a phrase, with its unit if one is written. */
    private fun numberAfter(query: String, phrase: String): Quantity? {
        val at = query.indexOf(phrase)
        if (at < 0) return null
        val tail = query.substring(at + phrase.length)
        val match = NUMBER_WITH_UNIT.find(tail) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val rawUnit = match.groupValues[2].takeIf { it.isNotBlank() }
        val unit = rawUnit?.let { Lexicon.UNIT_WORDS[it.lowercase()] ?: UnitConverter.canonical(it) }
        return Quantity(value = value, unit = unit, display = match.value.trim())
    }

    private fun topN(query: String): Int? {
        val hasSuperlative = Lexicon.MOST.any { mentions(query, it) } ||
                Lexicon.LEAST.any { mentions(query, it) }
        val explicit = Regex("""\btop\s+(\d{1,3})\b""").find(query)?.groupValues?.get(1)?.toIntOrNull()
        if (explicit != null) return explicit.coerceIn(1, 118)
        if (!hasSuperlative) return null
        // "5 densest nonmetals" — a bare number only counts alongside a superlative.
        val n = TOP_N.find(query)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return n.takeIf { it in 2..118 }
    }

    /** A requested output unit, e.g. "in Fahrenheit". */
    private fun targetUnit(query: String): String? {
        for ((word, unit) in Lexicon.UNIT_WORDS.entries.sortedByDescending { it.key.length }) {
            if (word.length < 2) continue
            if (!mentions(query, word)) continue
            // Require a preposition before it, so "kelvin" inside a name is not a conversion request.
            val at = query.indexOf(word)
            val before = query.substring(0, at).trimEnd().substringAfterLast(' ')
            if (before in Lexicon.UNIT_PREPOSITIONS || word.length > 6) return unit
        }
        return null
    }

    private fun ordinal(query: String): Int? {
        for ((word, n) in Lexicon.ORDINALS) if (mentions(query, word)) return n
        return null
    }

    /**
     * "period 6", "group 11", and the same in the other supported languages.
     *
     * Ported from the structural-query handler this replaced. The number may come after the word
     * or before it, since Chinese writes 第6周期 with the number first.
     */
    private fun periodOrGroup(query: String): Filter? {
        numberNear(query, PERIOD_WORDS)?.let { if (it in 1..7) return Filter.InPeriod(it) }
        numberNear(query, GROUP_WORDS)?.let { if (it in 1..18) return Filter.InGroup(it) }
        return null
    }

    private fun numberNear(query: String, words: List<String>): Int? {
        for (word in words) {
            val at = query.indexOf(word)
            if (at < 0) continue
            Regex("""^\D{0,2}(\d{1,2})""").find(query.substring(at + word.length))
                ?.let { return it.groupValues[1].toIntOrNull() }
            Regex("""(\d{1,2})\D{0,2}$""").find(query.substring(0, at))
                ?.let { return it.groupValues[1].toIntOrNull() }
        }
        return null
    }

    /** Words naming a row of the table. "row" is a period; "column" is a group. */
    private val PERIOD_WORDS = listOf(
        "period", "periode", "periodo", "row", "rad", "rangee", "fila", "zeile",
        "hang", "पंक्ति", "आवर्त", "周期", "dor"
    )

    private val GROUP_WORDS = listOf(
        "group", "grupp", "gruppe", "groupe", "grupo", "gruppo", "column", "kolumn",
        "spalte", "colonne", "columna", "colonna", "kolom", "hanay", "hilera",
        "समूह", "族", "gurooh"
    )

    private fun mentions(query: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        // Multi-word and non-Latin phrases match by containment; single Latin words by boundary.
        return if (phrase.contains(' ') || phrase.any { it.code > 0x2000 }) query.contains(phrase)
        else TextMatching.containsWord(query, phrase)
    }

    private fun longestMatch(query: String, phrases: List<String>): String? =
        phrases.filter { mentions(query, it) }.maxByOrNull { it.length }

    private fun firstMention(query: String, phrases: List<String>): String? =
        phrases.filter { mentions(query, it) }.minByOrNull { query.indexOf(it) }
}
