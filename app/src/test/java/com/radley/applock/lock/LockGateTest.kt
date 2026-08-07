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
    fun `AppLock can protect itself`() {
        // The gate no longer exempts our own package. Keeping the *lock screen* from locking
        // itself is LockEnforcer's job, because that needs the window's class name and the gate
        // only ever sees packages.
        assertFalse(gate.shouldLock(own), "not protected yet")

        protectedPackages = setOf(instagram, own)

        assertTrue(gate.shouldLock(own), "AppLock must be lockable like any other app")
    }

    @Test
    fun `unlocking AppLock lets it through like any other app`() {
        protectedPackages = setOf(own)
        sessions.grant(own, rule(RelockPolicy.UNTIL_SCREEN_OFF))

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
    fun `AppLock's own UI coming forward is a real app switch`() {
        sessions.grant(instagram, rule(RelockPolicy.IMMEDIATELY))
        gate.onForegroundPackage(instagram)

        // Opening AppLock's main screen genuinely means you left Instagram, so the session
        // ends. The one window that must NOT reach here is the lock screen itself — that is
        // filtered by LockEnforcer on the class name, before the gate is ever consulted,
        // because the gate only ever sees package names.
        gate.onForegroundPackage(own)

        assertTrue(gate.shouldLock(instagram))
    }
}
