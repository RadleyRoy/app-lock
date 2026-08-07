package com.radley.applock.lock

/**
 * Tracks which packages are currently unlocked.
 *
 * State is intentionally in-memory only: if the process dies, everything should re-lock.
 * Persisting sessions would mean a reboot or a low-memory kill could leave apps open.
 *
 * [clock] is injected so the expiry logic is testable without waiting in real time.
 *
 * ## Two ways the countdown can start
 *
 * [RelockTrigger.ON_UNLOCK] measures from [Session.unlockedAt] — an instant this app owns, so
 * it needs no knowledge of when an app was left. [RelockTrigger.ON_LEAVING] measures from
 * [Session.leftForegroundAt], which has to be inferred from window events; One UI's
 * gesture-driven home transition does not always produce one. That inference is therefore
 * written to fail *closed*: see [startOfCountdown].
 *
 * The one thing neither trigger will do is lock an app that is currently on screen.
 */
class SessionManager(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Session(
        val rule: RelockRule,
        val unlockedAt: Long,
        /** null while the app is still in front, or granted but not yet entered. */
        var leftForegroundAt: Long? = null,
    )

    private val sessions = mutableMapOf<String, Session>()
    private var foregroundPackage: String? = null

    @Synchronized
    fun grant(packageName: String, rule: RelockRule) {
        sessions[packageName] = Session(rule = rule, unlockedAt = clock())

        // Unlocking *is* entering: the lock screen only ever appears because this app is being
        // opened. Recording that here, rather than waiting for the window event to confirm it,
        // closes a gap where a zero-length policy could expire the session before the app came
        // forward — which would re-lock immediately and loop.
        foregroundPackage = packageName
    }

    @Synchronized
    fun isUnlocked(packageName: String): Boolean {
        val session = sessions[packageName] ?: return false

        // Never interrupt: an app that is on screen is not re-locked under the user, whatever
        // the countdown says. They are asked again the next time they open it.
        if (packageName == foregroundPackage) return true

        if (hasExpired(session)) {
            sessions.remove(packageName)
            return false
        }
        return true
    }

    /**
     * Report of which package is now in front.
     */
    @Synchronized
    fun onForeground(packageName: String) {
        if (packageName == foregroundPackage) return
        switchForegroundTo(packageName)
    }

    /**
     * Corrects the foreground belief from an authoritative source (usage stats), for callers
     * that have one. The event stream this class is normally driven by can be filtered,
     * delayed or delivered out of order, and nothing else would ever notice it had gone stale.
     */
    @Synchronized
    fun reconcileForeground(packageName: String?) {
        if (packageName == null || packageName == foregroundPackage) return
        switchForegroundTo(packageName)
    }

    /** Caller must hold the monitor. */
    private fun switchForegroundTo(packageName: String) {
        foregroundPackage?.let { previous ->
            sessions[previous]?.let { it.leftForegroundAt = clock() }
        }

        // Settle the incoming package's session *before* it gains foreground immunity in
        // isUnlocked(). Skipping this lets an expired session come back to life simply by
        // reopening the app, which silently defeats every policy except UNTIL_SCREEN_OFF.
        sessions[packageName]?.let { session ->
            if (hasExpired(session)) sessions.remove(packageName)
        }

        foregroundPackage = packageName
    }

    /** Screen off is an unconditional reset — every app re-locks, whatever its policy. */
    @Synchronized
    fun onScreenOff() {
        sessions.clear()
        foregroundPackage = null
    }

    /**
     * Drops the foreground belief without touching sessions, so a stale one cannot survive a
     * screen cycle and hand an app immunity it no longer deserves.
     */
    @Synchronized
    fun onScreenOn() {
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

    /** True when at least one live session is on a real countdown and worth reconciling for. */
    @Synchronized
    fun hasTimedSession(): Boolean = sessions.values.any { it.rule.policy.isTimed }

    private fun hasExpired(session: Session): Boolean {
        if (session.rule.policy == RelockPolicy.UNTIL_SCREEN_OFF) return false
        val elapsed = clock() - startOfCountdown(session)
        return elapsed >= session.rule.policy.graceMillis
    }

    private fun startOfCountdown(session: Session): Long = when (session.rule.trigger) {
        RelockTrigger.ON_UNLOCK -> session.unlockedAt

        // Stamped lazily rather than treated as "still inside". Reaching here means the app is
        // not the foreground package, so it has left — we just never saw the event. Returning
        // "unlocked" for a missing timestamp, as this once did, meant a single dropped event
        // kept an app unlocked forever. Assuming it left now costs at most one extra grace
        // period, which is bounded; the alternative was not.
        RelockTrigger.ON_LEAVING -> session.leftForegroundAt
            ?: clock().also { session.leftForegroundAt = it }
    }
}
