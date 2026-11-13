package com.alarming.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundaryCueTest {
    private val slots = listOf(
        SlotDraft(1, "Warm up", "1", "00", "Soft bell"),
        SlotDraft(2, "Work", "2", "00", "Mobile chirp"),
    )
    private val running = PrototypeState(slots = slots, timerStatus = TimerStatus.RUNNING, isSharedSound = false)

    @Test fun nextStageToneIsDueAtTheCurrentDeadline() {
        assertEquals(ScheduledCue("Mobile chirp", 60_000L), nextBoundaryCue(running, 60_000L))
    }

    @Test fun sharedToneOverridesStageTones() {
        val cue = nextBoundaryCue(running.copy(isSharedSound = true, sharedSound = "Digital beep"), 5_000L)
        assertEquals("Digital beep", cue?.soundId)
    }

    @Test fun finalStageSchedulesCompletionOnlyWhenEnabled() {
        val lastStage = running.copy(activeSlotIndex = 1)
        assertEquals(ScheduledCue("Completion chime", 9_000L), nextBoundaryCue(lastStage, 9_000L))
        assertNull(nextBoundaryCue(lastStage.copy(completionAlertEnabled = false), 9_000L))
    }

    @Test fun noCueWhenNotRunningSilentOrWithoutDeadline() {
        assertNull(nextBoundaryCue(running.copy(timerStatus = TimerStatus.PAUSED), 1_000L))
        assertNull(nextBoundaryCue(running, null))
        val silent = running.copy(slots = slots.map { it.copy(soundName = "Silent") })
        assertNull(nextBoundaryCue(silent, 1_000L))
    }
}
