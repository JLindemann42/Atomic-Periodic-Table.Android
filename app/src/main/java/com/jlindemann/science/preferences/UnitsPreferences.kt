package com.jlindemann.science.preferences

import android.content.Context
import android.content.SharedPreferences

class TemperatureUnits(context : Context) {

    private val preferenceName = "Units_Temperature_Preference"
    private val preferenceValue = "Units_Temperature Value"

    private val preference: SharedPreferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun getValue() : String? {
        return preference.getString (preferenceValue, "celsius")
    }

    fun setValue(tempUnit: String) {
        val editor = preference.edit()
        editor.putString(preferenceValue, tempUnit)
        editor.apply()
    }
}