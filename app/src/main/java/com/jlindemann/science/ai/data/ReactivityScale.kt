package com.jlindemann.science.ai.data

/**
 * A heuristic score for how reactive an element is.
 *
 * **Not a stored field.** Reactivity is derived from group and position rather than measured, which
 * is why `Lexicon.UNBACKED_CONCEPTS` lists it and the planner declines questions about it. This is
 * the one capability of the old keyword router that no field covers: "which is more reactive,
 * sodium or potassium" has a real answer and nothing to look it up in.
 *
 * The numbers are ordinal only. They order two elements and nothing more.
 */
object ReactivityScale {

    /**
     * Score an element. Higher is more reactive.
     *
     * @param atomicNumber breaks ties within a group, and carries the trend: reactivity increases
     *   down the alkali metals and decreases down the halogens
     * @param key the lowercase English element key. Three elements are special-cased because the
     *   group trend alone would rank them wrongly against each other.
     */
    fun of(series: SeriesId, atomicNumber: Int, key: String): Int {
        // Fluorine is the most reactive element there is, and the halogen trend below would
        // otherwise put it behind nothing at all. Caesium and francium top the alkali metals.
        if (key.equals("fluorine", ignoreCase = true)) return 200
        if (key.equals("caesium", ignoreCase = true) || key.equals("cesium", ignoreCase = true) ||
            key.equals("francium", ignoreCase = true)
        ) return 190
        return when (series) {
            SeriesId.ALKALI_METAL -> 100 + atomicNumber           // increases down the group
            SeriesId.ALKALINE_EARTH_METAL -> 80 + atomicNumber
            SeriesId.HALOGEN -> 100 - atomicNumber                // decreases down the group
            SeriesId.NOBLE_GAS -> 0
            else -> 50
        }
    }

    fun of(element: ElementRecord): Int = of(element.series, element.atomicNumber, element.key)

    /**
     * Which of two elements is more reactive, or null when the heuristic cannot separate them —
     * two elements that score the same are two this scale has nothing to say about.
     */
    fun moreReactive(left: ElementRecord, right: ElementRecord): ElementRecord? {
        val l = of(left)
        val r = of(right)
        if (l == r) return null
        return if (l > r) left else right
    }
}
