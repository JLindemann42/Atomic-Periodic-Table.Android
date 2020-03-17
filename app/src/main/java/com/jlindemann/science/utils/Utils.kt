package com.jlindemann.science.utils

import android.R
import android.content.Context
import android.os.Handler
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import okhttp3.*
import java.io.IOException
import java.io.InputStream


object Utils {


    fun gestureSetup(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }


    fun fadeInAnim(view: View, time: Long) {

        view.visibility = View.VISIBLE
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

    fun jsonTransition(view: View, time: Long) {
        //Fade out
        view.animate().setDuration(time)
        view.animate().alpha(0.0f)
        //Fade In
        val handler = Handler()
        handler.postDelayed({
            view.animate().setDuration(time)
            view.animate().alpha(1.0f)
        }, time+1)
    }

}