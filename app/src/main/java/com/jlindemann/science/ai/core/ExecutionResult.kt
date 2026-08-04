package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetRow
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.Quantity

/** Where an answer's information came from, so it can be shown and linked. */
data class Citation(
    val label: String,
    val source: String,
    val deepLink: com.jlindemann.science.ai.data.DeepLinkTarget,
    val args: Map<String, String> = emptyMap(),
    /** True when the source is one of the app's tables rather than the element data. */
    val fromTable: Boolean = false
)

/** One element paired with the value that was asked about. */
data class ValuedElement(val element: ElementRecord, val quantity: Quantity?, val display: String)

/**
 * What running a [QueryPlan] produced.
 *
 * [NoData] is as important as the success cases. When a field is resolved but the element has no
 * value for it, the engine reports that explicitly instead of printing the `"---"` sentinel or
 * falling through to an unrelated keyword branch, which is what the previous agent did.
 */
sealed class ExecutionResult {

    abstract val citations: List<Citation>

    /**
     * A single field of a single element.
     *
     * @property rank where this value sits among all elements that have the field, 1 being the
     *   highest. Null when the field is not rankable. Gives a bare number context: 19.3 g/cm³
     *   means little on its own, "the 6th densest of 105" means something.
     * @property rankedOutOf how many elements have a recorded value for the field
     */
    data class Property(
        val element: ElementRecord,
        val fieldId: String,
        val quantity: Quantity?,
        val display: String,
        override val citations: List<Citation>,
        val rank: Int? = null,
        val rankedOutOf: Int = 0
    ) : ExecutionResult()

    /** How two elements stand relative to one another on one property. */
    data class Comparative(
        val winner: ElementRecord,
        val loser: ElementRecord,
        val fieldId: String,
        val winnerValue: Quantity,
        val loserValue: Quantity,
        /** Set for a yes/no question: whether the claim as asked is true. */
        val claimHolds: Boolean?,
        /** Set when the question asked by how much. */
        val ratio: Double?,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** The element adjacent to another in atomic-number order. */
    data class Neighbour(
        val from: ElementRecord,
        val to: ElementRecord,
        val forward: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** Several elements compared across one or more fields. */
    data class Comparison(
        val elements: List<ElementRecord>,
        val fieldIds: List<String>,
        val values: Map<String, List<ValuedElement>>,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * A ranked or filtered set of elements.
     *
     * @property matched how many elements satisfied the filters
     * @property missing how many matched but had no value for the sorted field
     */
    data class ElementList(
        val results: List<ValuedElement>,
        val fieldId: String?,
        val matched: Int,
        val missing: Int,
        val descending: Boolean,
        override val citations: List<Citation>,
        /** How far down the ranking the shown rows start; non-zero for "the third densest". */
        val rankOffset: Int = 0,
        /** The next element down, so a single-element answer has something to be measured against. */
        val runnerUp: ValuedElement? = null
    ) : ExecutionResult()

    /** A statistic computed over a set of elements. */
    data class Aggregate(
        val aggregation: Aggregation,
        val fieldId: String,
        val value: Double,
        val unit: String?,
        val contributors: List<ValuedElement>,
        val missing: Int,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * An element's isotopes.
     *
     * @property shown the isotopes rendered, longest-lived first
     * @property total how many the element has in total, so a truncated list can say so
     */
    data class Isotopes(
        val element: ElementRecord,
        val shown: List<com.jlindemann.science.ai.data.Isotope>,
        val total: Int,
        val stableCount: Int,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** An element's NFPA 704 hazard ratings. */
    data class Safety(
        val element: ElementRecord,
        val nfpa: com.jlindemann.science.ai.data.Nfpa,
        val radioactive: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * An element's emission spectrum.
     *
     * Carries only the element, because that is genuinely all the app has: the spectrum exists as a
     * rendered image per element and there are no wavelengths, intensities or line names anywhere in
     * the data. Modelling it as a result of its own — rather than as a field with no value — is what
     * lets the answer show the picture while saying plainly that it cannot name a line.
     */
    data class EmissionSpectrum(
        val element: ElementRecord,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * A parsed chemical formula with its molar mass and composition.
     * @property wantsComposition whether the question asked for the percentage breakdown
     */
    data class Formula(
        val result: com.jlindemann.science.ai.data.FormulaResult,
        val wantsComposition: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** One nuclide with everything known about it, for side-by-side display. */
    data class NuclideFacts(
        val element: ElementRecord,
        val massNumber: Int,
        val protons: Int,
        val neutrons: Int,
        val isotope: com.jlindemann.science.ai.data.Isotope?
    )

    /** Two nuclides compared. */
    data class IsotopeComparison(
        val left: NuclideFacts,
        val right: NuclideFacts,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** Nucleon counts for a specific nuclide. */
    data class Nuclide(
        val element: ElementRecord,
        val massNumber: Int,
        val protons: Int,
        val neutrons: Int,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /** A mole/particle conversion. */
    data class MoleConversion(
        val moles: Double?,
        val particles: Double,
        val substance: String?,
        val toParticles: Boolean,
        /**
         * The mass the question supplied, when it asked the other way round.
         *
         * Null for "how many atoms are in 2.5 moles of iron"; set for "if I have 12 grams of
         * carbon-12, how many moles is that". Defaulted so existing construction sites are
         * untouched.
         */
        val grams: Double? = null,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()

    /**
     * A value the user supplied, expressed in another unit.
     *
     * @property bridged true when the conversion crossed a dimension through a physical constant —
     *   eV to kJ/mol — so the answer can name the constant rather than presenting the two as
     *   interchangeable quantities, which they are not.
     */
    data class UnitConversion(
        val value: Double,
        val fromUnit: String,
        val converted: Double,
        val toUnit: String,
        val bridged: Boolean,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * A chemical equation, balanced or honestly declined.
     *
     * [reason] being non-null is a first-class answer rather than an error. An equation that
     * cannot balance with positive integers, or that has several independent balances, has to be
     * *said* — answering with one of the possibilities and no warning is worse than not answering.
     *
     * @property tally the per-element proof: how many atoms each side ends up with. A balancer
     *   that prints only coefficients asks to be trusted; one that shows both counts can be checked.
     */
    data class BalancedEquation(
        val reactants: List<com.jlindemann.science.ai.data.Term>,
        val products: List<com.jlindemann.science.ai.data.Term>,
        val tally: List<com.jlindemann.science.ai.data.ElementTally>,
        val reason: com.jlindemann.science.ai.data.EquationBalancer.Reason?,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * A solution-chemistry calculation: n = c·V, a mass from a concentration, or a dilution.
     *
     * @property kind which unknown the question asked for, which decides the sentence the answer
     *   leads with. The other fields are filled in regardless, because the working is shown.
     */
    data class SolutionCalc(
        val kind: Kind,
        /** The compound as written, or as resolved from a common name. Null for a bare dilution. */
        val substance: String?,
        val molarMass: Double?,
        val moles: Double?,
        val molarity: Double?,
        val litres: Double?,
        val grams: Double?,
        val dilution: com.jlindemann.science.ai.data.Dilution?,
        override val citations: List<Citation>
    ) : ExecutionResult() {
        enum class Kind { MASS_NEEDED, MOLES, MOLARITY, VOLUME, DILUTION }
    }

    /**
     * Exponential decay of a nuclide over time.
     *
     * [Mode] separates three outcomes that all look like "no number" and are not the same thing:
     * the nuclide is stable, the app does not list it, or it is listed with a half-life the data
     * records as unknown. Collapsing them would tell a user the app has no data when what it has
     * is the opposite — a positive statement that nothing decays.
     */
    data class Decay(
        val element: ElementRecord,
        val massNumber: Int,
        val halfLifeSeconds: Double?,
        val halfLifeDisplay: String,
        val stable: Boolean,
        val mode: Mode,
        val initialAmount: Double?,
        val finalAmount: Double?,
        /** The unit the amounts were written in, echoed back rather than converted. */
        val amountUnit: String?,
        val elapsedSeconds: Double?,
        /** The unit the elapsed time was written in, so the answer can reply in it. */
        val elapsedUnit: String?,
        val halfLivesElapsed: Double?,
        val fractionRemaining: Double?,
        override val citations: List<Citation>
    ) : ExecutionResult() {
        enum class Mode { REMAINING, ELAPSED, HALF_LIVES, STABLE, NO_HALF_LIFE }
    }

    /** A row from one of the app's tables. */
    data class Dataset(
        val row: DatasetRow,
        override val citations: List<Citation>
    ) : ExecutionResult()

    /**
     * The question was understood but the app has no value for it.
     *
     * @property coverage how many of the 118 elements do have this field, so the answer can say
     *   how sparse it is rather than implying the data should have been there
     */
    data class NoData(
        val fieldId: String,
        val element: ElementRecord?,
        val coverage: Int,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()

    /**
     * The question was understood, but its answer is behind PRO or PRO+.
     *
     * A distinct case rather than a variant of [NoData], because the two say opposite things: NoData
     * means the app does not have the value, Locked means it has it and is not showing it. Conflating
     * them would have the agent claim ignorance of data the element screen visibly holds.
     *
     * Produced by the executor rather than the composer, so the value never reaches a rendering
     * layer at all — a filtered list or an aggregate over a gated field would otherwise leak it in
     * aggregate even if the sentence never named it.
     *
     * @property fieldIds the gated fields that caused this
     */
    data class Locked(
        val tier: com.jlindemann.science.ai.data.Tier,
        val fieldIds: List<String>,
        val element: ElementRecord?,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()

    /** The filters were understood but nothing satisfied them. */
    data class Empty(
        val describedFilters: List<String>,
        override val citations: List<Citation> = emptyList()
    ) : ExecutionResult()
}
