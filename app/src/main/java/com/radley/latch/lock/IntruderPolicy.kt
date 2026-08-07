package com.radley.latch.lock

/**
 * Decides when a wrong-PIN streak warrants a photo.
 *
 * Fires once per streak: hitting the threshold triggers a capture, and further failures stay
 * quiet until a success resets the count. Firing on every subsequent failure would fill the
 * log with near-identical frames of the same person from one fumbled session.
 */
class IntruderPolicy(
    /** Consecutive failures needed to trigger. [DISABLED] turns capture off entirely. */
    private val threshold: Int,
) {

    private var consecutiveFailures = 0
    private var firedThisStreak = false

    val failureCount: Int get() = consecutiveFailures

    /** @return true if this failure should trigger a capture. */
    fun onFailure(): Boolean {
        consecutiveFailures++
        if (threshold == DISABLED) return false
        if (firedThisStreak) return false
        if (consecutiveFailures < threshold) return false

        firedThisStreak = true
        return true
    }

    fun onSuccess() = reset()

    fun reset() {
        consecutiveFailures = 0
        firedThisStreak = false
    }

    companion object {
        const val DISABLED = 0
        const val DEFAULT_THRESHOLD = 3

        /** Wrong attempts before the pad goes into cooldown. */
        const val COOLDOWN_AFTER = 5
        const val COOLDOWN_MILLIS = 30_000L
    }
}
