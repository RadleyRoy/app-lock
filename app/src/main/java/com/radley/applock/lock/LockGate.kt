package com.radley.applock.lock

/**
 * The single place that decides whether a package coming to the foreground should be blocked.
 *
 * Everything it needs is supplied as a lambda rather than read from a Context, so the whole
 * decision is testable on the JVM and there is exactly one code path to reason about when a
 * lock fires when it should not have.
 *
 * There is deliberately **no exemption for AppLock's own package** — it can protect itself like
 * any other app. Keeping the lock screen from locking itself is [LockEnforcer]'s job, because
 * that needs the window's class name and this class only sees packages.
 */
class LockGate(
    private val sessions: SessionManager,
    private val protectedPackages: () -> Set<String>,
    private val protectionEnabled: () -> Boolean = { true },
) {

    fun shouldLock(packageName: String): Boolean {
        if (!protectionEnabled()) return false
        if (packageName !in protectedPackages()) return false
        return !sessions.isUnlocked(packageName)
    }

    /**
     * Records the foreground change *and* returns the decision, so callers cannot accidentally
     * update session state without asking, or ask without updating.
     */
    fun onForegroundPackage(packageName: String): Boolean {
        sessions.onForeground(packageName)
        return shouldLock(packageName)
    }
}
