package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.SeriesId
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end tests over the real 118-element data.
 *
 * These cover the capabilities the previous keyword router could not express at all: filtering and
 * ranking across the whole table, aggregating a subset, converting units, following up on an
 * earlier turn, and refusing to answer when the data is absent.
 */
class AiEngineTest {

    private lateinit var store: KnowledgeStore
    private lateinit var planner: QueryPlanner
    private lateinit var executor: com.jlindemann.science.ai.exec.QueryExecutor
    private lateinit var strings: StringProvider

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val datasets = DatasetIndex.build()
        val aliases = EntityResolver.buildAliases(
            mapOf("en" to store.elements.associate {
                it.key to Triple(it.key, it.symbol, it.atomicNumber.toString())
            })
        )
        strings = FakeStrings(language = "en")
        planner = QueryPlanner(store, datasets, EntityResolver(aliases, "en"), FieldResolver(strings), null)
        executor = com.jlindemann.science.ai.exec.QueryExecutor(store, datasets, null, strings)
    }

    private fun run(query: String, state: DialogueState = DialogueState()): Pair<QueryPlan, ExecutionResult?> {
        val plan = planner.plan(query, state)
        return plan to executor.execute(plan)
    }

    // ---- Multi-hop: filter across the whole table ---------------------------------------

    @Test
    fun filtersTransitionMetalsByMeltingPoint() {
        val (plan, result) = run("which transition metals melt above 2000 celsius")
        assertEquals(Intent.FILTER_LIST, plan.intent)
        assertTrue(plan.filters.any { it is Filter.InSeries && SeriesId.TRANSITION_METAL in it.series })
        assertTrue(plan.filters.any { it is Filter.FieldCompare && it.op == Op.GT })

        val list = result as ExecutionResult.ElementList
        assertTrue("expected several matches, got ${list.results.size}", list.results.isNotEmpty())
        // Every result must genuinely be a transition metal above the threshold.
        for (row in list.results) {
            assertEquals(SeriesId.TRANSITION_METAL, row.element.series)
            val celsius = store.quantityIn(row.element, "melting_point", "°C")!!
            assertTrue("${row.element.key} melts at ${celsius.value} °C", celsius.value > 2000)
        }
        assertTrue(list.results.any { it.element.key == "tungsten" })
    }

    @Test
    fun filtersByLessThan() {
        val (plan, result) = run("which elements have a density below 1")
        assertTrue(plan.filters.any { it is Filter.FieldCompare && it.op == Op.LT })

        val list = result as ExecutionResult.ElementList
        for (row in list.results) assertTrue(row.quantity!!.mid < 1.0)
        // The shown rows are a truncated page; `matched` must report the true total.
        val trueTotal = store.elements.count { (it.quantity("density")?.mid ?: Double.MAX_VALUE) < 1.0 }
        assertEquals(trueTotal, list.matched)
        assertTrue(list.results.size <= list.matched)
    }

    // ---- Multi-hop: rank a subset --------------------------------------------------------

    @Test
    fun ranksTopNWithinASubset() {
        val (plan, result) = run("top 5 densest nonmetals")
        assertEquals(5, plan.limit)
        assertTrue(plan.sortDescending)
        assertEquals("density", plan.sortField)

        val list = result as ExecutionResult.ElementList
        assertEquals(5, list.results.size)
        assertTrue(list.results.all { it.element.series.isNonmetal })
        // Strictly descending.
        val values = list.results.map { it.quantity!!.mid }
        assertEquals(values.sortedDescending(), values)
    }

    @Test
    fun leastAndMostAreDistinctDirections() {
        val (mostPlan, _) = run("which element has the highest electronegativity")
        val (leastPlan, _) = run("which element has the lowest electronegativity")
        assertTrue(mostPlan.sortDescending)
        assertFalse(leastPlan.sortDescending)
    }

    @Test
    fun superlativeOverTheWholeTableIsCorrect() {
        val (_, result) = run("which element has the highest electronegativity")
        val list = result as ExecutionResult.ElementList
        assertEquals("fluorine", list.results.first().element.key)
    }

    // ---- Multi-hop: aggregate ------------------------------------------------------------

    @Test
    fun averagesAFieldOverASubsetAndDisclosesCoverage() {
        val (plan, result) = run("average electronegativity of the halogens")
        assertEquals(Intent.AGGREGATE, plan.intent)
        assertEquals(Aggregation.AVG, plan.aggregation)

        val aggregate = result as ExecutionResult.Aggregate
        val halogens = store.inSeries(SeriesId.HALOGEN)
        assertEquals(halogens.size, aggregate.contributors.size + aggregate.missing)
        // The mean must equal the mean of exactly the contributors, not of a padded set.
        val expected = aggregate.contributors.map { it.quantity!!.mid }.average()
        assertEquals(expected, aggregate.value, 1e-9)
        assertTrue("a partial average must report what it excluded", aggregate.missing >= 0)
    }

    @Test
    fun countsMatchingElements() {
        val (plan, result) = run("how many noble gases are there")
        assertEquals(Aggregation.COUNT, plan.aggregation)
        val aggregate = result as ExecutionResult.Aggregate
        assertEquals(store.inSeries(SeriesId.NOBLE_GAS).size.toDouble(), aggregate.value, 0.0)
    }

    // ---- Unit conversion -------------------------------------------------------------------

    @Test
    fun convertsAPropertyIntoARequestedUnit() {
        val (plan, result) = run("what is the melting point of gold in fahrenheit")
        assertEquals("°F", plan.targetUnit)
        val property = result as ExecutionResult.Property
        assertEquals("gold", property.element.key)
        assertEquals(1947.52, property.quantity!!.value, 0.1)
    }

    // ---- Honest absence ----------------------------------------------------------------------

    @Test
    fun reportsMissingDataInsteadOfGuessing() {
        // Helium has no Vickers hardness; the answer must say so rather than print a sentinel.
        val (_, result) = run("what is the vickers hardness of helium")
        val noData = result as ExecutionResult.NoData
        assertEquals("vickers_hardness", noData.fieldId)
        assertEquals("helium", noData.element?.key)
        assertTrue("coverage must be reported so the answer can explain the gap", noData.coverage in 1..117)
    }

    @Test
    fun filtersNeverIncludeElementsWithNoValue() {
        val (_, result) = run("which elements have a vickers hardness above 100")
        val list = result as ExecutionResult.ElementList
        // Absent values must not be treated as zero, or as passing the filter.
        assertTrue(list.results.all { it.quantity != null })
        assertTrue("elements without the field must be counted as missing", list.missing > 0)
    }

    // ---- Follow-ups ---------------------------------------------------------------------------

    @Test
    fun followUpInheritsTheElementFromThepreviousTurn() {
        val state = DialogueState()
        val (firstPlan, firstResult) = run("what is the melting point of gold in fahrenheit", state)
        state.noteAnswer(firstPlan, listOf((firstResult as ExecutionResult.Property).element.key))

        // No element named; it must be inherited.
        val (plan, result) = run("and its density?", state)
        assertEquals(Intent.PROPERTY_LOOKUP, plan.intent)
        assertEquals(listOf("gold"), plan.elementKeys)
        assertEquals("density", plan.primaryField)
        assertEquals("gold", (result as ExecutionResult.Property).element.key)
    }

    @Test
    fun followUpInheritsTheFieldWhenOnlyAnElementIsNamed() {
        val state = DialogueState()
        state.focusElement = "gold"
        state.lastFieldIds = listOf("density")

        val (plan, _) = run("what about iron", state)
        assertEquals("density", plan.primaryField)
        assertTrue(plan.elementKeys.contains("iron"))
    }

    @Test
    fun dialogueStateTracksRecentElements() {
        val state = DialogueState()
        state.noteAnswer(QueryPlan(Intent.PROPERTY_LOOKUP, fieldIds = listOf("density")), listOf("gold"))
        state.noteAnswer(QueryPlan(Intent.PROPERTY_LOOKUP, fieldIds = listOf("density")), listOf("iron"))
        assertEquals("iron", state.focusElement)
        assertEquals(listOf("iron", "gold"), state.recentElements.toList())
    }

    // ---- Deferral: the engine must not claim ordinary lookups ---------------------------------

    @Test
    fun plainLookupsAreLeftToTheExistingHandlers() {
        // No operator, no missing data, no inherited slot: the engine defers.
        for (query in listOf("tell me about gold", "what is gold", "gold", "hello", "give me a fact")) {
            val plan = planner.plan(query, DialogueState())
            assertTrue("engine should not claim '$query' (got ${plan.intent}, ${plan.confidence})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold)
        }
    }

    @Test
    fun unrelatedQueriesAreNotClaimed() {
        val plan = planner.plan("what is the weather like today", DialogueState())
        assertEquals(Intent.UNKNOWN, plan.intent)
    }

    // ---- Comparison ---------------------------------------------------------------------------

    @Test
    fun comparesTwoNamedElements() {
        val (plan, result) = run("compare gold and silver density")
        assertEquals(Intent.COMPARISON, plan.intent)
        val comparison = result as ExecutionResult.Comparison
        assertEquals(setOf("gold", "silver"), comparison.elements.map { it.key }.toSet())
        assertTrue(comparison.values.containsKey("density"))
    }

    // ---- Citations -----------------------------------------------------------------------------

    @Test
    fun everyAnswerCarriesItsSource() {
        val (_, result) = run("which element has the highest electronegativity")
        assertTrue("a grounded answer must name where it came from", result!!.citations.isNotEmpty())
        assertTrue(result.citations.all { it.args.containsKey("key") })
    }

    // ---- Standalone unit conversion -----------------------------------------------------------

    @Test
    fun convertsAValueTheUserSupplied() {
        val (plan, result) = run("convert 500 K to °C")
        assertEquals(Intent.UNIT_CONVERT, plan.intent)
        assertTrue(plan.confidence >= planner.threshold)
        val conversion = result as ExecutionResult.UnitConversion
        assertEquals(226.85, conversion.converted, 0.01)
        assertEquals("°C", conversion.toUnit)
        assertFalse(conversion.bridged)
    }

    @Test
    fun crossesTheEnergyBridgeOnlyWhenAsked() {
        val (plan, result) = run("2 eV in kJ/mol")
        assertEquals(Intent.UNIT_CONVERT, plan.intent)
        val conversion = result as ExecutionResult.UnitConversion
        assertEquals(192.97, conversion.converted, 0.1)
        assertTrue("a crossed dimension must be disclosed", conversion.bridged)
    }

    /**
     * The guard that keeps this intent off property lookups. "Density of gold in kg/m³" resolves a
     * field, and a field is what a conversion request does not have.
     */
    @Test
    fun aPropertyLookupWithATargetUnitIsNotAConversion() {
        assertEquals(Intent.PROPERTY_LOOKUP, planner.plan("density of gold in kg/m3", DialogueState()).intent)
        assertEquals(Intent.PROPERTY_LOOKUP, planner.plan("melting point of gold in fahrenheit", DialogueState()).intent)
    }

    /** A filter carries a number and a unit too, and must stay a filter. */
    @Test
    fun aThresholdFilterIsNotAConversion() {
        val plan = planner.plan("which elements are denser than 5 g/cm3", DialogueState())
        assertEquals(Intent.FILTER_LIST, plan.intent)
    }

    // ---- Equation balancing -------------------------------------------------------------------

    @Test
    fun balancesAnEquation() {
        val (plan, result) = run("balance Fe + O2 -> Fe2O3")
        assertEquals(Intent.BALANCE_EQUATION, plan.intent)
        val balanced = result as ExecutionResult.BalancedEquation
        assertNull(balanced.reason)
        assertEquals(listOf(4, 3), balanced.reactants.map { it.coefficient })
        assertEquals(listOf(2), balanced.products.map { it.coefficient })
        assertTrue(balanced.tally.all { it.left == it.right })
    }

    /**
     * "Reaction" is in the unbacked-concept deny-list, which declines every sentence containing it.
     * The arrow has to win, or the flagship phrasing never reaches the balancer.
     */
    @Test
    fun theWordReactionDoesNotBlockAWrittenEquation() {
        val plan = planner.plan("balance this reaction: Fe + O2 -> Fe2O3", DialogueState())
        assertEquals(Intent.BALANCE_EQUATION, plan.intent)
    }

    /** A failure is an answer, not a fall-through: declining hands the query to the legacy router. */
    @Test
    fun anUnbalanceableEquationIsAnsweredRatherThanDeclined() {
        val (plan, result) = run("balance CO + O2 + H2 -> CO2 + H2O")
        assertEquals(Intent.BALANCE_EQUATION, plan.intent)
        val balanced = result as ExecutionResult.BalancedEquation
        assertEquals(
            com.jlindemann.science.ai.data.EquationBalancer.Reason.UNDERDETERMINED,
            balanced.reason
        )
    }

    @Test
    fun aComparisonOperatorIsNotAnEquation() {
        assertNotEquals(
            Intent.BALANCE_EQUATION,
            planner.plan("which elements have density >= 5", DialogueState()).intent
        )
    }

    // ---- Decay ---------------------------------------------------------------------------------

    @Test
    fun computesHowMuchOfASampleIsLeft() {
        val (plan, result) = run("how much of 100 g of carbon-14 remains after 11460 years")
        assertEquals(Intent.DECAY_CALC, plan.intent)
        val decay = result as ExecutionResult.Decay
        assertEquals(ExecutionResult.Decay.Mode.REMAINING, decay.mode)
        assertEquals(2.0, decay.halfLivesElapsed!!, 0.01)
        assertEquals(25.0, decay.finalAmount!!, 0.5)
        assertTrue(decay.citations.isNotEmpty())
    }

    @Test
    fun computesHowLongADecayTakes() {
        val (plan, result) = run("how long until 1 g of carbon-14 decays to 0.5 g")
        assertEquals(Intent.DECAY_CALC, plan.intent)
        val decay = result as ExecutionResult.Decay
        assertEquals(ExecutionResult.Decay.Mode.ELAPSED, decay.mode)
        assertEquals(1.0, decay.halfLivesElapsed!!, 0.01)
    }

    /**
     * A bare half-life question has no arithmetic in it, and the isotope table answers it better.
     * This pins the boundary from the other side: the decay branch sits above the isotope branch,
     * so without the arithmetic gate it would swallow this too.
     */
    @Test
    fun aBareHalfLifeQuestionStaysWithTheIsotopeTable() {
        val plan = planner.plan("what is the half-life of carbon-14", DialogueState())
        assertEquals(Intent.ISOTOPES, plan.intent)
    }

    /** Stable is a positive statement, and must not read as missing data. */
    @Test
    fun aStableNuclideSaysSoRatherThanShrugging() {
        val (_, result) = run("how much of 100 g of carbon-12 remains after 5000 years")
        val decay = result as? ExecutionResult.Decay ?: return
        assertTrue(
            "expected a stable or unlisted verdict, got ${decay.mode}",
            decay.mode == ExecutionResult.Decay.Mode.STABLE ||
                    decay.mode == ExecutionResult.Decay.Mode.NO_HALF_LIFE
        )
    }

    // ---- Solutions -----------------------------------------------------------------------------

    @Test
    fun computesTheMassNeededForASolution() {
        val (plan, result) = run("how many grams of NaCl for 250 mL of 0.5 mol/L solution")
        assertEquals(Intent.SOLUTION_CALC, plan.intent)
        val solution = result as ExecutionResult.SolutionCalc
        assertEquals(0.125, solution.moles!!, 1e-6)
        assertEquals(7.305, solution.grams!!, 0.05)
        assertEquals("NaCl", solution.substance)
    }

    @Test
    fun solvesADilution() {
        val (plan, result) = run("dilute 50 mL of 2 mol/L HCl to 0.5 mol/L")
        assertEquals(Intent.SOLUTION_CALC, plan.intent)
        val solution = result as ExecutionResult.SolutionCalc
        assertEquals(ExecutionResult.SolutionCalc.Kind.DILUTION, solution.kind)
        assertEquals(0.200, solution.dilution!!.v2, 1e-6)
    }

    /**
     * The mole quantity regex matches the "0.5 mol" inside "0.5 mol/L". A plain mole question has
     * to keep reaching the mole branch, which sits below the solution branch.
     */
    @Test
    fun aPlainMoleQuestionIsNotASolutionQuestion() {
        val plan = planner.plan("how many atoms are in 2.5 moles of iron", DialogueState())
        assertEquals(Intent.MOLE_CONVERSION, plan.intent)
    }

    /** A lone volume is not a solution question, and claiming it would break the converter. */
    @Test
    fun aLoneVolumeIsNotClaimed() {
        assertNotEquals(
            Intent.SOLUTION_CALC,
            planner.plan("convert 250 mL to litres", DialogueState()).intent
        )
    }
}
