package com.jlindemann.science.ai.compose

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ChemistryMath
import com.jlindemann.science.ai.data.FormulaResult
import com.jlindemann.science.ai.data.SolutionMath
import com.jlindemann.science.ai.data.UnitConverter
import java.math.BigDecimal
import java.math.MathContext

/**
 * A calculation's working, step by step (roadmap 2.2).
 *
 * Every answer already shows a short working - the atom tally, n = c·V, the number of half-lives -
 * because a number a reader cannot reproduce has to be taken on faith. Asked for the steps, it shows
 * the whole derivation instead: the equations a balance solves, each conversion with its units, each
 * formula with its numbers put in. The lines are mostly mathematics, which reads the same in every
 * language; the words around them are strings.
 *
 * Each function returns the steps in order, without numbers, or an empty list when there is nothing
 * to derive - the caller then keeps the short working.
 */
class Workings(private val strings: StringProvider) {

    /** PV = nRT rearranged for the unknown, with n = m ÷ M first when a mass was given (roadmap 2.8). */
    fun gasLaw(result: ExecutionResult.GasLaw): List<String> {
        val s = result.solution
        val r = "${number(com.jlindemann.science.ai.data.GasLaw.R)} L·atm/(mol·K)"
        val mass = if (result.grams != null && result.molarMass != null && result.substance != null)
            listOf("n = m ÷ M = ${number(result.grams)} g ÷ ${number(result.molarMass)} g/mol = ${number(s.moles)} mol") else emptyList()
        val p = "${number(s.atm)} atm"; val v = "${number(s.litres)} L"; val n = "${number(s.moles)} mol"; val t = "${number(s.kelvin)} K"
        val line = when (s.unknown) {
            com.jlindemann.science.ai.data.GasLaw.Unknown.PRESSURE -> "P = nRT ÷ V = $n × $r × $t ÷ $v = $p"
            com.jlindemann.science.ai.data.GasLaw.Unknown.VOLUME -> "V = nRT ÷ P = $n × $r × $t ÷ $p = $v"
            com.jlindemann.science.ai.data.GasLaw.Unknown.MOLES -> "n = PV ÷ RT = $p × $v ÷ ($r × $t) = $n"
            com.jlindemann.science.ai.data.GasLaw.Unknown.TEMPERATURE -> "T = PV ÷ nR = $p × $v ÷ ($n × $r) = $t"
        }
        return listOf(strings.get(R.string.ai_steps_gas)) + mass + line
    }

    /** Full dissociation, then the negative logarithm (roadmap 2.8). */
    fun ph(result: ExecutionResult.Ph): List<String> {
        val r = result.result
        val supplied = "${r.factor} × ${number(r.molarity)} M = ${number(r.molarity * r.factor)} M"
        return if (r.base) listOf(
            strings.get(R.string.ai_steps_ph),
            "[OH⁻] = $supplied",
            "pOH = −log₁₀(${number(r.hydroxide)}) = ${number(r.pOH)}",
            "pH = 14 − pOH = ${number(r.pH)}"
        ) else listOf(
            strings.get(R.string.ai_steps_ph),
            "[H⁺] = $supplied",
            "pH = −log₁₀(${number(r.hydrogen)}) = ${number(r.pH)}"
        )
    }

    /** From the neutral atom to the ion, electron by electron (roadmap 2.8). */
    fun ion(result: ExecutionResult.IonConfiguration): List<String> {
        val label = com.jlindemann.science.ai.data.IonConfig.ionLabel(result.element.symbol, result.charge)
        val change = if (result.charge > 0) "−${result.charge} e⁻" else "+${-result.charge} e⁻"
        return listOf(
            strings.get(R.string.ai_steps_ion),
            "${result.element.symbol}: ${result.neutral}",
            "$label: $change → ${result.ion}"
        )
    }

    /** M = Σ count × atomic mass. */
    fun formula(result: FormulaResult): List<String> {
        if (result.parts.isEmpty()) return emptyList()
        val rows = result.parts.map { part ->
            val atomic = part.massContribution / part.count
            "${part.symbol}: ${part.count} × ${number(atomic)} g/mol = ${number(part.massContribution)} g/mol"
        }
        val sum = result.parts.joinToString(" + ") { number(it.massContribution) }
        return listOf(strings.get(R.string.ai_steps_formula)) + rows +
                "M(${result.formula}) = $sum = ${number(result.molarMass)} g/mol"
    }

    /** n = m ÷ M, N = n × N_A, or n = N ÷ N_A. */
    fun moles(result: ExecutionResult.MoleConversion): List<String> {
        val moles = result.moles ?: return emptyList()
        val legend = strings.get(R.string.ai_steps_moles_legend)
        val substance = result.substance?.let { "($it)" }.orEmpty()
        result.grams?.let { grams ->
            if (moles == 0.0) return emptyList()
            val molarMass = grams / moles
            return listOf(
                legend,
                "M$substance = ${number(molarMass)} g/mol",
                "n = m ÷ M = ${number(grams)} g ÷ ${number(molarMass)} g/mol = ${number(moles)} mol"
            )
        }
        val avogadro = "${number(ChemistryMath.AVOGADRO)} /mol"
        return if (result.toParticles) listOf(
            legend,
            "N = n × N_A = ${number(moles)} mol × $avogadro = ${number(result.particles)}"
        ) else listOf(
            legend,
            "n = N ÷ N_A = ${number(result.particles)} ÷ $avogadro = ${number(moles)} mol"
        )
    }

    /** The volume in litres, then n = c × V, m = n × M, c = n ÷ V or V = n ÷ c; or c₁V₁ = c₂V₂. */
    fun solution(result: ExecutionResult.SolutionCalc): List<String> {
        val legend = strings.get(R.string.ai_steps_solution_legend)
        result.dilution?.let { d ->
            val added = SolutionMath.solventToAdd(d.v1, d.v2)
            return listOf(
                legend,
                "c₁ × V₁ = c₂ × V₂",
                "${number(d.c1)} mol/L × ${number(d.v1 * 1000)} mL = ${number(d.c2)} mol/L × ${number(d.v2 * 1000)} mL",
                "V₂ − V₁ = ${number(d.v2 * 1000)} mL − ${number(d.v1 * 1000)} mL = ${number(added * 1000)} mL"
            )
        }
        val moles = result.moles ?: return emptyList()
        val molarity = result.molarity ?: return emptyList()
        val litres = result.litres ?: return emptyList()
        val molarMass = result.molarMass
        val grams = result.grams
        val steps = mutableListOf(legend)
        val volume = "V = ${number(litres * 1000)} mL = ${number(litres)} L"
        // n from a mass, where the question gave one and the unknown is the concentration or volume.
        val molesFromMass = if (grams != null && molarMass != null) {
            "n = m ÷ M = ${number(grams)} g ÷ ${number(molarMass)} g/mol = ${number(moles)} mol"
        } else null
        val massLine = molarMass?.let {
            listOf(
                "M = ${number(it)} g/mol",
                "m = n × M = ${number(moles)} mol × ${number(it)} g/mol = ${number(moles * it)} g"
            )
        }.orEmpty()
        when (result.kind) {
            ExecutionResult.SolutionCalc.Kind.MOLARITY -> {
                molesFromMass?.let { steps += "M = ${number(molarMass!!)} g/mol"; steps += it }
                steps += volume
                steps += "c = n ÷ V = ${number(moles)} mol ÷ ${number(litres)} L = ${number(molarity)} mol/L"
            }
            ExecutionResult.SolutionCalc.Kind.VOLUME -> {
                molesFromMass?.let { steps += "M = ${number(molarMass!!)} g/mol"; steps += it }
                steps += "V = n ÷ c = ${number(moles)} mol ÷ ${number(molarity)} mol/L = ${number(litres)} L = ${number(litres * 1000)} mL"
            }
            else -> {
                steps += volume
                steps += "n = c × V = ${number(molarity)} mol/L × ${number(litres)} L = ${number(moles)} mol"
                if (result.kind == ExecutionResult.SolutionCalc.Kind.MASS_NEEDED) steps += massLine
            }
        }
        return steps
    }

    /** n = t ÷ t½, N ÷ N₀ = (½)ⁿ, N = N₀ × (½)ⁿ; or the same read backwards for the time. */
    fun decay(result: ExecutionResult.Decay): List<String> {
        val halfLifeSeconds = result.halfLifeSeconds ?: return emptyList()
        val n = result.halfLivesElapsed ?: return emptyList()
        val fraction = result.fractionRemaining ?: return emptyList()
        val unit = result.elapsedUnit ?: "s"
        val halfLife = UnitConverter.convert(halfLifeSeconds, "s", unit) ?: return emptyList()
        val elapsed = result.elapsedSeconds?.let { UnitConverter.convert(it, "s", unit) }
        val amount = result.amountUnit?.let { " $it" }.orEmpty()
        val steps = mutableListOf(strings.get(R.string.ai_steps_decay_legend), "t½ = ${number(halfLife)} $unit")
        when (result.mode) {
            ExecutionResult.Decay.Mode.ELAPSED -> {
                val initial = result.initialAmount ?: return emptyList()
                val final = result.finalAmount ?: return emptyList()
                steps += "N ÷ N₀ = ${number(final)}$amount ÷ ${number(initial)}$amount = ${number(fraction)}"
                steps += "n = log₂(N₀ ÷ N) = ${number(n)}"
                steps += "t = n × t½ = ${number(n)} × ${number(halfLife)} $unit = ${number(n * halfLife)} $unit"
            }
            ExecutionResult.Decay.Mode.REMAINING -> {
                val initial = result.initialAmount ?: return emptyList()
                elapsed ?: return emptyList()
                steps += "n = t ÷ t½ = ${number(elapsed)} $unit ÷ ${number(halfLife)} $unit = ${number(n)}"
                steps += "N ÷ N₀ = (½)ⁿ = ${number(fraction)}"
                steps += "N = N₀ × (½)ⁿ = ${number(initial)}$amount × ${number(fraction)} = ${number(initial * fraction)}$amount"
            }
            else -> {
                elapsed ?: return emptyList()
                steps += "n = t ÷ t½ = ${number(elapsed)} $unit ÷ ${number(halfLife)} $unit = ${number(n)}"
                steps += "N ÷ N₀ = (½)ⁿ = ${number(fraction)} = ${number(fraction * 100)} %"
            }
        }
        return steps
    }

    /**
     * One unknown coefficient per formula, one equation per element, the smallest whole numbers
     * that solve them, and the tally that checks them.
     */
    fun balance(result: ExecutionResult.BalancedEquation): List<String> {
        if (result.reason != null) return emptyList()
        val symbols = result.tally.map { it.symbol }.toSet()
        val terms = result.reactants + result.products
        if (terms.size > LETTERS.length) return emptyList()
        val counts = terms.map { ChemistryMath.elementCounts(it.formula) { symbol -> symbol in symbols } ?: return emptyList() }
        val letters = terms.indices.map { LETTERS[it].toString() }

        fun side(range: IntRange) = range.joinToString(" + ") { "${letters[it]} ${terms[it].formula}" }
        val reactants = result.reactants.indices
        val products = result.reactants.size until terms.size
        fun atoms(symbol: String, range: IntRange) = range.mapNotNull { i ->
            counts[i][symbol]?.takeIf { it > 0 }?.let { count -> if (count == 1) letters[i] else "$count${letters[i]}" }
        }.joinToString(" + ")

        return listOf(
            strings.get(R.string.ai_steps_balance_unknowns),
            "${side(reactants)} → ${side(products)}",
            strings.get(R.string.ai_steps_balance_equations)
        ) + result.tally.map { "${it.symbol}: ${atoms(it.symbol, reactants)} = ${atoms(it.symbol, products)}" } +
                strings.get(R.string.ai_steps_balance_solve) +
                terms.indices.joinToString(", ") { "${letters[it]} = ${terms[it].coefficient}" } +
                strings.get(R.string.ai_equation_check_header) +
                result.tally.map { strings.get(R.string.ai_equation_tally_row, it.symbol, it.left, it.right) }
    }

    /**
     * Four significant figures, and powers of ten outside 0.001 to a million: the steps are where a
     * reader checks the arithmetic, and the two decimals the answers round to would show a
     * remaining fraction of 0.0039 as 0.
     */
    private fun number(value: Double): String {
        if (value == 0.0 || !value.isFinite()) return UnitConverter.formatValue(value)
        val magnitude = kotlin.math.abs(value)
        if (magnitude >= 1e6 || magnitude < 1e-3) {
            val exponent = kotlin.math.floor(kotlin.math.log10(magnitude)).toInt()
            val mantissa = BigDecimal(value / Math.pow(10.0, exponent.toDouble())).round(MathContext(4))
            return "${mantissa.stripTrailingZeros().toPlainString()} × 10${superscript(exponent)}"
        }
        return BigDecimal(value).round(MathContext(4)).stripTrailingZeros().toPlainString()
    }

    private fun superscript(n: Int): String {
        val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        return (if (n < 0) "⁻" else "") + kotlin.math.abs(n).toString().map { digits[it - '0'] }.joinToString("")
    }

    private companion object {
        const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    }
}
