package com.radley.applock.lock

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Covers [RelockTrigger.ON_LEAVING] — the countdown starting when the user switches away.
 * [RelockRuleTest] covers [RelockTrigger.ON_UNLOCK] and the differences between the two.
 */
class SessionManagerTest {

    private var now = 1_000L
    private val sessions = SessionManager(clock = { now })

    private val instagram = "com.instagram.android"
    private val launcher = "com.sec.android.app.launcher"

    private fun onLeaving(policy: RelockPolicy) = RelockRule(RelockTrigger.ON_LEAVING, policy)

    @Test
    fun `unknown package is locked`() {
        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `granted package is unlocked`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.IMMEDIATELY))
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `IMMEDIATELY keeps the app unlocked while it is still in front`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.IMMEDIATELY))
        sessions.onForeground(instagram)
        now += 60_000
        // Re-locking under the user while they are still reading would be the worst bug here.
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `IMMEDIATELY re-locks as soon as the app leaves`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.IMMEDIATELY))
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `grace period expiry is exact at the boundary`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.AFTER_30_SECONDS))
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)

        now += 29_999
        assertTrue(sessions.isUnlocked(instagram), "still inside the grace window")

        now += 1
        assertFalse(sessions.isUnlocked(instagram), "grace window has elapsed")
    }

    @Test
    fun `returning within the grace window keeps it unlocked`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.AFTER_1_MINUTE))
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        now += 30_000
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `UNTIL_SCREEN_OFF survives arbitrarily long absences`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        now += 6 * 60 * 60 * 1000L
        // Long.MAX_VALUE grace must not overflow into a past expiry.
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `screen off clears every session regardless of policy`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))
        sessions.grant("com.whatsapp", onLeaving(RelockPolicy.AFTER_5_MINUTES))
        sessions.onForeground(instagram)

        sessions.onScreenOff()

        assertFalse(sessions.isUnlocked(instagram))
        assertFalse(sessions.isUnlocked("com.whatsapp"))
    }

    @Test
    fun `revoke drops only the named package`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))
        sessions.grant("com.whatsapp", onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))

        sessions.revoke(instagram)

        assertFalse(sessions.isUnlocked(instagram))
        assertTrue(sessions.isUnlocked("com.whatsapp"))
    }

    @Test
    fun `repeated foreground reports of the same package do not restart the grace clock`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.AFTER_10_SECONDS))
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)

        now += 5_000
        sessions.onForeground(launcher) // duplicate report
        now += 5_000

        assertFalse(sessions.isUnlocked(instagram), "clock should run from the first departure")
    }

    @Test
    fun `a session with no recorded departure still expires`() {
        // Regression guard. This once returned "unlocked" for a null departure timestamp, so a
        // single dropped window event kept an app unlocked forever. One UI's gesture-driven
        // home transition does not reliably emit one, which made that reachable in practice.
        sessions.grant(instagram, onLeaving(RelockPolicy.AFTER_30_SECONDS))

        // Drop the foreground belief without telling the manager where the user went, so the
        // session has to fall back on a departure timestamp it never received.
        sessions.onScreenOn()

        assertTrue(sessions.isUnlocked(instagram), "departure stamped lazily on first check")

        now += 30_000
        assertFalse(sessions.isUnlocked(instagram), "must not stay unlocked indefinitely")
    }

    @Test
    fun `reconcileForeground corrects a stale belief`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.IMMEDIATELY))
        sessions.onForeground(instagram)

        // Ground truth says the user actually left; the event stream never told us.
        sessions.reconcileForeground(launcher)

        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `reconcileForeground agreeing with the current belief changes nothing`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.IMMEDIATELY))
        sessions.onForeground(instagram)

        sessions.reconcileForeground(instagram)
        sessions.reconcileForeground(null)

        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `screen on drops foreground immunity without clearing sessions`() {
        sessions.grant(instagram, onLeaving(RelockPolicy.AFTER_1_MINUTE))
        sessions.onForeground(instagram)

        sessions.onScreenOn()

        // Session survives, but the app no longer gets "still on screen" immunity, so the
        // countdown applies from now.
        assertTrue(sessions.isUnlocked(instagram))
        now += 60_000
        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `hasTimedSession only reports real countdowns`() {
        assertFalse(sessions.hasTimedSession())

        sessions.grant(instagram, onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))
        assertFalse(sessions.hasTimedSession(), "until screen off is not a countdown")

        sessions.grant("com.whatsapp", onLeaving(RelockPolicy.IMMEDIATELY))
        assertFalse(sessions.hasTimedSession(), "immediately is not a countdown either")

        sessions.grant("com.spotify.music", onLeaving(RelockPolicy.AFTER_5_MINUTES))
        assertTrue(sessions.hasTimedSession())
    }
}
