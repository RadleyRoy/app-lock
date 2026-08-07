package com.radley.applock.lock

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The difference between the two clock-start modes.
 *
 * ON_UNLOCK measures from the moment the user authenticated; ON_LEAVING measures from the
 * moment they switched away. They diverge exactly when someone stays in an app for longer than
 * the chosen duration — which is the case that prompted adding the choice.
 */
class RelockRuleTest {

    private var now = 1_000L
    private val sessions = SessionManager(clock = { now })

    private val whatsapp = "com.whatsapp"
    private val launcher = "com.sec.android.app.launcher"

    private fun onUnlock(policy: RelockPolicy) = RelockRule(RelockTrigger.ON_UNLOCK, policy)
    private fun onLeaving(policy: RelockPolicy) = RelockRule(RelockTrigger.ON_LEAVING, policy)

    /** Unlock the app and enter it, as a real unlock does. */
    private fun unlockAndEnter(rule: RelockRule) {
        sessions.grant(whatsapp, rule)
        sessions.onForeground(whatsapp)
    }

    @Test
    fun `ON_UNLOCK never interrupts while the app is on screen`() {
        unlockAndEnter(onUnlock(RelockPolicy.AFTER_1_MINUTE))

        now += 10 * 60_000
        assertTrue(
            sessions.isUnlocked(whatsapp),
            "the countdown may have elapsed, but locking an app under the user is not allowed",
        )
    }

    @Test
    fun `ON_UNLOCK locks on the next entry once the countdown has elapsed`() {
        unlockAndEnter(onUnlock(RelockPolicy.AFTER_1_MINUTE))

        now += 2 * 60_000       // stayed in the app past the countdown
        sessions.onForeground(launcher)  // left

        assertFalse(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `ON_UNLOCK still counts time spent inside the app`() {
        unlockAndEnter(onUnlock(RelockPolicy.AFTER_1_MINUTE))

        now += 50_000                     // 50s inside
        sessions.onForeground(launcher)   // leave
        now += 20_000                     // 20s away; 70s total since unlocking

        // This is the whole point of ON_UNLOCK: only 20s away, but the minute is spent.
        assertFalse(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `ON_LEAVING ignores time spent inside the app`() {
        unlockAndEnter(onLeaving(RelockPolicy.AFTER_1_MINUTE))

        now += 50_000
        sessions.onForeground(launcher)
        now += 20_000

        // Same timeline as the test above, opposite outcome: the clock only starts on leaving.
        assertTrue(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `ON_UNLOCK returning before the countdown elapses does not re-prompt`() {
        unlockAndEnter(onUnlock(RelockPolicy.AFTER_1_MINUTE))

        now += 20_000
        sessions.onForeground(launcher)
        now += 20_000                     // 40s total, still inside the minute

        assertTrue(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `ON_UNLOCK boundary is exact`() {
        unlockAndEnter(onUnlock(RelockPolicy.AFTER_30_SECONDS))
        sessions.onForeground(launcher)

        now += 29_999
        assertTrue(sessions.isUnlocked(whatsapp))

        now += 1
        assertFalse(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `ON_UNLOCK does not depend on a departure ever being recorded`() {
        // The robustness argument for this mode: no window event is needed for the countdown
        // to be correct, only the instant of unlocking, which the app owns.
        sessions.grant(whatsapp, onUnlock(RelockPolicy.AFTER_30_SECONDS))

        // Foreground immunity dropped, but nothing was ever told where the user went — so
        // there is no departure timestamp to measure from. ON_UNLOCK does not need one.
        sessions.onScreenOn()

        now += 30_000

        assertFalse(sessions.isUnlocked(whatsapp))
    }

    @Test
    fun `both triggers behave identically for UNTIL_SCREEN_OFF`() {
        listOf(onUnlock(RelockPolicy.UNTIL_SCREEN_OFF), onLeaving(RelockPolicy.UNTIL_SCREEN_OFF))
            .forEach { rule ->
                val fresh = SessionManager(clock = { now })
                fresh.grant(whatsapp, rule)
                fresh.onForeground(whatsapp)
                fresh.onForeground(launcher)
                now += 24 * 60 * 60 * 1000L

                assertTrue(fresh.isUnlocked(whatsapp), "${rule.trigger} should still be unlocked")
            }
    }

    @Test
    fun `both triggers behave identically for IMMEDIATELY`() {
        listOf(onUnlock(RelockPolicy.IMMEDIATELY), onLeaving(RelockPolicy.IMMEDIATELY))
            .forEach { rule ->
                val fresh = SessionManager(clock = { now })
                fresh.grant(whatsapp, rule)
                fresh.onForeground(whatsapp)

                assertTrue(fresh.isUnlocked(whatsapp), "${rule.trigger}: on screen, no interrupt")

                fresh.onForeground(launcher)
                assertFalse(fresh.isUnlocked(whatsapp), "${rule.trigger}: spent once left")
            }
    }

    @Test
    fun `isTimed marks exactly the durations where the trigger changes anything`() {
        assertFalse(RelockPolicy.IMMEDIATELY.isTimed)
        assertFalse(RelockPolicy.UNTIL_SCREEN_OFF.isTimed)

        listOf(
            RelockPolicy.AFTER_10_SECONDS,
            RelockPolicy.AFTER_30_SECONDS,
            RelockPolicy.AFTER_1_MINUTE,
            RelockPolicy.AFTER_5_MINUTES,
            RelockPolicy.AFTER_15_MINUTES,
        ).forEach { assertTrue(it.isTimed, "$it should be timed") }
    }

    @Test
    fun `unknown or absent stored values fall back to the defaults`() {
        assertTrue(RelockTrigger.fromNameOrDefault(null) == RelockTrigger.DEFAULT)
        assertTrue(RelockTrigger.fromNameOrDefault("NONSENSE") == RelockTrigger.DEFAULT)
        assertTrue(RelockPolicy.fromNameOrDefault(null) == RelockPolicy.DEFAULT)
        assertTrue(RelockPolicy.fromNameOrDefault("REMOVED_IN_A_LATER_VERSION") == RelockPolicy.DEFAULT)
    }
}
