package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.FieldCategory
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The contract for retiring `handleElementContextQuery`.
 *
 * That handler was 408 lines of hand-written keyword-to-JSON-key mapping across ~30 branches. It
 * can only be removed if the engine answers everything it answered: single properties, the
 * composite branches that returned a whole family of fields at once, and all of it in every
 * shipped language.
 *
 * Property lookup is the most common thing users ask, so this is the highest-blast-radius part of
 * the agent. Every phrasing below came from the branches that handler actually served.
 */
class PropertyCoverageTest {

    private lateinit var store: KnowledgeStore
    private lateinit var datasets: DatasetIndex
    private lateinit var aliases: Map<String, List<com.jlindemann.science.ai.retrieval.ElementAlias>>

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        store = KnowledgeStore.build(TestAssets.elementTable("en"))
        datasets = DatasetIndex.build()
        aliases = EntityResolver.buildAliases(
            TestAssets.availableLanguages().associateWith { lang ->
                TestAssets.elementTable(lang).rows.mapValues { (_, row) ->
                    Triple(
                        row["element"]?.toString().orEmpty(),
                        row["short"]?.toString().orEmpty(),
                        row["element_atomic_number"]?.toString().orEmpty()
                    )
                }
            }
        )
    }

    private fun plannerFor(language: String): QueryPlanner {
        val strings = TestStrings(language)
        return QueryPlanner(store, datasets, EntityResolver(aliases, language), FieldResolver(strings), null)
    }

    private fun executorFor(language: String) =
        QueryExecutor(store, datasets, null, TestStrings(language))

    /** The engine must plan the query, resolve a field, and produce a real answer. */
    private fun answers(query: String, language: String = "en", expectField: String? = null) {
        val planner = plannerFor(language)
        val plan = planner.plan(query, DialogueState())
        assertTrue(
            "[$language] engine did not claim '$query' (intent=${plan.intent}, conf=${plan.confidence})",
            plan.intent in setOf(Intent.PROPERTY_LOOKUP, Intent.CATEGORY_LOOKUP) &&
                    plan.confidence >= planner.threshold
        )
        if (expectField != null) {
            assertTrue(
                "[$language] '$query' resolved to ${plan.fieldIds} but expected $expectField",
                expectField in plan.fieldIds
            )
        }
        val result = executorFor(language).execute(plan)
        assertNotNull("[$language] '$query' produced no result", result)
        assertFalse("[$language] '$query' produced an empty result", result is ExecutionResult.Empty)
    }

    // ---- Single properties, English ---------------------------------------------------------

    @Test
    fun answersEverySinglePropertyBranch() {
        val cases = listOf(
            "what is the atomic number of gold" to "atomic_number",
            "what is the symbol for gold" to "symbol",
            "what is the density of gold" to "density",
            "what is the melting point of gold" to "melting_point",
            "what is the boiling point of gold" to "boiling_point",
            "what is the electronegativity of gold" to "electronegativity",
            "what is the atomic mass of gold" to "atomic_mass",
            "what is the atomic radius of gold" to "atomic_radius",
            "what is the electron configuration of gold" to "electron_configuration",
            "what is the crystal structure of gold" to "crystal_structure",
            "what is the oxidation state of gold" to "oxidation_states",
            "what is the ion charge of gold" to "ion_charge",
            "what is the mohs hardness of gold" to "mohs_hardness",
            "what is the vickers hardness of gold" to "vickers_hardness",
            "what is the young modulus of gold" to "young_modulus",
            "what is the bulk modulus of gold" to "bulk_modulus",
            "what is the resistivity of gold" to "resistivity",
            "what is the thermal conductivity of gold" to "thermal_conductivity",
            "what is the electron affinity of gold" to "electron_affinity",
            "what is the work function of gold" to "work_function",
            "what is the molar volume of gold" to "molar_volume",
            "what is the space group of gold" to "space_group_name",
            "what is the cas number of gold" to "cas_number",
            "who discovered gold" to "discovered_by",
            "when was gold discovered" to "year_discovered",
            "what does gold look like" to "appearance",
            "what phase is gold at room temperature" to "phase",
            "is gold radioactive" to "radioactive",
            "what block is gold in" to "block",
            "what is the refractive index of gold" to "refractive_index"
        )
        for ((query, field) in cases) answers(query, "en", field)
    }

    @Test
    fun answersDerivedPeriodAndGroup() {
        // The old handler read element_period and element_group_number, which exist on 0 of 118
        // elements, so these questions used to answer with an empty string.
        answers("what period is gold in", "en")
        answers("what group is gold in", "en")
    }

    // ---- Composite branches -----------------------------------------------------------------

    /**
     * Several old branches returned a whole family at once — "thermal properties" gave fusion
     * heat, vaporization heat, specific heat and conductivity together. A single-field answer
     * would be a regression, so the engine needs a category intent.
     */
    @Test
    fun answersCompositeCategoryQuestions() {
        val cases = listOf(
            "tell me about the thermal properties of gold" to FieldCategory.THERMO,
            "what are the electromagnetic properties of gold" to FieldCategory.ELECTROMAGNETIC,
            "what are the mechanical properties of gold" to FieldCategory.MECHANICAL,
            "what are the atomic properties of gold" to FieldCategory.ATOMIC,
            "where is gold found" to FieldCategory.ABUNDANCE,
            "what is the abundance of gold" to FieldCategory.ABUNDANCE
        )
        val planner = plannerFor("en")
        for ((query, category) in cases) {
            val plan = planner.plan(query, DialogueState())
            assertTrue(
                "'$query' should be a category lookup, got ${plan.intent}",
                plan.intent == Intent.CATEGORY_LOOKUP && plan.confidence >= planner.threshold
            )
            val result = executorFor("en").execute(plan)
            assertTrue("'$query' should return several fields", result is ExecutionResult.Comparison)
            val fields = (result as ExecutionResult.Comparison).fieldIds
            assertTrue("'$query' returned only ${fields.size} field(s)", fields.size >= 2)
            assertTrue(
                "'$query' returned fields outside $category: $fields",
                fields.all { FieldRegistry.byId[it]?.category == category }
            )
        }
    }

    // ---- Other languages ----------------------------------------------------------------------

    /**
     * The localized labels the app already ships are what make this work without new translation.
     * If a language's `strings.xml` is incomplete, its formal field names still resolve.
     */
    @Test
    fun answersLocalizedPropertyQuestions() {
        val cases = listOf(
            Triple("sv", "vad är guldets densitet", "density"),
            Triple("sv", "vad är smältpunkten för guld", "melting_point"),
            Triple("de", "was ist die dichte von gold", "density"),
            Triple("de", "was ist der schmelzpunkt von gold", "melting_point"),
            Triple("fr", "quelle est la densité de l'or", "density"),
            Triple("es", "cuál es la densidad del oro", "density"),
            Triple("it", "qual è la densità dell'oro", "density"),
            Triple("pt", "qual é a densidade do ouro", "density"),
            Triple("af", "wat is die digtheid van goud", "density"),
            Triple("fil", "ano ang densidad ng ginto", "density"),
            Triple("hi", "सोने का घनत्व क्या है", "density"),
            Triple("ur", "سونے کی کثافت کیا ہے", "density"),
            Triple("zh", "金的密度是多少", "density")
        )
        for ((lang, query, field) in cases) answers(query, lang, field)
    }

    // ---- Deferral still holds -------------------------------------------------------------------

    @Test
    fun stillDefersNonPropertyQueries() {
        val planner = plannerFor("en")
        for (query in listOf("hello", "quiz me", "give me a fact", "what is the most reactive element")) {
            val plan = planner.plan(query, DialogueState())
            assertTrue(
                "engine should not claim '$query' (got ${plan.intent})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold
            )
        }
    }

    /** An overview request is narrative, not a field lookup, and stays with the legacy handler. */
    @Test
    fun defersOverviewRequests() {
        val planner = plannerFor("en")
        for (query in listOf("tell me about gold", "what is gold", "gold")) {
            val plan = planner.plan(query, DialogueState())
            assertTrue(
                "engine should not claim overview '$query' (got ${plan.intent})",
                plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold
            )
        }
    }
}
