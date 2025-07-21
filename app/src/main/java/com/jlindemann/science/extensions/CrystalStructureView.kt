package com.jlindemann.science.extensions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class CrystalStructureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var crystalSystem: String = "Cubic"
        set(value) {
            field = value
            resolveCrystalSystem()
            invalidate()
        }

    private var resolvedSystem: String = "Cubic"

    private fun resolveCrystalSystem() {
        val key = when {
            crystalSystem.contains("Hexagonal", ignoreCase = true) -> "Hexagonal"
            crystalSystem.equals("Body-centered cubic (bcc)", ignoreCase = true) -> "Body-centered cubic (bcc)"
            crystalSystem.equals("Face-centered cubic (fcc)", ignoreCase = true) -> "Face-centered cubic (fcc)"
            crystalSystem.equals("Rhombohedral", ignoreCase = true) -> "Rhombohedral"
            crystalSystem.equals("Trigonal", ignoreCase = true) -> "Trigonal"
            crystalSystem.equals("Tetragonal", ignoreCase = true) -> "Tetragonal"
            crystalSystem.equals("Orthorhombic", ignoreCase = true) -> "Orthorhombic"
            crystalSystem.equals("Monoclinic", ignoreCase = true) -> "Monoclinic"
            crystalSystem.equals("Triclinic", ignoreCase = true) -> "Triclinic"
            else -> "Cubic"
        }
        resolvedSystem = if (CrystalStructures.data.containsKey(key)) key else "Cubic"
    }

    private var yaw = 0.3f
    private var pitch = 0.3f
    private var roll = 0f

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val paint = Paint().apply {
        strokeWidth = 4f
        isAntiAlias = true
    }

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // Animator for slow rotation
    private var animator: ValueAnimator? = null

    private fun startRotation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10000L // 10 seconds per full cycle
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                val delta = 0.004f // radians per frame
                yaw += delta
                invalidate()
            }
            start()
        }
    }

    private fun stopRotation() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRotation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val structure = CrystalStructures.data[resolvedSystem] ?: return
        val w = width
        val h = height
        val cx = w / 2f
        val cy = h / 2f
        val scale = min(w, h) * 0.3f

        // Set paint color based on theme
        paint.color = if (isDarkTheme()) Color.WHITE else Color.BLACK

        val points2D = structure.vertices.map { v ->
            val r = CrystalMath.rotate(v[0], v[1], v[2], yaw, pitch, roll)
            PointF(cx + r[0] * scale, cy - r[1] * scale)
        }

        // Draw edges
        for (edge in structure.edges) {
            val p1 = points2D[edge[0]]
            val p2 = points2D[edge[1]]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                stopRotation() // Stop auto-rotation while dragging
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    yaw += dx * 0.01f
                    pitch += dy * 0.01f
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                startRotation() // Resume auto-rotation after drag
            }
        }
        return true
    }
}