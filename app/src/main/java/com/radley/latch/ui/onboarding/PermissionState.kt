package com.radley.latch.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.radley.latch.lock.LatchPermissions

data class LatchPermissionStatus(
    val accessibility: Boolean,
    val overlay: Boolean,
    val usageAccess: Boolean,
    val notifications: Boolean,
    val camera: Boolean,
    val unrestrictedBattery: Boolean,
) {
    val canLock: Boolean get() = overlay && (accessibility || usageAccess)
}

/**
 * Special-access permissions are granted in Settings, not through a dialog, so there is no
 * result callback to listen for. Re-reading them on every ON_RESUME is the only way to notice
 * the user came back having flipped one.
 */
@Composable
fun rememberPermissionStatus(): State<LatchPermissionStatus> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val status = remember { mutableStateOf(read(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status.value = read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return status
}

private fun read(context: Context) = LatchPermissionStatus(
    accessibility = LatchPermissions.isAccessibilityEnabled(context),
    overlay = LatchPermissions.canDrawOverlays(context),
    usageAccess = LatchPermissions.hasUsageAccess(context),
    notifications = LatchPermissions.hasNotificationPermission(context),
    camera = LatchPermissions.hasCameraPermission(context),
    unrestrictedBattery = LatchPermissions.isIgnoringBatteryOptimizations(context),
)

object PermissionIntents {

    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlay(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun usageAccess() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * One UI keeps the Unrestricted battery toggle inside the app's own info page rather than
     * exposing a direct action, so this opens App info and the copy says where to tap next.
     */
    fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}
