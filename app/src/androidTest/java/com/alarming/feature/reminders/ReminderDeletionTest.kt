package com.alarming.feature.reminders

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ReminderDeletionTest {
    @Test fun deletionSuccessWaitsForSharedReminderStateToRemoveWaterSchedule() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val store = ReminderStore.get(application)
        val dao = ReminderDatabase.get(application).reminders()
        val owner = ViewModelStore()
        val model = RemindersViewModel(application)
        instrumentation.runOnMainSync { owner.put("reminder-deletion-test", model) }
        val id = -912346L
        val record = ReminderRecord(
            id = id, title = "Water deletion regression", description = "Test only",
            repeat = "WATER", firstDate = LocalDate.now().toString(),
            startMinute = 0, endMinute = 1439, intervalMinutes = 120,
            zoneId = "UTC", sound = "Silent", vibration = false,
        )
        try {
            store.save(record)
            withTimeout(5_000) { model.records.first { rows -> rows.any { it.id == id } } }
            val result = CompletableDeferred<String?>()
            model.delete(id) { message ->
                if (model.records.value.any { it.id == id }) {
                    result.complete("Deleted reminder is still in the shared Home/Water state.")
                } else {
                    result.complete(message)
                }
            }
            assertNull(withTimeout(5_000) { result.await() })
            assertFalse(model.records.value.any { it.id == id })
            assertNull(dao.find(id))
        } finally {
            store.delete(id)
            instrumentation.runOnMainSync { owner.clear() }
        }
    }
}
