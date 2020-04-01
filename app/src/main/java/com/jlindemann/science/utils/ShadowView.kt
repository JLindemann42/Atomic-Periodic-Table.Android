package com.jlindemann.science.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import com.jlindemann.science.R
import kotlin.math.ceil
import kotlin.math.min

// Usually it has to be 16, but on Samsung devices it's 64 for some reason.
// If the bitmap size isn't divisible by this value, Renderscript will allocate an extra bitmap
private const val ROUNDING_VALUE = 64
private const val SCALE_FACTOR = 16f

class ShadowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var roundingWidthScaleFactor = 1f
    private var roundingHeightScaleFactor = 1f

    private lateinit var bitmap: Bitmap
    private val canvas = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val shadowDx: Float
    private val shadowDy: Float
    private val blurRadius: Float
    private val shadowAlpha: Int

    lateinit var blurScript: RenderScriptBlur

    init {
        clipChildren = false
        clipToPadding = false

        setWillNotDraw(false)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ShadowView,
            0, 0
        ).apply {
            shadowDx = getDimensionPixelSize(R.styleable.ShadowView_shadowDx, 0).toFloat()
            shadowDy = getDimensionPixelSize(R.styleable.ShadowView_shadowDy, 0).toFloat()
            blurRadius = min(getDimensionPixelSize(R.styleable.ShadowView_shadowRadius, 1).toFloat(), 25f)
            shadowAlpha = getInt(R.styleable.ShadowView_shadowAlpha, 255)
            recycle()
        }
        paint.alpha = shadowAlpha
    }

    private fun downScaleSize(value: Float): Int {
        return ceil((value / SCALE_FACTOR).toDouble()).toInt()
    }

    /**
     * Rounds a value to the nearest divisible by [ROUNDING_VALUE] to meet stride requirement
     */
    private fun roundSize(value: Int): Int {
        return if (value % ROUNDING_VALUE == 0) {
            value
        } else value - value % ROUNDING_VALUE + ROUNDING_VALUE
    }

    private fun setUpCanvasMatrix() {
        val scaleFactorX = SCALE_FACTOR * roundingWidthScaleFactor
        val scaleFactorY = SCALE_FACTOR * roundingHeightScaleFactor
        //shift picture by BLUR_RADIUS to fit blurred content
        canvas.translate(blurRadius, blurRadius)
        canvas.scale(1f / scaleFactorX, 1f / scaleFactorY)
    }

    private fun allocateBitmap(measuredWidth: Int, measuredHeight: Int): Bitmap {
        val nonRoundedScaledWidth = downScaleSize(measuredWidth.toFloat())
        val nonRoundedScaledHeight = downScaleSize(measuredHeight.toFloat())

        val scaledWidth = roundSize(nonRoundedScaledWidth)
        val scaledHeight = roundSize(nonRoundedScaledHeight)

        roundingHeightScaleFactor = nonRoundedScaledHeight.toFloat() / scaledHeight
        roundingWidthScaleFactor = nonRoundedScaledWidth.toFloat() / scaledWidth

        //Add space to fit the blurred part
        return Bitmap.createBitmap(
            scaledWidth + blurRadius.toInt() * 2,
            scaledHeight + blurRadius.toInt() * 2,
            Bitmap.Config.ARGB_8888
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap = allocateBitmap(w, h)
        canvas.setBitmap(bitmap)
    }

    override fun onDraw(canvas: Canvas) {
        //On first onDescendantInvalidated bitmap can be empty for some reason, so we need to draw it
        //here to be sure we have something to blur. Not sure how to avoid it yet
        blurChild(getChildAt(0))
        drawShadow(canvas)
        super.onDraw(canvas)
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        blurChild(child)
        super.onDescendantInvalidated(child, target)
    }

    //This is called prior to API 26 when child is invalidated
    @Suppress("OverridingDeprecatedMember")
    override fun invalidateChildInParent(location: IntArray?, dirty: Rect?): ViewParent? {
        blurChild(getChildAt(0))
        @Suppress("DEPRECATION")
        return super.invalidateChildInParent(location, dirty)
    }

    private fun blurChild(child: View) {
        canvas.save()
        bitmap.eraseColor(Color.TRANSPARENT)
        setUpCanvasMatrix()
        child.draw(canvas)
        canvas.restore()
        bitmap = blurScript.blur(bitmap, blurRadius)
    }

    private fun drawShadow(canvas: Canvas) {
        canvas.save()
        canvas.translate(shadowDx, shadowDy)
        //scale back to normal size
        canvas.scale(
            SCALE_FACTOR * roundingWidthScaleFactor,
            SCALE_FACTOR * roundingHeightScaleFactor
        )
        //translate back to the beginning of bitmap
        canvas.translate(-blurRadius, -blurRadius)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
    }
}