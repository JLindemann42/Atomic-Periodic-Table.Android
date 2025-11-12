package com.jlindemann.science.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.jlindemann.science.activities.tools.StreakReminderReceiver
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Lightweight streak tracker compatible with API 24+.
 *
 * Notes:
 * - Uses java.util.Calendar + SimpleDateFormat (no java.time) for broad API compatibility.
 * - Schedules a single exact alarm ~24h ahead when streak >= 3. The receiver reschedules itself daily
 *   while the streak requirement is still met.
 * - Uses defensive error handling; callers should still validate where appropriate.
 *
 * Additions:
 * - setCurrentStreak(ctx, value) was added so external sync code (ProgressSyncManager) can update
 *   the local streak when a larger streak comes from the cloud. This method preserves/increases
 *   best streak and schedules/cancels reminders the same way recordPlay does.
 */
object StreakManager {
    private const val PREFS = "streak_prefs"
    private const val KEY_LAST_PLAY = "last_play_date" // ISO yyyy-MM-dd
    private const val KEY_STREAK = "current_streak"
    private const val KEY_BEST = "best_streak"
    private const val KEY_REMINDER_SCHEDULED = "reminder_scheduled"
    private const val REMINDER_ACTION = "com.jlindemann.science.STREAK_REMINDER"

    // Use a stable pattern and Locale.US to avoid locale-dependent formats
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Call when the user has completed at least one play today.
     * Returns the new streak length.
     */
    fun recordPlay(ctx: Context): Int {
        val p = prefs(ctx)
        val todayS = todayString()

        val lastS = p.getString(KEY_LAST_PLAY, null)
        var streak = p.getInt(KEY_STREAK, 0)
        var best = p.getInt(KEY_BEST, 0)

        if (lastS == todayS) {
            // already recorded today
            return streak
        }

        if (lastS != null) {
            try {
                val lastDate = formatter.parse(lastS)
                val lastCal = Calendar.getInstance().apply { time = lastDate!! }
                val todayCal = Calendar.getInstance()
                // advance last by 1 day and compare year/day-of-year
                lastCal.add(Calendar.DAY_OF_YEAR, 1)
                val consecutive = lastCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                        lastCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                if (consecutive) {
                    // consecutive day
                    streak = streak + 1
                } else {
                    // not consecutive -> reset
                    streak = 1
                }
            } catch (e: Exception) {
                // parse error -> reset
                streak = 1
            }
        } else {
            // first recorded day
            streak = 1
        }

        if (streak > best) best = streak

        p.edit()
            .putString(KEY_LAST_PLAY, todayS)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST, best)
            .apply()

        // If we reach or exceed 3 days, schedule the reminder; otherwise cancel
        if (streak >= 3) {
            scheduleReminder(ctx)
        } else {
            cancelReminder(ctx)
        }

        return streak
    }

    /**
     * Return the current local streak (days).
     * This method validates the streak freshness and returns 0 if the streak has expired.
     */
    fun getCurrentStreak(ctx: Context): Int {
        val p = prefs(ctx)
        val streak = p.getInt(KEY_STREAK, 0)
        
        // If no streak, return 0
        if (streak <= 0) return 0
        
        // Validate freshness
        val lastPlayS = p.getString(KEY_LAST_PLAY, null) ?: return 0
        
        try {
            val lastDate = formatter.parse(lastPlayS)
            val lastCal = Calendar.getInstance().apply { time = lastDate!! }
            val todayCal = Calendar.getInstance()
            
            val daysDiff = calculateDaysDifference(lastCal, todayCal)
            
            // If more than 1 day has passed, streak is broken
            if (daysDiff > 1) {
                // Reset the streak silently
                p.edit()
                    .putInt(KEY_STREAK, 0)
                    .remove(KEY_LAST_PLAY)
                    .apply()
                cancelReminder(ctx)
                return 0
            }
            
            return streak
        } catch (e: Exception) {
            // On error, assume streak is broken
            p.edit()
                .putInt(KEY_STREAK, 0)
                .remove(KEY_LAST_PLAY)
                .apply()
            cancelReminder(ctx)
            return 0
        }
    }

    /**
     * Return the best streak seen locally.
     */
    fun getBestStreak(ctx: Context): Int {
        return prefs(ctx).getInt(KEY_BEST, 0)
    }

    /**
     * Get the last play date as ISO string (yyyy-MM-dd), or null if not set.
     */
    fun getLastPlayDate(ctx: Context): String? {
        return prefs(ctx).getString(KEY_LAST_PLAY, null)
    }

    /**
     * Reset the streak locally (clears last play and current streak, leaves best as-is).
     */
    fun resetStreak(ctx: Context) {
        val p = prefs(ctx)
        p.edit().remove(KEY_STREAK).remove(KEY_LAST_PLAY).apply()
        cancelReminder(ctx)
    }

    /**
     * Allow external code (e.g. cloud merge) to set the current streak value.
     * This will:
     * - update KEY_STREAK to the provided value if it's greater than current local,
     *   or set it to the provided value (we treat this as authoritative when called).
     * - update BEST if needed.
     * - schedule/cancel the reminder the same way recordPlay does (reminder when streak >= 3).
     * - IMPORTANT: Validates that the streak is still valid based on last play date.
     *   If the last play date is more than 1 day ago, the streak is reset to 0.
     *
     * Note: callers should ensure they only call this when appropriate (e.g., when cloud value
     * is known to be authoritative). This method is defensive and will not crash on errors.
     */
    fun setCurrentStreak(ctx: Context, streakValue: Int) {
        try {
            val p = prefs(ctx)
            val current = p.getInt(KEY_STREAK, 0)
            val best = p.getInt(KEY_BEST, 0)
            var newBest = best
            var newStreak = streakValue.coerceAtLeast(0)

            // Validate streak freshness: if last play was more than 1 day ago, reset to 0
            if (newStreak > 0) {
                val lastPlayS = p.getString(KEY_LAST_PLAY, null)
                if (lastPlayS != null) {
                    try {
                        val lastDate = formatter.parse(lastPlayS)
                        val lastCal = Calendar.getInstance().apply { time = lastDate!! }
                        val todayCal = Calendar.getInstance()
                        
                        // Calculate days difference
                        val daysDiff = calculateDaysDifference(lastCal, todayCal)
                        
                        // If more than 1 day has passed since last play, streak is broken
                        if (daysDiff > 1) {
                            newStreak = 0
                        }
                    } catch (e: Exception) {
                        // If we can't parse the date, assume streak is broken to be safe
                        newStreak = 0
                    }
                } else {
                    // No last play date recorded, so we can't validate - assume broken
                    newStreak = 0
                }
            }

            if (newStreak > newBest) {
                newBest = newStreak
            }

            p.edit()
                .putInt(KEY_STREAK, newStreak)
                .putInt(KEY_BEST, newBest)
                .apply()

            // If streak >= 3 schedule reminder; otherwise cancel
            if (newStreak >= 3) {
                scheduleReminder(ctx)
            } else {
                cancelReminder(ctx)
            }
        } catch (t: Throwable) {
            // swallow errors to avoid crashing callers
            t.printStackTrace()
        }
    }

    /**
     * Allow external code (e.g. cloud merge) to set both streak value and last play date.
     * This is more appropriate for cloud sync as it preserves the last play date from cloud.
     */
    fun setCurrentStreakWithDate(ctx: Context, streakValue: Int, lastPlayDate: String?) {
        try {
            val p = prefs(ctx)
            val best = p.getInt(KEY_BEST, 0)
            var newBest = best
            var newStreak = streakValue.coerceAtLeast(0)

            // Validate streak freshness if we have a last play date
            if (newStreak > 0 && lastPlayDate != null) {
                try {
                    val lastDate = formatter.parse(lastPlayDate)
                    val lastCal = Calendar.getInstance().apply { time = lastDate!! }
                    val todayCal = Calendar.getInstance()
                    
                    val daysDiff = calculateDaysDifference(lastCal, todayCal)
                    
                    // If more than 1 day has passed since last play, streak is broken
                    if (daysDiff > 1) {
                        newStreak = 0
                    }
                } catch (e: Exception) {
                    // If we can't parse the date, assume streak is broken
                    newStreak = 0
                }
            } else if (newStreak > 0 && lastPlayDate == null) {
                // No date provided, can't validate - reset to be safe
                newStreak = 0
            }

            if (newStreak > newBest) {
                newBest = newStreak
            }

            val editor = p.edit()
                .putInt(KEY_STREAK, newStreak)
                .putInt(KEY_BEST, newBest)
            
            if (newStreak > 0 && lastPlayDate != null) {
                editor.putString(KEY_LAST_PLAY, lastPlayDate)
            } else {
                editor.remove(KEY_LAST_PLAY)
            }
            
            editor.apply()

            // If streak >= 3 schedule reminder; otherwise cancel
            if (newStreak >= 3) {
                scheduleReminder(ctx)
            } else {
                cancelReminder(ctx)
            }
        } catch (t: Throwable) {
            // swallow errors to avoid crashing callers
            t.printStackTrace()
        }
    }

    /**
     * Calculate the number of days between two Calendar instances.
     * Returns 0 if same day, 1 if next day, etc.
     */
    private fun calculateDaysDifference(from: Calendar, to: Calendar): Int {
        val fromYear = from.get(Calendar.YEAR)
        val fromDay = from.get(Calendar.DAY_OF_YEAR)
        val toYear = to.get(Calendar.YEAR)
        val toDay = to.get(Calendar.DAY_OF_YEAR)
        
        if (fromYear == toYear) {
            return toDay - fromDay
        } else {
            // Different years - calculate total days
            var days = from.getActualMaximum(Calendar.DAY_OF_YEAR) - fromDay
            for (year in (fromYear + 1) until toYear) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, year)
                days += cal.getActualMaximum(Calendar.DAY_OF_YEAR)
            }
            days += toDay
            return days
        }
    }

    private fun todayString(): String {
        val cal = Calendar.getInstance()
        return formatter.format(cal.time)
    }

    private fun reminderPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx.applicationContext, StreakReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            ctx.applicationContext,
            0,
            intent,
            flags
        )
    }

    /**
     * Schedule a reminder ~24h from now. If a reminder already scheduled it will be replaced.
     * Uses AlarmManagerCompat.setExactAndAllowWhileIdle where available.
     */
    fun scheduleReminder(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
            val pi = reminderPendingIntent(ctx)
            // Use compat helper for best behavior across API levels
            AlarmManagerCompat.setExactAndAllowWhileIdle(am, AlarmManager.RTC_WAKEUP, triggerAt, pi)
            prefs(ctx).edit().putBoolean(KEY_REMINDER_SCHEDULED, true).apply()
        } catch (t: Throwable) {
            // don't crash the app for scheduling failures
            t.printStackTrace()
        }
    }

    /**
     * Cancel any scheduled reminder.
     */
    fun cancelReminder(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = reminderPendingIntent(ctx)
            am.cancel(pi)
            prefs(ctx).edit().putBoolean(KEY_REMINDER_SCHEDULED, false).apply()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /**
     * Return whether a reminder has been scheduled (local flag).
     */
    fun isReminderScheduled(ctx: Context): Boolean {
        return prefs(ctx).getBoolean(KEY_REMINDER_SCHEDULED, false)
    }
}