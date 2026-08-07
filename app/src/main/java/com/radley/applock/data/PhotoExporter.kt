package com.radley.applock.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies an intruder photo out of app-private storage and into the phone's gallery.
 *
 * Captures live in `filesDir`, which is private and invisible to the gallery — deliberately, so
 * they never turn up in a photo roll by accident. This is the explicit way out.
 *
 * No storage permission is involved: minSdk 29 means scoped storage, where an app may always
 * insert its own images into MediaStore. The `IS_PENDING` flag keeps the entry hidden from
 * other apps until the bytes are fully written, so a half-copied file is never visible.
 */
class PhotoExporter(context: Context) {

    private val appContext = context.applicationContext

    sealed interface Result {
        data class Saved(val location: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun saveToGallery(source: File, displayName: String): Result =
        withContext(Dispatchers.IO) {
            if (!source.exists()) return@withContext Result.Failed("The photo file is missing")

            val resolver = appContext.contentResolver
            val relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return@withContext Result.Failed("Could not create a gallery entry")

            val copied = runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: error("no output stream")
            }.isSuccess

            if (!copied) {
                // Leaving a pending row behind would be an invisible, undeletable stub.
                runCatching { resolver.delete(uri, null, null) }
                return@withContext Result.Failed("Could not write the photo")
            }

            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }

            Result.Saved(relativePath)
        }

    private companion object {
        const val ALBUM = "AppLock"
    }
}
