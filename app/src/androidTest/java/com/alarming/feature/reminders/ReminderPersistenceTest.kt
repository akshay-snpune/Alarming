package com.alarming.feature.reminders

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun record() = ReminderRecord(
        id = 912345, title = "Test water", description = "Test only", repeat = "WATER",
        firstDate = LocalDate.now().toString(), startMinute = 480, endMinute = 1200,
        intervalMinutes = 120, zoneId = "UTC", sound = "Silent", vibration = false,
    )

    @Test fun databaseReopenRetainsAllScheduleFields() = runBlocking {
        val name = "reminder-persistence-test.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, ReminderDatabase::class.java, name).build()
        try {
            val saved = record().copy(nextTrigger = 1893456000000L)
            database.reminders().save(saved)
            database.close()
            database = Room.databaseBuilder(context, ReminderDatabase::class.java, name).build()
            assertEquals(saved, database.reminders().find(saved.id))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun recoveryEditCompleteAndDeletePersistCorrectly() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).build()
        val store = ReminderStore(context, database)
        try {
            store.save(record())
            val saved = database.reminders().find(record().id)!!
            assertTrue(saved.nextTrigger!! > System.currentTimeMillis())
            store.recover()
            assertEquals(saved.nextTrigger, database.reminders().find(saved.id)!!.nextTrigger)
            store.save(saved.copy(title = "Edited water", intervalMinutes = 60))
            assertEquals(60, database.reminders().find(saved.id)!!.intervalMinutes)
            store.complete(saved.id)
            val completed = database.reminders().find(saved.id)!!
            assertTrue(completed.completed)
            assertFalse(completed.enabled)
            assertNull(completed.nextTrigger)
            store.recover()
            assertNull(database.reminders().find(saved.id)!!.nextTrigger)
            store.delete(saved.id)
            assertNull(database.reminders().find(saved.id))
        } finally { store.delete(record().id); database.close() }
    }

    @Test fun duplicateAndStaleAlarmDeliveryCannotAdvanceTwice() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).build()
        val store = ReminderStore(context, database)
        val due = System.currentTimeMillis() - 1000
        val once = record().copy(repeat = "ONCE", nextTrigger = due)
        try {
            database.reminders().save(once)
            store.deliver(once.id, due - 1)
            assertFalse(database.reminders().find(once.id)!!.completed)
            store.deliver(once.id, due)
            val completed = database.reminders().find(once.id)!!
            assertTrue(completed.completed)
            store.deliver(once.id, due)
            assertEquals(completed, database.reminders().find(once.id))
        } finally { store.delete(once.id); database.close() }
    }
}