package com.jlindemann.science.ai.chat

import com.jlindemann.science.ai.core.Entitlements
import com.jlindemann.science.study.Fsrs
import com.jlindemann.science.study.StudyAccess
import com.jlindemann.science.study.StudyDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The study coach from chat: answers become cards, and "review" asks the due ones (roadmap 2.6). */
class StudyCoachTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    private var now = 1_700_000_000_000L
    private val deck = StudyDeck()
    private var saves = 0
    private val access = StudyAccess(deck, save = { saves++ }, now = { now })

    @Test
    fun theCoachIsPro() {
        val free = Entitlements(isPro = false, isProPlus = false)
        val chat = ChatFixture.chat(1, entitlements = free, study = access)
        val text = chat.ask("review").text
        assertTrue(text, text.contains("PRO"))
        assertNull(chat.quizRun)

        // And a free user's quiz answers schedule nothing.
        chat.ask("quiz me on period 3")
        chat.ask(chat.quizRun!!.current.correctAnswer)
        assertTrue(deck.snapshot().isEmpty())
    }

    @Test
    fun nothingIsDueAtFirst() {
        val text = ChatFixture.chat(2, study = access).ask("what should I study").text
        assertTrue(text, text.contains("Nothing is due"))
    }

    @Test
    fun missedRunQuestionsComeBackAsAReview() {
        val chat = ChatFixture.chat(3, study = access)
        chat.ask("quiz me on period 3")
        val run = chat.quizRun!!
        repeat(run.questions.size) {
            val q = chat.quizRun!!.current
            chat.ask(q.alternatives.first { it != q.correctAnswer })
        }
        val scheduled = run.questions.mapNotNull { q ->
            q.subjectKey?.takeIf { q.category in StudyDeck.SKILLS }?.let { StudyDeck.key(q.category!!, it) }
        }.toSet()
        assertTrue("no question fed a card", scheduled.isNotEmpty())
        assertEquals(scheduled, deck.snapshot().keys)
        assertTrue(saves >= scheduled.size)
        assertTrue(deck.snapshot().values.all { it.lapses == 1 })

        // A missed card is due the next day.
        now += 2 * Fsrs.DAY_MS
        val reply = chat.ask("review")
        val review = chat.quizRun
        assertNotNull(reply.text, review)
        assertTrue(reply.text, reply.text.contains("Time to review"))
        val asked = review!!.questions.map { StudyDeck.key(it.category!!, it.subjectKey!!) }.toSet()
        assertEquals(scheduled, asked)
    }

    @Test
    fun aRightReviewAnswerPushesTheCardOut() {
        deck.record("periodic_group", "gold", false, now)
        now += 2 * Fsrs.DAY_MS
        val chat = ChatFixture.chat(4, study = access)
        chat.ask("review")
        val question = chat.quizRun!!.current
        assertEquals("gold", question.subjectKey)
        chat.ask(question.correctAnswer)
        val card = deck.card("periodic_group:gold")!!
        assertEquals(2, card.reps)
        assertTrue(card.due > now + Fsrs.DAY_MS)
        assertTrue(deck.due(now).isEmpty())
    }

    @Test
    fun reviewInsideALongerQuestionIsThatQuestion() {
        val chat = ChatFixture.chat(5, study = access)
        chat.ask("can you review the trend of electronegativity across period 3 for me please")
        assertNull(chat.quizRun)
    }
}
