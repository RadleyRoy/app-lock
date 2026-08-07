package com.radley.latch.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class IntruderPolicyTest {

    @Test
    fun `fires exactly on the threshold failure`() {
        val policy = IntruderPolicy(threshold = 3)
        assertFalse(policy.onFailure(), "1st")
        assertFalse(policy.onFailure(), "2nd")
        assertTrue(policy.onFailure(), "3rd should trigger")
    }

    @Test
    fun `does not fire again later in the same streak`() {
        val policy = IntruderPolicy(threshold = 3)
        repeat(3) { policy.onFailure() }

        // Otherwise one fumbled session fills the log with near-identical frames.
        assertFalse(policy.onFailure(), "4th")
        assertFalse(policy.onFailure(), "5th")
    }

    @Test
    fun `success resets the streak so a later run can fire again`() {
        val policy = IntruderPolicy(threshold = 3)
        repeat(3) { policy.onFailure() }
        policy.onSuccess()

        assertEquals(0, policy.failureCount)
        assertFalse(policy.onFailure())
        assertFalse(policy.onFailure())
        assertTrue(policy.onFailure(), "should arm again after a success")
    }

    @Test
    fun `a partial streak followed by success does not carry over`() {
        val policy = IntruderPolicy(threshold = 3)
        policy.onFailure()
        policy.onFailure()
        policy.onSuccess()

        assertFalse(policy.onFailure(), "count restarts at 1, not 3")
    }

    @Test
    fun `threshold of DISABLED never fires`() {
        val policy = IntruderPolicy(threshold = IntruderPolicy.DISABLED)
        repeat(20) { assertFalse(policy.onFailure()) }
    }

    @Test
    fun `threshold of one fires on the first failure`() {
        val policy = IntruderPolicy(threshold = 1)
        assertTrue(policy.onFailure())
    }

    @Test
    fun `failure count keeps rising past the threshold for the cooldown logic`() {
        val policy = IntruderPolicy(threshold = 3)
        repeat(5) { policy.onFailure() }
        assertEquals(5, policy.failureCount)
    }
}
