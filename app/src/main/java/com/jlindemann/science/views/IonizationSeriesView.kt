package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.ChartScale
import com.jlindemann.science.ai.cards.IonizationSeries
import kotlin.math.log10

/**
 * Successive ionization energies, with the shell closures marked.
 *
 * Contains no branch that depends on what the data *means*: whether a step is a shell closure and
 * whether the axis should be logarithmic are both decided by `IonizationSeriesReducer` and arrive
 * as fields. That rule is what makes it acceptable that this drawing has no test — if a condition
 * like `if (step.isClosure)` ever needs computing here, it belongs in the reducer instead.
 */
class IonizationSeriesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var series: IonizationSeries? = null
    private val palette = ChartPalette.from(context)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = palette.primary
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val closurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = palette.error
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 26f
    }

    fun setSeries(series: IonizationSeries?) {
        this.series = series
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = series ?: return
        if (data.steps.isEmpty() || width == 0 || height == 0) return

        val left = 40f
        val right = width - 20f
        val top = 20f
        val bottom = height - 40f

        val values = data.steps.map {
            if (data.suggestedScale == ChartScale.LOG10) log10(it.electronVolts) else it.electronVolts
        }
        val minValue = values.min()
        val maxValue = values.max()
        val span = (maxValue - minValue).takeIf { it > 0 } ?: 1.0

        fun x(index: Int): Float =
            left + (right - left) * index / (data.steps.size - 1).coerceAtLeast(1)
        fun y(value: Double): Float =
            (bottom - (bottom - top) * ((value - minValue) / span)).toFloat()

        // Dashed rules where a closed shell is being broken into. Drawn first so the series sits
        // over them.
        for ((index, step) in data.steps.withIndex()) {
            if (step.order in data.shellClosureAfter && index < data.steps.size - 1) {
                val mid = (x(index) + x(index + 1)) / 2f
                canvas.drawLine(mid, top, mid, bottom, closurePaint)
            }
        }

        for (index in 0 until data.steps.size - 1) {
            canvas.drawLine(x(index), y(values[index]), x(index + 1), y(values[index + 1]), linePaint)
        }
        for (index in data.steps.indices) {
            canvas.drawCircle(x(index), y(values[index]), 5f, dotPaint)
        }

        // Only the first, last and closure steps are labelled; thirty labels at this size is noise.
        val labelled = buildSet {
            add(0)
            add(data.steps.size - 1)
            data.steps.forEachIndexed { i, s -> if (s.order in data.shellClosureAfter) add(i) }
        }
        for (index in labelled) {
            val step = data.steps.getOrNull(index) ?: continue
            canvas.drawText(step.display, x(index) - 12f, y(values[index]) - 12f, textPaint)
        }
    }
}
