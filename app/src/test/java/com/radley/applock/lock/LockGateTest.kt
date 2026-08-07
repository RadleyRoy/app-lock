package com.radley.applock.lock

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LockGateTest {

    private val own = "com.radley.applock"
    private val instagram = "com.instagram.android"
    private val calculator = "com.sec.android.app.popupcalculator"

    private var now = 1_000L
    private var protectedPackages = setOf(instagram)
    private var enabled = true

    private val sessions = SessionManager(clock = { now })

    /** These cases are about the gate, not the timing mode; ON_LEAVING keeps them unchanged. */
    private fun rule(policy: RelockPolicy) = RelockRule(RelockTrigger.ON_LEAVING, policy)
    private val gate = LockGate(
        sessions = sessions,
        ownPackage = own,
        protectedPackages = { protectedPackages },
        protectionEnabled = { enabled },
    )

    @Test
    fun `protected package locks`() {
        assertTrue(gate.shouldLock(instagram))
    }

    @Test
    fun `unprotected package does not lock`() {
        assertFalse(gate.shouldLock(calculator))
    }

    @Test
    fun `our own package never locks`() {
        // The lock screen is itself a foreground activity; locking it would recurse forever.
        protectedPackages = setOf(instagram, own)
        assertFalse(gate.shouldLock(own))
    }

    @Test
    fun `disabling protection unlocks everything`() {
        enabled = false
        assertFalse(gate.shouldLock(instagram))
    }

    @Test
    fun `an active session suppresses the lock`() {
        sessions.grant(instagram, rule(RelockPolicy.UNTIL_SCREEN_OFF))
        assertFalse(gate.shouldLock(instagram))
    }

    @Test
    fun `it locks again once the session expires`() {
        sessions.grant(instagram, rule(RelockPolicy.AFTER_10_SECONDS))
        sessions.onForeground(instagram)
        sessions.onForeground("com.sec.android.app.launcher")
        now += 10_000

        assertTrue(gate.shouldLock(instagram))
    }

    @Test
    fun `adding a package to the protected set takes effect immediately`() {
        assertFalse(gate.shouldLock(calculator))
        protectedPackages = protectedPackages + calculator
        assertTrue(gate.shouldLock(calculator))
    }

    @Test
    fun `onForegroundPackage reports the decision and records the switch`() {
        sessions.grant(instagram, rule(RelockPolicy.IMMEDIATELY))

        assertFalse(gate.onForegroundPackage(instagram), "just unlocked, should pass through")
        assertFalse(gate.onForegroundPackage(calculator), "not protected")
        assertTrue(gate.onForegroundPackage(instagram), "IMMEDIATELY expired on leaving")
    }

    @Test
    fun `our own package coming forward does not end the protected app's session`() {
        sessions.grant(instagram, rule(RelockPolicy.IMMEDIATELY))
        gate.onForegroundPackage(instagram)

        // The lock screen appearing must not count as "you left Instagram".
        gate.onForegroundPackage(own)

        assertFalse(gate.shouldLock(instagram))
    }
}
