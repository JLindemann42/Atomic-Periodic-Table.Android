package com.jlindemann.science.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jlindemann.science.R
import com.jlindemann.science.activities.tools.FlashCardActivity

object NotificationHelper {
    private const val CHANNEL_ID = "lives_refill_channel"
    private const val CHANNEL_NAME = "Lives"
    private const val NOTIF_ID = 0xA1

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notifications about lives refilling"
            nm.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendLivesRefilledNotification(context: Context) {
        try {
            ensureChannel(context)
            val intent = Intent(context, FlashCardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

            val smallIconRes = if (hasResource(context, "icon_notification")) {
                context.resources.getIdentifier("icon_notification", "drawable", context.packageName)
            } else {
                android.R.drawable.ic_dialog_info
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Your lives have been refilled — come back and play!")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun hasResource(context: Context, name: String): Boolean {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return id != 0
    }
}