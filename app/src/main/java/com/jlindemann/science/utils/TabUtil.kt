package com.jlindemann.science.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import android.widget.Button
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

object TabUtil {
    fun openCustomTab(url: String, pkgManager: PackageManager, context: Context) {
        val PACKAGE_NAME = "com.android.chrome"
        val customTabBuilder = CustomTabsIntent.Builder()

        customTabBuilder.setToolbarColor(ContextCompat.getColor(context, com.jlindemann.science.R.color.colorLightPrimary))
        customTabBuilder.setSecondaryToolbarColor(ContextCompat.getColor(context, com.jlindemann.science.R.color.colorLightPrimary))
        customTabBuilder.setShowTitle(true)

        val CustomTab = customTabBuilder.build()
        val intent = CustomTab.intent
        intent.data = Uri.parse(url)

        val packageManager = pkgManager
        val resolveInfoList = packageManager.queryIntentActivities(CustomTab.intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            if (TextUtils.equals(packageName, PACKAGE_NAME))
                CustomTab.intent.setPackage(PACKAGE_NAME)
        }
        CustomTab.intent.data?.let { it1 -> CustomTab.launchUrl(context, it1) }
    }
}