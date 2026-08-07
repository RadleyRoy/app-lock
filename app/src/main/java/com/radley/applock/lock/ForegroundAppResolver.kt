package com.radley.applock.lock

/** A foreground transition, decoupled from `android.app.usage.UsageEvents` so it can be faked. */
data class ForegroundEvent(
    val packageName: String,
    val timestampMillis: Long,
)

/**
 * Reduces a window of usage events down to "what is on screen right now".
 *
 * Used only by the fallback detector. Events are not guaranteed to arrive in timestamp order
 * from `UsageStatsManager`, so this picks the latest by timestamp rather than trusting
 * position.
 */
class ForegroundAppResolver(
    /**
     * Packages that must never be reported as the foreground app. Our own lock screen belongs
     * here: it covers the protected app, and treating it as a foreground change would end the
     * protected app's session while the user is still authenticating.
     */
    private val ignoredPackages: Set<String> = emptySet(),
) {

    fun resolve(events: List<ForegroundEvent>): String? =
        events
            .filter { it.packageName !in ignoredPackages }
            .maxByOrNull { it.timestampMillis }
            ?.packageName
}
