package com.alarming.feature.intervaltimer

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.alarming.R
import com.alarming.audio.ToneLibrary
import com.alarming.feature.reminders.ReminderDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskArchiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun roundTripCarriesBundledAndImportedAudioAndPreservesStages() = runBlocking {
        val store = IntervalTaskStore.get(context)
        val library = ToneLibrary(context)
        val dao = ReminderDatabase.get(context).tones()
        val directory = File(context.filesDir, "tones").apply { mkdirs() }
        val source = File(directory, "archive-source-${UUID.randomUUID()}.wav")
        val createdTasks = mutableListOf<Long>()
        val createdTones = mutableListOf<String>()
        try {
            context.resources.openRawResource(R.raw.soft_bell).use { input -> source.outputStream().use { input.copyTo(it) } }
            val original = library.import(FileProvider.getUriForFile(context, "${context.packageName}.tones", source))
            createdTones += original.id
            val task = IntervalTaskEntity(name = "Archive fixture", description = "Round trip", sharedSound = "",
                startSound = "Soft bell", completionSound = original.id, startAlertEnabled = true,
                completionAlertEnabled = false, vibrationEnabled = true, createdAt = 0, updatedAt = 0)
            val id = store.save(task, listOf(
                IntervalSlotEntity(0, 0, "One", 17, original.id),
                IntervalSlotEntity(0, 1, "Two", 23, "Digital beep"),
            ))
            createdTasks += id
            val bytes = ByteArrayOutputStream()
            val archive = TaskArchive(context)
            archive.export(listOf(store.get(id)!!), bytes)
            val before = store.tasks.first().map { it.task.id }.toSet()
            assertEquals(1, archive.import(ByteArrayInputStream(bytes.toByteArray())))
            val restored = store.tasks.first().single { it.task.id !in before }
            createdTasks += restored.task.id
            val sounds = setOf(restored.task.startSound, restored.task.completionSound) + restored.slots.map { it.soundName }
            createdTones += sounds.filter { it.startsWith("user:") }
            assertEquals("Round trip", restored.task.description)
            assertTrue(restored.task.vibrationEnabled)
            assertFalse(restored.task.completionAlertEnabled)
            assertEquals(listOf(17, 23), restored.slots.sortedBy { it.position }.map { it.durationSeconds })
            assertEquals(restored.task.completionSound, restored.slots.single { it.position == 0 }.soundName)
            assertArrayEquals(source.readBytes(), library.importedFile(restored.task.completionSound)!!.readBytes())
            assertArrayEquals(context.resources.openRawResource(R.raw.soft_bell).use { it.readBytes() },
                library.importedFile(restored.task.startSound)!!.readBytes())
        } finally {
            createdTasks.forEach { store.delete(it) }
            createdTones.distinct().forEach { id ->
                dao.find(id)?.let { File(directory, it.filename).delete() }
                dao.delete(id)
            }
            source.delete()
        }
    }

    @Test fun missingAudioAndUnsafeEntriesAreRejectedWithoutCreatingTasks() = runBlocking {
        val store = IntervalTaskStore.get(context)
        val before = store.tasks.first().map { it.task.id }.toSet()
        val archive = TaskArchive(context)
        val fixtures = listOf(
            "manifest.json" to """{"version":1,"tasks":[{}],"tones":[{"id":"x","name":"X","path":"tones/0.wav"}]}""",
            "../escape" to "unsafe",
        )
        fixtures.forEach { (entry, content) ->
            val data = ByteArrayOutputStream()
            ZipOutputStream(data).use { zip ->
                zip.putNextEntry(ZipEntry(entry)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            try { archive.import(ByteArrayInputStream(data.toByteArray())); fail("Invalid archive was accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: ZipException) { }
        }
        assertEquals(before, store.tasks.first().map { it.task.id }.toSet())
    }

    @Test fun invalidAudioRollsBackEarlierTonesAndTasks() = runBlocking {
        val store = IntervalTaskStore.get(context)
        val dao = ReminderDatabase.get(context).tones()
        val beforeTasks = store.tasks.first().map { it.task.id }.toSet()
        val beforeTones = dao.observe().first().map { it.id }.toSet()
        val manifest = """{"version":1,"tasks":[{"name":"Fixture","description":"","sharedSound":"first","startSound":"second","completionSound":"Silent","startAlert":true,"completionAlert":true,"vibration":false,"slots":[{"name":"Stage","duration":5,"sound":"first"}]}],"tones":[{"id":"first","name":"First","path":"tones/0.wav"},{"id":"second","name":"Second","path":"tones/1.wav"}]}"""
        val data = ByteArrayOutputStream()
        ZipOutputStream(data).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("tones/0.wav")); context.resources.openRawResource(R.raw.soft_bell).use { it.copyTo(zip) }; zip.closeEntry()
            zip.putNextEntry(ZipEntry("tones/1.wav")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
        }
        try {
            TaskArchive(context).import(ByteArrayInputStream(data.toByteArray()))
            fail("Invalid audio was accepted")
        } catch (_: IllegalArgumentException) {
        } catch (_: RuntimeException) {
        }
        assertEquals(beforeTasks, store.tasks.first().map { it.task.id }.toSet())
        assertEquals(beforeTones, dao.observe().first().map { it.id }.toSet())
    }
}
