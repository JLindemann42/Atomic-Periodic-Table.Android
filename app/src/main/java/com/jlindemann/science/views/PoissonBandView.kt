package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.PoissonBand
import com.jlindemann.science.ai.cards.PoissonBandReducer

/**
 * An element's Poisson ratio against the materials the app already tabulates.
 *
 * "0.29" on its own tells almost nobody anything. Placed against rock and soil, with the
 * incompressible limit at 0.5 marked, it becomes a statement about how the material behaves — which
 * is the entire reason this card is a band and not a number.
 */
class PoissonBandView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var band: PoissonBand? = null
    private val palette = ChartPalette.from(context)

    private val spanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.outline
        alpha = 70
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = palette.error
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.outline
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
    }

    fun setBand(band: PoissonBand?) {
        this.band = band
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = band ?: return
        if (width == 0 || height == 0) return

        val left = 60f
        val right = width - 16f
        val span = (data.axisMax - data.axisMin).takeIf { it > 0 } ?: 1.0
        fun x(value: Double): Float = (left + (right - left) * ((value - data.axisMin) / span)).toFloat()

        val rowHeight = 26f
        var y = 22f

        // Reference materials behind, so the element reads as sitting among them.
        for (reference in data.references) {
            canvas.drawText(reference.label.take(9), 2f, y + 15f, textPaint)
            canvas.drawRect(x(reference.low), y, x(reference.high), y + rowHeight * 0.6f, spanPaint)
            y += rowHeight
        }

        val axisY = y + 8f
        canvas.drawLine(left, axisY, right, axisY, axisPaint)

        // The thermodynamic ceiling for an isotropic material — the annotation is the payoff.
        val limitX = x(PoissonBandReducer.INCOMPRESSIBLE_LIMIT)
        canvas.drawLine(limitX, 16f, limitX, axisY, limitPaint)
        canvas.drawText("0.5", limitX - 16f, height - 6f, textPaint)

        // The element in front of everything.
        val markerY = axisY - rowHeight * 0.3f
        if (data.elementHigh > data.elementLow) {
            canvas.drawRect(
                x(data.elementLow), markerY - 7f, x(data.elementHigh), markerY + 7f, markerPaint
            )
        } else {
            canvas.drawCircle(x(data.elementLow), markerY, 7f, markerPaint)
        }
        canvas.drawText(
            "${data.symbol} ${data.elementDisplay}", x(data.elementLow) - 8f, markerY - 12f, textPaint
        )
    }
}
