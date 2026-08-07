package com.radley.applock.lock

/**
 * How long an unlock lasts.
 *
 * Pair with a [RelockTrigger], which decides *when the clock starts*. The two together are a
 * [RelockRule].
 */
enum class RelockPolicy(val graceMillis: Long) {
    /** The unlock is spent as soon as it is used: the next fresh entry asks again. */
    IMMEDIATELY(0L),
    AFTER_10_SECONDS(10_000L),
    AFTER_30_SECONDS(30_000L),
    AFTER_1_MINUTE(60_000L),
    AFTER_5_MINUTES(300_000L),
    AFTER_15_MINUTES(900_000L),

    /** Stays unlocked until the screen turns off. The default: least nagging, still safe. */
    UNTIL_SCREEN_OFF(Long.MAX_VALUE);

    /**
     * True when the duration is a real countdown. [IMMEDIATELY] and [UNTIL_SCREEN_OFF] behave
     * identically whichever trigger is chosen, so the UI only offers a trigger for these.
     */
    val isTimed: Boolean
        get() = this != IMMEDIATELY && this != UNTIL_SCREEN_OFF

    companion object {
        val DEFAULT = UNTIL_SCREEN_OFF

        fun fromNameOrDefault(name: String?): RelockPolicy =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * When the re-lock countdown starts.
 *
 * [ON_UNLOCK] is the more dependable of the two: its clock runs from the instant the user
 * authenticated, which this app knows exactly. [ON_LEAVING] has to infer when an app was left
 * from window events, which OEM launchers do not emit consistently — see [SessionManager] for
 * how that inference is kept from failing open.
 */
enum class RelockTrigger {
    /** A 1-minute unlock expires 1 minute after unlocking, however long you stay in the app. */
    ON_UNLOCK,

    /** A 1-minute unlock expires 1 minute after you switch away; staying in it keeps it alive. */
    ON_LEAVING;

    companion object {
        val DEFAULT = ON_UNLOCK

        fun fromNameOrDefault(name: String?): RelockTrigger =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * A complete re-lock setting.
 *
 * Neither half means much alone, and they are always read together, so they travel together
 * rather than as two loose parameters that could be passed in the wrong order.
 */
data class RelockRule(
    val trigger: RelockTrigger = RelockTrigger.DEFAULT,
    val policy: RelockPolicy = RelockPolicy.DEFAULT,
) {
    companion object {
        val DEFAULT = RelockRule()
    }
}
