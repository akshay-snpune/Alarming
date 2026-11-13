package com.alarming.feature.reminders

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    private fun instant(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    @Test fun waterIncludesEndWithoutDriftAndRollsToTomorrow() {
        val schedule = ReminderSchedule(RepeatRule.WATER, LocalDate.parse("2026-11-01"), 480, 1200, 120, "UTC")
        assertEquals(instant("2026-11-01T10:00:00Z"), schedule.nextAfter(instant("2026-11-01T08:00:00Z")))
        assertEquals(instant("2026-11-01T20:00:00Z"), schedule.nextAfter(instant("2026-11-01T19:59:00Z")))
        assertEquals(instant("2026-11-02T08:00:00Z"), schedule.nextAfter(instant("2026-11-01T20:00:00Z")))
    }

    @Test fun missedWaterOccurrencesAreNotReplayed() {
        val schedule = ReminderSchedule(RepeatRule.WATER, LocalDate.parse("2026-11-01"), 480, 1200, 120, "UTC")
        assertEquals(instant("2026-11-01T16:00:00Z"), schedule.nextAfter(instant("2026-11-01T15:23:00Z")))
    }

    @Test fun onceDoesNotRepeat() {
        val schedule = ReminderSchedule(RepeatRule.ONCE, LocalDate.parse("2026-11-01"), 480, zoneId = "UTC")
        assertNull(schedule.nextAfter(instant("2026-11-01T08:00:00Z")))
    }

    @Test fun weeklyKeepsChosenWeekday() {
        val schedule = ReminderSchedule(RepeatRule.WEEKLY, LocalDate.parse("2026-11-01"), 480, zoneId = "UTC")
        assertEquals(instant("2026-11-08T08:00:00Z"), schedule.nextAfter(instant("2026-11-01T08:00:00Z")))
    }

    @Test fun springGapUsesNextValidInstant() {
        val schedule = ReminderSchedule(RepeatRule.DAILY, LocalDate.parse("2026-03-29"), 150, zoneId = "Europe/Berlin")
        assertEquals(instant("2026-03-29T03:00:00+02:00"), schedule.nextAfter(instant("2026-03-29T00:00:00+01:00")))
    }

    @Test fun autumnOverlapUsesOnlyFirstOccurrence() {
        val schedule = ReminderSchedule(RepeatRule.DAILY, LocalDate.parse("2026-10-25"), 150, zoneId = "Europe/Berlin")
        assertEquals(instant("2026-10-26T02:30:00+01:00"), schedule.nextAfter(instant("2026-10-25T02:45:00+02:00")))
    }

    @Test fun futureStartDateIsRespected() {
        val schedule = ReminderSchedule(RepeatRule.WATER, LocalDate.parse("2026-11-03"), 480, 1200, zoneId = "UTC")
        assertEquals(instant("2026-11-03T08:00:00Z"), schedule.nextAfter(instant("2026-11-01T12:00:00Z")))
    }

    @Test fun dailyScheduleHonorsSelectedWeekdays() {
        val mask = ReminderSchedule.dayBit(java.time.DayOfWeek.MONDAY) or ReminderSchedule.dayBit(java.time.DayOfWeek.WEDNESDAY)
        val schedule = ReminderSchedule(RepeatRule.DAILY, LocalDate.parse("2026-11-01"), 480, zoneId = "UTC", daysOfWeekMask = mask)
        assertEquals(instant("2026-11-02T08:00:00Z"), schedule.nextAfter(instant("2026-11-01T08:00:00Z")))
        assertEquals(instant("2026-11-04T08:00:00Z"), schedule.nextAfter(instant("2026-11-02T08:00:00Z")))
    }

    @Test fun waterScheduleHonorsSelectedWeekdays() {
        val mask = ReminderSchedule.dayBit(java.time.DayOfWeek.TUESDAY) or ReminderSchedule.dayBit(java.time.DayOfWeek.THURSDAY)
        val schedule = ReminderSchedule(RepeatRule.WATER, LocalDate.parse("2026-11-01"), 480, 1200, 120, "UTC", mask)
        assertEquals(instant("2026-11-03T08:00:00Z"), schedule.nextAfter(instant("2026-11-02T20:00:00Z")))
        assertEquals(instant("2026-11-03T10:00:00Z"), schedule.nextAfter(instant("2026-11-03T08:00:00Z")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedWaterWindowIsRejected() {
        ReminderSchedule(RepeatRule.WATER, LocalDate.now(), 1200, 480)
    }
}