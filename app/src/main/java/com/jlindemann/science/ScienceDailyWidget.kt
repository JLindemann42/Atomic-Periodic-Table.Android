package com.jlindemann.science

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
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

    /** Re-lay the fact out when the user resizes the widget. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.science_daily_widget)

        // Get a random fact
        val language = ElementDataLoader.getAppLanguage(context)
        val fact = AIPersonality.getRandomFact(context, language)

        // The fact sits in a weighted row, so anything taller than the widget would be
        // clipped mid-word. Size the text to the space we actually have and trim the
        // fact to fit, so it always ends on a whole word with an ellipsis.
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            .takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
            .takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP

        val textSizeSp = factTextSizeSp(heightDp)
        views.setTextViewTextSize(R.id.widget_fact_text, TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        views.setTextViewText(R.id.widget_fact_text, fitToBox(fact, widthDp, heightDp, textSizeSp))

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

    private fun factTextSizeSp(heightDp: Int): Float {
        val available = factAreaHeightDp(heightDp)
        return when {
            available >= 96 -> 16f
            available >= 64 -> 15f
            available >= 40 -> 14f
            else -> 13f
        }
    }

    /** Height left for the fact once the title, the button and the padding are taken out. */
    private fun factAreaHeightDp(heightDp: Int): Int =
        (heightDp - CHROME_HEIGHT_DP).coerceAtLeast(MIN_FACT_AREA_DP)

    /**
     * Trims [fact] to roughly what fits in the fact area, cutting on a word boundary.
     * The estimate is deliberately conservative: a slightly short fact reads better than
     * one whose last line is sliced in half by the widget edge.
     */
    private fun fitToBox(fact: String, widthDp: Int, heightDp: Int, textSizeSp: Float): String {
        val lines = (factAreaHeightDp(heightDp) / (textSizeSp * LINE_HEIGHT_FACTOR)).toInt().coerceAtLeast(1)
        val usableWidthDp = (widthDp - HORIZONTAL_PADDING_DP).coerceAtLeast(MIN_USABLE_WIDTH_DP)
        val charsPerLine = (usableWidthDp / (textSizeSp * AVERAGE_CHAR_WIDTH_FACTOR)).toInt().coerceAtLeast(8)
        val capacity = lines * charsPerLine

        if (fact.length <= capacity) return fact

        val cut = fact.take(capacity - 1)
        val lastSpace = cut.lastIndexOf(' ')
        val trimmed = if (lastSpace > capacity / 2) cut.substring(0, lastSpace) else cut
        return trimmed.trimEnd(' ', ',', ';', ':', '-') + "…"
    }

    private companion object {
        /** Matches targetCellWidth/Height in science_daily_widget_info.xml. */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 150

        /** Title + button + vertical padding, as laid out in science_daily_widget.xml. */
        const val CHROME_HEIGHT_DP = 84
        const val MIN_FACT_AREA_DP = 20
        const val HORIZONTAL_PADDING_DP = 32
        const val MIN_USABLE_WIDTH_DP = 80

        /** sp -> dp line height, including the layout's 1.15 line spacing multiplier. */
        const val LINE_HEIGHT_FACTOR = 1.4f
        /** Average glyph advance as a fraction of the text size, for Latin text. */
        const val AVERAGE_CHAR_WIDTH_FACTOR = 0.52f
    }
}
