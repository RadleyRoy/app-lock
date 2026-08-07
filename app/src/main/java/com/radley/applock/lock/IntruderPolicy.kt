package com.radley.applock.lock

/**
 * Decides when a wrong-PIN streak warrants a photo.
 *
 * Fires once per streak: hitting the threshold triggers a capture, and further failures stay
 * quiet until a success resets the count. Firing on every subsequent failure would fill the
 * log with near-identical frames of the same person from one fumbled session.
 */
class IntruderPolicy(
    /**
     * Consecutive failures needed to trigger. [DISABLED] turns capture off entirely.
     *
     * A supplier rather than a value because this object outlives any one lock screen, so a
     * change in Settings has to take effect without recreating it.
     */
    private val threshold: () -> Int,
) {

    constructor(threshold: Int) : this({ threshold })

    private var consecutiveFailures = 0
    private var firedThisStreak = false

    /**
     * When the PIN pad is frozen until, or 0. Held here rather than in the activity because the
     * activity is finished whenever the user leaves — otherwise tapping HOME would clear the
     * cooldown and the failure count, which is exactly the move someone guessing a PIN would
     * make.
     */
    var cooldownUntilMillis: Long = 0L
        private set

    fun startCooldown(until: Long) {
        cooldownUntilMillis = until
    }

    val failureCount: Int get() = consecutiveFailures

    /** @return true if this failure should trigger a capture. */
    fun onFailure(): Boolean {
        consecutiveFailures++
        val limit = threshold()
        if (limit == DISABLED) return false
        if (firedThisStreak) return false
        if (consecutiveFailures < limit) return false

        firedThisStreak = true
        return true
    }

    fun onSuccess() = reset()

    fun reset() {
        consecutiveFailures = 0
        firedThisStreak = false
        cooldownUntilMillis = 0L
    }

    companion object {
        const val DISABLED = 0
        const val DEFAULT_THRESHOLD = 3

        /** Wrong attempts before the pad goes into cooldown. */
        const val COOLDOWN_AFTER = 5
        const val COOLDOWN_MILLIS = 30_000L
    }
}
