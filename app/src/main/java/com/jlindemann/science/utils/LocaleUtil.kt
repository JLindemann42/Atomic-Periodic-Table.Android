package com.jlindemann.science.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleUtil {
    fun wrap(context: Context, locale: Locale?): Context {
        val targetLocale = locale ?: getSystemLocale(context)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(targetLocale)
            return context.createConfigurationContext(config)
        } else {
            config.locale = targetLocale
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
    fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            context.resources.configuration.locale
        }
    }
    fun restartForLocaleChange(activity: android.app.Activity) {
        val intent = activity.intent
        activity.finish()
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
    }
}