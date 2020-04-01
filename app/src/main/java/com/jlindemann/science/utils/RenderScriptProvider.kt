package com.jlindemann.science.utils

import android.content.Context
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

class RenderScriptProvider(context: Context) {

    val renderScript: RenderScript by lazy(LazyThreadSafetyMode.NONE) {
        RenderScript.create(context)
    }

    val blurScript: ScriptIntrinsicBlur by lazy(LazyThreadSafetyMode.NONE) {
        ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
    }
}