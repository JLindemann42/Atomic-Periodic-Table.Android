package com.jlindemann.science.ai

import android.content.Context
import android.content.SharedPreferences
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import java.util.Calendar

/**
 * Manages rate limiting for AI chat messages based on user's PRO status.
 */
class AIRateLimiter(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_rate_limit_prefs", Context.MODE_PRIVATE)
    
    private val proPref = ProVersion(context)
    private val proPlusPref = ProPlusVersion(context)

    fun canSendMessage(): Boolean {
        if (isProPlus()) return true
        
        val limit = if (isPro()) 64 else 16
        val count = getTodayMessageCount()
        
        return count < limit
    }

    fun incrementMessageCount() {
        if (isProPlus()) return
        val today = getTodayKey()
        prefs.edit().putInt(today, prefs.getInt(today, 0) + 1).apply()
    }

    fun getRemainingMessages(): Int {
        if (isProPlus()) return Int.MAX_VALUE
        
        val limit = if (isPro()) 64 else 16
        val count = getTodayMessageCount()
        
        return (limit - count).coerceAtLeast(0)
    }

    fun getDailyLimit(): Int {
        if (isProPlus()) return Int.MAX_VALUE
        return if (isPro()) 64 else 16
    }

    fun isPro(): Boolean = proPref.getValue() == 100
    fun isProPlus(): Boolean = proPlusPref.getValue() == 100

    /**
     * When the count rolls over, as a wall-clock timestamp.
     *
     * The counter is keyed by calendar day in the device's own time zone — see [getTodayKey] — so
     * the reset is the next local midnight, and this derives it from the same Calendar rather than
     * assuming 24 hours from now. That distinction is the whole point of telling the user: someone
     * who runs out at 23:50 gets their messages back in ten minutes, not tomorrow evening.
     */
    fun resetTimeMillis(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun getTodayMessageCount(): Int {
        return prefs.getInt(getTodayKey(), 0)
    }

    private fun getTodayKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "msg_count_${year}_${month}_${day}"
    }
}
