package com.radley.latch.lock

import android.content.Context
import android.util.Log
import com.radley.latch.di.ServiceLocator
import com.radley.latch.ui.lock.LockActivity

/**
 * The single action path shared by both detectors: given a package that just came forward,
 * decide, shield, and launch.
 *
 * Both [LockAccessibilityService] and [LockWatchService] funnel through here so there is one
 * definition of "what happens when a protected app opens", rather than two that drift.
 */
object LockEnforcer {

    /**
     * Windows that are not app switches.
     *
     * System UI matters most: pulling down the notification shade or the quick-settings panel
     * fires a window-state change, and treating that as "you left Instagram" would end the
     * session and re-lock the app the moment the shade closed.
     */
    private val NOT_AN_APP_SWITCH = setOf(
        "com.android.systemui",
        "android",
        "com.samsung.android.app.cocktailbarservice", // edge panel
        "com.samsung.android.honeyboard", // Samsung keyboard
        "com.google.android.inputmethod.latin",
    )

    fun handle(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        if (packageName in NOT_AN_APP_SWITCH) return
        if (packageName == context.packageName) return

        if (!ServiceLocator.lockGate.onForegroundPackage(packageName)) return

        // Order matters: the shield goes up synchronously, so nothing of the protected app is
        // on screen during the frames it takes the activity to start.
        OverlayShield.show(context)

        runCatching { context.startActivity(LockActivity.intent(context, packageName)) }
            .onFailure {
                // Starting from the background is only permitted because we hold
                // SYSTEM_ALERT_WINDOW. If that was revoked, drop the shield rather than
                // leaving a black rectangle stuck over the user's screen.
                Log.w(TAG, "Could not start lock screen for $packageName", it)
                OverlayShield.hide()
            }
    }

    private const val TAG = "LockEnforcer"
}
