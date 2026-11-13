package com.alarming.feature.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.DayOfWeek

enum class RepeatRule { ONCE, DAILY, WEEKLY, WATER }

data class ReminderSchedule(
    val rule: RepeatRule,
    val firstDate: LocalDate,
    val startMinute: Int,
    val endMinute: Int = startMinute,
    val intervalMinutes: Int = 120,
    val zoneId: String = ZoneId.systemDefault().id,
    val daysOfWeekMask: Int = 0,
) {
    init {
        require(startMinute in 0..1439 && endMinute in 0..1439)
        require(intervalMinutes in 1..1440)
        require(daysOfWeekMask in 0..127)
        require(rule != RepeatRule.WATER || endMinute >= startMinute)
        ZoneId.of(zoneId)
    }

    fun nextAfter(afterMillis: Long): Long? {
        val zone = ZoneId.of(zoneId)
        val afterDate = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate()
        if (rule == RepeatRule.ONCE) {
            return occurrence(firstDate, startMinute, zone).takeIf { it > afterMillis }
        }
        val selectedDays = when {
            daysOfWeekMask != 0 -> daysOfWeekMask
            rule == RepeatRule.WEEKLY -> dayBit(firstDate.dayOfWeek)
            else -> 127
        }
        val baseDate = maxOf(firstDate, afterDate)
        for (dayOffset in 0L..14L) {
            val date = baseDate.plusDays(dayOffset)
            if (selectedDays and dayBit(date.dayOfWeek) == 0) continue
            val minutes = if (rule == RepeatRule.WATER) {
                (startMinute..endMinute step intervalMinutes).toList()
            } else listOf(startMinute)
            val next = minutes.map { occurrence(date, it, zone) }
                .distinct().filter { it > afterMillis }.minOrNull()
            if (next != null) return next
        }
        return null
    }

    companion object {
        fun dayBit(day: DayOfWeek) = 1 shl ((day.value + 6) % 7)
    }

    private fun occurrence(date: LocalDate, minute: Int, zone: ZoneId): Long {
        val local = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
        val offsets = zone.rules.getValidOffsets(local)
        val resolved = if (offsets.isEmpty()) {
            zone.rules.getTransition(local)!!.dateTimeAfter.atZone(zone)
        } else local.atZone(zone).withEarlierOffsetAtOverlap()
        return resolved.toInstant().toEpochMilli()
    }
}