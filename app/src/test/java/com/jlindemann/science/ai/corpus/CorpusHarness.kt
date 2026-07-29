package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.core.AiEngine
import com.jlindemann.science.ai.core.TestStrings
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.ElementKey
import com.jlindemann.science.ai.data.ElementTable
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedElement
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.Bm25Index
import com.jlindemann.science.ai.retrieval.ElementAlias
import com.jlindemann.science.ai.retrieval.EntityResolver
import com.jlindemann.science.ai.retrieval.HybridRetriever

/**
 * Builds the real agent stack for the corpus tests, one wiring per language.
 *
 * **This is the reason the corpus exists in this shape.** Every existing engine test constructs
 * `QueryPlanner(..., retriever = null)`, so the dataset-retrieval fallback never runs and every
 * must-defer assertion in the suite passes vacuously — `PropertyCoverageTest` asserts the engine
 * does not claim "quiz me" and is green, while the shipped app answers it with the electron mass
 * from the constants table. Here the retriever is real, so a deferral assertion means something.
 *
 * Everything is built once and cached: parsing 118 elements and indexing ~780 documents per
 * language is far too slow to repeat per row.
 */
object CorpusHarness {

    /** One fully wired stack. */
    class Stack(
        val language: String,
        val store: KnowledgeStore,
        val datasets: DatasetIndex,
        val localized: LocalizedView?,
        val strings: TestStrings,
        val entities: com.jlindemann.science.ai.retrieval.EntityResolver,
        val retriever: com.jlindemann.science.ai.retrieval.HybridRetriever?,
        val index: com.jlindemann.science.ai.retrieval.Bm25Index,
        val planner: QueryPlanner,
        val executor: QueryExecutor,
        val composer: AnswerComposer,
        val engine: AiEngine
    )

    /** True when the real assets and strings are both reachable. */
    val available: Boolean by lazy { TestAssets.available() && TestStrings.available }

    /** Languages the corpus can be run in — those with both an element table and a strings file. */
    val languages: List<String> by lazy {
        if (!available) emptyList() else TestAssets.availableLanguages()
    }

    private val stacks = HashMap<String, Stack>()

    /** The shared English-parsed numeric index. Built once for every language. */
    private val baseStore: KnowledgeStore by lazy {
        KnowledgeStore.build(TestAssets.elementTable(KnowledgeStore.BASE_LANGUAGE))
    }

    private val datasetIndex: DatasetIndex by lazy { DatasetIndex.build() }

    /**
     * Element aliases across every shipped language at once, which is what lets a Swedish query
     * resolve "Väte" and an English one resolve "Hydrogen" through the same resolver.
     */
    private val aliases: Map<String, List<ElementAlias>> by lazy {
        EntityResolver.buildAliases(
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

    fun stackFor(language: String): Stack = stacks.getOrPut(language) { build(language) }

    private fun build(language: String): Stack {
        val store = baseStore
        val datasets = datasetIndex
        val localized = overlayFor(language)
        // English fallback on, because that is what the app does. Without it every non-English row
        // would fail on the `str:<id>` sentinel from the ~70 untranslated agent strings, before its
        // routing expectation was ever reached — measuring translation coverage instead of
        // behaviour. `StringCoverageTest` is where the missing translations get asserted.
        val strings = TestStrings(language, fallBackToEnglish = true)
        val entities = EntityResolver(aliases, language)
        // A real corpus and a real index, so the dataset fallback behaves as it does in the app.
        val index = Bm25Index(HybridRetriever.buildCorpus(store, datasets, localized))
        val retriever = HybridRetriever(store, datasets, index, entities, localized)
        val planner = QueryPlanner(store, datasets, entities, FieldResolver(strings), retriever)
        val executor = QueryExecutor(store, datasets, localized, strings)
        val composer = AnswerComposer(store, localized, strings)
        val engine = AiEngine(store, datasets, localized, strings, entities, retriever)
        return Stack(language, store, datasets, localized, strings, entities, retriever, index, planner, executor, composer, engine)
    }

    /**
     * The seven-field display overlay for a language.
     *
     * Built here rather than through `KnowledgeStore.overlay` so the corpus does not depend on the
     * process-wide overlay cache, which other tests clear in their `@Before`.
     */
    private fun overlayFor(language: String): LocalizedView? {
        if (language == KnowledgeStore.BASE_LANGUAGE) return overlayOf(language, TestAssets.elementTable(language))
        val table = runCatching { TestAssets.elementTable(language) }.getOrNull() ?: return null
        return overlayOf(language, table)
    }

    private fun overlayOf(language: String, table: ElementTable): LocalizedView {
        val map = HashMap<ElementKey, LocalizedElement>(table.size * 2)
        for ((key, row) in table.rows) {
            map[key] = LocalizedElement(
                name = row["element"]?.toString().orEmpty(),
                description = row["description"]?.toString().orEmpty(),
                seriesName = row["element_group"]?.toString().orEmpty(),
                appearance = row["element_appearance"]?.toString().orEmpty(),
                phase = row["element_phase"]?.toString().orEmpty(),
                electricalType = row["electrical_type"]?.toString().orEmpty(),
                magneticType = row["magnetic_type"]?.toString().orEmpty()
            )
        }
        return LocalizedView(language, map)
    }
}
