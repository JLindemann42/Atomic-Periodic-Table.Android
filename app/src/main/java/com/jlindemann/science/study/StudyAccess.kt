package com.jlindemann.science.study

/**
 * What the chat needs from the study coach (roadmap 2.6): the deck, a way to keep a change, and
 * the clock. The app wraps its [StudyStore]; a test passes a fresh deck and a fixed time.
 */
class StudyAccess(
    val deck: StudyDeck,
    val save: () -> Unit = {},
    val now: () -> Long = System::currentTimeMillis
)
