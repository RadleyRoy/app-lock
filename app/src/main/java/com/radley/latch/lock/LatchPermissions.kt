package com.radley.latch.lock

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runtime checks for the special-access grants Latch depends on.
 *
 * These are all "special access" permissions: they cannot be requested with a dialog and have
 * to be toggled by the user in Settings, so the onboarding wizard needs to poll their state
 * rather than handle a permission result.
 */
object LatchPermissions {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, LockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        // The list is colon-separated, and entries can be written either fully qualified or
        // with a short class name, so compare against both forms.
        return enabled.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry) ?: return@any false
            component.packageName == expected.packageName &&
                component.className == expected.className
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * `unsafeCheckOpNoThrow` is marked deprecated but has no replacement available to a normal
     * app: usage access is an appop with no public query API, and the alternative — issuing a
     * real query and inferring the grant from an empty result — cannot distinguish "denied"
     * from "genuinely no activity in the window".
     */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(android.os.PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /** True once locking can actually work; the rest are quality-of-life grants. */
    fun canLock(context: Context): Boolean =
        canDrawOverlays(context) && (isAccessibilityEnabled(context) || hasUsageAccess(context))
}
