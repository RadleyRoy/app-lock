package com.radley.applock.lock

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Regression tests for a bug that was reproducible on WhatsApp and almost nothing else.
 *
 * `TYPE_WINDOW_STATE_CHANGED` also fires for toasts, dialogs and popups, carrying the package
 * of whoever raised them rather than whatever is on screen. WhatsApp raises those from the
 * background constantly; most apps do not. Acting on them locked apps the user had not opened,
 * and pinned a stale package as "foreground" so genuine re-entry stopped prompting.
 */
class ActivityWindowFilterTest {

    private val whatsapp = "com.whatsapp"

    /** Stands in for the PackageManager: only these components are declared activities. */
    private fun filterWith(vararg activities: String): Pair<ActivityWindowFilter, () -> Int> {
        var lookups = 0
        val declared = activities.toSet()
        val filter = ActivityWindowFilter(
            ActivityWindowFilter.Resolver { pkg, cls ->
                lookups++
                "$pkg/$cls" in declared
            },
        )
        return filter to { lookups }
    }

    @Test
    fun `a declared activity counts as coming to the foreground`() {
        val (filter, _) = filterWith("$whatsapp/com.whatsapp.HomeActivity")
        assertTrue(filter.isForegroundActivity(whatsapp, "com.whatsapp.HomeActivity"))
    }

    @Test
    fun `a toast does not`() {
        // The actual shape of the bug: a background toast reading as an app launch.
        val (filter, _) = filterWith("$whatsapp/com.whatsapp.HomeActivity")
        assertFalse(filter.isForegroundActivity(whatsapp, "android.widget.Toast"))
    }

    @Test
    fun `a bare dialog does not`() {
        val (filter, _) = filterWith("$whatsapp/com.whatsapp.HomeActivity")
        assertFalse(filter.isForegroundActivity(whatsapp, "android.app.Dialog"))
        assertFalse(filter.isForegroundActivity(whatsapp, "android.widget.PopupWindow"))
    }

    @Test
    fun `a dialog-themed activity still counts`() {
        // Themed like a dialog but declared in the manifest, so it is a real foreground change.
        val (filter, _) = filterWith("$whatsapp/com.whatsapp.DialogToastActivity")
        assertTrue(filter.isForegroundActivity(whatsapp, "com.whatsapp.DialogToastActivity"))
    }

    @Test
    fun `an unknown class name fails open`() {
        // Missing a lock is a security failure; an extra lock is an annoyance. When the class
        // name is absent there is no way to tell, so the uncertain case must be accepted.
        val (filter, _) = filterWith()
        assertTrue(filter.isForegroundActivity(whatsapp, null))
        assertTrue(filter.isForegroundActivity(whatsapp, ""))
        assertTrue(filter.isForegroundActivity(whatsapp, "   "))
    }

    @Test
    fun `a blank package is rejected`() {
        val (filter, _) = filterWith()
        assertFalse(filter.isForegroundActivity("", "com.whatsapp.HomeActivity"))
    }

    @Test
    fun `results are cached so the hot path does not hit PackageManager repeatedly`() {
        // This runs for every window change on the device.
        val (filter, lookups) = filterWith("$whatsapp/com.whatsapp.HomeActivity")

        repeat(50) { filter.isForegroundActivity(whatsapp, "com.whatsapp.HomeActivity") }
        repeat(50) { filter.isForegroundActivity(whatsapp, "android.widget.Toast") }

        assertEquals(2, lookups(), "one resolution per distinct component")
    }

    @Test
    fun `the same class in different packages is resolved separately`() {
        val (filter, _) = filterWith("com.instagram.android/android.widget.Toast")

        assertFalse(filter.isForegroundActivity(whatsapp, "android.widget.Toast"))
        assertTrue(filter.isForegroundActivity("com.instagram.android", "android.widget.Toast"))
    }
}
