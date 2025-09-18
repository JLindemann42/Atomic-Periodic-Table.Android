package com.jlindemann.science.activities.tools

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

object TitleBarAnimator {

    @JvmStatic
    fun animateVisibility(
        view: View,
        setVisible: Boolean,
        duration: Long = 200,
        visibleAlpha: Float = 1.0f
    ) {
        // Cancel any running animation
        view.animate().cancel()

        if (setVisible) {
            if (view.visibility != View.VISIBLE || view.alpha < visibleAlpha) {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate()
                    .alpha(visibleAlpha)
                    .setDuration(duration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            view.alpha = visibleAlpha
                            view.visibility = View.VISIBLE
                        }
                    })
            }
        } else {
            if (view.visibility != View.INVISIBLE || view.alpha > 0f) {
                view.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            view.alpha = 0f
                            view.visibility = View.INVISIBLE
                        }
                    })
            }
        }
    }
}