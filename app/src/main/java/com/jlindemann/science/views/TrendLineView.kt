package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.ChartScale
import com.jlindemann.science.ai.cards.TrendSeries
import com.jlindemann.science.ai.data.UnitConverter
import kotlin.math.log10

/**
 * One field across a period, group or series, as a line in atomic-number order (roadmap 2.3).
 *
 * Like the other card views it decides nothing about the data: the points, their order and whether
 * the axis is logarithmic all arrive from `TrendSeriesReducer`. It draws the line, a symbol under
 * each point, and the values at the ends and the extremes.
 */
class TrendLineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var series: TrendSeries? = null
    private val palette = ChartPalette.from(context)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = palette.primary
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = palette.outline
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    fun setSeries(series: TrendSeries?) {
        this.series = series
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = series ?: return
        if (data.points.isEmpty() || width == 0 || height == 0) return

        val left = 24f
        val right = width - 24f
        val top = 32f
        val bottom = height - 36f
        val log = data.suggestedScale == ChartScale.LOG10

        val values = data.points.map { if (log) log10(it.value.coerceAtLeast(1e-30)) else it.value }
        val minValue = values.min()
        val maxValue = values.max()
        val span = (maxValue - minValue).takeIf { it > 0 } ?: 1.0

        fun x(index: Int): Float = if (data.points.size == 1) (left + right) / 2f
        else left + (right - left) * index / (data.points.size - 1)
        fun y(value: Double): Float = (bottom - (bottom - top) * ((value - minValue) / span)).toFloat()

        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        for (index in 0 until data.points.size - 1) {
            canvas.drawLine(x(index), y(values[index]), x(index + 1), y(values[index + 1]), linePaint)
        }
        for (index in data.points.indices) canvas.drawCircle(x(index), y(values[index]), 5f, dotPaint)

        // A symbol under every point while they fit; past that, every other one, so a long series
        // such as the lanthanides stays legible on a phone.
        val stride = if (data.points.size > MAX_LABELLED) 2 else 1
        for ((index, point) in data.points.withIndex()) {
            if (index % stride == 0 || index == data.points.size - 1) {
                canvas.drawText(point.symbol, x(index), bottom + 26f, symbolPaint)
            }
        }

        // The values worth reading off: the two ends, and the highest and lowest if they are elsewhere.
        val labelled = setOf(0, data.points.size - 1, values.indexOf(maxValue), values.indexOf(minValue))
        for (index in labelled) {
            val point = data.points.getOrNull(index) ?: continue
            canvas.drawText(UnitConverter.formatValue(point.value), x(index), y(values[index]) - 12f, valuePaint)
        }
    }

    private companion object {
        /** Beyond this many points a symbol under each one runs together. */
        const val MAX_LABELLED = 12
    }
}
