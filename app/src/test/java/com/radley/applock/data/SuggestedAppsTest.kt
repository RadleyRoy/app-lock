package com.radley.applock.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SuggestedAppsTest {

    private fun app(packageName: String, label: String = "App") =
        InstalledApp(packageName = packageName, label = label, isSystem = false)

    @Test
    fun `known social and messaging apps are suggested`() {
        listOf(
            "com.whatsapp",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.snapchat.android",
        ).forEach { assertTrue(SuggestedApps.isSuggested(app(it)), it) }
    }

    @Test
    fun `finance keywords are suggested`() {
        listOf(
            "com.hdfc.bank",
            "net.one97.paytm",
            "com.phonepe.app",
            "com.example.authenticator",
        ).forEach { assertTrue(SuggestedApps.isSuggested(app(it)), it) }
    }

    @Test
    fun `ordinary apps are not suggested`() {
        listOf(
            "com.sec.android.app.popupcalculator",
            "com.spotify.music",
            "com.google.android.calculator",
        ).forEach { assertFalse(SuggestedApps.isSuggested(app(it)), it) }
    }

    @Test
    fun `keyword matching is segment bounded`() {
        // "pay" must match com.paypal.x but not a company called Paypalette, so matching runs
        // per dot-separated segment rather than over the whole string.
        assertTrue(SuggestedApps.isSuggested(app("com.paypal.android.p2pmobile")))
        assertFalse(SuggestedApps.isSuggested(app("com.acmecorp.wallpaperapp")))
    }

    @Test
    fun `matching ignores case in the package name`() {
        assertTrue(SuggestedApps.isSuggested(app("com.Example.Bank")))
    }

    @Test
    fun `partition splits suggested from the rest and keeps everything`() {
        val apps = listOf(
            app("com.whatsapp"),
            app("com.spotify.music"),
            app("com.hdfc.bank"),
            app("com.google.android.calculator"),
        )

        val (suggested, others) = SuggestedApps.partition(apps)

        assertEquals(2, suggested.size)
        assertEquals(2, others.size)
        assertEquals(apps.size, suggested.size + others.size)
    }

    @Test
    fun `label is never used for matching`() {
        // Labels are localised and user-renameable; only the package name is stable.
        assertFalse(SuggestedApps.isSuggested(app("com.acme.notes", label = "My Bank")))
    }
}
