package com.jlindemann.science.ai.chat

/**
 * The screens a chat can be opened from (roadmap 2.9), as the ids `datasets/screen_chips.tsv`
 * uses. The element page and the periodic table are not here: an element page focuses its element,
 * and the table has the general chips.
 */
object Screens {
    const val NUCLIDES = "nuclides"
    const val EMISSION = "emission"
    const val CONSTANTS = "constants"
    const val DICTIONARY = "dictionary"
    const val EQUATIONS = "equations"
    const val POISSON = "poisson"
    const val GEOLOGY = "geology"
    const val SOLUBILITY = "solubility"
    const val IONS = "ions"
    const val ELECTRODE = "electrode"
    const val PH = "ph"
    const val ALLOYS = "alloys"
    const val MOLAR_MASS = "molar_mass"
    const val UNIT_CONVERTER = "unit_converter"
    const val IDEAL_GAS = "ideal_gas"
    const val BALANCER = "balancer"
}

/**
 * The questions a chat opened from a screen offers first (roadmap 2.9): from the nuclide chart,
 * isotope questions; from the ideal gas calculator, gas-law ones.
 *
 * Shared data, so both apps offer the same chips. `ScreenChipsTest` asks every one in every
 * language and fails on any the assistant would decline, the same promise the follow-up chips keep.
 * A language without a row for a screen gets the general chips instead; there is no English
 * fallback, since a chip in another language than the chat is worse than none.
 */
class ScreenChips private constructor(private val chips: Map<String, List<String>>) {

    /** The chips for [screen] in [language], in file order; empty when there are none. */
    fun forScreen(screen: String, language: String): List<String> = chips["$screen\t$language"].orEmpty()

    /** Every screen and language that has chips, for the test. */
    val keys: Set<Pair<String, String>> get() = chips.keys.map { it.substringBefore('\t') to it.substringAfter('\t') }.toSet()

    companion object {
        val EMPTY = ScreenChips(emptyMap())

        /** Rows of `screen`, `lang`, `chip`; `#` starts a comment and the first other row is the header. */
        fun parse(text: String?): ScreenChips {
            if (text == null) return EMPTY
            val chips = LinkedHashMap<String, MutableList<String>>()
            text.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .forEach { line ->
                    val cells = line.split('\t')
                    if (cells.size < 3 || cells[2].isBlank()) return@forEach
                    chips.getOrPut("${cells[0]}\t${cells[1]}") { ArrayList() }.add(cells[2].trim())
                }
            return ScreenChips(chips)
        }
    }
}
