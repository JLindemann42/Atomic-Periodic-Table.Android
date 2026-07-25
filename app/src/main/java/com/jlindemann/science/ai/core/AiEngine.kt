package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.compose.ComposedAnswer
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import com.jlindemann.science.ai.retrieval.HybridRetriever

/**
 * Plans, executes and composes an answer from the app's own data.
 *
 * The engine runs *before* the existing intent handlers and answers only when its plan clears the
 * planner's confidence threshold. Anything it does not claim returns null, and the caller falls
 * through to the previous behaviour unchanged — so this adds capability without putting the
 * existing answers at risk.
 *
 * What it adds that the keyword router could not express:
 *  - filtering, ranking and aggregating across all 118 elements in one question
 *  - unit conversion and addressing a specific slot of a banked field
 *  - follow-ups that inherit the element or property from the previous turn
 *  - saying plainly that the app has no value, instead of printing a sentinel
 */
class AiEngine(
    private val store: KnowledgeStore,
    private val datasets: DatasetIndex,
    private val localized: LocalizedView?,
    private val strings: StringProvider,
    entities: EntityResolver,
    retriever: HybridRetriever?
) {

    private val planner = QueryPlanner(store, datasets, entities, FieldResolver(strings), retriever)
    private val executor = QueryExecutor(store, datasets, localized, strings)
    private val composer = AnswerComposer(store, localized, strings)

    /**
     * Answer a question, or return null to defer.
     *
     * @param state carries the conversation's focus so follow-ups resolve; updated on success
     */
    fun answer(query: String, state: DialogueState): ComposedAnswer? {
        if (query.isBlank()) return null

        val plan = planner.plan(query, state)
        if (plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold) return null

        val result = executor.execute(plan) ?: return null
        val composed = composer.compose(result, plan)
        if (composed.text.isBlank()) return null

        state.noteAnswer(plan, resultKeys(result))
        return composed
    }

    /** Plan without executing. Exposed so tests can assert planning separately from rendering. */
    fun plan(query: String, state: DialogueState): QueryPlan = planner.plan(query, state)

    /**
     * Run an intent directly against a known element, bypassing query parsing.
     *
     * Used when the conversation already establishes what is being asked — a "yes" to a suggested
     * follow-up, or one answer falling back to another — so there is no natural-language question
     * to plan from.
     */
    fun answerFor(intent: Intent, elementKey: String): ComposedAnswer? {
        val plan = QueryPlan(
            intent = intent,
            entities = listOf(EntityRef.Element(elementKey)),
            fieldIds = if (intent == Intent.CATEGORY_LOOKUP) emptyList() else emptyList(),
            limit = if (intent == Intent.ISOTOPES) DEFAULT_ISOTOPES else 1,
            confidence = 1.0
        )
        val result = executor.execute(plan) ?: return null
        return composer.compose(result, plan)
    }

    /** Every populated field of one family, for one element. */
    fun categoryAnswerFor(
        elementKey: String,
        category: com.jlindemann.science.ai.data.FieldCategory
    ): ComposedAnswer? {
        val element = store.element(elementKey) ?: return null
        val populated = com.jlindemann.science.ai.data.FieldRegistry.byCategory(category)
            .filter { !element.value(it.id).isMissing }
            .map { it.id }
        if (populated.isEmpty()) return null
        val plan = QueryPlan(
            intent = Intent.CATEGORY_LOOKUP,
            entities = listOf(EntityRef.Element(elementKey)),
            fieldIds = populated,
            confidence = 1.0
        )
        val result = executor.execute(plan) ?: return null
        return composer.compose(result, plan)
    }

    private companion object {
        const val DEFAULT_ISOTOPES = 8
    }

    private fun resultKeys(result: ExecutionResult): List<String> = when (result) {
        is ExecutionResult.Property -> listOf(result.element.key)
        is ExecutionResult.Comparison -> result.elements.map { it.key }
        is ExecutionResult.ElementList -> result.results.map { it.element.key }
        is ExecutionResult.Aggregate -> result.contributors.map { it.element.key }
        is ExecutionResult.Isotopes -> listOf(result.element.key)
        is ExecutionResult.Safety -> listOf(result.element.key)
        is ExecutionResult.Nuclide -> listOf(result.element.key)
        is ExecutionResult.NoData -> listOfNotNull(result.element?.key)
        is ExecutionResult.Formula, is ExecutionResult.MoleConversion,
        is ExecutionResult.Dataset, is ExecutionResult.Empty -> emptyList()
    }
}
