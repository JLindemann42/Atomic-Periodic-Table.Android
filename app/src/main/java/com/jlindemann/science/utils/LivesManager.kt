package com.jlindemann.science.util

import android.content.Context
import android.content.SharedPreferences
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ProPlusVersion

object LivesManager {
    private const val PREFS_NAME = "lives_prefs"
    private const val LIVES_KEY = "lives_count"
    private const val LAST_REFILL_KEY = "last_refill_time"
    private const val DEFAULT_MAX_LIVES = 30
    private const val PRO_MAX_LIVES = 60
    private const val REFILL_AMOUNT = 5
    private const val REFILL_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Determines max lives based on user tier (Default, Pro, ProPlus).
     */
    fun getMaxLives(context: Context): Int {
        val proPlus = ProPlusVersion(context)
        val pro = ProVersion(context)
        val proPlusValue = proPlus.getValue()
        val proValue = pro.getValue()
        return when {
            proPlusValue == 100 -> Int.MAX_VALUE // Unlimited lives
            proValue == 100 -> PRO_MAX_LIVES
            else -> DEFAULT_MAX_LIVES
        }
    }

    fun getLives(context: Context): Int {
        val maxLives = getMaxLives(context)
        val lives = getPrefs(context).getInt(LIVES_KEY, maxLives)
        return if (maxLives == Int.MAX_VALUE) Int.MAX_VALUE else lives.coerceAtMost(maxLives)
    }

    fun setLives(context: Context, lives: Int) {
        val maxLives = getMaxLives(context)
        val value = if (maxLives == Int.MAX_VALUE) Int.MAX_VALUE else lives.coerceIn(0, maxLives)
        getPrefs(context).edit().putInt(LIVES_KEY, value).apply()
    }

    fun loseLife(context: Context): Boolean {
        val prefs = getPrefs(context)
        val maxLives = getMaxLives(context)
        val lives = getLives(context)
        if (maxLives == Int.MAX_VALUE) return true // Unlimited lives, never lose
        return if (lives > 0) {
            prefs.edit().putInt(LIVES_KEY, lives - 1).apply()
            true
        } else {
            false
        }
    }

    fun loseLives(context: Context, count: Int): Boolean {
        val prefs = getPrefs(context)
        val maxLives = getMaxLives(context)
        val lives = getLives(context)
        if (maxLives == Int.MAX_VALUE) return true // Unlimited
        if (lives <= 0) return false
        val newLives = (lives - count).coerceAtLeast(0)
        prefs.edit().putInt(LIVES_KEY, newLives).apply()
        return true
    }

    fun refillLivesIfNeeded(context: Context): Boolean {
        val prefs = getPrefs(context)
        val maxLives = getMaxLives(context)
        if (maxLives == Int.MAX_VALUE) return false // Unlimited, nothing to refill

        var lives = getLives(context)
        var lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()

        if (lastRefill == 0L) {
            prefs.edit().putLong(LAST_REFILL_KEY, now).apply()
            return false
        }

        if (lives >= maxLives) {
            prefs.edit().putLong(LAST_REFILL_KEY, now).apply()
            return false
        }

        val intervalsPassed = ((now - lastRefill) / REFILL_INTERVAL_MS).toInt()
        if (intervalsPassed > 0) {
            val refillLives = intervalsPassed * REFILL_AMOUNT
            val newLives = (lives + refillLives).coerceAtMost(maxLives)
            prefs.edit()
                .putInt(LIVES_KEY, newLives)
                .putLong(LAST_REFILL_KEY, lastRefill + intervalsPassed * REFILL_INTERVAL_MS)
                .apply()
            return newLives > lives
        }
        return false
    }

    fun getMillisToRefill(context: Context): Long {
        val prefs = getPrefs(context)
        val maxLives = getMaxLives(context)
        val lives = getLives(context)
        if (maxLives == Int.MAX_VALUE || lives >= maxLives) return 0
        val lastRefill = prefs.getLong(LAST_REFILL_KEY, 0L)
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastRefill
        val timeLeft = REFILL_INTERVAL_MS - (timeSinceLast % REFILL_INTERVAL_MS)
        return if (timeLeft > 0) timeLeft else 0
    }

    fun getRefillAmount(context: Context): Int {
        return REFILL_AMOUNT
    }

    fun getRefillIntervalMs(context: Context): Long {
        return REFILL_INTERVAL_MS
    }
}