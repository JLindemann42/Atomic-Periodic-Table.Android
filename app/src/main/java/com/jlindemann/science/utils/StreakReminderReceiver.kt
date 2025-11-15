package com.jlindemann.science.activities.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jlindemann.science.R
import com.jlindemann.science.utils.StreakManager
import java.util.*

class StreakReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "streak_reminder_channel"
        private const val NOTIF_ID = 9101
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // Show notification reminding user to "save their streak"
        val streak = StreakManager.getCurrentStreak(context)

        // If the streak dropped under 3, cancel reminders
        if (streak < 3) {
            StreakManager.cancelReminder(context)
            return
        }

        createChannelIfNeeded(context)

        val title = context.getString(R.string.streak_notification_title, streak)
        val body = context.getString(R.string.streak_notification_body, streak)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification) // replace with a small app icon if available
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIF_ID + (Date().time % 1000).toInt(), builder.build())
        }

        // Reschedule next day's reminder (so reminders repeat daily until streak broken)
        StreakManager.scheduleReminder(context)
    }

    private fun createChannelIfNeeded(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Streak reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders to play and save your streak"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}