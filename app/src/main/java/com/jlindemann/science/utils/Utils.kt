package com.jlindemann.science.utils

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.preferences.ElementSendAndLoad

object Utils {

    fun gestureSetup(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }


    fun fadeInAnim(view: View, time: Long) {

        view.alpha = 0.0f
        view.animate().setDuration(time)
        view.animate().alpha(1.0f)

    }

    fun fadeOutAnim(view: View, time: Long) {
        view.animate().setDuration(time)
        view.animate().alpha(0.0f)
        val handler = Handler()
        handler.postDelayed({
            view.visibility = View.GONE
        }, time+1)

    }


}