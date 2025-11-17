package com.jlindemann.science.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result - continue to main activity regardless
        proceedToMain()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if we should request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if this is the first launch
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val hasRequestedPermission = prefs.getBoolean("notification_permission_requested", false)
            
            if (!hasRequestedPermission) {
                // Mark that we've requested permission
                prefs.edit().putBoolean("notification_permission_requested", true).apply()
                
                // Check if permission is already granted
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request the permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
        }

        // If we don't need to request permission, proceed directly
        proceedToMain()
    }

    private fun proceedToMain() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasRequestedPermission = prefs.getBoolean("notification_permission_requested", false)
        val hasShownIntroduction = prefs.getBoolean("introduction_shown", false)
        
        // Show introduction only after notification permission has been requested
        // and if introduction hasn't been shown before
        val intent = if (hasRequestedPermission && !hasShownIntroduction) {
            Intent(this, IntroductionActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}