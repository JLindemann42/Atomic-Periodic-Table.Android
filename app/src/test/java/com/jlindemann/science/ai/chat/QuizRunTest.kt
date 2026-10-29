package com.jlindemann.science.ai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Quiz runs from chat: scoped, counted, answered with chips, and earning XP (roadmap 2.5). */
class QuizRunTest {

    @Before
    fun setUp() {
        assumeTrue(ChatFixture.available)
    }

    @Test
    fun aScopedRequestStartsARun() {
        for (query in listOf("quiz me on the halogens", "quiz me on period 3", "five isotope questions on uranium", "quiz me on gold")) {
            val chat = ChatFixture.chat(1)
            val reply = chat.ask(query)
            assertNotNull("$query: ${reply.text}", chat.quizRun)
            assertTrue(query, reply.suggestions.size >= 2)
            assertTrue(query, reply.suggestions.contains(chat.quizRun!!.current.correctAnswer))
        }
    }

    @Test
    fun aBareQuizMeIsStillOneQuestion() {
        val chat = ChatFixture.chat(2)
        chat.ask("quiz me")
        assertNull(chat.quizRun)
        assertNotNull(chat.state.pendingQuizAnswer)
    }

    @Test
    fun theCountIsRead() {
        assertEquals(5, ChatFixture.chat(3).also { it.ask("five isotope questions on uranium") }.quizRun!!.questions.size)
        assertEquals(3, ChatFixture.chat(3).also { it.ask("give me 3 quiz questions") }.quizRun!!.questions.size)
        assertEquals(QuizRun.DEFAULT_COUNT, ChatFixture.chat(3).also { it.ask("quiz me on period 3") }.quizRun!!.questions.size)
    }

    @Test
    fun periodThreeIsAScopeNotACount() {
        val run = ChatFixture.chat(4).also { it.ask("quiz me on period 3") }.quizRun!!
        assertEquals(setOf("sodium", "magnesium", "aluminium", "silicon", "phosphorus", "sulfur", "chlorine", "argon"), run.scopeKeys)
    }

    @Test
    fun isotopeQuestionsAreAboutTheNamedElement() {
        val run = ChatFixture.chat(5).also { it.ask("five isotope questions on uranium") }.quizRun!!
        for (question in run.questions) {
            assertTrue(question.question, question.question.contains("U-") || question.alternatives.all { it.startsWith("U-") })
        }
    }

    @Test
    fun rightAnswersEarnXpAndTheRunEnds() {
        var earned = 0
        val chat = ChatFixture.chat(6, onXp = { earned += it })
        chat.ask("quiz me on the noble gases")
        val run = chat.quizRun!!
        var last = ""
        repeat(run.questions.size) { last = chat.ask(chat.quizRun!!.current.correctAnswer).text }
        assertNull(chat.quizRun)
        assertEquals(run.questions.sumOf { it.baseXp }, earned)
        assertTrue(last, last.contains("${run.questions.size} of ${run.questions.size}"))
    }

    @Test
    fun aWrongAnswerSaysTheRightOne() {
        val chat = ChatFixture.chat(7)
        chat.ask("quiz me on period 2")
        val question = chat.quizRun!!.current
        val wrong = question.alternatives.first { it != question.correctAnswer }
        val text = chat.ask(wrong).text
        assertTrue(text, text.contains("Not quite") && text.contains(question.correctAnswer))
        assertEquals(1, chat.quizRun!!.index)
    }

    @Test
    fun stopEndsTheRun() {
        val chat = ChatFixture.chat(8)
        chat.ask("quiz me on the alkali metals")
        val text = chat.ask("stop").text
        assertNull(chat.quizRun)
        assertTrue(text, text.contains("stopped", ignoreCase = true))
    }

    @Test
    fun anUnrelatedQuestionLeavesTheRun() {
        val chat = ChatFixture.chat(9)
        chat.ask("quiz me on period 4")
        val text = chat.ask("what is the density of gold").text
        assertNull(chat.quizRun)
        assertFalse(text, text.contains("Not quite"))
        assertTrue(text, text.contains("19.3"))
    }

    @Test
    fun aRunInSwedish() {
        val chat = ChatFixture.chat(10, language = "sv")
        val reply = chat.ask("frågesport om ädelgaserna")
        assertNotNull(reply.text, chat.quizRun)
        assertTrue(reply.text, reply.text.contains("Fråga 1 av"))
    }
}
