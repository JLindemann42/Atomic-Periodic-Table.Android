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
        
        val limit = if (isPro()) 200 else 30
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
        
        val limit = if (isPro()) 200 else 30
        val count = getTodayMessageCount()
        
        return (limit - count).coerceAtLeast(0)
    }

    fun getDailyLimit(): Int {
        if (isProPlus()) return Int.MAX_VALUE
        return if (isPro()) 200 else 30
    }

    fun isPro(): Boolean = proPref.getValue() == 100
    fun isProPlus(): Boolean = proPlusPref.getValue() == 100

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
