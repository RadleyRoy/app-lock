package com.radley.latch.lock

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ForegroundAppResolverTest {

    private val own = "com.radley.latch"
    private val resolver = ForegroundAppResolver(ignoredPackages = setOf(own))

    @Test
    fun `empty event window resolves to nothing`() {
        assertNull(resolver.resolve(emptyList()))
    }

    @Test
    fun `resolves the most recent event`() {
        val events = listOf(
            ForegroundEvent("com.whatsapp", 100),
            ForegroundEvent("com.instagram.android", 200),
        )
        assertEquals("com.instagram.android", resolver.resolve(events))
    }

    @Test
    fun `resolves by timestamp not list order`() {
        // UsageStatsManager does not guarantee ordering, so position must not be trusted.
        val events = listOf(
            ForegroundEvent("com.instagram.android", 300),
            ForegroundEvent("com.whatsapp", 100),
            ForegroundEvent("com.spotify.music", 200),
        )
        assertEquals("com.instagram.android", resolver.resolve(events))
    }

    @Test
    fun `ignores our own package`() {
        val events = listOf(
            ForegroundEvent("com.instagram.android", 100),
            ForegroundEvent(own, 200),
        )
        // Our lock screen sitting on top must not read as the user having switched apps.
        assertEquals("com.instagram.android", resolver.resolve(events))
    }

    @Test
    fun `resolves to nothing when every event is ignored`() {
        val events = listOf(ForegroundEvent(own, 100), ForegroundEvent(own, 200))
        assertNull(resolver.resolve(events))
    }

    @Test
    fun `the launcher is a real foreground change and is not filtered`() {
        val events = listOf(
            ForegroundEvent("com.instagram.android", 100),
            ForegroundEvent("com.sec.android.app.launcher", 200),
        )
        assertEquals("com.sec.android.app.launcher", resolver.resolve(events))
    }
}
