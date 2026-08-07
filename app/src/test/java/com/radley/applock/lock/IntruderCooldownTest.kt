package com.radley.applock.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The lock screen is finished whenever the user leaves it, so failure state has to live longer
 * than the activity. Otherwise tapping HOME between guesses would clear the cooldown and the
 * attempt counter — which is precisely what someone working through a PIN would do.
 */
class IntruderCooldownTest {

    @Test
    fun `cooldown deadline survives on the policy rather than the screen`() {
        val policy = IntruderPolicy(threshold = 3)
        policy.startCooldown(until = 50_000L)

        assertEquals(50_000L, policy.cooldownUntilMillis)
    }

    @Test
    fun `failure count is not reset by starting a cooldown`() {
        val policy = IntruderPolicy(threshold = 3)
        repeat(5) { policy.onFailure() }
        policy.startCooldown(until = 50_000L)

        assertEquals(5, policy.failureCount)
    }

    @Test
    fun `a successful unlock clears the cooldown`() {
        val policy = IntruderPolicy(threshold = 3)
        repeat(5) { policy.onFailure() }
        policy.startCooldown(until = 50_000L)

        policy.onSuccess()

        assertEquals(0L, policy.cooldownUntilMillis)
        assertEquals(0, policy.failureCount)
    }

    @Test
    fun `threshold is read at failure time so a settings change takes effect`() {
        // The policy outlives any one lock screen, so it cannot capture the threshold once.
        var threshold = IntruderPolicy.DISABLED
        val policy = IntruderPolicy { threshold }

        repeat(5) { assertFalse(policy.onFailure(), "disabled: must never fire") }

        policy.reset()
        threshold = 2

        assertFalse(policy.onFailure())
        assertTrue(policy.onFailure(), "picks up the new threshold without being recreated")
    }
}
