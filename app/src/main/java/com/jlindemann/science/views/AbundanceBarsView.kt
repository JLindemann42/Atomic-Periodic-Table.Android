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
 * Three things a naive version gets wrong, all handled in the reducer and honoured here: a log bar
 * has **no zero**, so bars start at a drawn, labelled floor rather than at the left edge; a
 * "trace" reading is real information, so it is drawn as a hatched stub instead of vanishing; and
 * the reservoirs are **not all measured in the same unit**, so they are drawn in unit groups, each
 * headed by its unit, rather than as one run of bars that invites a comparison the data does not
 * support.
 */
class AbundanceBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var profile: AbundanceProfile? = null
    private val palette = ChartPalette.from(context)
    private val density = resources.displayMetrics.density

    /** One reservoir row. Matches the row height the fixed-height version used to produce. */
    private val rowHeight = 24f * density

    /** A group's unit heading. Shorter than a row: it carries text but no bar. */
    private val headerHeight = 16f * density

    private val verticalPadding = 8f

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
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.outline
        alpha = 110
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 22f
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 19f
        alpha = 170
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textSize = 20f
        alpha = 200
    }

    fun setProfile(profile: AbundanceProfile?) {
        this.profile = profile
        requestLayout()
        invalidate()
    }

    /**
     * Height is driven by the content, because the content's size is not fixed: between three and
     * nine reservoirs, in one to four unit groups.
     *
     * The card layout notes that these views measure to zero under `wrap_content` because none of
     * them override `onMeasure`. This one does, so `card_ai_abundance.xml` can stop hard-coding a
     * height that a nine-bar, four-group profile would overflow.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val data = profile
        val desired = if (data == null) 0f
        else data.bars.size * rowHeight + data.groups.size * headerHeight + verticalPadding * 2
        // resolveSize honours the parent's constraint but not android:minHeight, and the profile
        // is null until the row binds — without the floor the card measures to nothing and the
        // PRO+ scrim has an empty frame to cover.
        val height = ceil(desired).toInt().coerceAtLeast(suggestedMinimumHeight)
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = profile ?: return
        if (data.bars.isEmpty() || width == 0 || height == 0) return

        val left = 8f
        val right = width - 8f
        val barLeft = left + 150f
        val barHeight = (rowHeight * 0.55f).coerceAtMost(18f)

        val axisMax = ceil(data.maxLog) + 0.5
        val axisSpan = (axisMax - data.floorLog).takeIf { it > 0 } ?: 1.0
        fun x(log: Double): Float =
            (barLeft + (right - barLeft - 60f) * ((log - data.floorLog) / axisSpan)).toFloat()

        var y = verticalPadding
        for ((groupIndex, group) in data.groups.withIndex()) {
            if (groupIndex > 0) canvas.drawLine(left, y, right, y, separatorPaint)

            // The unit heads its own bars rather than being repeated on each one: it is the same
            // for every row below it, and a 280 dp card has no width to spare.
            canvas.drawText(group.unitLabel, barLeft, y + headerHeight - 4f, unitPaint)
            y += headerHeight

            // The floor is drawn and labelled: without it the bar lengths carry no meaning at all.
            val groupTop = y
            val groupBottom = y + group.bars.size * rowHeight
            canvas.drawLine(barLeft, groupTop, barLeft, groupBottom, floorPaint)

            for (bar in group.bars) {
                val centreY = y + rowHeight / 2f
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
                y += rowHeight
            }
        }
    }
}
