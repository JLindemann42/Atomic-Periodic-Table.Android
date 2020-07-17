package com.jlindemann.science.animations

import android.R
import android.content.Context
import android.os.Handler
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.jlindemann.science.MainActivity
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.group_1.*
import kotlinx.android.synthetic.main.group_14.*
import kotlinx.android.synthetic.main.group_15.*
import kotlinx.android.synthetic.main.group_16.*
import kotlinx.android.synthetic.main.group_17.*
import kotlinx.android.synthetic.main.isotope_panel.*
import okhttp3.*
import java.io.IOException
import java.io.InputStream

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