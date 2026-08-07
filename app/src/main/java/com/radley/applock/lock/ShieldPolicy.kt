package com.radley.applock.lock

/**
 * The lifetime rules for [OverlayShield], separated from the WindowManager calls so they can be
 * tested on the JVM.
 *
 * This exists because of a real failure: v1.0.0 removed the shield only on the two success
 * paths, so pressing HOME left an opaque, touch-blocking window over the entire screen with no
 * way to dismiss it short of rebooting the phone. The rule "a shield must never outlive its
 * deadline" is now a tested invariant rather than something spread across activity callbacks.
 */
class ShieldPolicy(
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MILLIS,
) {

    private var deadline: Long? = null

    val isShown: Boolean get() = deadline != null

    /** Arms the shield, or re-arms it if one is already up. Never stacks. */
    fun onShow() {
        deadline = clock() + lifetimeMillis
    }

    fun onHide() {
        deadline = null
    }

    /**
     * True when a shield is up and has outlived its deadline, so the caller must tear it down
     * regardless of what else is going on.
     */
    fun isExpired(): Boolean {
        val current = deadline ?: return false
        return clock() >= current
    }

    fun millisUntilExpiry(): Long? = deadline?.let { (it - clock()).coerceAtLeast(0) }

    companion object {
        /**
         * The shield only has to cover the gap between detecting a protected app and
         * [com.radley.applock.ui.lock.LockActivity] drawing — a couple of hundred milliseconds.
         * A second and a half is generous for that and short enough that a stuck shield is a
         * blink rather than a problem.
         */
        const val DEFAULT_LIFETIME_MILLIS = 1_500L
    }
}
