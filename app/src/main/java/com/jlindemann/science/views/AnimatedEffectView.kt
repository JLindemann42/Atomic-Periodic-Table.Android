package com.jlindemann.science.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.random.Random

class AnimatedEffectView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Shapes for animated filled circles
    data class Shape(
        var x: Float,
        var y: Float,
        val fillColor: Int,
        var dx: Float,
        var dy: Float,
        val startRadius: Float,
        val endRadius: Float,
        var currentRadius: Float = 0f
    )

    // Big outlined circles around screen edge
    data class CornerOutline(
        val cx: Float,
        val cy: Float,
        val color: Int,
        val maxRadius: Float,
        val minRadius: Float,
        val stroke: Float,
        var animatedRadius: Float = 0f
    )

    private val shapes = mutableListOf<Shape>()
    private val cornerOutlines = mutableListOf<CornerOutline>()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var fadeAlpha: Float = 0f

    /**
     * Starts the animation of shapes with the given palette.
     * All coloring is controlled by the colors parameter.
     */
    fun startAnimation(colors: List<Int>, shapeCount: Int = 12, onAnimationEnd: (() -> Unit)? = null) {
        shapes.clear()
        cornerOutlines.clear()

        val palette = if (colors.isNotEmpty()) colors else listOf(Color.GRAY)
        val outlinePalette = palette.shuffled()

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        // Animated shapes (center area, with centers possibly outside bounds)
        for (i in 0 until shapeCount) {
            val fillColor = palette[i % palette.size]
            val startRadius = Random.nextInt(30, 100).toFloat()
            val endRadius = startRadius + Random.nextInt(10, 100)
            // Allow centers to be a bit outside the screen
            val x = Random.nextFloat() * (w * 1.1f) - (w * 0.1f)
            val y = Random.nextFloat() * (h * 1.1f) - (h * 0.1f)
            val angle = Random.nextFloat() * 2 * Math.PI
            val speed = Random.nextInt(200, 1300) / 1000f
            val dx = (Math.cos(angle) * speed * w / 8).toFloat()
            val dy = (Math.sin(angle) * speed * h / 8).toFloat()
            shapes.add(
                Shape(
                    x, y, fillColor,
                    dx, dy,
                    startRadius, endRadius,
                    startRadius
                )
            )
        }

        /**
         * Helper for random edge center:
         * - Ensures that at least 50% of the circle appears in the screen.
         * - Places the center so that the circle is "hugging" the edge but at least half is visible.
         */
        fun randomEdgeCenter(w: Float, h: Float, margin: Float, radius: Float): Pair<Float, Float> {
            return when (Random.nextInt(4)) {
                // Top edge: y in [radius * 0.5, margin + radius], x in [0, w]
                0 -> Pair(
                    Random.nextFloat() * w,
                    Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f
                )
                // Bottom edge: y in [h - margin - radius, h - radius * 0.5], x in [0, w]
                1 -> Pair(
                    Random.nextFloat() * w,
                    h - (Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f)
                )
                // Left edge: x in [radius * 0.5, margin + radius], y in [0, h]
                2 -> Pair(
                    Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f,
                    Random.nextFloat() * h
                )
                // Right edge: x in [w - margin - radius, w - radius * 0.5], y in [0, h]
                else -> Pair(
                    w - (Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f),
                    Random.nextFloat() * h
                )
            }
        }

        // Corner Outlines (randomized edges & size)
        val margin = min(w, h) * 0.06f
        val maxR = min(w, h) * 0.5f
        val minR = min(w, h) * 0.15f
        val strokeWidth = min(w, h) * 0.08f

        for (i in 0 until 4) {
            val color = outlinePalette[i % outlinePalette.size]
            val thisMaxR = Random.nextFloat() * (maxR - minR) + minR
            val thisMinR = thisMaxR * (0.8f + Random.nextFloat() * 0.2f)
            val center = randomEdgeCenter(w, h, margin, thisMaxR)
            cornerOutlines.add(
                CornerOutline(
                    cx = center.first,
                    cy = center.second,
                    color = color,
                    maxRadius = thisMaxR,
                    minRadius = thisMinR,
                    stroke = strokeWidth,
                    animatedRadius = thisMinR
                )
            )
        }

        visibility = View.VISIBLE
        fadeAlpha = 0f

        val totalDuration = 2000L
        val fadeDuration = 250L
        val moveDuration = totalDuration - 2 * fadeDuration

        // Fade in animator
        val fadeInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeDuration
            addUpdateListener { va ->
                fadeAlpha = va.animatedValue as Float
                invalidate()
            }
        }

        // Move & outline grow animator
        var lastFraction = 0f
        val moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = moveDuration
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val delta = fraction - lastFraction
                lastFraction = fraction
                shapes.forEach {
                    it.x += it.dx * delta
                    it.y += it.dy * delta
                    it.currentRadius = it.startRadius + (it.endRadius - it.startRadius) * fraction
                }
                cornerOutlines.forEach {
                    it.animatedRadius = it.minRadius + (it.maxRadius - it.minRadius) * fraction
                }
                invalidate()
            }
        }

        // Fade out animator
        val fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = fadeDuration
            addUpdateListener { va ->
                fadeAlpha = va.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    onAnimationEnd?.invoke()
                }
            })
        }

        // Chain animators
        fadeInAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                moveAnimator.start()
            }
        })
        moveAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                fadeOutAnimator.start()
            }
        })

        fadeInAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fillPaint.alpha = (fadeAlpha * 255).toInt().coerceIn(0, 255)
        outlinePaint.alpha = fillPaint.alpha

        // Draw animated filled circles
        for (shape in shapes) {
            fillPaint.color = shape.fillColor
            canvas.drawCircle(shape.x, shape.y, shape.currentRadius, fillPaint)
        }

        // Draw thick outlined circles around the edge, with growing radius
        for (corner in cornerOutlines) {
            outlinePaint.color = corner.color
            outlinePaint.strokeWidth = corner.stroke
            canvas.drawCircle(corner.cx, corner.cy, corner.animatedRadius, outlinePaint)
        }
    }
}