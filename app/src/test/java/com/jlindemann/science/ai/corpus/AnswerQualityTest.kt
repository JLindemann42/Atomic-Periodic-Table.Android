package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.data.FieldRegistry
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * What an answer has to *say*, not merely which plan produced it.
 *
 * `CorpusTest` asserts routing: the right intent, the right field, a non-blank result. That leaves a
 * large gap — an answer can be correctly routed and still be a bare number with no context, which is
 * how most of the reported complaints actually felt to the user. "19.3 g/cm³" is right and useless;
 * "19.3 g/cm³, the 6th densest of 105 elements with a recorded density" is the same lookup made
 * worth reading.
 *
 * Each rule below is a property of the *shape* of an answer, checked over every corpus row that
 * produces that shape. Like `CorpusTest`, failures are aggregated so the output is a list of thin
 * answers rather than the first one.
 */
class AnswerQualityTest {

    @Before
    fun setUp() {
        assumeTrue("real assets/strings not reachable", CorpusHarness.available)
        assumeTrue("corpus not reachable", QuestionCorpus.available)
    }

    /**
     * A ranked value must be placed against the field's range.
     *
     * This is the single highest-value sentence the agent produces: it converts a figure nobody can
     * calibrate into a statement about where the element sits.
     */
    @Test
    fun rankableValuesCarryTheirRank() = check("rank context") { result, text, _ ->
        val property = result as? ExecutionResult.Property ?: return@check null
        if (property.rank == null || property.rankedOutOf < 2) return@check null
        // The rank sentence names the total; that number is the evidence it was included.
        if (!text.contains(property.rankedOutOf.toString())) {
            "ranked ${property.rank}/${property.rankedOutOf} but the answer never says so"
        } else null
    }

    /** A comparison must name both sides and both values, or it is not a comparison. */
    @Test
    fun comparativesShowBothSides() = check("both operands") { result, text, _ ->
        val comparative = result as? ExecutionResult.Comparative ?: return@check null
        val missing = listOf(
            comparative.winnerValue.display to "winner value",
            comparative.loserValue.display to "loser value"
        ).filterNot { text.contains(it.first) }
        if (missing.isNotEmpty()) "omits ${missing.joinToString { it.second }}" else null
    }

    /**
     * A list must say how many matched, not only show the top rows.
     *
     * Ten rows out of eleven and ten out of ninety are very different answers, and a reader cannot
     * tell them apart from the rows alone.
     */
    @Test
    fun listsDiscloseHowManyMatched() = check("match count") { result, text, _ ->
        val list = result as? ExecutionResult.ElementList ?: return@check null
        // A superlative also arrives as an ElementList, but its `matched` is the candidate pool it
        // was chosen from, not a truncated set of rows — asserting truncation there would be
        // measuring the wrong thing. Its own rule is below.
        if (list.results.size <= 1) return@check null
        if (list.matched <= list.results.size) return@check null
        if (!text.contains(list.matched.toString())) {
            "showed ${list.results.size} of ${list.matched} matches without saying so"
        } else null
    }

    /**
     * A superlative must say what it won against.
     *
     * "The densest element is osmium" is true and thin; "…the highest of the 105 elements with a
     * recorded density" is the same fact with the scale attached. A plain property lookup has always
     * done this, and the superlative — the more emphatic claim — did not.
     */
    @Test
    fun superlativesNameThePoolTheyTopped() = check("superlative pool") { result, text, _ ->
        val list = result as? ExecutionResult.ElementList ?: return@check null
        if (list.results.size != 1 || list.fieldId == null || list.rankOffset > 0) return@check null
        val pool = list.matched - list.missing
        if (pool < 3) return@check null
        if (!text.contains(pool.toString())) {
            "topped a pool of $pool without naming it"
        } else null
    }

    /**
     * An aggregate must disclose what it was computed over.
     *
     * A mean that silently skips the elements with no recorded value implies a completeness the
     * data does not have.
     */
    @Test
    fun aggregatesDiscloseTheirBasis() = check("aggregate basis") { result, text, _ ->
        val aggregate = result as? ExecutionResult.Aggregate ?: return@check null
        if (aggregate.contributors.isEmpty()) return@check null
        if (!text.contains(aggregate.contributors.size.toString())) {
            "averaged ${aggregate.contributors.size} values without saying how many"
        } else null
    }

    /**
     * An average or median must show the spread it came from.
     *
     * The mean density of the transition metals is about 10 g/cm3, and nothing in that figure hints
     * that the set runs from scandium to osmium. A central value with no range is the statistic most
     * likely to be quoted back as if it described every member.
     */
    @Test
    fun averagesShowTheirSpread() = check("spread") { result, text, _ ->
        val aggregate = result as? ExecutionResult.Aggregate ?: return@check null
        if (aggregate.aggregation == Aggregation.SUM || aggregate.aggregation == Aggregation.COUNT) {
            return@check null
        }
        val ends = aggregate.contributors.filter { it.quantity != null }.sortedBy { it.quantity!!.value }
        val low = ends.firstOrNull() ?: return@check null
        val high = ends.lastOrNull() ?: return@check null
        if (ends.size < 3 || low.element.key == high.element.key) return@check null
        if (!text.contains(low.display) || !text.contains(high.display)) {
            "averaged over ${ends.size} values without showing the range"
        } else null
    }

    /** An isotope answer must state the totals, since the list itself is truncated. */
    @Test
    fun isotopeAnswersStateTheTotals() = check("isotope totals") { result, text, _ ->
        val isotopes = result as? ExecutionResult.Isotopes ?: return@check null
        if (isotopes.total <= isotopes.shown.size) return@check null
        if (!text.contains(isotopes.total.toString())) {
            "showed ${isotopes.shown.size} of ${isotopes.total} isotopes without saying so"
        } else null
    }

    /**
     * A two-element comparison must say who came out ahead.
     *
     * A table of five properties against two elements is data, not an answer: it hands the reader
     * back the comparison they asked the agent to make.
     */
    @Test
    fun comparisonsDeclareAWinner() = check("verdict") { result, text, _ ->
        val comparison = result as? ExecutionResult.Comparison ?: return@check null
        if (comparison.elements.size != 2) return@check null
        val decided = comparison.fieldIds.count { fieldId ->
            val rows = comparison.values[fieldId].orEmpty().filter { it.quantity != null }
            rows.size == 2 && rows[0].quantity!!.value != rows[1].quantity!!.value
        }
        if (decided < 2) return@check null
        val named = comparison.elements.any { text.contains(it.key, ignoreCase = true) }
        if (!named) return@check null
        if (!text.contains(decided.toString())) "compared $decided fields without a verdict" else null
    }

    /**
     * A "no data" answer must point at something that does exist.
     *
     * Honest and a dead end is still a dead end. When the element has neighbouring properties in the
     * same family, naming them costs nothing and gives the reader somewhere to go.
     */
    @Test
    fun noDataOffersAnAlternative() = check("alternatives") { result, text, lang ->
        val noData = result as? ExecutionResult.NoData ?: return@check null
        val element = noData.element ?: return@check null
        val category = FieldRegistry.byId[noData.fieldId]?.category ?: return@check null
        val siblings = FieldRegistry.ALL.filter {
            it.category == category && it.id != noData.fieldId && element.value(it.id) != null
        }
        if (siblings.isEmpty()) return@check null
        // The answer must mention at least one of them by its label.
        val offered = siblings.any { spec ->
            runCatching { CorpusHarness.stackFor(lang).strings.get(spec.labelRes) }
                .getOrNull()
                ?.replace(":", "")?.trim()
                ?.let { it.isNotEmpty() && text.contains(it, ignoreCase = true) } == true
        }
        if (!offered) "${siblings.size} sibling properties are recorded but none was offered" else null
    }

    /** Nothing may reach the user with an unresolved resource or a missing-value sentinel. */
    @Test
    fun noAnswerLeaksInternals() = check("no leaks") { _, text, _ ->
        when {
            text.contains("str:") -> "leaked an unresolved string resource"
            text.contains("---") -> "leaked the missing-value sentinel"
            text.contains("null") -> "leaked a null"
            else -> null
        }
    }

    // ---- Runner ---------------------------------------------------------------------------------

    /**
     * Run a rule over every corpus row that the engine answers.
     *
     * @param rule receives the result, the composed text and the row's language, and returns a
     *   description of the problem, or null when the answer is fine. Rules that
     *   do not apply to a result shape return null, so each test states one property and ignores
     *   everything else.
     */
    private fun check(name: String, rule: (ExecutionResult, String, String) -> String?) {
        val failures = ArrayList<String>()
        var considered = 0

        for (file in QuestionCorpus.FILES) {
            for (session in QuestionCorpus.sessions(file)) {
                val state = DialogueState()
                for (case in session) {
                    if (case.mustDefer) continue
                    val stack = CorpusHarness.stackFor(case.lang)
                    val plan = runCatching { stack.planner.plan(case.query, state) }.getOrNull() ?: continue
                    if (plan.intent == Intent.UNKNOWN || plan.confidence < stack.planner.threshold) continue
                    val result = runCatching { stack.executor.execute(plan) }.getOrNull() ?: continue
                    val composed = runCatching { stack.composer.compose(result, plan) }.getOrNull() ?: continue
                    considered++
                    rule(result, composed.text, case.lang)?.let {
                        failures.add("${case.where} '${case.query}' — $it")
                    }
                    runCatching { state.noteAnswer(plan, emptyList()) }
                }
            }
        }

        println("[quality] $name: ${considered - failures.size}/$considered answers satisfied the rule")
        if (failures.isNotEmpty()) {
            fail(
                "$name: ${failures.size} of $considered answers fall short\n  " +
                        failures.take(40).joinToString("\n  ") +
                        if (failures.size > 40) "\n  …and ${failures.size - 40} more" else ""
            )
        }
    }
}
