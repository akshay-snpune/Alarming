package com.alarming.ui

import android.app.Application
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerToneCueTest {
    @Test fun startAndAutomaticBoundaryCuesUseConfiguredToneIds() = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model = PrototypeViewModel(application)
        model.updateTaskName("Tone transition test")
        val firstSlot = model.state.value.slots.first().id
        model.updateSlotDuration(firstSlot, minutes = "0", seconds = "01")
        model.addSlot()
        val secondSlot = model.state.value.slots.last().id
        model.updateSlotDuration(secondSlot, minutes = "0", seconds = "01")
        model.setSharedSoundEnabled(false)
        model.openSoundPicker(slotId = secondSlot)
        model.chooseSound("Mobile chirp")
        model.goBack()

        model.startTimer()
        assertEquals("Focus tone", withTimeout(1_000) { model.timerCues.first() }.soundId)
        val started = SystemClock.elapsedRealtime()
        val boundary = withTimeout(1_000) { model.upcomingCue.filterNotNull().first() }
        assertEquals("Mobile chirp", boundary.soundId)
        assertTrue("Cue is due at the one-second boundary", abs(boundary.atElapsedRealtime - (started + 1_000)) < 250)
        delay(1_100)
        model.refreshTimer()
        assertEquals(1, model.state.value.activeSlotIndex)
        assertEquals("Completion chime", model.upcomingCue.value?.soundId)
    }
}