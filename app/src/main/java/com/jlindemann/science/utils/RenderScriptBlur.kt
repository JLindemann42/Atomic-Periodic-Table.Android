package com.jlindemann.science.utils

import android.graphics.Bitmap
import android.renderscript.Allocation

class RenderScriptBlur(renderScriptProvider: RenderScriptProvider) {

    private val renderScript = renderScriptProvider.renderScript
    private val blurScript = renderScriptProvider.blurScript
    private var outAllocation: Allocation? = null

    private var lastBitmapWidth = -1
    private var lastBitmapHeight = -1

    private fun canReuseAllocation(bitmap: Bitmap): Boolean {
        return bitmap.height == lastBitmapHeight && bitmap.width == lastBitmapWidth
    }

    fun blur(bitmap: Bitmap, blurRadius: Float): Bitmap {
        val inAllocation = Allocation.createFromBitmap(renderScript, bitmap)

        if (!canReuseAllocation(bitmap)) {
            outAllocation?.destroy()
            outAllocation = Allocation.createTyped(renderScript, inAllocation.type)
            lastBitmapWidth = bitmap.width
            lastBitmapHeight = bitmap.height
        }

        blurScript.setRadius(blurRadius)
        blurScript.setInput(inAllocation)
        blurScript.forEach(outAllocation)
        outAllocation!!.copyTo(bitmap)

        inAllocation.destroy()
        return bitmap
    }
}