package com.radley.latch.lock

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SessionManagerTest {

    private var now = 1_000L
    private val sessions = SessionManager(clock = { now })

    private val instagram = "com.instagram.android"
    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun `unknown package is locked`() {
        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `granted package is unlocked`() {
        sessions.grant(instagram, RelockPolicy.IMMEDIATELY)
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `IMMEDIATELY keeps the app unlocked while it is still in front`() {
        sessions.grant(instagram, RelockPolicy.IMMEDIATELY)
        sessions.onForeground(instagram)
        now += 60_000
        // Re-locking under the user while they are still reading would be the worst bug here.
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `IMMEDIATELY re-locks as soon as the app leaves`() {
        sessions.grant(instagram, RelockPolicy.IMMEDIATELY)
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        assertFalse(sessions.isUnlocked(instagram))
    }

    @Test
    fun `grace period expiry is exact at the boundary`() {
        sessions.grant(instagram, RelockPolicy.AFTER_30_SECONDS)
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)

        now += 29_999
        assertTrue(sessions.isUnlocked(instagram), "still inside the grace window")

        now += 1
        assertFalse(sessions.isUnlocked(instagram), "grace window has elapsed")
    }

    @Test
    fun `returning within the grace window keeps it unlocked`() {
        sessions.grant(instagram, RelockPolicy.AFTER_1_MINUTE)
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        now += 30_000
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `UNTIL_SCREEN_OFF survives arbitrarily long absences`() {
        sessions.grant(instagram, RelockPolicy.UNTIL_SCREEN_OFF)
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)
        now += 6 * 60 * 60 * 1000L
        // Long.MAX_VALUE grace must not overflow into a past expiry.
        assertTrue(sessions.isUnlocked(instagram))
    }

    @Test
    fun `screen off clears every session regardless of policy`() {
        sessions.grant(instagram, RelockPolicy.UNTIL_SCREEN_OFF)
        sessions.grant("com.whatsapp", RelockPolicy.AFTER_5_MINUTES)
        sessions.onForeground(instagram)

        sessions.onScreenOff()

        assertFalse(sessions.isUnlocked(instagram))
        assertFalse(sessions.isUnlocked("com.whatsapp"))
    }

    @Test
    fun `revoke drops only the named package`() {
        sessions.grant(instagram, RelockPolicy.UNTIL_SCREEN_OFF)
        sessions.grant("com.whatsapp", RelockPolicy.UNTIL_SCREEN_OFF)

        sessions.revoke(instagram)

        assertFalse(sessions.isUnlocked(instagram))
        assertTrue(sessions.isUnlocked("com.whatsapp"))
    }

    @Test
    fun `repeated foreground reports of the same package do not restart the grace clock`() {
        sessions.grant(instagram, RelockPolicy.AFTER_10_SECONDS)
        sessions.onForeground(instagram)
        sessions.onForeground(launcher)

        now += 5_000
        sessions.onForeground(launcher) // duplicate report
        now += 5_000

        assertFalse(sessions.isUnlocked(instagram), "clock should run from the first departure")
    }
}
