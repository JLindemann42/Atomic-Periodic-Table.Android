package com.jlindemann.science.preferences

import android.content.Context

class IsoPreferences(context : Context) {

    val PREFERENCE_NAME = "Iso_Preference"
    val PREFERENCE_VALUE = "Iso_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : Int{
        return preference.getInt (PREFERENCE_VALUE, 0)
        //0 == Alphabetical
        //1 == Element Number
    }

    fun setValue(count:Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE,count)
        editor.apply()
    }
}

class sendIso(context : Context) {

    val PREFERENCE_NAME = "send_Iso_pref"
    val PREFERENCE_VALUE = "send_iso_value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue() : String{
        return preference.getString (PREFERENCE_VALUE, "false")!!
        //0 == Not sent
        //1 == Sent
    }

    fun setValue(count:String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE,count)
        editor.apply()
    }
}