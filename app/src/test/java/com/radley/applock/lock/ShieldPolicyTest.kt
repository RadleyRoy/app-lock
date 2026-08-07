package com.radley.applock.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.0.0 lockup: the overlay shield was removed only on the two
 * success paths, so pressing HOME left an opaque, touch-blocking window over the whole screen
 * and the phone had to be rebooted.
 *
 * The invariant these protect is "a shield never outlives its deadline".
 */
class ShieldPolicyTest {

    private var now = 1_000L
    private val policy = ShieldPolicy(clock = { now }, lifetimeMillis = 1_500L)

    @Test
    fun `nothing is shown initially`() {
        assertFalse(policy.isShown)
        assertFalse(policy.isExpired())
    }

    @Test
    fun `show marks it shown`() {
        policy.onShow()
        assertTrue(policy.isShown)
    }

    @Test
    fun `hide is idempotent`() {
        policy.onShow()
        policy.onHide()
        policy.onHide()
        assertFalse(policy.isShown)
    }

    @Test
    fun `hiding a shield that was never shown is safe`() {
        policy.onHide()
        assertFalse(policy.isShown)
        assertFalse(policy.isExpired())
    }

    @Test
    fun `a shield expires once its lifetime elapses`() {
        policy.onShow()

        now += 1_499
        assertFalse(policy.isExpired(), "still within its lifetime")

        now += 1
        assertTrue(policy.isExpired(), "must be torn down now")
    }

    @Test
    fun `showing again re-arms rather than stacking`() {
        policy.onShow()
        now += 1_000
        policy.onShow() // a second detection while the first shield is still up

        now += 1_000 // 2000ms after the first show, 1000ms after the second
        assertFalse(policy.isExpired(), "deadline runs from the most recent show")

        now += 500
        assertTrue(policy.isExpired())
    }

    @Test
    fun `an expired shield stops being expired once hidden`() {
        policy.onShow()
        now += 5_000
        assertTrue(policy.isExpired())

        policy.onHide()

        assertFalse(policy.isShown)
        assertFalse(policy.isExpired())
    }

    @Test
    fun `time remaining never goes negative`() {
        policy.onShow()
        assertEquals(1_500L, policy.millisUntilExpiry())

        now += 10_000
        assertEquals(0L, policy.millisUntilExpiry(), "clamped, so a caller cannot post a negative delay")
    }

    @Test
    fun `no deadline is reported when nothing is shown`() {
        assertEquals(null, policy.millisUntilExpiry())
    }

    @Test
    fun `the default lifetime is short enough that a stuck shield is a blink`() {
        // The shield only covers the gap before the lock activity paints (~200ms). If this
        // ever grows past a second or two, a failure to remove it becomes user-visible again.
        assertTrue(ShieldPolicy.DEFAULT_LIFETIME_MILLIS <= 2_000L)
    }
}
