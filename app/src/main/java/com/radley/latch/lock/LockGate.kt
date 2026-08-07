package com.radley.latch.lock

/**
 * The single place that decides whether a package coming to the foreground should be blocked.
 *
 * Everything it needs is supplied as a lambda rather than read from a Context, so the whole
 * decision is testable on the JVM and there is exactly one code path to reason about when a
 * lock fires when it should not have.
 */
class LockGate(
    private val sessions: SessionManager,
    private val ownPackage: String,
    private val protectedPackages: () -> Set<String>,
    private val protectionEnabled: () -> Boolean = { true },
) {

    fun shouldLock(packageName: String): Boolean {
        if (!protectionEnabled()) return false

        // Locking ourselves would recurse: the lock screen is itself a foreground activity.
        if (packageName == ownPackage) return false

        if (packageName !in protectedPackages()) return false

        return !sessions.isUnlocked(packageName)
    }

    /**
     * Records the foreground change *and* returns the decision, so callers cannot accidentally
     * update session state without asking, or ask without updating.
     */
    fun onForegroundPackage(packageName: String): Boolean {
        if (packageName != ownPackage) {
            sessions.onForeground(packageName)
        }
        return shouldLock(packageName)
    }
}
