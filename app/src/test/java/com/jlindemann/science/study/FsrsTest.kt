package com.jlindemann.science.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The study coach's scheduler against `shared/golden/fsrs.tsv`, which `tools/fsrs_golden.py` writes
 * and the Windows `FsrsTest` reads too (roadmap 2.6).
 */
class FsrsTest {

    private val golden: File? by lazy {
        listOf(File("../shared/golden/fsrs.tsv"), File("shared/golden/fsrs.tsv")).firstOrNull { it.isFile }
    }

    @Test
    fun everyGoldenReviewMatches() {
        val file = golden
        assumeTrue("shared/golden/fsrs.tsv not reachable", file != null)
        val rows = file!!.readLines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("case\t") }
        assertTrue(rows.size > 30)

        var case = ""
        var card: CardState? = null
        var now = START
        for (row in rows) {
            val c = row.split('\t')
            if (c[0] != case) { case = c[0]; card = null; now = START }
            if (c[1].toInt() > 0) now += (c[2].toDouble() * Fsrs.DAY_MS).roundToLong()
            card = Fsrs.review(card, c[3].toInt(), now)
            val label = "${c[0]} step ${c[1]}"
            assertClose(label, c[4].toDouble(), card.stability)
            assertClose(label, c[5].toDouble(), card.difficulty)
            assertEquals(label, c[6].toLong(), (card.due - card.last) / Fsrs.DAY_MS)
            assertEquals(label, c[7].toInt(), card.reps)
            assertEquals(label, c[8].toInt(), card.lapses)
        }
    }

    @Test
    fun aMissedCardIsDueSoonerThanAKnownOne() {
        val good = Fsrs.review(null, Fsrs.GOOD, START)
        val again = Fsrs.review(null, Fsrs.AGAIN, START)
        assertTrue(again.due < good.due)
    }

    @Test
    fun theDeckSchedulesOnlyItsSkills() {
        val deck = StudyDeck()
        assertNull(deck.record("superlative_highest", "gold", true, START))
        assertNull(deck.record("periodic_group", null, true, START))
        assertEquals("periodic_group:gold", deck.record("periodic_group", "Gold", false, START))
        assertEquals(1, deck.snapshot().size)
    }

    @Test
    fun dueCardsComeMostOverdueFirst() {
        val deck = StudyDeck()
        deck.record("periodic_group", "gold", true, START)       // due in 3 days
        deck.record("periodic_period", "iron", false, START)     // due in 1 day
        assertEquals(emptyList<String>(), deck.due(START))
        assertEquals(listOf("periodic_period:iron"), deck.due(START + 2 * Fsrs.DAY_MS))
        assertEquals(listOf("periodic_period:iron", "periodic_group:gold"), deck.due(START + 10 * Fsrs.DAY_MS))
    }

    @Test
    fun theLaterReviewWinsAMerge() {
        val older = Fsrs.review(null, Fsrs.GOOD, START)
        val newer = Fsrs.review(older, Fsrs.AGAIN, START + 3 * Fsrs.DAY_MS)
        val deck = StudyDeck(mapOf("periodic_group:gold" to newer))
        deck.mergeFrom(mapOf("periodic_group:gold" to older, "periodic_group:iron" to older))
        assertEquals(newer, deck.card("periodic_group:gold"))
        assertEquals(older, deck.card("periodic_group:iron"))
        assertEquals(newer, StudyDeck.newer(older, newer))
        assertEquals(newer, StudyDeck.newer(newer, older))
    }

    @Test
    fun theLocalFileRoundTrips() {
        val deck = StudyDeck()
        deck.record("periodic_group", "gold", true, START)
        deck.record("isotope_neutrons", "uranium", false, START + 5)
        assertEquals(deck.snapshot(), StudyDeck.decode(StudyDeck.encode(deck.snapshot())))
        assertEquals(emptyMap<String, CardState>(), StudyDeck.decode("garbage\nline"))
    }

    private fun assertClose(label: String, expected: Double, actual: Double) {
        assertTrue("$label: expected $expected, was $actual", abs(expected - actual) <= 1e-9 * max(1.0, abs(expected)))
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
