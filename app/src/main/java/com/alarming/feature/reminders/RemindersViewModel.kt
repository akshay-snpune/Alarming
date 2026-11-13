package com.alarming.feature.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ReminderStore.get(application)
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    val records = store.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { recover() }

    fun recover() { action { store.recover() } }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); failure.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure.value = "Could not update reminders. Please retry." }
        }
    }

    fun save(record: ReminderRecord, result: (String?) -> Unit) {
        viewModelScope.launch {
            try { store.save(record); result(null) }
            catch (error: IllegalArgumentException) { result(error.message ?: "Check reminder inputs.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result("Could not save this reminder. Please retry.") }
        }
    }

    fun delete(id: Long, result: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                store.delete(id)
                records.first { rows -> rows.none { it.id == id } }
                failure.value = null
                result(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val message = "Could not delete this reminder. Please retry."
                failure.value = message
                result(message)
            }
        }
    }
    fun complete(id: Long) { action { store.complete(id) } }
    fun enable(id: Long, enabled: Boolean, result: (String?) -> Unit) {
        viewModelScope.launch {
            try { store.setEnabled(id, enabled); result(null) }
            catch (error: IllegalArgumentException) { result(error.message ?: "Edit this reminder to choose a future time.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result("Could not update this reminder. Please retry.") }
        }
    }
}