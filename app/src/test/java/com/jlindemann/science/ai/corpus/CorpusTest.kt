package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.nlu.ClauseSplitter
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the training corpus against the real agent stack.
 *
 * Deliberately a **scoreboard, not a tripwire**: every row in a file is evaluated and the failures
 * are reported together with a pass rate. Failing on the first bad row would make a 3,000-question
 * corpus useless for the thing it is for — seeing how wide a defect is, and watching a number move
 * as it gets fixed.
 *
 * Two pseudo-intents are recognised in `expect_intent`, because clause splitting is a decision made
 * before any intent exists:
 *  - `COMPOUND` — the query must split into clauses and the engine must answer it
 *  - `NOSPLIT` — the query must *not* split; this is the guard on "compare gold and silver"
 */
class CorpusTest {

    /** Card expectations are enforced now that the card layer exists. */
    private val enforceCards = true

    @Before
    fun setUp() {
        assumeTrue("real assets/strings not reachable", CorpusHarness.available)
        assumeTrue("corpus not reachable", QuestionCorpus.available)
    }

    @Test fun properties() = runFile("properties")
    @Test fun categories() = runFile("categories")
    @Test fun superlativesAndFilters() = runFile("superlatives_filters")
    @Test fun comparisons() = runFile("comparisons")
    @Test fun aggregates() = runFile("aggregates")
    @Test fun isotopesAndNuclides() = runFile("isotopes_nuclides")
    @Test fun safety() = runFile("safety")
    @Test fun calculations() = runFile("calculations")

    /**
     * The four calculators, each with the negative rows that pin where its branch stops.
     *
     * Kept as separate files rather than folded into `calculations`, because the guard ordering
     * between them is the fragile part: each new branch sits above an older one that would
     * otherwise claim the same query, and a failure here should name which boundary moved.
     */
    @Test fun unitConversion() = runFile("unit_convert")
    @Test fun balanceEquations() = runFile("balance_equations")
    @Test fun decay() = runFile("decay")
    @Test fun solutions() = runFile("solutions")
    @Test fun compound() = runFile("compound")
    @Test fun followups() = runFile("followups")

    /** Questions that need several mechanisms at once — a subset and a threshold and an ordinal. */
    @Test fun complex() = runFile("complex")

    /**
     * Whole conversations rather than isolated turns.
     *
     * A rule that holds for one follow-up can still drift over five, and an interruption that is
     * correctly declined can still leave the thread broken behind it. These replay each session
     * against one `DialogueState`, so a failure part-way through stops that thread — which is the
     * point: a conversation is only as good as its weakest hop.
     */
    @Test fun conversationsSimple() = runFile("conversations_simple")

    @Test fun conversationsComplex() = runFile("conversations_complex")
    @Test fun datasets() = runFile("datasets")
    @Test fun mustDefer() = runFile("must_defer")

    /**
     * "What can you do", in every language. A question about the assistant, whose answer is the
     * feature overview the personality layer holds — so the grounded engine has to decline all of
     * them, or the user is shown a property of an element instead.
     */
    @Test fun capabilities() = runFile("capabilities")
    @Test fun cards() = runFile("cards")
    @Test fun localizedSwedish() = runFile("localized_sv")
    @Test fun localizedOther() = runFile("localized_other")

    /**
     * The same twenty-two question shapes in each of the eleven non-English languages.
     *
     * Uniform by design: a gap shows up as a row of failures down one language rather than as
     * scattered noise, and each shape can be compared directly against its English equivalent.
     */
    @Test fun multilingual() = runFile("multilingual")

    /**
     * Thirty *further* shapes in each of the twelve languages, reaching the half of the field
     * registry the first multilingual pass never touched — conductivity, hardness, appearance,
     * phase, the two discovery fields, oxidation states, configuration, group, period, valence
     * electrons, protons — plus the aggregate and count shapes.
     */
    @Test fun multilingualWide() = runFile("multilingual_wide")

    /**
     * The computational shapes in every language: thresholds, ranges, negation, ranks, subset
     * superlatives, medians, mole conversions, nuclide comparisons, neighbours and compound
     * questions. English is the only language where an operator word is never inflected and never
     * collides with an article, so this is where the operator layer is actually tested.
     */
    @Test fun multilingualOperators() = runFile("multilingual_operators")

    /** Two whole threads per language, where localized follow-up inheritance actually breaks. */
    @Test fun conversationsMultilingual() = runFile("conversations_multi")

    /**
     * Threads that change what *kind* of question they are asking while the subject stays put.
     *
     * The complement to [conversationsDeep], which varies the subject and holds the shape. Each hop
     * here has to hand on a different slot than the one before it, which is where a dialogue state
     * leaks.
     */
    @Test fun conversationsRepair() = runFile("conversations_repair")

    /**
     * Three six-turn threads per language.
     *
     * Longer than [conversationsMultilingual] because drift is cumulative: a rule that survives one
     * follow-up can still be wrong by the fifth, and a deferral that quietly resets the dialogue
     * state is only visible in the turn *after* it.
     */
    @Test fun conversationsDeep() = runFile("conversations_deep")

    /**
     * The queries from the bug reports. Unlike the other files this one is a tripwire — every row
     * is a defect a user actually hit, so a single regression here should fail loudly.
     */
    @Test fun regressions() = runFile("regressions")

    /**
     * The second round of reported conversations. A tripwire like [regressions], for the same
     * reason: every row is a turn a user watched go wrong.
     */
    @Test fun reported() = runFile("reported")

    // ---- Runner ---------------------------------------------------------------------------------

    private fun runFile(name: String) {
        val sessions = QuestionCorpus.sessions(name)
        if (sessions.isEmpty()) fail("corpus file $name.tsv is empty or missing")

        val failures = ArrayList<String>()
        var evaluated = 0

        for (session in sessions) {
            // One state per session, so a follow-up row inherits from the row before it and a
            // standalone row starts clean.
            val state = DialogueState()
            for (case in session) {
                evaluated++
                runCatching { evaluate(case, state) }
                    .onSuccess { problem -> problem?.let { failures.add("${case.where} $it") } }
                    .onFailure { failures.add("${case.where} threw ${it::class.simpleName}: ${it.message}") }
            }
        }

        val passed = evaluated - failures.size
        val rate = if (evaluated == 0) 0.0 else 100.0 * passed / evaluated
        println("[corpus] $name: $passed/$evaluated passed (%.1f%%)".format(rate))

        if (failures.isNotEmpty()) {
            val report = failures.joinToString("\n  ") { it }
            fail("$name.tsv: ${failures.size} of $evaluated failed (%.1f%% passed)\n  %s".format(rate, report))
        }
    }

    /** @return a description of what went wrong, or null when the case passed. */
    private fun evaluate(case: CorpusCase, state: DialogueState): String? {
        val stack = CorpusHarness.stackFor(case.lang)

        when (case.expectIntent) {
            "NOSPLIT" -> return if (ClauseSplitter.split(case.query) != null) {
                "'${case.query}' must not split, but did"
            } else null

            "COMPOUND" -> {
                val clauses = ClauseSplitter.split(case.query)
                    ?: return "'${case.query}' must split into clauses, but did not"
                if (clauses.size < 2) return "'${case.query}' split into only ${clauses.size} clause"
                val answer = stack.engine.answer(case.query, state)
                    ?: return "'${case.query}' split correctly but the engine declined it"
                return if (answer.text.isBlank()) "'${case.query}' produced a blank compound answer" else null
            }
        }

        val plan = stack.planner.plan(case.query, state)
        val claimed = plan.intent != Intent.UNKNOWN && plan.confidence >= stack.planner.threshold

        if (case.mustDefer) {
            return if (claimed) {
                "'${case.query}' should have been declined but was claimed as ${plan.intent} " +
                        "(conf=${"%.2f".format(plan.confidence)}, evidence=${plan.evidence})"
            } else null
        }

        if (!claimed) {
            return "'${case.query}' was not claimed (intent=${plan.intent}, " +
                    "conf=${"%.2f".format(plan.confidence)})"
        }

        case.expectIntent?.let { expected ->
            if (plan.intent.name != expected) {
                return "'${case.query}' planned as ${plan.intent}, expected $expected"
            }
        }

        val missingFields = case.expectFields.filterNot { it in plan.fieldIds }
        if (missingFields.isNotEmpty()) {
            return "'${case.query}' resolved fields ${plan.fieldIds}, missing $missingFields"
        }

        val result = stack.executor.execute(plan)
            ?: return "'${case.query}' planned as ${plan.intent} but produced no result"
        if (result is ExecutionResult.Empty) {
            return "'${case.query}' produced an empty result"
        }

        val composed = stack.composer.compose(result, plan)
        if (composed.text.isBlank()) {
            return "'${case.query}' composed a blank answer from ${result::class.simpleName}"
        }
        // The renderer understands only `###` and `**bold**`; a leaked resource sentinel or a
        // sentinel value is a user-visible defect, not a cosmetic one.
        if (composed.text.contains("str:")) {
            return "'${case.query}' leaked an unresolved string resource: ${composed.text.take(120)}"
        }
        if (composed.text.contains("---")) {
            return "'${case.query}' leaked the missing-value sentinel: ${composed.text.take(120)}"
        }

        // Record the turn so the next row in this session can inherit from it.
        state.noteAnswer(plan, resultKeys(result))

        if (enforceCards) {
            case.expectCard?.let { expected ->
                val actual = composed.card?.kind?.name
                if (expected == "none" && actual != null) {
                    return "'${case.query}' should produce no card but produced $actual"
                }
                if (expected != "none" && actual != expected) {
                    return "'${case.query}' produced card $actual, expected $expected"
                }
            }
        }

        return null
    }

    private fun resultKeys(result: ExecutionResult): List<String> = when (result) {
        is ExecutionResult.Property -> listOf(result.element.key)
        is ExecutionResult.Comparison -> result.elements.map { it.key }
        is ExecutionResult.ElementList -> result.results.map { it.element.key }
        is ExecutionResult.Aggregate -> result.contributors.map { it.element.key }
        is ExecutionResult.Isotopes -> listOf(result.element.key)
        is ExecutionResult.Safety -> listOf(result.element.key)
        is ExecutionResult.Comparative -> listOf(result.winner.key, result.loser.key)
        is ExecutionResult.Neighbour -> listOf(result.to.key)
        is ExecutionResult.Nuclide -> listOf(result.element.key)
        is ExecutionResult.IsotopeComparison ->
            listOf(result.left.element.key, result.right.element.key).distinct()
        is ExecutionResult.NoData -> listOfNotNull(result.element?.key)
        else -> emptyList()
    }
}
