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
    val isListQuestion: Boolean = false,
    /** A range, when the query said "between X and Y". */
    val range: Pair<Quantity, Double>? = null,
    /** Which place in the ranking was asked for: "the third densest" gives 3. */
    val rankOrdinal: Int? = null
) {
    val hasOperatorEvidence: Boolean
        get() = comparators.isNotEmpty() || subsetFilters.isNotEmpty() ||
                aggregation != Aggregation.NONE || superlativeDescending != null ||
                topN != null || targetUnit != null || range != null
}

/**
 * Pulls comparators, subsets, aggregations and unit requests out of a query.
 *
 * This is what makes multi-hop questions expressible. "Which transition metals melt above
 * 2000 °C" yields a series filter plus a greater-than comparator, which together drive a scan
 * over all 118 elements — a shape the previous keyword router had no way to represent.
 */
object OperatorExtractor {

    /**
     * The quantity written *before* a comparator, for the languages that put it there.
     *
     * Hindi, Urdu and Chinese are postpositional: "2000 केल्विन से ऊपर", "2000 کیلون سے اوپر" and
     * "2000开尔文以上" all read "2000 kelvin above". Looking only after the comparator found no
     * number at all, so the threshold silently vanished and the query became an unfiltered list.
     */
    private fun numberBefore(query: String, phrase: String): Quantity? {
        val at = query.indexOf(phrase)
        if (at <= 0) return null
        val before = query.substring(0, at)
        val match = NUMBER_WITH_UNIT.findAll(before).lastOrNull() ?: return null
        return quantityOf(match)
    }

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
            (numberAfter(query, phrase) ?: numberBefore(query, phrase))
                ?.let { comparators.add(Op.GT to it) }
        }
        less?.let { phrase ->
            (numberBefore(query, phrase) ?: numberAfter(query, phrase))
                ?.let { comparators.add(Op.LT to it) }
        }

        // ---- Subsets ----------------------------------------------------------------------
        val series = LinkedHashSet<SeriesId>()
        // Family names are consumed here so their words cannot be read again below: "noble
        // gases" names a group, and treating its "gases" as a phase filter as well would
        // silently exclude oganesson, which is predicted to be solid.
        var remaining = query
        for ((word, ids) in Lexicon.SERIES_WORDS.entries.sortedByDescending { it.key.length }) {
            if (mentionsFamily(remaining, word)) {
                series.addAll(ids)
                remaining = remaining.replace(word, " ")
            }
        }
        if (series.isNotEmpty()) subsets.add(Filter.InSeries(series))
        else if (Lexicon.METAL_WORDS.any { mentions(query, it) }) subsets.add(Filter.IsMetal(true))

        for ((word, block) in Lexicon.BLOCK_WORDS) {
            if (mentions(query, word)) { subsets.add(Filter.InBlock(block)); break }
        }
        // A phase word inside a fixed term is not a phase. "What is the gas constant" and "what is
        // the ideal gas law" were answered with a list of every gaseous element, because "gas" is a
        // phase word and nothing looked at what it was part of.
        for (phrase in Lexicon.PHASE_EXEMPT_PHRASES) {
            if (remaining.contains(phrase)) remaining = remaining.replace(phrase, " ")
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
        // Suffix-tolerant for the same reason superlatives are: German "durchschnittliche" carries
        // an adjective ending that a word-boundary match misses entirely.
        val aggregation = when {
            Lexicon.AVERAGE.any { marksStatistic(query, it) } -> Aggregation.AVG
            Lexicon.MEDIAN.any { marksStatistic(query, it) } -> Aggregation.MEDIAN
            Lexicon.SUM.any { mentionsFamily(query, it) } -> Aggregation.SUM
            Lexicon.COUNT.any { mentionsFamily(query, it) } -> Aggregation.COUNT
            else -> Aggregation.NONE
        }

        // ---- Superlative direction --------------------------------------------------------
        // Kept as two distinct sets so "least dense" is not read as "densest".
        // Suffix-tolerant for the same reason family names are: Swedish and German inflect these
        // ("högst" -> "högsta", "dicht" -> "dichtesten"), and a superlative that goes unrecognised
        // does not merely lose the ranking — it lets the turn inherit a subject and collapse into a
        // plain property lookup.
        // A comparison particle disqualifies the superlative reading entirely: "plus dense que l'or"
        // uses the same "plus" as "le plus dense", and only the "que" tells them apart.
        val comparisonParticle = carriesComparisonParticle(query)
        val most = !comparisonParticle && Lexicon.MOST.any { marksDegree(query, it) }
        val least = !comparisonParticle && Lexicon.LEAST.any { marksDegree(query, it) }
        val descending = when {
            least && !most -> false
            most && !least -> true
            // Both directions matched. The earlier marker wins, and at the same position the
            // longer one does — which is what tells 最低 ("lowest") from the bare 最 ("most") that
            // is a prefix of it. Chinese superlatives are built by prefixing a single character, so
            // every "lowest" question also contains a "highest" marker at the same index.
            most && least -> {
                val leastMarker = firstMention(query, Lexicon.LEAST)!!
                val mostMarker = firstMention(query, Lexicon.MOST)!!
                val leastAt = query.indexOf(leastMarker)
                val mostAt = query.indexOf(mostMarker)
                if (leastAt != mostAt) leastAt > mostAt else leastMarker.length <= mostMarker.length
            }
            else -> null
        }

        // ---- Negation ---------------------------------------------------------------------
        // "which noble gases are not radioactive" must invert the filter, not drop it.
        val negated = Lexicon.NEGATIONS.any { query.contains(it) }
        val finalSubsets = if (negated) subsets.map { invert(it) } else subsets

        return Operators(
            comparators = comparators,
            subsetFilters = finalSubsets,
            aggregation = aggregation,
            superlativeDescending = descending,
            topN = topN(query),
            targetUnit = targetUnit(query),
            ordinal = ordinal(query),
            // Naming a family in the plural is itself a request for its members, whatever opens the
            // sentence: "wat is die edelgasse" and "ano ang mga noble gas" carry no interrogative
            // the WHICH list knows, so neither counted as a list question and both fell through.
            isListQuestion = Lexicon.WHICH.any { query.startsWith("$it ") || mentions(query, it) } ||
                    Lexicon.PLURAL_SERIES_WORDS.any { query.contains(it) },
            range = range(query),
            rankOrdinal = rankOrdinal(query)
        )
    }

    /**
     * Whether a degree marker appears, allowing for markers that are phrases.
     *
     * [mentionsFamily] matches one inflected word, which is right for the Germanic and Romance
     * superlatives it was written for. Urdu builds its superlative out of two words — "سب سے" plus
     * the adjective — so a single-word matcher found none of them and every Urdu superlative was
     * read as a plain lookup.
     */
    private fun marksDegree(query: String, marker: String): Boolean {
        if (marker.contains(' ')) return query.contains(marker)
        if (mentionsFamily(query, marker)) return true
        // German compounds the rank onto the superlative: "das zweitdichteste Element". The marker
        // is the tail of a longer word, which the prefix-tolerant match above cannot see, so the
        // query carried no superlative at all and was declined outright.
        if (marker.length < MIN_COMPOUND_MARKER) return false
        return TextMatching.splitQueryTokens(query).any {
            it.length > marker.length && it.endsWith(marker)
        }
    }

    /**
     * Whether a statistic is asked for, allowing the marker to open a compound word.
     *
     * [mentionsFamily] tolerates an *ending* on the marker, which is enough for "durchschnittliche"
     * and "medianen". It is not enough for Swedish and German, which write the statistic and its
     * field as one word: "mediandensitet", "medianatommassan", "Durchschnittsdichte". There the
     * marker is a prefix of a much longer token, and the whole aggregation was lost — the question
     * became an unfiltered list of the subset.
     *
     * Confined to markers of five characters or more, so the short ones ("avg", "sum", "media")
     * cannot swallow an unrelated word.
     */
    private fun marksStatistic(query: String, marker: String): Boolean {
        if (mentionsFamily(query, marker)) return true
        if (marker.length < MIN_COMPOUND_MARKER || marker.contains(' ')) return false
        return TextMatching.splitQueryTokens(query).any {
            it.length > marker.length && it.startsWith(marker)
        }
    }

    /** Shortest statistic marker that may open a compound. See [marksStatistic]. */
    private const val MIN_COMPOUND_MARKER = 5

    /**
     * Whether the query carries a comparison particle that is genuinely comparing two things.
     *
     * A bare "is there a `than`-word anywhere" test cost two whole languages their superlatives:
     *
     *  - Urdu builds its superlative *out of* the particle — سب سے زیادہ is literally "than all,
     *    more" — so every Urdu superlative contained سے and was demoted to a comparison. Multi-word
     *    degree markers are therefore blanked out before the query is searched.
     *  - Spanish and Portuguese spell the interrogative "qué"/"qual" with the same letters as their
     *    comparison particle "que", so *"qué elemento tiene el punto de ebullición más alto"* looked
     *    like a comparison too. Position settles that one: a Romance comparison particle follows its
     *    degree word ("más denso **que** el oro"), it never precedes it.
     */
    private fun carriesComparisonParticle(query: String): Boolean {
        // Blanked rather than removed, so every index still lines up with the original.
        val masked = (Lexicon.MOST + Lexicon.LEAST)
            .filter { it.contains(' ') }
            .fold(query) { acc, marker -> acc.replace(marker, " ".repeat(marker.length)) }

        val degreeAt = (Lexicon.MOST + Lexicon.LEAST)
            .filter { marksDegree(query, it) }
            .mapNotNull { marker -> query.indexOf(marker).takeIf { it >= 0 } }
            .minOrNull()

        return Lexicon.THAN_WORDS.any { particle ->
            if (!mentions(masked, particle)) return@any false
            degreeAt == null || masked.indexOf(particle, degreeAt) >= 0
        }
    }

    /**
     * Invert a filter for a negated question.
     *
     * Only the boolean-shaped filters can be meaningfully inverted. A negated series or phase
     * would mean "every element that is not a halogen", which is almost never what is being
     * asked, so those are left alone: in "which noble gases are not radioactive" the series
     * names the set and only the radioactivity is negated.
     */
    private fun invert(filter: Filter): Filter = when (filter) {
        is Filter.IsRadioactive -> Filter.IsRadioactive(!filter.radioactive)
        is Filter.IsSynthetic -> Filter.IsSynthetic(!filter.synthetic)
        is Filter.IsMetal -> Filter.IsMetal(!filter.metal)
        else -> filter
    }

    /** "between 1000 and 2000 kelvin" — the two bounds and the unit they share. */
    private fun range(query: String): Pair<Quantity, Double>? {
        val opener = Lexicon.BETWEEN_WORDS.firstOrNull { mentions(query, it) } ?: return null
        // Hindi, Urdu and Chinese are postpositional: the bounds come *before* the word that says
        // they are a range — "5 और 10 के बीच", "5 اور 10 کے درمیان", "5到10之间". Reading only what
        // followed the opener found no numbers at all, so every range question in those three
        // languages was declined. Whichever side carries a joined pair of numbers is the range.
        val after = query.substringAfter(opener)
        val before = query.substringBefore(opener)
        val tail = if (joinedPairIn(after) != null) after else before
        val joiner = joinedPairIn(tail) ?: return null

        val separator = if (tail.contains(" $joiner ")) " $joiner " else joiner
        val low = NUMBER_WITH_UNIT.find(tail.substringBefore(separator))
        val highPart = tail.substringAfter(separator)
        val high = NUMBER_WITH_UNIT.find(highPart)
        val lowValue = low?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val highValue = high?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null

        // The unit is usually written once, after the second bound.
        val rawUnit = high.groupValues[2].takeIf { it.isNotBlank() }
            ?: low.groupValues[2].takeIf { it.isNotBlank() }
        val unit = rawUnit?.let { Lexicon.UNIT_WORDS[it.lowercase()] ?: UnitConverter.canonical(it) }
        return Quantity(
            value = minOf(lowValue, highValue), unit = unit, display = "$lowValue–$highValue"
        ) to maxOf(lowValue, highValue)
    }

    /**
     * The joiner of a pair of bounds inside [text], or null when it holds no such pair.
     *
     * Chinese writes the joiner with no spaces around it (5到10), so both spellings are tried.
     */
    private fun joinedPairIn(text: String): String? = Lexicon.RANGE_JOINERS
        .filter {
            text.contains(" $it ") ||
                    (com.jlindemann.science.ai.retrieval.Tokenizer.isUnspacedScript(it) &&
                            text.contains(it))
        }
        .minByOrNull {
            val spaced = text.indexOf(" $it ")
            if (spaced >= 0) spaced else text.indexOf(it)
        }

    /** "the third densest element" — which place in the ranking was asked for. */
    private fun rankOrdinal(query: String): Int? {
        val hasSuperlative = Lexicon.MOST.any { mentions(query, it) } ||
                Lexicon.LEAST.any { mentions(query, it) }
        if (!hasSuperlative) return null
        return Lexicon.RANK_ORDINALS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { mentions(query, it.key) }
            ?.value
    }

    /** The number following a phrase, with its unit if one is written. */
    private fun numberAfter(query: String, phrase: String): Quantity? {
        val at = query.indexOf(phrase)
        if (at < 0) return null
        val tail = query.substring(at + phrase.length)
        val match = NUMBER_WITH_UNIT.find(tail) ?: return null
        return quantityOf(match)
    }

    /** A regex match of [NUMBER_WITH_UNIT], read as a value and a canonical unit. */
    private fun quantityOf(match: MatchResult): Quantity? {
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
            // Require a preposition beside it, so "kelvin" inside a name is not a conversion
            // request. Either side, because Hindi, Urdu and Filipino are postpositional: "सेल्सियस
            // में" puts the preposition after the unit, and looking only in front meant a bare
            // "in celsius" follow-up carried no unit at all in those three languages.
            val at = query.indexOf(word)
            val before = query.substring(0, at).trimEnd().substringAfterLast(' ')
            val after = query.substring(at + word.length).trimStart().substringBefore(' ')
            if (before in Lexicon.UNIT_PREPOSITIONS ||
                after in Lexicon.UNIT_PREPOSITIONS ||
                word.length > 6
            ) return unit
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
        "hang", "पंक्ति", "आवर्त", "周期", "dor", "دور", "panahon", "periodo"
    )

    private val GROUP_WORDS = listOf(
        "group", "grupp", "gruppe", "groupe", "grupo", "gruppo", "column", "kolumn",
        "spalte", "colonne", "columna", "colonna", "kolom", "hanay", "hilera",
        "समूह", "族", "gurooh", "groep", "گروپ", "pangkat"
    )

    private fun mentions(query: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        // Multi-word and non-Latin phrases match by containment; single Latin words by boundary.
        return if (phrase.contains(' ') || phrase.any { it.code > 0x2000 }) query.contains(phrase)
        else TextMatching.containsWord(query, phrase)
    }

    /**
     * Like [mentions], but tolerant of a grammatical ending on a family name.
     *
     * Swedish and German inflect these heavily, and the definite plural is the form people actually
     * write: "ädelgaserna" for "ädelgas", "halogenerna", "alkalimetallerna", "Edelgasen". Matching on
     * word boundaries alone missed every one of them, so "Vilka är ädelgaserna?" resolved no subset
     * and was answered with a single-element dump instead of the list.
     *
     * Four characters covers "-erna"; the tolerance is confined to family names rather than applied
     * to every subset word, because a loose match on a phase or a metal word would silently widen a
     * filter instead of failing to find one.
     */
    private fun mentionsFamily(query: String, phrase: String): Boolean {
        if (mentions(query, phrase)) return true
        if (phrase.length < MIN_FAMILY_STEM || phrase.contains(' ')) return false
        return TextMatching.splitQueryTokens(query).any { word ->
            word.length > phrase.length &&
                    word.length - phrase.length <= MAX_FAMILY_SUFFIX &&
                    word.startsWith(phrase)
        }
    }

    /** Longest grammatical ending accepted on a family name, e.g. "adelgas" + "erna". */
    private const val MAX_FAMILY_SUFFIX = 4

    /** Shortest family name worth matching by stem, so a two-letter token cannot widen a filter. */
    private const val MIN_FAMILY_STEM = 5

    private fun longestMatch(query: String, phrases: List<String>): String? =
        phrases.filter { mentions(query, it) }.maxByOrNull { it.length }

    private fun firstMention(query: String, phrases: List<String>): String? =
        phrases.filter { mentionsFamily(query, it) }.minByOrNull { query.indexOf(it) }
}
