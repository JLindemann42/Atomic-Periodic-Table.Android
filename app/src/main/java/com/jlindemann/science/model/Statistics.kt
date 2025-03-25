package com.jlindemann.science.model

import android.content.Context
import android.content.SharedPreferences

data class Statistics(
    val id: Int,
    val title: String,
    var progress: Int,
) {
    fun incrementProgress(context: Context, increment: Int = 1) {
        progress += increment
        saveProgress(context)
    }


    private fun saveProgress(context: Context) {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("statistics", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putInt("statistics_$id", progress)
            apply()
        }
    }

    fun loadProgress(context: Context) {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("statistics", Context.MODE_PRIVATE)
        progress = sharedPreferences.getInt("statistics_$id", 0)
    }
}
