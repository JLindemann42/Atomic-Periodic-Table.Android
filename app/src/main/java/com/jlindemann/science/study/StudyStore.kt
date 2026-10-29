package com.jlindemann.science.study

import android.content.Context
import android.util.Log
import com.jlindemann.science.utils.EntitlementManager
import java.io.File

/**
 * The study coach's deck on this device (roadmap 2.6), kept in `study_cards.tsv` in the app's files.
 * One deck per process, loaded on first use and written after every change, so the Learning Games,
 * the chat and the progress sync all see the same cards.
 */
object StudyStore {

    private const val TAG = "StudyStore"
    private const val FILE = "study_cards.tsv"

    @Volatile
    private var deck: StudyDeck? = null

    fun deck(context: Context): StudyDeck =
        deck ?: synchronized(this) {
            deck ?: StudyDeck(load(context)).also { deck = it }
        }

    /**
     * Review the card an answered question feeds, if the user has PRO and the question is about
     * one element in a scheduled skill. Safe to call for every answer.
     */
    fun record(context: Context, category: String?, subjectKey: String?, correct: Boolean) {
        if (!EntitlementManager.hasAnyPro(context)) return
        val deck = deck(context)
        if (deck.record(category, subjectKey, correct, System.currentTimeMillis()) != null) save(context, deck)
    }

    /** Fold in the cloud's cards after a sync, keeping each card's later review. */
    fun mergeFrom(context: Context, cards: Map<String, CardState>) {
        if (cards.isEmpty()) return
        val deck = deck(context)
        deck.mergeFrom(cards)
        save(context, deck)
    }

    fun save(context: Context, deck: StudyDeck) {
        synchronized(this) {
            try {
                File(context.filesDir, FILE).writeText(StudyDeck.encode(deck.snapshot()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save the study deck", e)
            }
        }
    }

    private fun load(context: Context): Map<String, CardState> = try {
        File(context.filesDir, FILE).takeIf { it.isFile }?.readText()?.let(StudyDeck::decode).orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load the study deck", e)
        emptyMap()
    }
}
