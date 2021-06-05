package com.jlindemann.science.animations

import android.os.Handler
import android.view.View

object Anim {

    fun fadeIn(view: View, time: Long) {
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

}