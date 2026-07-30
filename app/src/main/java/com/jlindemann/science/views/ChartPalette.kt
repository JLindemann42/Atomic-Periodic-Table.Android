package com.jlindemann.science.views

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.jlindemann.science.R

/**
 * Theme colours a chart needs, resolved once.
 *
 * `ElectronShellView` reads `typedValue.data` directly, which only works because this app happens to
 * define its theme attributes as literal hex. That is fragile rather than wrong: the moment an
 * attribute points at an `@color` resource instead, the literal read yields a resource id used as a
 * colour. [color] handles both forms.
 */
data class ChartPalette(
    val primary: Int,
    val onSurface: Int,
    val outline: Int,
    val tertiary: Int,
    val error: Int,
    val secondary: Int
) {
    companion object {
        fun from(context: Context): ChartPalette = ChartPalette(
            primary = color(context, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#3F51B5")),
            onSurface = color(context, R.attr.colorOnSurface, Color.DKGRAY),
            outline = color(context, R.attr.colorOutline, Color.GRAY),
            tertiary = color(context, R.attr.colorTertiary, Color.parseColor("#00897B")),
            // No colorError attribute is declared by this app, so the accent is a literal rather
            // than an invented theme attribute nobody defines.
            error = Color.parseColor("#B3261E"),
            secondary = color(context, R.attr.colorSecondary, Color.parseColor("#7E57C2"))
        )

        /** Resolves an attribute whether it holds a literal colour or points at a colour resource. */
        private fun color(context: Context, attr: Int, fallback: Int): Int {
            val value = TypedValue()
            if (!context.theme.resolveAttribute(attr, value, true)) return fallback
            return if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                value.data
            } else {
                runCatching { ContextCompat.getColor(context, value.resourceId) }.getOrDefault(fallback)
            }
        }
    }

    /** The colour for a decay group, so a legend and its markers cannot disagree. */
    fun forDecay(group: com.jlindemann.science.ai.cards.DecayGroup): Int = when (group) {
        com.jlindemann.science.ai.cards.DecayGroup.STABLE -> tertiary
        com.jlindemann.science.ai.cards.DecayGroup.ALPHA -> error
        com.jlindemann.science.ai.cards.DecayGroup.BETA_MINUS -> primary
        com.jlindemann.science.ai.cards.DecayGroup.BETA_PLUS_EC -> secondary
        com.jlindemann.science.ai.cards.DecayGroup.ISOMERIC -> outline
        com.jlindemann.science.ai.cards.DecayGroup.FISSION -> Color.parseColor("#EF6C00")
        else -> outline
    }
}
