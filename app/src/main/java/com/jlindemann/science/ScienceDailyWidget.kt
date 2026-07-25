package com.jlindemann.science

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.ai.AIPersonality
import com.jlindemann.science.utils.ElementDataLoader

/**
 * Science Daily Fact Widget.
 * Displays a random science fact that refreshes daily.
 */
class ScienceDailyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.science_daily_widget)
        
        // Get a random fact
        val language = ElementDataLoader.getAppLanguage(context)
        val fact = AIPersonality.getRandomFact(context, language)
        views.setTextViewText(R.id.widget_fact_text, fact)

        // Intent to open AI Chat with the fact
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_AI_CHAT"
            putExtra("initial_query", fact)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}