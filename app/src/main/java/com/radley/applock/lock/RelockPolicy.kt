package com.radley.applock.lock

/**
 * How long a just-unlocked app stays unlocked *after you leave it*.
 *
 * The grace clock deliberately starts when the app leaves the foreground, not when it was
 * unlocked. Otherwise [IMMEDIATELY] would re-lock an app while you were still reading it, and
 * a 30-second grace would expire mid-scroll.
 */
enum class RelockPolicy(val graceMillis: Long) {
    /** Re-locks the moment you switch away. */
    IMMEDIATELY(0L),
    AFTER_10_SECONDS(10_000L),
    AFTER_30_SECONDS(30_000L),
    AFTER_1_MINUTE(60_000L),
    AFTER_5_MINUTES(300_000L),

    /** Stays unlocked until the screen turns off. The default: least nagging, still safe. */
    UNTIL_SCREEN_OFF(Long.MAX_VALUE);

    companion object {
        val DEFAULT = UNTIL_SCREEN_OFF

        fun fromNameOrDefault(name: String?): RelockPolicy =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
