package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.compose.ComposedAnswer
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.ClauseSplitter
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
    retriever: HybridRetriever?,
    /**
     * What the user may be told. Defaulted to full access so tests and any existing construction
     * site are unaffected; the app supplies the real entitlements.
     */
    private val entitlements: Entitlements = Entitlements.FULL,
    /**
     * Which visuals may be attached. The app passes the user's offline setting through here, so the
     * chat withholds the emission spectrum image exactly where the element screen already does.
     */
    private val cardPolicy: com.jlindemann.science.ai.cards.ChatCardPolicy =
        com.jlindemann.science.ai.cards.ChatCardPolicy.DEFAULT
) {

    private val planner = QueryPlanner(store, datasets, entities, FieldResolver(strings), retriever)
    private val executor = QueryExecutor(store, datasets, localized, strings, entitlements)
    private val composer = AnswerComposer(store, localized, strings, cardPolicy)

    /**
     * The plan behind the most recent answer, or null when the engine deferred.
     *
     * The plan is otherwise discarded inside [answer], which leaves the caller unable to say *what*
     * the engine understood — the difference between "the engine claimed 80% of questions" and
     * "the engine claimed 80% of questions and they were nearly all property lookups". Read by the
     * chat panel for analytics, so it stays a plain property here and this package keeps no
     * dependency on Firebase.
     */
    var lastPlan: QueryPlan? = null
        private set

    /**
     * Answer a question, or return null to defer.
     *
     * @param state carries the conversation's focus so follow-ups resolve; updated on success
     */
    fun answer(query: String, state: DialogueState): ComposedAnswer? {
        lastPlan = null
        if (query.isBlank()) return null

        // A question that asks two things is tried first. The whole query often plans to
        // something — "the density of gold and how does it compare to lead" plans as a
        // comparison — so checking single-clause first would answer half the question and
        // never notice the other half.
        compoundAnswer(query, state)?.let { return it }

        val plan = planner.plan(query, state)
        if (plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold) return null

        val result = executor.execute(plan) ?: return null
        val composed = composer.compose(result, plan)
        if (composed.text.isBlank()) return null

        state.noteAnswer(plan, resultKeys(result))
        lastPlan = plan
        return composed
    }

    /**
     * Answer a two-part question, or return null so it is planned as one.
     *
     * Each clause is planned in turn against a scratch state, so the second inherits the
     * subject of the first — "…and how does **it** compare to lead" resolves through exactly
     * the same slot-inheritance that makes a follow-up turn work.
     *
     * All or nothing: if either clause fails to plan, the whole attempt is abandoned and the
     * query falls through to be handled whole. A partial compound answer would be worse than
     * the single-clause answer it replaced.
     */
    private fun compoundAnswer(query: String, state: DialogueState): ComposedAnswer? {
        val clauses = ClauseSplitter.split(query) ?: return null

        val scratch = state.copyOf()
        val answers = ArrayList<ComposedAnswer>(clauses.size)
        val citations = ArrayList<Citation>(clauses.size * 2)
        var lastClausePlan: QueryPlan? = null
        for (clause in clauses) {
            val plan = planner.plan(clause, scratch)
            if (plan.intent == Intent.UNKNOWN || plan.confidence < planner.threshold) return null
            lastClausePlan = plan
            val result = executor.execute(plan) ?: return null
            // Bodies only, so the merged answer carries one sources section rather than one
            // after every clause.
            val composed = composer.composeBody(result, plan)
            if (composed.text.isBlank()) return null
            scratch.noteAnswer(plan, resultKeys(result))
            answers.add(composed)
            citations.addAll(result.citations)
        }
        if (answers.size < 2) return null

        state.adopt(scratch)
        // The final clause's plan. A compound answer has no single plan, and the last clause is the
        // one the conversation is left focused on.
        lastPlan = lastClausePlan
        return ComposedAnswer(
            text = answers.joinToString("\n\n") { it.text } +
                    composer.citationsFor(citations.distinctBy { it.label to it.source }),
            actions = answers.flatMap { it.actions }.distinctBy { it.label },
            // One card per message. Two visuals in a single bubble is clutter, and it would make the
            // adapter's view-type scheme combinatorial for no gain.
            card = answers.firstNotNullOfOrNull { it.card }
        )
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
        lastPlan = plan
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
        lastPlan = plan
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
        is ExecutionResult.EmissionSpectrum -> listOf(result.element.key)
        is ExecutionResult.Comparative -> listOf(result.winner.key, result.loser.key)
        is ExecutionResult.Neighbour -> listOf(result.to.key)
        is ExecutionResult.Nuclide -> listOf(result.element.key)
        is ExecutionResult.IsotopeComparison ->
            listOf(result.left.element.key, result.right.element.key).distinct()
        is ExecutionResult.NoData -> listOfNotNull(result.element?.key)
        // A withheld answer still establishes what the conversation is about, so a follow-up such
        // as "and its density?" resolves against the element the user just asked about.
        is ExecutionResult.Locked -> listOfNotNull(result.element?.key)
        is ExecutionResult.Decay -> listOf(result.element.key)
        // A conversion, a balanced equation and a solution calculation are about a number or a
        // compound, not about an element, so none of them establishes a subject for "and its
        // density?" to inherit.
        is ExecutionResult.Formula, is ExecutionResult.MoleConversion,
        is ExecutionResult.UnitConversion, is ExecutionResult.BalancedEquation,
        is ExecutionResult.SolutionCalc,
        is ExecutionResult.Dataset, is ExecutionResult.Empty -> emptyList()
    }
}
