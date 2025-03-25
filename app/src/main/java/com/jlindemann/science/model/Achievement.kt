package com.jlindemann.science.model

import android.content.Context
import android.content.SharedPreferences

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    var progress: Int,
    val maxProgress: Int
) {
    fun incrementProgress(context: Context, increment: Int = 1) {
        if (progress < maxProgress) {
            progress += increment
            if (progress > maxProgress) {
                progress = maxProgress
            }
            saveProgress(context)
        }
    }

    private fun saveProgress(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putInt("achievement_$id", progress)
            apply()
        }
    }

    fun loadProgress(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        progress = sharedPreferences.getInt("achievement_$id", 0)
    }

    fun isMaxProgressReached(): Boolean {
        return progress >= maxProgress
    }

    fun isToastShown(context: Context): Boolean {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("achievement_toast_shown_$id", false)
    }

    fun markToastShown(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("achievement_toast_shown_$id", true)
            apply()
        }
    }
}