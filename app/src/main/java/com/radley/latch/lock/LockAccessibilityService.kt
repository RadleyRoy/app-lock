package com.radley.latch.lock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Primary detector.
 *
 * An accessibility service is the only way to learn that an app came to the foreground the
 * moment it happens, rather than up to a poll interval later. That difference is the whole
 * user-visible quality of an app lock: poll-based lockers show a flash of the protected app.
 *
 * This service reads exactly one field — the package name on a window-state change. It does
 * not request `canRetrieveWindowContent`, so it is not able to read screen text, form fields
 * or anything the user types, even in principle.
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        LockEnforcer.handle(this, packageName)
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The watcher hosts the screen-off receiver, which is what clears sessions. Locking
        // still works without it, but apps would stay unlocked across a screen off.
        LockWatchService.start(this)
    }
}
