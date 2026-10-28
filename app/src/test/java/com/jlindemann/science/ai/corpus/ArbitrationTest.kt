package com.jlindemann.science.ai.corpus

import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.DeclineReason
import com.jlindemann.science.ai.nlu.Arbiter
import com.jlindemann.science.ai.nlu.Branch
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
    fun anEarlierBranchWinsAndWithinOneTheFirstProposal() {
        val planner = CorpusHarness.stackFor("en").planner
        val proposals = planner.proposals("which elements are in the same group as gold", DialogueState())
        val pick = Arbiter.pick(proposals)!!
        assertTrue(proposals.all { it.branch.ordinal >= pick.branch.ordinal })
        assertEquals(proposals.first { it.branch == pick.branch }, pick)
        assertEquals(DeclineReason.NO_MATCH, proposals.last().plan.declined)
    }

    /** Which branches compete, and how often each wins: a scoreboard, not a gate. */
    @Test
    fun reportsWhereTheOrderStillDecides() {
        val contests = HashMap<Pair<Branch, Branch>, Int>()
        var contested = 0
        for (case in rows) {
            val proposals = CorpusHarness.stackFor(case.lang).planner.proposals(case.query, DialogueState())
                .filter { it.branch != Branch.NO_MATCH }
            val winner = Arbiter.pick(proposals) ?: continue
            val losers = proposals.map { it.branch }.filter { it != winner.branch }.toSet()
            if (losers.isNotEmpty()) contested++
            for (loser in losers) contests.merge(winner.branch to loser, 1, Int::plus)
        }
        println("[arbitration] ${rows.size} rows, $contested with more than one branch proposing")
        contests.entries.sortedByDescending { it.value }.forEach { (pair, count) ->
            println("[arbitration] ${pair.first} over ${pair.second}: $count")
        }
    }
}
