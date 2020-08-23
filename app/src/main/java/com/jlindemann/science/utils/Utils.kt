package com.jlindemann.science.utils

import android.os.Handler
import android.view.View
import com.sothree.slidinguppanel.SlidingUpPanelLayout

object Utils {
    fun fadeInAnim(view: View, time: Long) {
        view.visibility = View.VISIBLE
        view.alpha = 0.0f
        view.animate().setDuration(time)
        view.animate().alpha(1.0f)
    }

    fun slideUp(panel: SlidingUpPanelLayout) {
        panel.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
    }

    fun fadeInAnimCard(view: View, time: Long) {

        view.visibility = View.VISIBLE
        view.alpha = 0.0f
        view.animate().setDuration(time)
        view.animate().alpha(0.85f)

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