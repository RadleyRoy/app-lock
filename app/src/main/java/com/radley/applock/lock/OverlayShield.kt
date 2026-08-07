package com.radley.applock.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * An opaque view placed over the screen the instant a protected app is detected.
 *
 * [com.radley.applock.ui.lock.LockActivity] takes a frame or two to start, and without this the
 * protected app's content is briefly visible underneath — enough to read a message preview or a
 * bank balance.
 *
 * ## Why this class is defensive out of proportion to its size
 *
 * In v1.0.0 the shield was removed only when the user unlocked or tapped the close button.
 * Pressing HOME stopped the lock activity without finishing it, so neither path ran, and the
 * shield — opaque, full-screen and touch-consuming — stayed up permanently. The phone became
 * unusable and had to be restarted.
 *
 * Four independent safeguards now prevent that, any one of which is sufficient:
 *
 *  1. **[WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE]** — the shield is purely cosmetic. The
 *     lock activity sits above it and takes input itself, so the shield never needed to consume
 *     touches. A stuck shield is now something you can tap straight through.
 *  2. **A self-destruct timer** ([ShieldPolicy]), re-armed on each show, that removes the view
 *     no matter what the caller does or fails to do.
 *  3. **Lifecycle teardown** from `LockActivity.onStop()`, covering HOME, task switch and
 *     process death.
 *  4. **Screen-off teardown** from [LockWatchService].
 */
object OverlayShield {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val policy = ShieldPolicy()

    private var windowManager: WindowManager? = null
    private var shield: View? = null

    private val selfDestruct = Runnable { hide() }

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        runOnMain {
            armSelfDestruct()

            if (shield != null) return@runOnMain
            if (!canDraw(context)) return@runOnMain

            val manager = context.getSystemService(WindowManager::class.java) ?: return@runOnMain
            val view = View(context).apply { setBackgroundColor(SHIELD_COLOR) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCHABLE is the load-bearing flag here, not an optimisation: it is what
                // makes a failure to remove this window survivable. NOT_FOCUSABLE alone only
                // declines keyboard focus — it still swallows every touch on the device.
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply { gravity = Gravity.TOP or Gravity.START }

            runCatching { manager.addView(view, params) }
                .onSuccess {
                    windowManager = manager
                    shield = view
                }
                .onFailure {
                    // Could not add the window; make sure we are not left believing one is up.
                    policy.onHide()
                    mainHandler.removeCallbacks(selfDestruct)
                }
        }
    }

    fun hide() {
        runOnMain {
            mainHandler.removeCallbacks(selfDestruct)
            policy.onHide()

            val view = shield ?: return@runOnMain
            runCatching { windowManager?.removeView(view) }
            shield = null
            windowManager = null
        }
    }

    private fun armSelfDestruct() {
        policy.onShow()
        mainHandler.removeCallbacks(selfDestruct)
        mainHandler.postDelayed(selfDestruct, ShieldPolicy.DEFAULT_LIFETIME_MILLIS)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Matches the lock screen's background, so there is no colour flash between the two. */
    private val SHIELD_COLOR = Color.parseColor("#FF030303")
}
