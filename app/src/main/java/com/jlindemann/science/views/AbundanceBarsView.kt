package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.AbundanceProfile
import kotlin.math.ceil

/**
 * An element's abundance across the reservoirs the app records, on a log axis.
 *
 * Horizontal bars because the reservoir names are long — "abundance in the solar system" will not
 * fit as a rotated tick label in a 280 dp card. Logarithmic because the values span roughly ten
 * orders of magnitude.
 *
 * Two things a naive version gets wrong, both handled in the reducer and honoured here: a log bar
 * has **no zero**, so bars start at a drawn, labelled floor rather than at the left edge; and a
 * "trace" reading is real information, so it is drawn as a hatched stub instead of vanishing.
 */
class AbundanceBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var profile: AbundanceProfile? = null
    private val palette = ChartPalette.from(context)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.primary
        alpha = 90
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.outline
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 20f
        alpha = 200
    }

    fun setProfile(profile: AbundanceProfile?) {
        this.profile = profile
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = profile ?: return
        if (data.bars.isEmpty() || width == 0 || height == 0) return

        val left = 8f
        val right = width - 8f
        val barLeft = left + 150f
        val rowHeight = (height - 16f) / data.bars.size
        val barHeight = (rowHeight * 0.55f).coerceAtMost(18f)

        val axisMax = ceil(data.maxLog) + 0.5
        val axisSpan = (axisMax - data.floorLog).takeIf { it > 0 } ?: 1.0
        fun x(log: Double): Float =
            (barLeft + (right - barLeft - 60f) * ((log - data.floorLog) / axisSpan)).toFloat()

        // The floor is drawn and labelled: without it the bar lengths carry no meaning at all.
        canvas.drawLine(barLeft, 8f, barLeft, height - 8f, floorPaint)

        for ((index, bar) in data.bars.withIndex()) {
            val centreY = 8f + rowHeight * index + rowHeight / 2f
            canvas.drawText(bar.label.take(18), left, centreY + 7f, labelPaint)

            val log = bar.logMgPerKg
            if (log == null) {
                // Present but unquantified. A short outlined stub, distinct from a real bar.
                canvas.drawRect(
                    barLeft, centreY - barHeight / 2f, barLeft + 14f, centreY + barHeight / 2f, tracePaint
                )
            } else {
                canvas.drawRect(
                    barLeft, centreY - barHeight / 2f, x(log), centreY + barHeight / 2f, barPaint
                )
                canvas.drawText(bar.display.take(12), x(log) + 6f, centreY + 6f, valuePaint)
            }
        }
    }
}
