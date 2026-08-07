package com.radley.latch.ui.lock

/**
 * Immutable state of the PIN pad.
 *
 * Kept free of Compose and Android types so the interaction rules — auto-submit, what a
 * keypress does while an error is showing, what happens during cooldown — are unit tested
 * rather than only observable by tapping a real device.
 */
data class PinEntry(
    val digits: String = "",
    val length: Int = DEFAULT_LENGTH,
    val error: Boolean = false,
    val lockedOutUntil: Long = 0L,
) {

    /** True once enough digits are in. The UI submits on this rather than on an OK button. */
    val isComplete: Boolean get() = digits.length >= length

    fun isLockedOut(now: Long): Boolean = now < lockedOutUntil

    fun append(digit: Char, now: Long): PinEntry = when {
        isLockedOut(now) -> this

        // A keypress after a failure starts the next attempt rather than being swallowed by
        // a "clear the error first" tap.
        error -> copy(digits = digit.toString(), error = false)

        isComplete -> this
        else -> copy(digits = digits + digit)
    }

    fun backspace(now: Long): PinEntry = when {
        isLockedOut(now) -> this
        error -> cleared()
        digits.isEmpty() -> this
        else -> copy(digits = digits.dropLast(1))
    }

    /** Keeps the filled dots so they can flash and shake before the next keypress clears them. */
    fun withError(): PinEntry = copy(error = true)

    fun cleared(): PinEntry = copy(digits = "", error = false)

    fun lockedOut(until: Long): PinEntry =
        copy(digits = "", error = false, lockedOutUntil = until)

    companion object {
        const val DEFAULT_LENGTH = 4
    }
}
