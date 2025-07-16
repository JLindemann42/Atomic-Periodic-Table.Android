package com.jlindemann.science.util

import android.content.Context
import kotlin.math.roundToInt
import kotlin.math.pow

object XpManager {
    private const val XP_KEY = "user_xp"
    private const val PREFS = "xp_prefs"
    private const val XP_START = 100     // XP required for level 1
    private const val LEVELS = 100

    // Each level requires double the XP of the previous
    private fun xpToReachLevel(n: Int): Int {
        if (n <= 1) return 0
        val xp = XP_START * 2.0.pow(n - 1)
        return xp.roundToInt()
    }

    fun getLevel(xp: Int): Int {
        var level = 1
        while (level < LEVELS && xpToReachLevel(level + 1) <= xp) {
            level++
        }
        return level
    }

    fun getXpForLevel(level: Int): Int {
        return xpToReachLevel(level)
    }

    fun getXp(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(XP_KEY, 0)
    }

    fun addXp(context: Context, amount: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldXp = prefs.getInt(XP_KEY, 0)
        val newXp = oldXp + amount
        prefs.edit().putInt(XP_KEY, newXp).apply()
    }

    fun getCurrentLevel(context: Context): Int {
        return getLevel(getXp(context))
    }

    fun getXpProgressInLevel(context: Context): Pair<Int, Int> {
        val xp = getXp(context)
        val level = getLevel(xp)
        val minXp = getXpForLevel(level)
        val maxXp = getXpForLevel(level + 1)
        return Pair(xp - minXp, maxXp - minXp)
    }
}