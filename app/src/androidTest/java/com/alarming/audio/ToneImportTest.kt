package com.alarming.audio

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.alarming.R
import com.alarming.feature.reminders.ReminderDatabase
import com.alarming.feature.intervaltimer.IntervalSlotEntity
import com.alarming.feature.intervaltimer.IntervalTaskEntity
import com.alarming.feature.intervaltimer.IntervalTaskStore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToneImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun copiedToneSurvivesSourceRemovalAndHasReadableContentUri() = runBlocking {
        val directory = File(context.filesDir, "tones").apply { mkdirs() }
        val source = File(directory, "fixture-${UUID.randomUUID()}.wav")
        context.resources.openRawResource(R.raw.soft_bell).use { input -> source.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.tones", source)
        val library = ToneLibrary(context)
        var imported: com.alarming.feature.reminders.ReminderTone? = null
        try {
            imported = library.import(uri)
            source.delete()
            val reloaded = ToneLibrary(context)
            val copyUri = reloaded.uri(imported.id)!!
            assertEquals("content", copyUri.scheme)
            assertEquals(imported.name, reloaded.name(imported.id))
            context.contentResolver.openInputStream(copyUri)!!.use { assertEquals('R'.code, it.read()) }
            val player = MediaPlayer()
            try { player.setDataSource(context, copyUri); player.prepare(); assertEquals(2000, player.duration) }
            finally { player.release() }
        } finally {
            source.delete()
            imported?.let { tone ->
                val dao = ReminderDatabase.get(context).tones()
                dao.find(tone.id)?.let { File(directory, it.filename).delete() }
                dao.delete(tone.id)
            }
        }
    }

    @Test fun corruptAudioIsRejectedAndCopyIsRemoved() = runBlocking {
        val directory = File(context.filesDir, "tones").apply { mkdirs() }
        val source = File(directory, "bad-${UUID.randomUUID()}.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val before = directory.list()!!.toSet()
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.tones", source)
            try { ToneLibrary(context).import(uri); fail("Corrupt audio must not be imported") }
            catch (_: IllegalArgumentException) { }
            catch (_: RuntimeException) { }
            assertEquals(before, directory.list()!!.toSet())
        } finally { source.delete() }
    }

    @Test fun fiveSecondClipIsAcceptedAndLongerClipIsRejected() = runBlocking {
        val directory = File(context.filesDir, "tones").apply { mkdirs() }
        val library = ToneLibrary(context)
        var importedId: String? = null
        val sources = mutableListOf<File>()
        try {
            for (duration in listOf(5000, 5100)) {
                val samples = 8000 * duration / 1000
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
                    putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
                    putShort(2); putShort(16); put("data".toByteArray()); putInt(samples * 2)
                }.array()
                val source = File(directory, "duration-${UUID.randomUUID()}.wav")
                sources += source
                source.outputStream().use { it.write(header); it.write(ByteArray(samples * 2)) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.tones", source)
                if (duration == 5000) {
                    importedId = library.import(uri).id
                } else {
                    try { library.import(uri); fail("A clip over five seconds must be rejected") }
                    catch (_: IllegalArgumentException) { }
                }

                @Test fun protectedAndReferencedTonesCannotBeDeleted() = runBlocking {
                    val directory = File(context.filesDir, "tones").apply { mkdirs() }
                    val source = File(directory, "used-${UUID.randomUUID()}.wav")
                    context.resources.openRawResource(R.raw.soft_bell).use { input -> source.outputStream().use { input.copyTo(it) } }
                    val library = ToneLibrary(context)
                    val store = IntervalTaskStore.get(context)
                    var id: String? = null
                    var taskId: Long? = null
                    try {
                        try { library.delete("Soft bell"); fail("Protected tone was deleted") }
                        catch (_: IllegalArgumentException) { }
                        id = library.import(FileProvider.getUriForFile(context, "${context.packageName}.tones", source)).id
                        taskId = store.save(
                            IntervalTaskEntity(name = "Tone deletion fixture", description = "", sharedSound = id,
                                startSound = "Soft bell", completionSound = "Completion chime", startAlertEnabled = true,
                                completionAlertEnabled = true, vibrationEnabled = false, createdAt = 0, updatedAt = 0),
                            listOf(IntervalSlotEntity(0, 0, "Stage", 10, id)),
                        )
                        try { library.delete(id); fail("Referenced tone was deleted") }
                        catch (_: IllegalArgumentException) { }
                        assertNotNull(library.importedFile(id))
                    } finally {
                        taskId?.let { store.delete(it) }
                        id?.let { library.delete(it) }
                        source.delete()
                    }
                }
            }
        } finally {
            sources.forEach { it.delete() }
            importedId?.let { library.delete(it) }
        }
    }

    @Test fun versionOneMigrationPreservesReminderAndCreatesToneTable() = runBlocking {
        val name = "tone-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, repeat TEXT NOT NULL, firstDate TEXT NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, intervalMinutes INTEGER NOT NULL, zoneId TEXT NOT NULL, sound TEXT NOT NULL, vibration INTEGER NOT NULL, enabled INTEGER NOT NULL, completed INTEGER NOT NULL, nextTrigger INTEGER)")
            val values = ContentValues().apply {
                put("id", 1); put("title", "Existing water"); put("description", "Keep me")
                put("repeat", "WATER"); put("firstDate", "2026-11-01")
                put("startMinute", 480); put("endMinute", 1200); put("intervalMinutes", 120)
                put("zoneId", "UTC"); put("sound", "Soft bell"); put("vibration", 0)
                put("enabled", 1); put("completed", 0); put("nextTrigger", 1893456000000L)
            }
            old.insertOrThrow("reminders", null, values)
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, ReminderDatabase::class.java, name)
            .addMigrations(ReminderDatabase.MIGRATION_1_2, ReminderDatabase.MIGRATION_2_3, ReminderDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Existing water", migrated.reminders().find(1)!!.title)
            assertEquals(1893456000000L, migrated.reminders().find(1)!!.nextTrigger)
            migrated.tones().insert(ImportedTone("test", "Fixture", "fixture.wav", 2000))
            assertEquals("Fixture", migrated.tones().find("test")!!.name)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}