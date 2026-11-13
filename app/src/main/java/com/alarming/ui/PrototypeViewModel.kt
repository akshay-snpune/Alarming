package com.alarming.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarming.feature.intervaltimer.IntervalSlotEntity
import com.alarming.feature.intervaltimer.IntervalTaskEntity
import com.alarming.feature.intervaltimer.IntervalTaskStore
import com.alarming.feature.intervaltimer.IntervalTaskWithSlots
import com.alarming.feature.settings.AppSettings
import com.alarming.feature.settings.AppSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val MAX_SLOT_SECONDS = 5 * 60 * 60
const val MAX_SLOTS_PER_TASK = 50

private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_TICK_MILLIS = 250L

internal enum class AppRoute {
    HOME,
    EDITOR,
    TIMER,
    SOUNDS,
    COUNTER,
}

internal enum class AppDestination {
    HOME,
    REMINDERS,
    WATER,
    STOPWATCH,
    SETTINGS,
}

internal enum class TimerStatus {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
}

internal enum class SoundTarget {
    SHARED,
    SLOT,
    START,
    COMPLETION,
}

internal data class SlotDraft(
    val id: Int,
    val name: String,
    val minutesText: String,
    val secondsText: String,
    val soundName: String,
) {
    val durationSeconds: Int?
        get() {
            val minutes = minutesText.toLongOrNull() ?: return null
            val seconds = secondsText.toLongOrNull() ?: return null
            if (minutes < 0 || seconds !in 0..59) return null
            val total = minutes * 60 + seconds
            return total.toInt().takeIf { total in 1..MAX_SLOT_SECONDS.toLong() }
        }
}

internal data class SavedIntervalTask(
    val id: Long,
    val name: String,
    val description: String,
    val slots: List<SlotDraft>,
) {
    val totalDurationSeconds: Int get() = slots.sumOf { it.durationSeconds ?: 0 }
}

internal data class TimerCue(val soundId: String)

/** A boundary tone that the audio thread starts as soon as `SystemClock.elapsedRealtime()` reaches [atElapsedRealtime]. */
internal data class ScheduledCue(val soundId: String, val atElapsedRealtime: Long)

internal fun stageTone(state: PrototypeState, index: Int): String =
    if (state.isSharedSound) state.sharedSound else state.slots[index].soundName

/** The cue for the end of the active stage: the next stage's tone, or the completion tone after the final stage. */
internal fun nextBoundaryCue(state: PrototypeState, deadlineElapsed: Long?): ScheduledCue? {
    if (state.timerStatus != TimerStatus.RUNNING || deadlineElapsed == null) return null
    val nextIndex = state.activeSlotIndex + 1
    val soundId = when {
        nextIndex <= state.slots.lastIndex -> stageTone(state, nextIndex)
        state.completionAlertEnabled -> state.completionSound
        else -> return null
    }
    return soundId.takeIf { it.isNotBlank() && it != "Silent" }?.let { ScheduledCue(it, deadlineElapsed) }
}

internal data class PrototypeState(
    val route: AppRoute = AppRoute.HOME,
    val destination: AppDestination = AppDestination.HOME,
    val currentTaskId: Long? = null,
    val savedTasks: List<SavedIntervalTask> = emptyList(),
    val taskSaveError: String? = null,
    val taskName: String = "",
    val description: String = "",
    val slots: List<SlotDraft> = listOf(SlotDraft(1, "Stage 1", "5", "00", "Soft bell")),
    val isSharedSound: Boolean = true,
    val sharedSound: String = "Soft bell",
    val startSound: String = "Focus tone",
    val completionSound: String = "Completion chime",
    val startAlertEnabled: Boolean = true,
    val completionAlertEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val soundTargetSlotId: Int? = null,
    val soundTarget: SoundTarget = SoundTarget.SHARED,
    val counterValue: Int = 12,
    val timerStatus: TimerStatus = TimerStatus.READY,
    val activeSlotIndex: Int = 0,
    val remainingMillis: Long = 0L,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsedMillis: Long = 0L,
    val stopwatchLaps: List<Long> = emptyList(),
) {
    val totalDurationSeconds: Int
        get() = slots.sumOf { it.durationSeconds ?: 0 }

    val taskIsValid: Boolean
        get() = taskName.isNotBlank() &&
            slots.isNotEmpty() &&
            slots.size <= MAX_SLOTS_PER_TASK &&
            slots.all { it.durationSeconds != null }
}

internal class PrototypeViewModel(application: Application) : AndroidViewModel(application) {
    private val taskStore = IntervalTaskStore.get(application)
    private var defaults = AppSettings()
    private val mutableState = MutableStateFlow(PrototypeState())
    val state: StateFlow<PrototypeState> = mutableState.asStateFlow()
    private val timerCueChannel = Channel<TimerCue>(Channel.BUFFERED)
    val timerCues = timerCueChannel.receiveAsFlow()
    private val mutableUpcomingCue = MutableStateFlow<ScheduledCue?>(null)
    /** Automatic stage-boundary and completion cues, played on time by the audio thread rather than the UI tick. */
    val upcomingCue: StateFlow<ScheduledCue?> = mutableUpcomingCue.asStateFlow()

    private var nextSlotId = 2
    private var timerDeadlineElapsed: Long? = null
    private var pausedRemainingMillis = 0L
    private var stopwatchStartedElapsed = 0L
    private var stopwatchAccumulatedMillis = 0L

    init {
        viewModelScope.launch {
            AppSettingsStore.get(application).settings.collect { defaults = it }
        }
        viewModelScope.launch {
            taskStore.seedIfEmpty()
            taskStore.tasks.collect { rows ->
                mutableState.update { current -> current.copy(savedTasks = rows.map(::toSavedTask)) }
            }
        }
    }

    private fun toSavedTask(row: IntervalTaskWithSlots) = SavedIntervalTask(
        id = row.task.id,
        name = row.task.name,
        description = row.task.description,
        slots = row.slots.sortedBy { it.position }.mapIndexed { index, slot ->
            SlotDraft(index + 1, slot.name, (slot.durationSeconds / 60).toString(), (slot.durationSeconds % 60).toString().padStart(2, '0'), slot.soundName)
        },
    )

    fun selectDestination(destination: AppDestination) {
        mutableState.update { it.copy(destination = destination, route = AppRoute.HOME) }
        if (destination == AppDestination.STOPWATCH) refreshStopwatch()
    }

    fun openCounter() {
        mutableState.update { it.copy(route = AppRoute.COUNTER, destination = AppDestination.HOME) }
    }

    fun openEditor() {
        nextSlotId = 2
        mutableState.update { current ->
            current.copy(
                route = AppRoute.EDITOR,
                destination = AppDestination.HOME,
                currentTaskId = null,
                taskName = "",
                description = "",
                slots = listOf(SlotDraft(1, "Stage 1", "5", "00", defaults.defaultStageTone)),
                isSharedSound = true,
                sharedSound = defaults.defaultStageTone,
                startSound = defaults.defaultStartTone,
                completionSound = defaults.defaultCompletionTone,
                startAlertEnabled = true,
                completionAlertEnabled = true,
                vibrationEnabled = defaults.defaultVibration,
                taskSaveError = null,
            )
        }
    }

    private fun loadTask(row: IntervalTaskWithSlots) {
        val slots = row.slots.sortedBy { it.position }.mapIndexed { index, slot ->
            SlotDraft(index + 1, slot.name, (slot.durationSeconds / 60).toString(), (slot.durationSeconds % 60).toString().padStart(2, '0'), slot.soundName)
        }
        nextSlotId = (slots.maxOfOrNull { it.id } ?: 0) + 1
        mutableState.update { current ->
            current.copy(
                currentTaskId = row.task.id,
                taskName = row.task.name,
                description = row.task.description,
                slots = slots,
                sharedSound = row.task.sharedSound.ifBlank { current.sharedSound },
                isSharedSound = row.task.sharedSound.isNotBlank(),
                startSound = row.task.startSound,
                completionSound = row.task.completionSound,
                startAlertEnabled = row.task.startAlertEnabled,
                completionAlertEnabled = row.task.completionAlertEnabled,
                vibrationEnabled = row.task.vibrationEnabled,
                taskSaveError = null,
            )
        }
    }

    fun editTask(taskId: Long) {
        viewModelScope.launch {
            val row = taskStore.get(taskId) ?: return@launch
            loadTask(row)
            mutableState.update { it.copy(route = AppRoute.EDITOR, destination = AppDestination.HOME) }
        }
    }

    fun startSavedTask(taskId: Long) {
        viewModelScope.launch {
            val row = taskStore.get(taskId) ?: return@launch
            loadTask(row)
            startTimerNow()
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { taskStore.delete(taskId) }
    }

    fun updateTaskName(value: String) {
        mutableState.update { it.copy(taskName = value.take(60)) }
    }

    fun updateDescription(value: String) {
        mutableState.update { it.copy(description = value.take(240)) }
    }

    fun updateSlotName(slotId: Int, value: String) {
        mutableState.update { current ->
            current.copy(slots = current.slots.map { slot ->
                if (slot.id == slotId) slot.copy(name = value.take(48)) else slot
            })
        }
    }

    fun updateSlotDuration(slotId: Int, minutes: String? = null, seconds: String? = null) {
        mutableState.update { current ->
            current.copy(slots = current.slots.map { slot ->
                if (slot.id == slotId) {
                    slot.copy(
                        minutesText = minutes?.filter(Char::isDigit)?.take(3) ?: slot.minutesText,
                        secondsText = seconds?.filter(Char::isDigit)?.take(2) ?: slot.secondsText,
                    )
                } else {
                    slot
                }
            })
        }
    }

    fun addSlot() {
        mutableState.update { current ->
            if (current.slots.size >= MAX_SLOTS_PER_TASK) return@update current
            current.copy(
                slots = current.slots + SlotDraft(
                    id = nextSlotId++,
                    name = "New stage",
                    minutesText = "5",
                    secondsText = "00",
                    soundName = current.sharedSound,
                ),
            )
        }
    }

    fun removeSlot(slotId: Int) {
        mutableState.update { current ->
            if (current.slots.size <= 1) return@update current
            current.copy(slots = current.slots.filterNot { it.id == slotId })
        }
    }

    fun moveSlot(slotId: Int, offset: Int) {
        mutableState.update { current ->
            val index = current.slots.indexOfFirst { it.id == slotId }
            val target = index + offset
            if (index < 0 || target !in current.slots.indices) return@update current
            val reordered = current.slots.toMutableList()
            val item = reordered.removeAt(index)
            reordered.add(target, item)
            current.copy(slots = reordered)
        }
    }

    fun setSharedSoundEnabled(enabled: Boolean) {
        mutableState.update { it.copy(isSharedSound = enabled) }
    }

    fun setStartAlertEnabled(enabled: Boolean) {
        mutableState.update { it.copy(startAlertEnabled = enabled) }
    }

    fun setCompletionAlertEnabled(enabled: Boolean) {
        mutableState.update { it.copy(completionAlertEnabled = enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        mutableState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun openSoundPicker(slotId: Int? = null, target: SoundTarget? = null) {
        val resolvedTarget = target ?: if (slotId == null) SoundTarget.SHARED else SoundTarget.SLOT
        mutableState.update {
            it.copy(route = AppRoute.SOUNDS, soundTargetSlotId = slotId, soundTarget = resolvedTarget)
        }
    }

    fun chooseSound(soundName: String) {
        mutableState.update { current ->
            when (current.soundTarget) {
                SoundTarget.SHARED -> current.copy(sharedSound = soundName, route = AppRoute.EDITOR)
                SoundTarget.START -> current.copy(startSound = soundName, route = AppRoute.EDITOR)
                SoundTarget.COMPLETION -> current.copy(completionSound = soundName, route = AppRoute.EDITOR)
                SoundTarget.SLOT -> current.copy(
                    slots = current.slots.map { slot ->
                        if (slot.id == current.soundTargetSlotId) slot.copy(soundName = soundName) else slot
                    },
                    route = AppRoute.EDITOR,
                )
            }
        }
    }

    fun saveTask() {
        if (!mutableState.value.taskIsValid) return
        viewModelScope.launch {
            try {
                persistCurrentTask()
                mutableState.update { it.copy(route = AppRoute.HOME, destination = AppDestination.HOME, taskSaveError = null) }
            } catch (error: Exception) {
                mutableState.update { it.copy(taskSaveError = error.message ?: "Unable to save task.") }
            }
        }
    }

    fun startTimer() {
        if (!mutableState.value.taskIsValid) return
        if (mutableState.value.route == AppRoute.EDITOR) {
            viewModelScope.launch {
                try { persistCurrentTask(); startTimerNow() }
                catch (error: Exception) { mutableState.update { it.copy(taskSaveError = error.message ?: "Unable to save task.") } }
            }
        } else startTimerNow()
    }

    private suspend fun persistCurrentTask() {
        val current = mutableState.value
        check(current.taskIsValid) { "Add a task name and valid stage durations." }
        val now = System.currentTimeMillis()
        val id = taskStore.save(
            IntervalTaskEntity(
                id = current.currentTaskId ?: 0,
                name = current.taskName.trim(),
                description = current.description.trim(),
                sharedSound = if (current.isSharedSound) current.sharedSound else "",
                startSound = current.startSound,
                completionSound = current.completionSound,
                startAlertEnabled = current.startAlertEnabled,
                completionAlertEnabled = current.completionAlertEnabled,
                vibrationEnabled = current.vibrationEnabled,
                createdAt = 0,
                updatedAt = now,
            ),
            current.slots.mapIndexed { index, slot ->
                IntervalSlotEntity(0, index, slot.name.trim().ifBlank { "Stage ${index + 1}" }, slot.durationSeconds!!, slot.soundName)
            },
        )
        mutableState.update { it.copy(currentTaskId = id) }
    }

    private fun startTimerNow() {
        val current = mutableState.value
        if (!current.taskIsValid) return
        val now = SystemClock.elapsedRealtime()
        val firstSlotMillis = current.slots.first().durationSeconds!!.toLong() * MILLIS_PER_SECOND
        timerDeadlineElapsed = now + firstSlotMillis
        pausedRemainingMillis = firstSlotMillis
        mutableState.update {
            it.copy(
                route = AppRoute.TIMER,
                destination = AppDestination.HOME,
                timerStatus = TimerStatus.RUNNING,
                activeSlotIndex = 0,
                remainingMillis = firstSlotMillis,
            )
        }
        if (current.startAlertEnabled) emitTimerCue(current.startSound)
        publishUpcomingCue()
    }

    private fun emitTimerCue(soundId: String) {
        if (soundId.isNotBlank() && soundId != "Silent") timerCueChannel.trySend(TimerCue(soundId))
    }

    private fun publishUpcomingCue() {
        mutableUpcomingCue.value = nextBoundaryCue(mutableState.value, timerDeadlineElapsed)
    }

    fun refreshTimer() {
        val current = mutableState.value
        if (current.timerStatus != TimerStatus.RUNNING) return
        var index = current.activeSlotIndex
        var deadline = timerDeadlineElapsed ?: return
        val now = SystemClock.elapsedRealtime()

        while (now >= deadline && index < current.slots.lastIndex) {
            index += 1
            deadline += current.slots[index].durationSeconds!!.toLong() * MILLIS_PER_SECOND
        }

        // Boundary tones are not emitted here: the audio thread already started them from upcomingCue.
        if (now >= deadline) {
            timerDeadlineElapsed = null
            mutableState.update {
                it.copy(timerStatus = TimerStatus.COMPLETED, activeSlotIndex = it.slots.lastIndex, remainingMillis = 0L)
            }
            publishUpcomingCue()
            return
        }

        timerDeadlineElapsed = deadline
        mutableState.update {
            it.copy(
                activeSlotIndex = index,
                remainingMillis = (deadline - now).coerceAtLeast(0L),
            )
        }
        if (index > current.activeSlotIndex) publishUpcomingCue()
    }

    /** Time until the countdown display changes or the current stage ends, so boundaries are handled on time. */
    fun millisUntilNextTick(): Long {
        val deadline = timerDeadlineElapsed ?: return MAX_TICK_MILLIS
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (remaining <= 0L) return 0L
        val toSecondEdge = remaining % MILLIS_PER_SECOND
        return (if (toSecondEdge == 0L) MILLIS_PER_SECOND else toSecondEdge).coerceAtMost(MAX_TICK_MILLIS)
    }

    fun pauseTimer() {
        refreshTimer()
        val current = mutableState.value
        if (current.timerStatus != TimerStatus.RUNNING) return
        pausedRemainingMillis = current.remainingMillis
        timerDeadlineElapsed = null
        mutableState.update { it.copy(timerStatus = TimerStatus.PAUSED) }
        publishUpcomingCue()
    }

    fun resumeTimer() {
        if (mutableState.value.timerStatus != TimerStatus.PAUSED) return
        val remaining = pausedRemainingMillis.coerceAtLeast(1L)
        timerDeadlineElapsed = SystemClock.elapsedRealtime() + remaining
        mutableState.update { it.copy(timerStatus = TimerStatus.RUNNING, remainingMillis = remaining) }
        publishUpcomingCue()
    }

    fun skipSlot() {
        refreshTimer()
        val current = mutableState.value
        if (current.timerStatus !in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED)) return
        if (current.activeSlotIndex >= current.slots.lastIndex) {
            timerDeadlineElapsed = null
            mutableState.update { it.copy(timerStatus = TimerStatus.COMPLETED, remainingMillis = 0L) }
            publishUpcomingCue()
            return
        }
        val nextIndex = current.activeSlotIndex + 1
        val duration = current.slots[nextIndex].durationSeconds!!.toLong() * MILLIS_PER_SECOND
        emitTimerCue(stageTone(current, nextIndex))
        if (current.timerStatus == TimerStatus.RUNNING) {
            timerDeadlineElapsed = SystemClock.elapsedRealtime() + duration
        } else {
            pausedRemainingMillis = duration
        }
        mutableState.update {
            it.copy(activeSlotIndex = nextIndex, remainingMillis = duration)
        }
        publishUpcomingCue()
    }

    fun restartSlot() {
        val current = mutableState.value
        if (current.timerStatus !in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED)) return
        val duration = current.slots[current.activeSlotIndex].durationSeconds!!.toLong() * MILLIS_PER_SECOND
        if (current.timerStatus == TimerStatus.RUNNING) {
            timerDeadlineElapsed = SystemClock.elapsedRealtime() + duration
        } else {
            pausedRemainingMillis = duration
        }
        mutableState.update { it.copy(remainingMillis = duration) }
        publishUpcomingCue()
    }

    fun stopTimer() {
        timerDeadlineElapsed = null
        pausedRemainingMillis = 0L
        mutableState.update {
            it.copy(route = AppRoute.HOME, destination = AppDestination.HOME, timerStatus = TimerStatus.READY, activeSlotIndex = 0, remainingMillis = 0L)
        }
        publishUpcomingCue()
    }

    fun incrementCounter() {
        mutableState.update { it.copy(counterValue = it.counterValue + 1) }
    }

    fun decrementCounter() {
        mutableState.update { it.copy(counterValue = it.counterValue - 1) }
    }

    fun resetCounter() {
        mutableState.update { it.copy(counterValue = 0) }
    }

    fun startStopwatch() {
        if (mutableState.value.stopwatchRunning) return
        stopwatchStartedElapsed = SystemClock.elapsedRealtime()
        mutableState.update { it.copy(stopwatchRunning = true) }
    }

    fun pauseStopwatch() {
        if (!mutableState.value.stopwatchRunning) return
        stopwatchAccumulatedMillis += SystemClock.elapsedRealtime() - stopwatchStartedElapsed
        mutableState.update { it.copy(stopwatchRunning = false, stopwatchElapsedMillis = stopwatchAccumulatedMillis) }
    }

    fun refreshStopwatch() {
        if (!mutableState.value.stopwatchRunning) return
        val elapsed = stopwatchAccumulatedMillis + SystemClock.elapsedRealtime() - stopwatchStartedElapsed
        mutableState.update { it.copy(stopwatchElapsedMillis = elapsed) }
    }

    fun resetStopwatch() {
        stopwatchStartedElapsed = 0L
        stopwatchAccumulatedMillis = 0L
        mutableState.update { it.copy(stopwatchRunning = false, stopwatchElapsedMillis = 0L, stopwatchLaps = emptyList()) }
    }

    fun recordLap() {
        refreshStopwatch()
        mutableState.update { it.copy(stopwatchLaps = listOf(it.stopwatchElapsedMillis) + it.stopwatchLaps) }
    }

    fun goBack() {
        mutableState.update { current ->
            when (current.route) {
                AppRoute.SOUNDS -> current.copy(route = AppRoute.EDITOR, soundTargetSlotId = null, soundTarget = SoundTarget.SHARED)
                AppRoute.EDITOR -> current.copy(route = AppRoute.HOME)
                AppRoute.TIMER -> current.copy(route = AppRoute.HOME, timerStatus = TimerStatus.READY)
                AppRoute.COUNTER -> current.copy(route = AppRoute.HOME)
                AppRoute.HOME -> current
            }
        }
        publishUpcomingCue()
    }
}
