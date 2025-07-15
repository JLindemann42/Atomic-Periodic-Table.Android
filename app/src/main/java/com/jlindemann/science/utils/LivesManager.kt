package com.jlindemann.science.util

import android.content.Context
import android.content.SharedPreferences

object LivesManager {
    private const val PREFS_NAME = "lives_prefs"
    private const val LIVES_KEY = "lives_count"
    private const val LAST_REFILL_KEY = "last_refill_time"
    private const val MAX_LIVES = 2000
    private const val REFILL_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLives(context: Context): Int {
        return getPrefs(context).getInt(LIVES_KEY, MAX_LIVES)
    }

    // Ensure lives never go below 0 or above MAX_LIVES
    fun setLives(context: Context, lives: Int) {
        getPrefs(context).edit().putInt(LIVES_KEY, lives.coerceIn(0, MAX_LIVES)).apply()
    }

    fun loseLife(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        return if (lives > 0) {
            prefs.edit().putInt(LIVES_KEY, lives - 1).apply()
            true
        } else {
            false
        }
    }

    /**
     * Lose multiple lives atomically and never go below 0.
     * Returns true if any lives were lost, false if already 0.
     */
    fun loseLives(context: Context, count: Int): Boolean {
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        if (lives <= 0) return false
        val newLives = (lives - count).coerceAtLeast(0)
        prefs.edit().putInt(LIVES_KEY, newLives).apply()
        return true
    }

    /**
     * Only refills lives if all lives are 0 AND 4 hours have passed since last refill.
     * Returns true if refill happened.
     */
    fun refillLivesIfNeeded(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        if (lives == 0 && (now - lastRefill) >= REFILL_INTERVAL_MS) {
            prefs.edit()
                .putInt(LIVES_KEY, MAX_LIVES)
                .putLong(LAST_REFILL_KEY, now)
                .apply()
            return true
        }
        // Initialize lastRefill if first run
        if (lastRefill == 0L) {
            prefs.edit().putLong(LAST_REFILL_KEY, now).apply()
        }
        return false
    }

    /**
     * Returns ms until lives refill (only meaningful if lives == 0).
     * If lives > 0, returns 0.
     */
    fun getMillisToRefill(context: Context): Long {
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        if (lives > 0) return 0
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        val timeLeft = REFILL_INTERVAL_MS - (now - lastRefill)
        return if (timeLeft > 0) timeLeft else 0
    }
}