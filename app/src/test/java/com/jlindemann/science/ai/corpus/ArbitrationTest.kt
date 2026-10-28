package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.DeclineReason
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.nlu.Arbiter
import com.jlindemann.science.ai.nlu.Branch
import com.jlindemann.science.ai.nlu.Proposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The planner's branches and the arbiter that chooses among them (roadmap 1.8), over every corpus
 * row, each asked fresh.
 *
 * Besides the invariants, this prints which branches propose together and which wins: the map of
 * where the order of [Branch] still decides an answer, which is what the later stages of 1.8 take
 * apart one conflict at a time.
 */
class ArbitrationTest {

    @Before
    fun setUp() {
        assumeTrue(CorpusHarness.available && QuestionCorpus.available)
    }

    private val rows by lazy { QuestionCorpus.FILES.flatMap { QuestionCorpus.sessions(it) }.flatten() }

    @Test
    fun everyQueryEndsInNoMatchAndThePickIsThePlan() {
        for (case in rows) {
            val planner = CorpusHarness.stackFor(case.lang).planner
            val proposals = planner.proposals(case.query, DialogueState())
            assertEquals(case.where, Branch.NO_MATCH, proposals.last().branch)
            val plan = planner.plan(case.query, DialogueState())
            // The speller may plan a corrected query after a NO_MATCH; otherwise the plan is the pick.
            if (plan.evidence.none { it.startsWith("spelling: ") }) {
                assertEquals(case.where, Arbiter.pick(proposals)!!.plan, plan)
            }
        }
    }

    @Test
    fun aPreemptingBranchWinsByOrder() {
        val planner = CorpusHarness.stackFor("en").planner
        val proposals = planner.proposals("which elements are in the same group as gold", DialogueState())
        val pick = Arbiter.pick(proposals)!!
        assertEquals(Branch.SET_OF_AN_ELEMENT, pick.branch)
        assertTrue(pick.branch.preempts)
        assertTrue(proposals.all { it.branch.ordinal >= pick.branch.ordinal })
        assertEquals(DeclineReason.NO_MATCH, proposals.last().plan.declined)
    }

    @Test
    fun otherwiseTheReadingThatUsesMostOfTheQueryWins() {
        val planner = CorpusHarness.stackFor("en").planner
        val proposals = planner.proposals("what is the average electronegativity of the halogens", DialogueState())
        val pick = Arbiter.pick(proposals)!!
        assertEquals(Branch.AGGREGATE, pick.branch)
        assertTrue("statistic" in pick.uses)
        val list = proposals.first { it.branch == Branch.SUBSET_LIST }
        assertTrue(pick.score > list.score)
    }

    @Test
    fun onATieTheEarlierBranchAndWithinOneTheFirstProposal() {
        val plan = QueryPlan(Intent.UNKNOWN)
        val a = Proposal(Branch.COMPARISON, plan, setOf("element au"))
        val b = Proposal(Branch.COMPARISON, plan, setOf("element ag"))
        val c = Proposal(Branch.DIRECT_COMPARATIVE, plan, setOf("element pb"))
        assertTrue(Arbiter.pick(listOf(a, b)) === a)
        assertTrue(Arbiter.pick(listOf(a, b, c)) === c)
    }

    /**
     * Which branches compete, and what decided each contest: the evidence, or - on a tie - the
     * order. A scoreboard, not a gate. Also lists every row where the evidence picks another
     * branch than the order would have.
     */
    @Test
    fun reportsWhereTheOrderStillDecides() {
        val byEvidence = HashMap<Pair<Branch, Branch>, Int>()
        val byOrder = HashMap<Pair<Branch, Branch>, Int>()
        var contested = 0
        var changed = 0
        for (case in rows) {
            val proposals = CorpusHarness.stackFor(case.lang).planner.proposals(case.query, DialogueState())
                .filter { it.branch != Branch.NO_MATCH }
            val winner = Arbiter.pick(proposals) ?: continue
            // Two branches that reach the same plan agree; only a different answer is a conflict.
            val answer = winner.plan.copy(evidence = emptyList(), confidence = 0.0)
            val losers = proposals.filter {
                it.branch != winner.branch && it.plan.copy(evidence = emptyList(), confidence = 0.0) != answer
            }.groupBy { it.branch }
            if (losers.isNotEmpty()) contested++
            for ((loser, theirs) in losers) {
                val tally = if (winner.branch.preempts || theirs.all { it.score == winner.score }) byOrder else byEvidence
                tally.merge(winner.branch to loser, 1, Int::plus)
                if (tally === byOrder && !winner.branch.preempts)
                    println("[tie] ${winner.branch}/${loser}: ${case.query} ${winner.uses} vs ${theirs.first().uses}")
            }
            val old = Arbiter.byOrder(proposals)!!
            if (old.branch != winner.branch) {
                changed++
                println("[arbitration] changed ${case.where}: ${case.query} -> ${winner.branch} ${winner.uses} over ${old.branch} ${old.uses}")
            }
        }
        println("[arbitration] ${rows.size} rows, $contested with branches proposing different answers, $changed picked differently than by order")
        for ((name, tally) in listOf("evidence" to byEvidence, "order" to byOrder)) {
            tally.entries.sortedByDescending { it.value }.forEach { (pair, count) ->
                println("[arbitration] by $name: ${pair.first} over ${pair.second}: $count")
            }
        }
    }
}
