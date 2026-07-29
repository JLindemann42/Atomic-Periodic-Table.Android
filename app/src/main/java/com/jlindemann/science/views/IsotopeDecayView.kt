package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.DecadeLabels
import com.jlindemann.science.ai.cards.IsotopeSeries
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Half-life against mass number, coloured by decay mode.
 *
 * The valley of stability shows as a peak: the stable isotopes sit at the top and half-lives fall
 * away on both sides. `decay_type` is in the shipped JSON and read by nothing else in the app, so
 * this chart shows data the app has always carried and never displayed.
 *
 * The one thing that must not be got wrong: a stable isotope has **no** finite half-life. It is
 * drawn in a pinned band above the plot rather than at the top of the axis, because placing it on
 * the axis would assert a number the data does not contain.
 */
class IsotopeDecayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var series: IsotopeSeries? = null
    private val palette = ChartPalette.from(context)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.outline
        alpha = 90
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.tertiary
        alpha = 40
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
    }

    fun setSeries(series: IsotopeSeries?) {
        this.series = series
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = series ?: return
        if (data.points.isEmpty() || width == 0 || height == 0) return

        val left = 72f
        val right = width - 16f
        val top = 20f
        val bottom = height - 34f
        // The band for stable isotopes, above the plot area and visually separate from it.
        val bandBottom = top + 26f

        val masses = data.points.map { it.massNumber }
        val minMass = masses.min()
        val maxMass = masses.max()
        val massSpan = (maxMass - minMass).takeIf { it > 0 } ?: 1

        val lowLog = floor(data.minLogSeconds) - 0.5
        val highLog = ceil(data.maxLogSeconds) + 0.5
        val logSpan = (highLog - lowLog).takeIf { it > 0 } ?: 1.0

        fun x(mass: Int): Float = left + (right - left) * (mass - minMass) / massSpan
        fun y(log: Double): Float =
            (bottom - (bottom - bandBottom - 12f) * ((log - lowLog) / logSpan)).toFloat()

        canvas.drawRect(left, top, right, bandBottom, bandPaint)
        canvas.drawText(STABLE_BAND_LABEL, 4f, bandBottom - 6f, textPaint)

        // Decade gridlines, labelled in human time units rather than powers of ten.
        var decade = ceil(lowLog).toInt()
        while (decade <= highLog.toInt()) {
            val gridY = y(decade.toDouble())
            canvas.drawLine(left, gridY, right, gridY, gridPaint)
            canvas.drawText(DecadeLabels.timeLabel(decade), 2f, gridY - 3f, textPaint)
            decade += DECADE_STEP
        }

        for (point in data.points) {
            markerPaint.color = palette.forDecay(point.decayGroup)
            val markerX = x(point.massNumber)
            if (point.stable || point.logSeconds == null) {
                // A square in the stable band: a different shape as well as a different place, so
                // it does not read as a data point that happens to be very high.
                val half = 5f
                canvas.drawRect(
                    markerX - half, top + 6f, markerX + half, bandBottom - 6f, markerPaint
                )
            } else {
                canvas.drawCircle(markerX, y(point.logSeconds), 5f, markerPaint)
            }
        }

        canvas.drawText(minMass.toString(), left, height - 8f, textPaint)
        canvas.drawText(maxMass.toString(), right - 34f, height - 8f, textPaint)
    }

    private companion object {
        const val STABLE_BAND_LABEL = "stable"

        /** Every third decade; fifty gridlines in a 200 dp card is unreadable. */
        const val DECADE_STEP = 3
    }
}
