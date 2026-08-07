package com.radley.latch.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the launcher's view of installed apps.
 *
 * Queries by LAUNCHER intent rather than `getInstalledPackages`, so the picker lists things you
 * can actually open instead of a few hundred invisible system services.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager get() = appContext.packageManager

    /** Icons are bitmaps; holding every one for a 200-app device is a real memory cost. */
    private val iconCache = object : LruCache<String, Drawable>(120) {}

    suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName == appContext.packageName) return@mapNotNull null
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    isSystem = appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            // The launcher query returns one row per launchable activity; an app with several
            // entry points would otherwise appear more than once.
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun iconFor(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName).also { iconCache.put(packageName, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun labelFor(packageName: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        // Uninstalled between the lock firing and the screen drawing; show something sane.
        packageName
    }
}
