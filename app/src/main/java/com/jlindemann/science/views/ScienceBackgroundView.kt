package com.jlindemann.science.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class ScienceBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class IconInstance(
        val x: Float,
        val y: Float,
        val rotation: Float, // degrees rotation
        val type: IconType,
        val size: Float      // base size multiplier
    )
    private enum class IconType { PI, ATOM }

    private val icons = mutableListOf<IconInstance>()
    private val iconCount = 8 // Controller of amount of icon to be displayed

    //Base sizes for icons, later adapted to be different sizes random
    private val basePiSize = 90f      // Used for textSize
    private val baseAtomRadius = 72f  // Used for drawing atom

    private val piPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2299FF")
        alpha = 25
        style = Paint.Style.FILL
        textSize = basePiSize // This will be scaled
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val atomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55C27E")
        alpha = 25
        style = Paint.Style.STROKE
        strokeWidth = 5f // This will be scaled
    }

    private var lastWidth = 0
    private var lastHeight = 0

    // Helper to get the "bounding radius" of each icon, for overlap avoidance
    private fun getIconRadius(size: Float, type: IconType): Float {
        return when (type) {
            IconType.PI -> basePiSize * size * 0.7f // Pi is text, approx
            IconType.ATOM -> baseAtomRadius * size * 1.8f // Atom's largest orbit
        }
    }

    // Generate icon positions/rotations/sizes anywhere on the view, avoiding overlap
    private fun generateIcons(w: Int, h: Int) {
        icons.clear()
        val maxAttempts = 180 //Attempts used to avoid placing icons on top of each-other
        val minSize = 0.68f
        val maxSize = 1.8f

        val placedIcons = mutableListOf<IconInstance>()

        var tries = 0
        var placed = 0

        while (placed < iconCount && tries < iconCount * maxAttempts) {
            tries++
            val type = if (placed % 2 == 0) IconType.PI else IconType.ATOM
            val size = Random.nextFloat() * (maxSize - minSize) + minSize
            val radius = getIconRadius(size, type)

            // Try to keep icons inside the screen, with margin for their radius
            val x = Random.nextFloat() * (w - 2 * radius) + radius
            val y = Random.nextFloat() * (h - 2 * radius) + radius
            val rotation = Random.nextFloat() * 60f - 30f // -30 to +30 degrees

            // Check for overlap
            var overlaps = false
            for (other in placedIcons) {
                val dist = hypot(x - other.x, y - other.y)
                val minDist = radius + getIconRadius(other.size, other.type)
                if (dist < minDist) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) {
                placedIcons.add(IconInstance(x, y, rotation, type, size))
                placed++
            }
            // If too many attempts, break to avoid infinite loop
            if (tries > iconCount * maxAttempts) break
        }
        icons.clear()
        icons.addAll(placedIcons)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastWidth = w
        lastHeight = h
        generateIcons(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (icons.isEmpty()) generateIcons(width, height)
        for (icon in icons) {
            canvas.save()
            canvas.translate(icon.x, icon.y)
            canvas.rotate(icon.rotation)
            canvas.scale(icon.size, icon.size)
            when (icon.type) {
                IconType.PI -> drawPiSymbol(canvas)
                IconType.ATOM -> drawAtom(canvas)
            }
            canvas.restore()
        }
        // In future, if I decide to add extra backgrounds elements, do it here
    }

    private fun drawPiSymbol(canvas: Canvas) {
        // Centered at (0,0), draw pi symbol
        canvas.drawText("π", -basePiSize/2, basePiSize/2, piPaint)
    }

    private fun drawAtom(canvas: Canvas) {
        // Centered at (0,0), simple atom illustration
        val r = baseAtomRadius
        canvas.drawCircle(0f, 0f, r, atomPaint) // nucleus
        // 3 orbits, slightly rotated
        for (i in 0..2) {
            val angle = i * 45f
            canvas.save()
            canvas.rotate(angle)
            canvas.drawOval(RectF(-r*1.7f, -r*0.7f, r*1.7f, r*0.7f), atomPaint)
            canvas.restore()
        }
        // Draw nucleus dot
        val nucleusPaint = Paint(atomPaint).apply {
            style = Paint.Style.FILL
            alpha = 35 //alpha bit higher than base stroke to make dot have more "weight"
        }
        canvas.drawCircle(0f, 0f, r/4, nucleusPaint)
        for (i in 0..2) {
            val theta = Math.toRadians((i * 120).toDouble())
            val ex = (r * 1.7 * cos(theta)).toFloat()
            val ey = (r * 0.7 * sin(theta)).toFloat()
            canvas.drawCircle(ex, ey, 6f, nucleusPaint)
        }
    }
}