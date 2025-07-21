package com.jlindemann.science.util

import android.content.Context
import kotlin.math.roundToInt
import kotlin.math.pow

object XpManager {
    private const val XP_KEY = "user_xp"
    private const val PREFS = "xp_prefs"
    private const val LEVELS = 100

    // Custom XP table for first 21 levels for fine control, then scale up exponentially
    private val xpTable = intArrayOf(
        0,       // Level 1
        90,      // Level 2
        180,     // Level 3
        270,     // Level 4
        360,     // Level 5
        495,     // Level 6
        630,     // Level 7
        765,     // Level 8
        900,     // Level 9
        1050,    // Level 10
        1275,    // Level 11
        1500,    // Level 12
        1725,    // Level 13
        1950,    // Level 14
        2175,    // Level 15
        2550,    // Level 16
        2925,    // Level 17
        3300,    // Level 18
        3675,    // Level 19
        4050,    // Level 20
        4575     // Level 21
    )

    // After level 21, scale exponentially and then linearly for very high levels
    private fun xpToReachLevel(n: Int): Int {
        if (n <= 1) return 0
        if (n - 1 < xpTable.size) return xpTable[n - 1]
        // Exponential scaling after level 21, then slow to linear at high levels
        // This formula can be adjusted for balance
        val base = xpTable.last()
        val extraLevels = n - xpTable.size
        return (base + (800 * (1.10.pow(extraLevels) - 1) / 0.10)).roundToInt()
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