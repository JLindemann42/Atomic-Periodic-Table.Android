package com.jlindemann.science.util

import android.content.Context
import android.content.SharedPreferences

object LivesManager {
    private const val PREFS_NAME = "lives_prefs"
    private const val LIVES_KEY = "lives_count"
    private const val LAST_REFILL_KEY = "last_refill_time"
    private const val MAX_LIVES = 100
    private const val REFILL_AMOUNT = 5
    private const val REFILL_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes

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
     * Refills 5 lives every 20 minutes if not at max.
     * Returns true if refill happened (lives increased).
     */
    fun refillLivesIfNeeded(context: Context): Boolean {
        val prefs = getPrefs(context)
        var lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        var lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()

        // Initialize lastRefill if first run
        if (lastRefill == 0L) {
            prefs.edit().putLong(LAST_REFILL_KEY, now).apply()
            return false
        }

        // If already at max, just update lastRefill time, no refill
        if (lives >= MAX_LIVES) {
            prefs.edit().putLong(LAST_REFILL_KEY, now).apply()
            return false
        }

        // Calculate how many refills have passed
        val intervalsPassed = ((now - lastRefill) / REFILL_INTERVAL_MS).toInt()
        if (intervalsPassed > 0) {
            val refillLives = intervalsPassed * REFILL_AMOUNT
            val newLives = (lives + refillLives).coerceAtMost(MAX_LIVES)
            // Update lives and lastRefill time
            prefs.edit()
                .putInt(LIVES_KEY, newLives)
                .putLong(LAST_REFILL_KEY, lastRefill + intervalsPassed * REFILL_INTERVAL_MS)
                .apply()
            return newLives > lives
        }
        return false
    }

    /**
     * Returns ms until next lives refill.
     * If at max, returns 0.
     */
    fun getMillisToRefill(context: Context): Long {
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        if (lives >= MAX_LIVES) return 0
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastRefill
        val timeLeft = REFILL_INTERVAL_MS - (timeSinceLast % REFILL_INTERVAL_MS)
        return if (timeLeft > 0) timeLeft else 0
    }
}