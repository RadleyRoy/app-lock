package com.radley.latch.security

import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `same pin hashed twice produces different records`() {
        // If this fails the salt is not random, and identical PINs would be linkable.
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test
    fun `correct pin verifies`() {
        val stored = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", stored))
    }

    @Test
    fun `wrong pin does not verify`() {
        val stored = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("1235", stored))
        assertFalse(PinHasher.verify("", stored))
        assertFalse(PinHasher.verify("12345", stored))
    }

    @Test
    fun `stored record does not contain the pin`() {
        assertFalse(PinHasher.hash("1234").contains("1234"))
    }

    @Test
    fun `malformed stored values fail closed rather than throwing`() {
        val garbage = listOf(
            null,
            "",
            "   ",
            "not-a-record",
            "v1:only-two-parts",
            "v1:!!!not-base64!!!:!!!nope!!!",
            "v2:AAAA:BBBB",          // unknown scheme version
            "v1::",                   // empty salt and hash
            "v1:AAAA:BBBB:extra",
        )
        garbage.forEach { assertFalse(PinHasher.verify("1234", it), "should reject: $it") }
    }

    @Test
    fun `a record from one pin does not verify another`() {
        val a = PinHasher.hash("0000")
        val b = PinHasher.hash("9999")
        assertFalse(PinHasher.verify("9999", a))
        assertFalse(PinHasher.verify("0000", b))
    }
}
