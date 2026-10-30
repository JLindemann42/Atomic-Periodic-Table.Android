package com.jlindemann.science.extensions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.jlindemann.science.R
import kotlin.math.min

class CrystalStructureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var crystalSystem: String = "Cubic"
        set(value) {
            field = value
            resolveCrystalSystem()
            invalidate()
        }

    /**
     * Whether the model turns on its own.
     *
     * True keeps the element screen exactly as it was. False is for an embedded preview: inside a
     * RecyclerView an always-running animator invalidates every frame for every pooled row,
     * including rows scrolled off screen.
     */
    var autoRotate: Boolean = true
        set(value) {
            field = value
            if (value && isAttachedToWindow) startRotation() else stopRotation()
        }

    /**
     * Whether dragging rotates the model.
     *
     * False inside a scrolling container. [onTouchEvent] used to return true unconditionally, which
     * claims ACTION_DOWN and stops the parent ever seeing the gesture — a chat list would become
     * unscrollable anywhere this view sat under the user's thumb.
     */
    var interactive: Boolean = true

    init {
        attrs?.let {
            val styled = context.obtainStyledAttributes(it, R.styleable.CrystalStructureView, defStyleAttr, 0)
            autoRotate = styled.getBoolean(R.styleable.CrystalStructureView_autoRotate, true)
            interactive = styled.getBoolean(R.styleable.CrystalStructureView_interactive, true)
            styled.recycle()
        }
    }

    private var resolvedSystem: String = "Cubic"

    private fun resolveCrystalSystem() {
        // Delegated so the recognition rule has one home. The fallback stays here and only here:
        // on this screen an unrecognised lattice drawn as a cube is a guess beside a text label
        // that states the truth, whereas in a chat card the picture *is* the answer, so the card
        // layer uses the nullable result and shows nothing rather than fabricating a cell.
        resolvedSystem = com.jlindemann.science.ai.cards.CrystalSystemResolver.resolve(crystalSystem)
            ?: "Cubic"
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
        if (autoRotate) startRotation()
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
        // Decline the gesture outright when not interactive, so the parent can scroll. Returning
        // true for ACTION_DOWN is what made a chat row unscrollable over this view.
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Claim the gesture only once it is genuinely a drag on this view.
                parent?.requestDisallowInterceptTouchEvent(true)
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
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                if (autoRotate) startRotation()
            }
        }
        return true
    }
}