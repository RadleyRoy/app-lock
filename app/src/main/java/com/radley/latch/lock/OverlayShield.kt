package com.radley.latch.lock

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
 * An opaque view slammed over the screen the instant a protected app is detected.
 *
 * [LockActivity] takes a frame or two to start, and without this the protected app's content
 * is briefly visible underneath — which is enough to read a message preview or a bank balance.
 * The shield closes that window: it is added synchronously from the accessibility callback,
 * long before the activity draws.
 *
 * A single view, kept in a singleton rather than per-service, so an accessibility event and a
 * usage-stats poll racing on the same app cannot stack two shields that never come down.
 */
object OverlayShield {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var shield: View? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        runOnMain {
            if (shield != null) return@runOnMain
            if (!canDraw(context)) return@runOnMain

            val manager = context.getSystemService(WindowManager::class.java) ?: return@runOnMain
            val view = View(context).apply { setBackgroundColor(SHIELD_COLOR) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not focusable: the shield must never steal input from the lock screen that
                // is about to appear on top of it.
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
        }
    }

    fun hide() {
        runOnMain {
            val view = shield ?: return@runOnMain
            runCatching { windowManager?.removeView(view) }
            shield = null
            windowManager = null
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Matches the lock screen's background, so there is no colour flash between the two. */
    private val SHIELD_COLOR = Color.parseColor("#FF030303")
}
