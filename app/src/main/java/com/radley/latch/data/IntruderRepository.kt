package com.radley.latch.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IntruderEvent(
    val id: String,
    val timestampMillis: Long,
    val targetPackage: String,
    val targetLabel: String,
    val photoFileName: String,
    val failedAttempts: Int,
)

/**
 * The intruder log: metadata in DataStore, JPEGs in internal storage.
 *
 * Internal storage specifically — `filesDir` is private to the app and excluded from
 * MediaStore, so captures never turn up in the phone's gallery.
 */
class IntruderRepository(
    context: Context,
    private val settings: SettingsStore,
) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val photoDir: File
        get() = File(appContext.filesDir, "intruders").apply { mkdirs() }

    val events: Flow<List<IntruderEvent>> = settings.intruderEventsJson.map { raw ->
        // A corrupt record must not take down the log screen.
        runCatching { json.decodeFromString<List<IntruderEvent>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.timestampMillis }
    }

    fun photoFile(fileName: String): File = File(photoDir, fileName)

    fun newPhotoFile(): File = File(photoDir, "intruder_${System.currentTimeMillis()}.jpg")

    suspend fun record(event: IntruderEvent) = withContext(Dispatchers.IO) {
        val current = readAll()
        write(current + event)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val current = readAll()
        current.firstOrNull { it.id == id }?.let { photoFile(it.photoFileName).delete() }
        write(current.filterNot { it.id == id })
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        readAll().forEach { photoFile(it.photoFileName).delete() }
        // Sweep the directory too, in case a capture completed but never got a record.
        photoDir.listFiles()?.forEach(File::delete)
        write(emptyList())
    }

    private suspend fun readAll(): List<IntruderEvent> {
        val raw = settings.intruderEventsJson.first()
        return runCatching { json.decodeFromString<List<IntruderEvent>>(raw) }
            .getOrDefault(emptyList())
    }

    private suspend fun write(events: List<IntruderEvent>) {
        settings.setIntruderEventsJson(json.encodeToString(events))
    }
}
