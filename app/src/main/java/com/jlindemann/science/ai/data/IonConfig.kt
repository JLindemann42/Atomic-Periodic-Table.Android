package com.jlindemann.science.ai.data

/**
 * The electron configuration of an ion (roadmap 2.8): Fe³⁺ is [Ar] 3d⁵, not iron's [Ar] 3d⁶ 4s²
 * with three electrons taken from the end.
 *
 * A cation loses electrons from the outermost shell first - the highest n, and within it the
 * highest l - which is why iron's 4s pair goes before any 3d electron. An anion gains them in
 * filling order. The element files hold only the configuration past the noble-gas core
 * ("3d^6 4s^2"), so the core is added back by atomic number.
 */
object IonConfig {

    /** One subshell: n, the letter, the electrons in it. */
    data class Subshell(val n: Int, val l: Char, val electrons: Int) {
        override fun toString(): String = "$n$l${superscript(electrons)}"
    }

    /**
     * @property core the noble gas inside, by symbol, or null for none
     * @property subshells past the core, in the order the configuration is written
     * @property isoelectronicWith the noble gas with the same electrons, when the ion has a full shell
     */
    data class Configuration(val core: String?, val subshells: List<Subshell>, val isoelectronicWith: String?) {
        override fun toString(): String =
            (listOfNotNull(core?.let { "[$it]" }) + subshells.map { it.toString() }).joinToString(" ").ifEmpty { "—" }
    }

    private val NOBLE = listOf(2 to "He", 10 to "Ne", 18 to "Ar", 36 to "Kr", 54 to "Xe", 86 to "Rn", 118 to "Og")

    /** Subshells in filling order (Madelung). */
    private val FILLING = listOf(
        "1s", "2s", "2p", "3s", "3p", "4s", "3d", "4p", "5s", "4d", "5p", "6s", "4f", "5d", "6p", "7s", "5f", "6d", "7p"
    )

    private val CAPACITY = mapOf('s' to 2, 'p' to 6, 'd' to 10, 'f' to 14)
    private val L_ORDER = mapOf('s' to 0, 'p' to 1, 'd' to 2, 'f' to 3)
    private val SUBSHELL = Regex("""(\d)([spdf])\^?(\d+)""")

    /** Read the element files' "3d^6 4s^2" or "4f^14, 5d^4, 6s^2". */
    fun parse(text: String): List<Subshell> =
        SUBSHELL.findAll(text).map { Subshell(it.groupValues[1].toInt(), it.groupValues[2][0], it.groupValues[3].toInt()) }.toList()

    /**
     * The configuration of the ion with [charge] (positive for a cation) of the element with
     * [atomicNumber] and outer configuration [outer]. Null when the ion would lose electrons from
     * inside the noble-gas core, or the data cannot be read.
     */
    fun ion(atomicNumber: Int, outer: String, charge: Int): Configuration? {
        val subshells = parse(outer).toMutableList()
        if (subshells.isEmpty()) return null
        // What the outer configuration does not account for is the core, and it must be a noble gas.
        val coreZ = atomicNumber - subshells.sumOf { it.electrons }
        val coreElectrons = if (coreZ == 0) null else NOBLE.firstOrNull { it.first == coreZ } ?: return null
        val core = coreElectrons?.second

        if (charge > 0) {
            repeat(charge) {
                // The outermost shell first, and within it the highest l.
                val from = subshells.filter { it.electrons > 0 }
                    .maxWithOrNull(compareBy<Subshell>({ it.n }, { L_ORDER.getValue(it.l) })) ?: return null
                val at = subshells.indexOf(from)
                subshells[at] = from.copy(electrons = from.electrons - 1)
            }
        } else if (charge < 0) {
            repeat(-charge) {
                // The last subshell begun, in filling order, until it is full; then the next one.
                val open = subshells.filter { it.electrons < CAPACITY.getValue(it.l) }
                    .maxByOrNull { FILLING.indexOf("${it.n}${it.l}") }
                if (open != null && FILLING.indexOf("${open.n}${open.l}") >= subshells.maxOf { FILLING.indexOf("${it.n}${it.l}") }) {
                    subshells[subshells.indexOf(open)] = open.copy(electrons = open.electrons + 1)
                } else {
                    val last = subshells.maxOf { FILLING.indexOf("${it.n}${it.l}") }
                    val next = FILLING.getOrNull(last + 1) ?: return null
                    subshells.add(Subshell(next[0].digitToInt(), next[1], 1))
                }
            }
        }

        val remaining = subshells.filter { it.electrons > 0 }
        val total = (coreElectrons?.first ?: 0) + remaining.sumOf { it.electrons }
        val noble = NOBLE.firstOrNull { it.first == total }?.second
        // A full shell reads as its noble gas: Na⁺ is [Ne], Cl⁻ is [Ar].
        return if (noble != null) Configuration(noble, emptyList(), noble)
        else Configuration(core, remaining, null)
    }

    /** "Fe³⁺", "Cl⁻", "O²⁻". */
    fun ionLabel(symbol: String, charge: Int): String {
        val sign = if (charge > 0) "⁺" else "⁻"
        val size = kotlin.math.abs(charge)
        return symbol + (if (size == 1) "" else superscript(size)) + sign
    }

    private fun superscript(n: Int): String = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
}
