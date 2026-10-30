package com.jlindemann.science.ai.data

import com.jlindemann.science.ai.core.Filter
import com.jlindemann.science.ai.retrieval.TextMatching

/** Which way a trend explanation reads the table (roadmap 2.4). */
enum class ExplanationScope { GROUP, PERIOD, TABLE }

/** What an explanation is shown with, drawn from the app's own data when it is shown. */
sealed class ExplanationCard {
    /** A field across a set - a line - or, with no filter, the whole table - a heat map (roadmap 2.3). */
    data class Trend(val fieldId: String, val filter: Filter?) : ExplanationCard()

    /** One element's successive ionization energies. */
    data class Ionization(val elementKey: String) : ExplanationCard()
}

/**
 * One reviewed explanation of a mechanism (roadmap 2.4).
 *
 * @property fieldId and [scope] say which trend question it answers; null for one reached by its
 *   [topics] alone
 * @property topics trigger phrases, folded; each is a list of parts that must all appear
 * @property language the language [text] is in - English where the reader's language has no row
 */
data class Explanation(
    val key: String,
    val fieldId: String?,
    val scope: ExplanationScope?,
    val card: ExplanationCard?,
    val text: String,
    val language: String,
    val topics: List<List<String>>
)

/**
 * The explanations for one language, from the shared data's `explanations.tsv` (what each explains
 * and the card it is shown with) and `explanations_<lang>.tsv` (the words), with English filling any
 * row a language does not have yet.
 */
class Explanations(val all: List<Explanation>) {

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String): Explanation? = byKey[key]

    /**
     * The explanation whose trigger phrase [normalized] contains, the most specific first: the one
     * whose parts are longest in total. "Why is fluorine the most electronegative" names fluorine
     * and electronegativity, and the fluorine explanation is a closer answer than the group trend.
     */
    fun forTopic(normalized: String): Explanation? = all
        .mapNotNull { explanation ->
            explanation.topics
                .filter { parts -> parts.all { normalized.contains(it) } }
                .maxOfOrNull { parts -> parts.sumOf { it.length } }
                ?.let { explanation to it }
        }
        .maxByOrNull { it.second }?.first

    /** The trend explanation for [fieldId] in [scope], or the first for that field when no scope was said. */
    fun forTrend(fieldId: String, scope: ExplanationScope?): Explanation? {
        val family = FAMILY[fieldId] ?: fieldId
        val candidates = all.filter { it.fieldId == family }
        return candidates.firstOrNull { it.scope == scope } ?: candidates.firstOrNull().takeIf { scope == null }
    }

    companion object {
        val EMPTY = Explanations(emptyList())

        /**
         * Fields a question may name for the one an explanation is written about: "radius" resolves
         * whichever radius matches best, and every radius shrinks across a period for the same reason.
         */
        private val FAMILY = mapOf(
            "atomic_radius_empirical" to "atomic_radius", "covalent_radius" to "atomic_radius",
            "van_der_waals_radius" to "atomic_radius", "electronegativity_allen" to "electronegativity"
        )

        /**
         * Builds the set for [language] from the spec and the two text files. A spec row with no text
         * in either is left out, so a half-written file never shows an empty answer.
         */
        fun parse(spec: String?, texts: String?, english: String?, language: String): Explanations {
            val local = rows(texts)
            val fallback = rows(english)
            val entries = specRows(spec).mapNotNull { (key, fieldId, scope, card) ->
                val row = local[key]?.takeIf { it.second.isNotBlank() }
                val text = row?.second ?: fallback[key]?.second ?: return@mapNotNull null
                // The reader's own trigger phrases, and the English ones too: "noble gas" is how a
                // Filipino question names the family as often as not.
                val topics = (local[key]?.first.orEmpty() + fallback[key]?.first.orEmpty()).distinct()
                Explanation(key, fieldId, scope, card, text, if (row != null) language else "en", topics)
            }
            return Explanations(entries)
        }

        private data class SpecRow(val key: String, val fieldId: String?, val scope: ExplanationScope?, val card: ExplanationCard?)

        private fun specRows(text: String?): List<SpecRow> = lines(text).mapNotNull { cells ->
            val key = cells.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SpecRow(
                key,
                cells.getOrNull(1)?.takeIf { it.isNotBlank() },
                cells.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { runCatching { ExplanationScope.valueOf(it.uppercase()) }.getOrNull() },
                cells.getOrNull(3)?.let(::card)
            )
        }

        /** key to (topics, text) from one language's file. */
        private fun rows(text: String?): Map<String, Pair<List<List<String>>, String>> = lines(text).associate { cells ->
            val topics = cells.getOrNull(2).orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
                .map { phrase -> phrase.split('+').map { TextMatching.normalizeForLookup(it.trim()) }.filter { it.isNotEmpty() } }
            cells[0] to (topics to cells.getOrNull(3).orEmpty().trim())
        }

        /** The data rows of a TSV file: no comments, no header, no blank lines. */
        private fun lines(text: String?): List<List<String>> = text.orEmpty().lines()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("key\t") }
            .map { it.split('\t') }

        /** `trend:<field>:group=1`, `trend:<field>:table`, `ionization:<element>`. */
        private fun card(spec: String): ExplanationCard? {
            val parts = spec.trim().split(':')
            return when (parts.firstOrNull()) {
                "ionization" -> parts.getOrNull(1)?.let { ExplanationCard.Ionization(it) }
                "trend" -> {
                    val fieldId = parts.getOrNull(1) ?: return null
                    val scope = parts.getOrNull(2) ?: return null
                    if (scope == "table") return ExplanationCard.Trend(fieldId, null)
                    val (kind, value) = scope.split('=').takeIf { it.size == 2 } ?: return null
                    val filter = when (kind) {
                        "group" -> value.toIntOrNull()?.let { Filter.InGroup(it) }
                        "period" -> value.toIntOrNull()?.let { Filter.InPeriod(it) }
                        "series" -> runCatching { SeriesId.valueOf(value.uppercase()) }.getOrNull()?.let { Filter.InSeries(setOf(it)) }
                        else -> null
                    } ?: return null
                    ExplanationCard.Trend(fieldId, filter)
                }
                else -> null
            }
        }
    }
}
