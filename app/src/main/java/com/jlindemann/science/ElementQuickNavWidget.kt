package com.jlindemann.science

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.jlindemann.science.activities.MainActivity

/**
 * Element Quick Nav Widget.
 * Grid of tiles for quick navigation.
 */
class ElementQuickNavWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.element_quick_nav_widget)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Table
            val tableIntent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_TABLE"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(R.id.nav_table, PendingIntent.getActivity(context, 101, tableIntent, flags))

            // Quiz
            val quizIntent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_QUIZ"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(R.id.nav_quiz, PendingIntent.getActivity(context, 102, quizIntent, flags))

            // AI
            val aiIntent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_AI_CHAT"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(R.id.nav_ai, PendingIntent.getActivity(context, 103, aiIntent, flags))

            // Molar Mass
            val molarIntent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_MOLAR_MASS"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(R.id.nav_molar, PendingIntent.getActivity(context, 104, molarIntent, flags))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}