package com.radley.applock.ui.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PinEntryTest {

    private val now = 1_000L

    private fun PinEntry.type(digits: String, at: Long = now): PinEntry =
        digits.fold(this) { state, c -> state.append(c, at) }

    @Test
    fun `typing digits accumulates them`() {
        assertEquals("123", PinEntry().type("123").digits)
    }

    @Test
    fun `becomes complete at the configured length`() {
        assertFalse(PinEntry().type("123").isComplete)
        assertTrue(PinEntry().type("1234").isComplete, "the 4th digit auto-submits")
    }

    @Test
    fun `ignores extra digits once complete`() {
        val state = PinEntry().type("123456")
        assertEquals("1234", state.digits)
    }

    @Test
    fun `backspace removes the last digit`() {
        assertEquals("12", PinEntry().type("123").backspace(now).digits)
    }

    @Test
    fun `backspace on empty input is a no-op`() {
        assertEquals("", PinEntry().backspace(now).digits)
    }

    @Test
    fun `a keypress after an error starts the next attempt`() {
        val failed = PinEntry().type("1234").withError()
        val next = failed.append('9', now)

        // The first tap after a wrong PIN should enter a digit, not just dismiss the error.
        assertEquals("9", next.digits)
        assertFalse(next.error)
    }

    @Test
    fun `backspace after an error clears the whole attempt`() {
        val failed = PinEntry().type("1234").withError()
        val next = failed.backspace(now)
        assertEquals("", next.digits)
        assertFalse(next.error)
    }

    @Test
    fun `error keeps the digits so the dots can flash before clearing`() {
        assertEquals("1234", PinEntry().type("1234").withError().digits)
    }

    @Test
    fun `input is ignored during cooldown`() {
        val cooling = PinEntry().lockedOut(until = now + 30_000)

        assertTrue(cooling.isLockedOut(now))
        assertEquals("", cooling.type("1234", at = now).digits)
        assertEquals("", cooling.backspace(now).digits)
    }

    @Test
    fun `input resumes once cooldown elapses`() {
        val cooling = PinEntry().lockedOut(until = now + 30_000)
        val after = now + 30_000

        assertFalse(cooling.isLockedOut(after))
        assertEquals("12", cooling.type("12", at = after).digits)
    }

    @Test
    fun `entering cooldown discards whatever was typed`() {
        val state = PinEntry().type("12").lockedOut(until = now + 30_000)
        assertEquals("", state.digits)
    }

    @Test
    fun `a custom pin length is respected`() {
        val six = PinEntry(length = 6)
        assertFalse(six.type("12345").isComplete)
        assertTrue(six.type("123456").isComplete)
    }
}
