package com.jlindemann.science.ai.chat

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.retrieval.TextMatching

/**
 * "What happens when sodium reacts with water" — what an element does, by where it sits.
 *
 * No stored field holds this, so the engine declines it. The answers are general chemistry rather
 * than a lookup — alkali metals react violently with water, noble gases hardly at all — which is why
 * they are ten authored strings rather than a table.
 */
object ElementReactions {

    private val REACTION_WORDS = listOf(
        "react", "mix", "combine", "together", "reaction",
        "reagera", "blanda", "tillsammans", "reaktion",
        "reagieren", "mischen", "zusammen",
        "reagir", "melanger", "ensemble",
        "reaccionar", "mezclar", "juntos", "reaccion",
        "reagire", "mescolare", "insieme", "reazione",
        "misturar", "junto", "reacao",
        "प्रतिक्रिया", "अभिक्रिया", "मिलाएं", "साथ",
        "反应", "混合", "结合", "fanying", "hunhe",
        "radd-e-amal", "milana", "reageer", "reaksyon", "paggawa"
    )

    private val WATER_WORDS = listOf("water", "vatten", "wasser", "eau", "agua", "acqua", "pani", "जल", "पानी", "水")

    private val OXYGEN_WORDS = listOf(
        "oxygen", "air", "syre", "luft", "sauerstoff", "oxygene", "oxigeno", "ossigeno",
        "ar", "hava", "ऑक्सीजन", "हवा", "氧", "空气"
    )

    private val ACID_WORDS = listOf("acid", "syra", "saure", "acide", "acido", "अम्ल", "तेजाब", "酸")

    /**
     * Whether this asks what something reacts with. Substring rather than whole word: the question
     * is almost always inflected — "reacts", "reaction", "reagerar" — and a word-boundary test
     * matches the bare stem only.
     */
    fun asks(normalizedQuery: String?): Boolean =
        !normalizedQuery.isNullOrBlank() && REACTION_WORDS.any { normalizedQuery.contains(it) }

    /** The answer, or null when no element is in play and there is nothing to say. */
    fun answer(
        named: List<ElementRecord>,
        normalizedQuery: String,
        strings: StringProvider,
        nameOf: (ElementRecord) -> String
    ): String? {
        if (named.size >= 2) return betweenElements(named[0], named[1], strings, nameOf)
        val element = named.firstOrNull() ?: return null
        val name = nameOf(element)
        return when {
            mentions(normalizedQuery, WATER_WORDS) -> withWater(element, name, strings)
            mentions(normalizedQuery, OXYGEN_WORDS) -> strings.get(R.string.ai_reaction_oxygen, name)
            mentions(normalizedQuery, ACID_WORDS) -> strings.get(R.string.ai_reaction_acid, name)
            else -> strings.get(R.string.ai_reaction_complex, name, strings.get(element.series.labelRes))
        }
    }

    private fun withWater(element: ElementRecord, name: String, strings: StringProvider): String = when {
        element.series == SeriesId.ALKALI_METAL -> strings.get(R.string.ai_reaction_alkali_water, name)
        // Magnesium is the exception its own string exists for: slow in cold water, rapid in steam.
        element.series == SeriesId.ALKALINE_EARTH_METAL && element.key == "magnesium" ->
            strings.get(R.string.ai_reaction_magnesium_water, name)
        element.series == SeriesId.ALKALINE_EARTH_METAL -> strings.get(R.string.ai_reaction_alkaline_earth_water, name)
        else -> strings.get(R.string.ai_reaction_generic_water, name)
    }

    private fun betweenElements(
        first: ElementRecord,
        second: ElementRecord,
        strings: StringProvider,
        nameOf: (ElementRecord) -> String
    ): String {
        val a = nameOf(first)
        val b = nameOf(second)
        val saltFormers = (first.series == SeriesId.ALKALI_METAL && isHalogen(second)) ||
                (second.series == SeriesId.ALKALI_METAL && isHalogen(first))
        if (saltFormers) return strings.get(R.string.ai_reaction_alkali_halogen, a, b)
        // A noble gas in the pair settles it, whichever side it is on.
        if (first.series == SeriesId.NOBLE_GAS) return strings.get(R.string.ai_reaction_noble_gas, a)
        if (second.series == SeriesId.NOBLE_GAS) return strings.get(R.string.ai_reaction_noble_gas, b)
        return strings.get(R.string.ai_reaction_generic_elements, a, b)
    }

    /**
     * Group 17 as well as [SeriesId.HALOGEN]: the shipped data classified chlorine as a reactive
     * nonmetal until roadmap 2.5, so matching on the series alone never fired and sodium and chlorine
     * never formed a salt. The group check keeps it right whatever the series data says.
     */
    private fun isHalogen(element: ElementRecord): Boolean =
        element.series == SeriesId.HALOGEN || element.groupNumber == 17

    private fun mentions(query: String, words: List<String>): Boolean =
        words.any { TextMatching.containsWord(query, it) }
}
