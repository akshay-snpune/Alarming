package com.alarming.feature.intervaltimer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.alarming.audio.ToneImportRules
import com.alarming.audio.ToneLibrary
import com.alarming.feature.reminders.ReminderDatabase
import com.alarming.feature.reminders.ReminderTones
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
private const val MAX_MANIFEST_BYTES = 1024 * 1024
private const val MAX_TASKS = 100
private const val MAX_TONES = 200

internal class TaskArchive(private val context: Context) {
    private val database = ReminderDatabase.get(context)
    private val store = IntervalTaskStore.get(context)
    private val library = ToneLibrary(context)

    suspend fun export(tasks: List<IntervalTaskWithSlots>, output: OutputStream) = withContext(Dispatchers.IO) {
        require(tasks.isNotEmpty() && tasks.size <= MAX_TASKS) { "Select between 1 and $MAX_TASKS tasks." }
        val ids = tasks.flatMap { row ->
            listOf(row.task.sharedSound, row.task.startSound, row.task.completionSound) + row.slots.map { it.soundName }
        }.filter { it.isNotBlank() && it != "Silent" }.distinct()
        require(ids.size <= MAX_TONES) { "Too many different tones in this export." }
        val tones = JSONArray()
        val taskArray = JSONArray()
        tasks.forEach { row ->
            val task = row.task
            val slots = JSONArray()
            row.slots.sortedBy { it.position }.forEach { slot ->
                slots.put(JSONObject().put("name", slot.name).put("duration", slot.durationSeconds).put("sound", slot.soundName))
            }
            taskArray.put(JSONObject()
                .put("name", task.name).put("description", task.description)
                .put("sharedSound", task.sharedSound).put("startSound", task.startSound)
                .put("completionSound", task.completionSound)
                .put("startAlert", task.startAlertEnabled).put("completionAlert", task.completionAlertEnabled)
                .put("vibration", task.vibrationEnabled).put("slots", slots))
        }
        val audio = mutableListOf<Pair<String, ByteArray>>()
        var total = 0
        ids.forEachIndexed { index, id ->
            val bundled = ReminderTones.all.firstOrNull { it.id == id }
            val file = if (bundled == null) library.importedFile(id) else null
            val extension = if (bundled != null) "wav" else file?.extension?.lowercase()
            require(extension in ToneImportRules.extensions) { "A task references an unavailable tone: $id" }
            val uri = library.uri(id) ?: throw IllegalArgumentException("A task references an unavailable tone: $id")
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it, ToneImportRules.MAX_BYTES) }
                ?: throw IllegalArgumentException("Unable to read tone: $id")
            require(bytes.isNotEmpty()) { "A task references an empty tone: $id" }
            total += bytes.size
            require(total <= MAX_ARCHIVE_BYTES) { "The task tones exceed the 32 MB export limit." }
            val path = "tones/$index.$extension"
            tones.put(JSONObject().put("id", id).put("name", library.name(id)).put("path", path))
            audio += path to bytes
        }
        val manifest = JSONObject().put("version", 1).put("tasks", taskArray).put("tones", tones).toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST_BYTES && total + manifest.size <= MAX_ARCHIVE_BYTES) { "The export is too large." }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            audio.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    suspend fun import(input: InputStream): Int = withContext(Dispatchers.IO) {
        val entries = readEntries(input)
        val manifestBytes = requireNotNull(entries.remove("manifest.json")) { "The archive has no manifest." }
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "The archive manifest is too large." }
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        require(manifest.getInt("version") == 1) { "This archive version is not supported." }
        val toneArray = manifest.getJSONArray("tones")
        val tasks = manifest.getJSONArray("tasks")
        require(toneArray.length() <= MAX_TONES && tasks.length() in 1..MAX_TASKS) { "The archive contains too many items." }
        val toneSpecs = (0 until toneArray.length()).map { index ->
            val tone = toneArray.getJSONObject(index)
            val id = tone.getString("id")
            val name = tone.getString("name")
            val path = tone.getString("path")
            require(id.isNotBlank() && id != "Silent" && id.length <= 200 && name.isNotBlank() && name.length <= 200) { "Invalid tone metadata." }
            require(path.matches(Regex("tones/[0-9]+\\.(mp3|wav|m4a|aac|ogg|flac)"))) { "Invalid tone path." }
            val bytes = requireNotNull(entries.remove(path)) { "A tone is missing from the archive." }
            require(bytes.isNotEmpty() && bytes.size <= ToneImportRules.MAX_BYTES) { "Invalid tone size." }
            ArchiveTone(id, name, path.substringAfterLast('.'), bytes)
        }
        require(toneSpecs.map { it.id }.toSet().size == toneSpecs.size && entries.isEmpty()) { "The archive contains duplicate or unexpected entries." }
        val available = toneSpecs.map { it.id }.toSet()
        val rows = (0 until tasks.length()).map { parseTask(tasks.getJSONObject(it), available) }
        val staging = File(context.filesDir, "tones/archive-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Unable to prepare imported tones." }
        val createdFiles = mutableListOf<File>()
        try {
            database.withTransaction {
                val mapped = mutableMapOf<String, String>()
                toneSpecs.forEachIndexed { index, tone ->
                    val safeName = tone.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60).ifBlank { "Tone" }
                    val source = File(staging, "$index-$safeName.${tone.extension}")
                    source.writeBytes(tone.bytes)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.tones", source)
                    val imported = library.import(uri)
                    val file = requireNotNull(library.importedFile(imported.id)) { "Imported tone is unavailable." }
                    createdFiles.add(file)
                    mapped[tone.id] = imported.id
                }
                fun mappedSound(id: String) = if (id.isBlank() || id == "Silent") id else mapped.getValue(id)
                rows.forEach { row ->
                    store.save(
                        row.task.copy(sharedSound = mappedSound(row.task.sharedSound), startSound = mappedSound(row.task.startSound), completionSound = mappedSound(row.task.completionSound)),
                        row.slots.map { it.copy(soundName = mappedSound(it.soundName)) },
                    )
                }
            }
        } catch (error: Throwable) {
            createdFiles.forEach { if (it.exists() && !it.delete()) android.util.Log.e("TaskArchive", "Unable to remove a rolled-back tone: ${it.name}") }
            throw error
        } finally {
            staging.listFiles()?.forEach { if (!it.delete()) android.util.Log.e("TaskArchive", "Unable to remove staging file: ${it.name}") }
            if (!staging.delete()) android.util.Log.e("TaskArchive", "Unable to remove staging directory: ${staging.name}")
        }
        rows.size
    }

    private fun parseTask(json: JSONObject, tones: Set<String>): IntervalTaskWithSlots {
        fun text(key: String, max: Int): String = json.getString(key).also { require(it.length <= max) { "Invalid task $key." } }
        fun sound(key: String): String = text(key, 200).also { require(it.isBlank() || it == "Silent" || it in tones) { "A referenced tone is missing." } }
        val name = text("name", 200)
        require(name.isNotBlank()) { "A task name is missing." }
        val slots = json.getJSONArray("slots")
        require(slots.length() in 1..50) { "A task must have 1 to 50 stages." }
        val parsed = (0 until slots.length()).map { index ->
            val slot = slots.getJSONObject(index)
            val slotName = slot.getString("name")
            val duration = slot.getInt("duration")
            val sound = slot.getString("sound")
            require(slotName.length <= 200 && duration in 1..18_000) { "Invalid task stage." }
            require(sound.isBlank() || sound == "Silent" || sound in tones) { "A stage tone is missing." }
            IntervalSlotEntity(0, index, slotName, duration, sound)
        }
        return IntervalTaskWithSlots(
            IntervalTaskEntity(name = name, description = text("description", 2000), sharedSound = sound("sharedSound"),
                startSound = sound("startSound"), completionSound = sound("completionSound"),
                startAlertEnabled = json.getBoolean("startAlert"), completionAlertEnabled = json.getBoolean("completionAlert"),
                vibrationEnabled = json.getBoolean("vibration"), createdAt = 0, updatedAt = 0),
            parsed,
        )
    }

    private fun readEntries(input: InputStream): MutableMap<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && (entry.name == "manifest.json" || entry.name.matches(Regex("tones/[0-9]+\\.(mp3|wav|m4a|aac|ogg|flac)")))) { "Unexpected archive entry." }
                require(entry.name !in entries && entries.size <= MAX_TONES) { "Duplicate or excessive archive entries." }
                val bytes = readBounded(zip, if (entry.name == "manifest.json") MAX_MANIFEST_BYTES else ToneImportRules.MAX_BYTES)
                total += bytes.size
                require(total <= MAX_ARCHIVE_BYTES) { "The archive is too large." }
                entries[entry.name] = bytes
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "An archive entry is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private data class ArchiveTone(val id: String, val name: String, val extension: String, val bytes: ByteArray)
