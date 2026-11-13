package com.alarming.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.media.MediaCodecList
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.room.*
import com.alarming.feature.reminders.ReminderDatabase
import com.alarming.feature.reminders.ReminderTone
import com.alarming.feature.reminders.ReminderTones
import com.alarming.feature.settings.AppSettingsStore
import com.alarming.feature.settings.DefaultToneSlot
import com.alarming.feature.settings.tone
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Entity(tableName = "imported_tones")
data class ImportedTone(
    @PrimaryKey val id: String,
    val name: String,
    val filename: String,
    val durationMillis: Long,
)

@Dao
interface ImportedToneDao {
    @Query("SELECT * FROM imported_tones ORDER BY name") fun observe(): kotlinx.coroutines.flow.Flow<List<ImportedTone>>
    @Query("SELECT * FROM imported_tones WHERE id = :id") suspend fun find(id: String): ImportedTone?
    @Insert suspend fun insert(tone: ImportedTone)
    @Query("DELETE FROM imported_tones WHERE id = :id") suspend fun delete(id: String)
}

object ToneImportRules {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_DURATION_MILLIS = 5000L
    val extensions = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")

    fun copyBounded(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "Choose an audio file smaller than 5 MB." }
            output.write(buffer, 0, count)
        }
        require(total > 0) { "This file is empty." }
    }
}

class ToneLibrary(private val context: Context) {
    private val database = ReminderDatabase.get(context)
    private val dao = database.tones()
    private val settings = AppSettingsStore.get(context)
    private val directory = File(context.filesDir, "tones")
    val tones = combine(dao.observe(), settings.hiddenTones) { imported, hidden ->
        ReminderTones.all.filterNot { it.id in hidden } + imported.map { ReminderTone(it.name, 0, "Imported · ${it.durationMillis} ms", it.id) }
    }

    suspend fun import(uri: Uri): ReminderTone = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: throw IllegalArgumentException("This file has no readable name.")
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in ToneImportRules.extensions) { "Choose MP3, WAV, M4A, AAC, OGG, or FLAC audio." }
        require(directory.isDirectory || directory.mkdirs()) { "Unable to create tone storage." }
        val id = "user:${UUID.randomUUID()}"
        val filename = "${id.substringAfter(':')}.$extension"
        val file = File(directory, filename)
        try {
            resolver.openInputStream(uri)?.use { source ->
                file.outputStream().use { destination -> ToneImportRules.copyBounded(source, destination) }
            } ?: throw IllegalArgumentException("This file cannot be read.")
            val metadata = MediaMetadataRetriever()
            val duration = try {
                metadata.setDataSource(file.absolutePath)
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally { metadata.release() }
            require(duration != null && duration in 1..ToneImportRules.MAX_DURATION_MILLIS) { "Choose a playable audio clip up to 5 seconds long." }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                require((0 until extractor.trackCount).any { index ->
                    val format = extractor.getTrackFormat(index)
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true && codecs.findDecoderForFormat(format) != null
                }) { "This audio format cannot be decoded on this device." }
            } finally { extractor.release() }
            val name = displayName.substringBeforeLast('.').take(80).ifBlank { "Imported tone" }
            dao.insert(ImportedTone(id, name, filename, duration))
            ReminderTone(name, 0, "Imported · $duration ms", id)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    suspend fun uri(id: String): Uri? {
        ReminderTones.all.firstOrNull { it.id == id }?.let {
            return Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(it.resourceId)}")
        }
        val tone = dao.find(id) ?: return null
        val file = File(directory, tone.filename)
        if (!file.isFile) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.tones", file)
    }

    suspend fun importedFile(id: String): File? = withContext(Dispatchers.IO) {
        dao.find(id)?.let { File(directory, it.filename) }?.takeIf { it.isFile }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        require(id !in ReminderTones.protectedIds) { "This default tone cannot be deleted." }
        val defaults = settings.settings.first()
        require(DefaultToneSlot.entries.none { defaults.tone(it) == id } &&
            !database.intervalTasks().usesTone(id) && !database.reminders().usesTone(id)) {
            "This tone is in use. Change it in your tasks, reminders and default settings before deleting it."
        }
        if (ReminderTones.all.any { it.id == id }) {
            settings.hideTone(id)
        } else {
            val tone = requireNotNull(dao.find(id)) { "This tone is no longer available." }
            val file = File(directory, tone.filename)
            require(!file.exists() || file.delete()) { "Could not delete the tone file." }
            dao.delete(id)
        }
    }

    suspend fun name(id: String): String = ReminderTones.all.firstOrNull { it.id == id }?.name ?: dao.find(id)?.name ?: "Silent"
}