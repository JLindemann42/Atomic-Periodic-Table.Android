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

    fun startAnimation(colors: List<Int>, shapeCount: Int = 12, onAnimationEnd: (() -> Unit)? = null) {
        shapes.clear()
        cornerOutlines.clear()

        val palette = if (colors.isNotEmpty()) colors else listOf(Color.GRAY)
        val outlinePalette = palette.shuffled()

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        for (i in 0 until shapeCount) {
            val fillColor = palette[i % palette.size]
            val startRadius = Random.nextInt(30, 100).toFloat()
            val endRadius = startRadius + Random.nextInt(10, 100)
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

        fun randomEdgeCenter(w: Float, h: Float, margin: Float, radius: Float): Pair<Float, Float> {
            return when (Random.nextInt(4)) {
                0 -> Pair(
                    Random.nextFloat() * w,
                    Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f
                )
                1 -> Pair(
                    Random.nextFloat() * w,
                    h - (Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f)
                )
                2 -> Pair(
                    Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f,
                    Random.nextFloat() * h
                )
                else -> Pair(
                    w - (Random.nextFloat() * (margin + radius - radius * 0.5f) + radius * 0.5f),
                    Random.nextFloat() * h
                )
            }
        }

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
        val fadeDuration = 300L
        val moveDuration = totalDuration - 2 * fadeDuration

        val fadeInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeDuration
            addUpdateListener { va ->
                fadeAlpha = va.animatedValue as Float
                invalidate()
            }
        }
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
        val alpha = (fadeAlpha * 255).toInt().coerceIn(0, 255)
        fillPaint.alpha = alpha
        outlinePaint.alpha = alpha

        for (shape in shapes) {
            fillPaint.color = shape.fillColor
            canvas.drawCircle(shape.x, shape.y, shape.currentRadius, fillPaint)
        }
        for (corner in cornerOutlines) {
            outlinePaint.color = corner.color
            outlinePaint.strokeWidth = corner.stroke
            canvas.drawCircle(corner.cx, corner.cy, corner.animatedRadius, outlinePaint)
        }
    }
}