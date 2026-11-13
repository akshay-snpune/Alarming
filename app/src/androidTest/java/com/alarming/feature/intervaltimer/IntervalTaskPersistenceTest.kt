package com.alarming.feature.intervaltimer

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.alarming.feature.reminders.ReminderDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IntervalTaskPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun task(name: String) = IntervalTaskEntity(
        name = name, description = "Independent task", sharedSound = "Soft bell",
        startSound = "Focus tone", completionSound = "Completion chime",
        startAlertEnabled = true, completionAlertEnabled = true, vibrationEnabled = false,
        createdAt = 0, updatedAt = 0,
    )

    private fun slots(count: Int) = (0 until count).map { index ->
        IntervalSlotEntity(0, index, "Stage ${index + 1}", (index + 1) * 60, "Soft bell")
    }

    @Test fun multipleTasksKeepIndependentOrderedSlotsAcrossReopen() = runBlocking {
        val name = "interval-task-persistence-test.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, ReminderDatabase::class.java, name)
            .addMigrations(ReminderDatabase.MIGRATION_1_2, ReminderDatabase.MIGRATION_2_3).build()
        val store = IntervalTaskStore(database)
        try {
            val shortId = store.save(task("Short task"), slots(2))
            val layeredId = store.save(task("Many stages"), slots(7))
            assertNotEquals(shortId, layeredId)
            database.close()
            database = Room.databaseBuilder(context, ReminderDatabase::class.java, name)
                .addMigrations(ReminderDatabase.MIGRATION_1_2, ReminderDatabase.MIGRATION_2_3).build()
            val shortTask = database.intervalTasks().find(shortId)!!
            val layeredTask = database.intervalTasks().find(layeredId)!!
            assertEquals("Short task", shortTask.task.name)
            assertEquals(listOf(0, 1), shortTask.slots.sortedBy { it.position }.map { it.position })
            assertEquals(2, shortTask.slots.size)
            assertEquals(7, layeredTask.slots.size)
            assertEquals(300, layeredTask.slots[4].durationSeconds)
            assertEquals(2, database.intervalTasks().count())
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun updatingTaskReplacesOnlyItsOwnStagesAndDeleteCascades() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).build()
        val store = IntervalTaskStore(database)
        try {
            val first = store.save(task("First"), slots(1))
            val second = store.save(task("Second"), slots(3))
            val savedFirst = database.intervalTasks().find(first)!!.task
            store.save(savedFirst.copy(name = "First updated"), slots(5))
            assertEquals(5, database.intervalTasks().find(first)!!.slots.size)
            assertEquals("Second", database.intervalTasks().find(second)!!.task.name)
            store.delete(first)
            assertNull(database.intervalTasks().find(first))
            assertEquals(3, database.intervalTasks().find(second)!!.slots.size)
        } finally { database.close() }
    }
}