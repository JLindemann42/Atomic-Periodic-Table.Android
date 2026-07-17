package com.jlindemann.science.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.jlindemann.science.R
import kotlin.math.cos
import kotlin.math.sin

class ElectronShellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var shells = mutableListOf<Int>()
    private var elementSymbol: String = ""

    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val electronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nucleusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var colorPrimary: Int = Color.BLUE
    private var colorOnSurface: Int = Color.BLACK
    private var colorOutline: Int = Color.GRAY

    init {
        val typedValue = TypedValue()
        val theme = context.theme

        if (theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)) {
            colorPrimary = typedValue.data
        }
        if (theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)) {
            colorOnSurface = typedValue.data
        }
        if (theme.resolveAttribute(R.attr.colorOutline, typedValue, true)) {
            colorOutline = typedValue.data
        }

        shellPaint.color = colorOutline
        shellPaint.alpha = 80
        electronPaint.color = colorPrimary
        nucleusPaint.color = colorPrimary
        textPaint.color = Color.WHITE
    }

    fun setShellData(shellString: String, symbol: String) {
        elementSymbol = symbol
        shells.clear()
        
        // Clean and parse "K2 L8 M18 N32 O18 P9 Q2 R0" or "K2 L8 18 ..."
        val cleaned = shellString.replace(Regex("[A-Z]"), " ").trim()
        val parts = cleaned.split(Regex("\\s+"))
        
        parts.forEach {
            val count = it.toIntOrNull()
            if (count != null && count > 0) {
                shells.add(count)
            }
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shells.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = (minOf(width, height) / 2f) * 0.85f
        val nucleusRadius = minOf(width, height) * 0.08f
        val shellCount = shells.size
        val shellStep = if (shellCount > 0) (maxRadius - nucleusRadius) / shellCount else 0f

        // Draw nucleus
        canvas.drawCircle(centerX, centerY, nucleusRadius, nucleusPaint)
        
        textPaint.textSize = nucleusRadius * 0.9f
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(elementSymbol, centerX, textY, textPaint)

        shells.forEachIndexed { index, electronCount ->
            val radius = nucleusRadius + (index + 1) * shellStep
            
            // Draw shell orbit
            canvas.drawCircle(centerX, centerY, radius, shellPaint)
            
            // Draw electrons
            val electronRadius = minOf(width, height) * 0.015f
            for (i in 0 until electronCount) {
                // Offset each shell's starting angle for better visual distribution
                val startAngle = (index * 0.5).toFloat()
                val angle = startAngle + (2 * Math.PI * i / electronCount).toFloat()
                val ex = centerX + radius * cos(angle.toDouble()).toFloat()
                val ey = centerY + radius * sin(angle.toDouble()).toFloat()
                
                canvas.drawCircle(ex, ey, electronRadius, electronPaint)
            }
        }
    }
}
