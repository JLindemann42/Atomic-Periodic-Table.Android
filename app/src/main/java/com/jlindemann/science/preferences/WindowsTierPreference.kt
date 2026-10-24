package com.jlindemann.science.preferences

import android.content.Context

/**
 * The tier the Windows app last reported, as the wire string "free", "pro" or "pro+".
 *
 * Cached locally purely so the account screen can show a "Windows PRO+" badge without a network
 * round trip. This app never writes entitlements.windows to the cloud - Windows owns that key, the
 * same way Windows only reads and never writes entitlements.android.
 */
class WindowsTierPreference(context: Context) {

    val PREFERENCE_NAME = "Windows_Tier_Preference"
    val PREFERENCE_VALUE = "Windows_Tier_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getValue(): String {
        return preference.getString(PREFERENCE_VALUE, "") ?: ""
    }

    fun setValue(tier: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_VALUE, tier)
        editor.apply()
    }
}
