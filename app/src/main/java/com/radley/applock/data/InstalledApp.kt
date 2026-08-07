package com.radley.applock.data

/** One entry in the app picker. Icons are fetched separately so this stays cheap to hold. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * Picks out the apps most people want locked, so the picker can lead with them instead of
 * making you scroll 200 entries to find your bank.
 *
 * Matching is on the package name rather than the display label, because labels are localised
 * and user-renameable while package names are stable.
 */
object SuggestedApps {

    private val EXACT = setOf(
        // Messaging / social
        "com.whatsapp", "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.katana", "com.facebook.orca",
        "com.snapchat.android",
        "org.telegram.messenger",
        "com.twitter.android", "com.x.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.discord",
        "com.google.android.apps.messaging",
        // Photos / files
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.sec.android.app.myfiles",
        "com.google.android.documentsui",
        // Wallets / payments
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "com.paypal.android.p2pmobile",
        // Notes / passwords
        "com.samsung.android.app.notes",
        "com.google.android.keep",
    )

    private val KEYWORDS = listOf(
        "bank", "banking", "upi", "paytm", "phonepe", "gpay", "wallet", "finance",
        "money", "pay", "invest", "trading", "broker", "insurance", "authenticator",
        "password", "vault", "crypto", "wealth",
    )

    fun isSuggested(app: InstalledApp): Boolean {
        if (app.packageName in EXACT) return true
        val pkg = app.packageName.lowercase()
        // Split on dots so "com.paypal.x" matches but "com.company.paypalette" does not.
        val segments = pkg.split('.')
        return KEYWORDS.any { keyword -> segments.any { it.contains(keyword) } }
    }

    fun partition(apps: List<InstalledApp>): Pair<List<InstalledApp>, List<InstalledApp>> =
        apps.partition(::isSuggested)
}
