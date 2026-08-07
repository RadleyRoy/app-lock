package com.radley.applock.lock

/**
 * Tracks which packages are currently unlocked.
 *
 * State is intentionally in-memory only: if the process dies, everything should re-lock.
 * Persisting sessions would mean a reboot or a low-memory kill could leave apps open.
 *
 * [clock] is injected so the grace logic is testable without waiting in real time.
 */
class SessionManager(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Session(
        val policy: RelockPolicy,
        /** null while the app is still in front, or granted but not yet entered. */
        var leftForegroundAt: Long? = null,
    )

    private val sessions = mutableMapOf<String, Session>()
    private var foregroundPackage: String? = null

    @Synchronized
    fun grant(packageName: String, policy: RelockPolicy) {
        sessions[packageName] = Session(policy)
    }

    @Synchronized
    fun isUnlocked(packageName: String): Boolean {
        val session = sessions[packageName] ?: return false

        // Still on screen: never re-lock under the user.
        if (packageName == foregroundPackage) return true

        val leftAt = session.leftForegroundAt ?: return true
        if (session.policy == RelockPolicy.UNTIL_SCREEN_OFF) return true

        val elapsed = clock() - leftAt
        if (elapsed >= session.policy.graceMillis) {
            sessions.remove(packageName)
            return false
        }
        return true
    }

    /**
     * Report of which package is now in front. Stamps the outgoing package's departure time,
     * which is what starts its grace countdown.
     */
    @Synchronized
    fun onForeground(packageName: String) {
        val previous = foregroundPackage
        if (previous == packageName) return
        if (previous != null) {
            sessions[previous]?.let { it.leftForegroundAt = clock() }
        }

        // Settle the incoming package's session *before* it gains foreground immunity in
        // isUnlocked(). Skipping this lets an expired session come back to life simply by
        // reopening the app, which silently defeats every policy except UNTIL_SCREEN_OFF.
        expireIfElapsed(packageName)

        foregroundPackage = packageName
    }

    private fun expireIfElapsed(packageName: String) {
        val session = sessions[packageName] ?: return
        val leftAt = session.leftForegroundAt ?: return
        if (session.policy == RelockPolicy.UNTIL_SCREEN_OFF) return
        if (clock() - leftAt >= session.policy.graceMillis) {
            sessions.remove(packageName)
        }
    }

    /** Screen off is an unconditional reset — every app re-locks, whatever its policy. */
    @Synchronized
    fun onScreenOff() {
        sessions.clear()
        foregroundPackage = null
    }

    @Synchronized
    fun revoke(packageName: String) {
        sessions.remove(packageName)
    }

    @Synchronized
    fun revokeAll() {
        sessions.clear()
    }
}
