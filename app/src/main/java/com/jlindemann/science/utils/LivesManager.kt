package com.jlindemann.science.util

import android.content.Context
import android.content.SharedPreferences

object LivesManager {
    private const val PREFS_NAME = "lives_prefs"
    private const val LIVES_KEY = "lives_count"
    private const val LAST_REFILL_KEY = "last_refill_time"
    private const val MAX_LIVES = 20
    private const val REFILL_INTERVAL_MS =  60 * 1000L // 60s cooldown

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLives(context: Context): Int {
        checkAndRefillLives(context)
        return getPrefs(context).getInt(LIVES_KEY, MAX_LIVES)
    }

    fun setLives(context: Context, lives: Int) {
        getPrefs(context).edit().putInt(LIVES_KEY, lives.coerceAtMost(MAX_LIVES)).apply()
    }

    fun loseLife(context: Context): Boolean {
        checkAndRefillLives(context)
        val prefs = getPrefs(context)
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        return if (lives > 0) {
            prefs.edit().putInt(LIVES_KEY, lives - 1).apply()
            true
        } else {
            false
        }
    }

    fun refillLivesIfNeeded(context: Context) {
        checkAndRefillLives(context)
    }

    private fun checkAndRefillLives(context: Context) {
        val prefs = getPrefs(context)
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        val lives = prefs.getInt(LIVES_KEY, MAX_LIVES)
        if (now - lastRefill > REFILL_INTERVAL_MS && lives < MAX_LIVES) {
            // Refill
            prefs.edit().putInt(LIVES_KEY, MAX_LIVES)
                .putLong(LAST_REFILL_KEY, now)
                .apply()
        }
    }

    fun getMillisToRefill(context: Context): Long {
        val prefs = getPrefs(context)
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        return REFILL_INTERVAL_MS - (now - lastRefill)
    }
}