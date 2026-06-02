package com.jlindemann.science.preferences

import android.content.Context
import android.content.SharedPreferences

class ToolOrderPreference(context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("tool_order", Context.MODE_PRIVATE)

    fun saveOrder(order: List<String>) {
        val editor = sharedPref.edit()
        editor.putString("order", order.joinToString(","))
        editor.apply()
    }

    fun getOrder(): List<String> {
        val orderString = sharedPref.getString("order", "")
        return if (orderString.isNullOrEmpty()) {
            emptyList()
        } else {
            orderString.split(",")
        }
    }
}
