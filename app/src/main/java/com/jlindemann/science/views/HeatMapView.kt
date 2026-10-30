package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jlindemann.science.ai.cards.HeatMap
import com.jlindemann.science.ai.data.UnitConverter

/**
 * One field over the whole table, as a mini periodic table shaded by value (roadmap 2.3).
 *
 * Every placement and every shade arrives from `HeatMapReducer`: where an element sits, and its value
 * as a share of the field's span on whichever scale the reducer chose. An element with no value is
 * an outline, so a gap in the data never reads as the lowest value.
 */
class HeatMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var map: HeatMap? = null
    private val palette = ChartPalette.from(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.outline
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
    }

    fun setMap(map: HeatMap?) {
        this.map = map
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = map ?: return
        if (width == 0 || height == 0) return

        // Eighteen columns; seven periods, a half-row gap, and the two f-block rows.
        val legendHeight = 34f
        val cell = minOf(width / COLUMNS.toFloat(), (height - legendHeight) / (ROWS + GAP))
        val inset = cell * 0.08f
        val originX = (width - cell * COLUMNS) / 2f

        fun top(row: Int): Float = (row - 1 + if (row > 7) GAP else 0f) * cell

        for (c in data.cells) {
            val x = originX + (c.column - 1) * cell
            val y = top(c.row)
            val fraction = c.fraction
            if (fraction == null) {
                canvas.drawRect(x + inset, y + inset, x + cell - inset, y + cell - inset, emptyPaint)
            } else {
                fillPaint.color = blend(fraction)
                canvas.drawRect(x + inset, y + inset, x + cell - inset, y + cell - inset, fillPaint)
            }
        }

        // The legend: the lowest shade, the highest, and the values they stand for.
        val legendY = height - 10f
        val swatch = cell * 0.9f
        fillPaint.color = blend(0.0)
        canvas.drawRect(originX, legendY - swatch, originX + swatch, legendY, fillPaint)
        val low = UnitConverter.formatValue(data.min)
        canvas.drawText(low, originX + swatch + 6f, legendY - 4f, legendPaint)
        val high = UnitConverter.formatValue(data.max)
        val highWidth = legendPaint.measureText(high)
        val highX = originX + cell * COLUMNS - highWidth
        fillPaint.color = blend(1.0)
        canvas.drawRect(highX - swatch - 6f, legendY - swatch, highX - 6f, legendY, fillPaint)
        canvas.drawText(high, highX, legendY - 4f, legendPaint)
    }

    /** A shade from the faint end of the primary colour to the full one. */
    private fun blend(fraction: Double): Int {
        val alpha = (MIN_ALPHA + (255 - MIN_ALPHA) * fraction).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
    }

    private companion object {
        const val COLUMNS = 18
        const val ROWS = 9
        const val GAP = 0.5f
        const val MIN_ALPHA = 40
    }
}
