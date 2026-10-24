package com.jlindemann.science.preferences

import android.content.Context

/**
 * PRO+ Ultimate ownership.
 *
 * Stored exactly like [ProVersion] and [ProPlusVersion] - 1 means not owned, 100 means owned -
 * so the three tiers read the same way everywhere in the app.
 *
 * Unlike the other two this flag is not owned by Google Play alone. Ultimate is bought once, on
 * whichever platform the user happens to be holding, and the shared account document carries it to
 * the others. See EntitlementManager for the rule that keeps it grant-only.
 */
class UltimateVersion(context: Context) {

    val PREFERENCE_NAME = "Ultimate_Preference"
    val PREFERENCE_VALUE = "Ultimate_Value"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    // DO NOT CHANGE VALUE FROM 1 BEFORE PUSHING RELEASE
    fun getValue(): Int {
        return preference.getInt(PREFERENCE_VALUE, 1) //1 == NO ULTIMATE, 100 == ULTIMATE
    }

    fun setValue(count: Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_VALUE, count)
        editor.apply()
    }
}
