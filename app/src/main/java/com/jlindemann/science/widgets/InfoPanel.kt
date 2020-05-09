package com.jlindemann.science.widgets

import android.R
import android.content.Context
import android.os.Handler
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import okhttp3.*
import java.io.IOException
import java.io.InputStream

object InfoPanel {

    fun showInfoPanel(title: String, view_title: TextView, text: String, view_text: TextView, info_view: View, back_btn: View) {

        info_view.visibility = View.VISIBLE
        info_view.alpha = 0.0f
        info_view.animate().setDuration(150)
        info_view.animate().alpha(1.0f)

        view_title.text = title
        view_text.text = text

        back_btn.setOnClickListener {
            hideInfoPanel(info_view)
        }

    }

    private fun hideInfoPanel(info_view: View) {
        info_view.animate().setDuration(150)
        info_view.animate().alpha(0.0f)

        val handler = Handler()
        handler.postDelayed({
            info_view.visibility = View.GONE
        }, 150+1)
    }

}