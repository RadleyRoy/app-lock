package com.radley.applock.lock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Decides whether an accessibility window event represents an app actually coming to the
 * foreground, or merely a window that some app raised while sitting in the background.
 *
 * ## Why this exists
 *
 * `TYPE_WINDOW_STATE_CHANGED` fires for **toasts, dialogs and popup windows too**, and it
 * carries the package of whoever raised them — not the package of whatever is actually on
 * screen. Treating those as foreground changes produced a bug that was reproducible on
 * WhatsApp and almost nothing else, because WhatsApp is unusually chatty with background
 * windows (delivery toasts, media dialogs, connection popups) where most apps stay silent.
 *
 * The result was two symptoms from one cause: a lock screen appearing while the user was on
 * the home screen, and — because the stale package got pinned as "foreground" —
 * [SessionManager] refusing to re-prompt when the app was genuinely reopened.
 *
 * An Activity has a manifest entry; a toast or a bare dialog does not. Resolving the
 * component is therefore a reliable way to tell them apart, and needs no extra permission.
 */
class ActivityWindowFilter(
    private val resolver: Resolver,
    cacheSize: Int = DEFAULT_CACHE_SIZE,
) {

    /** Indirection so the decision is testable without a real PackageManager. */
    fun interface Resolver {
        /** True when `packageName/className` names a declared Activity. */
        fun isActivity(packageName: String, className: String): Boolean
    }

    /**
     * A plain access-ordered LinkedHashMap rather than `android.util.LruCache`, so this class
     * has no Android dependency and its caching is genuinely exercised by tests — the framework
     * stub would silently no-op and the guarantee would go unverified.
     */
    private val cache = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean =
            size > cacheSize
    }

    /**
     * @param className the event's class name, which may be absent.
     *
     * Fails **open**: when the class name is missing there is no way to tell a real activity
     * from a popup, and the safe answer is to accept. A missed lock is a security failure,
     * while an extra lock is only an annoyance — so the uncertain case must not be rejected.
     */
    @Synchronized
    fun isForegroundActivity(packageName: String, className: String?): Boolean {
        if (packageName.isBlank()) return false
        val cls = className?.takeIf { it.isNotBlank() } ?: return true

        val key = "$packageName/$cls"
        cache[key]?.let { return it }

        return resolver.isActivity(packageName, cls).also { cache[key] = it }
    }

    companion object {
        /**
         * Comfortably larger than the number of distinct activities a phone cycles through, so
         * the steady state is pure cache hits — this runs for every window change on the device.
         */
        private const val DEFAULT_CACHE_SIZE = 256

        fun from(context: Context): ActivityWindowFilter {
            val packageManager = context.packageManager
            return ActivityWindowFilter(
                Resolver { packageName, className ->
                    try {
                        packageManager.getActivityInfo(ComponentName(packageName, className), 0)
                        true
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Not a declared activity: a toast, a bare dialog or a popup window.
                        false
                    }
                },
            )
        }
    }
}
