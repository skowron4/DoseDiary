package com.example.dosediary.domain.schedule

import com.example.dosediary.domain.model.ReminderTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure calculation of when an intake is due, shared by every scheduling mechanism (exact alarms and
 * the WorkManager fallback) so they can never disagree.
 *
 * An intake happens on days `startEpochDay + k * frequencyDays` (k >= 0 or negative, so the pattern
 * also extends backwards) at the intake's time of day, in the device's current time zone.
 */
object IntakeSchedule {

    /** Whether [date] is an intake day for a medication taken every [frequencyDays] days from [startEpochDay]. */
    fun isIntakeDay(date: LocalDate, frequencyDays: Int, startEpochDay: Long): Boolean {
        val step = frequencyDays.coerceAtLeast(1)
        if (step == 1) return true
        return Math.floorMod(date.toEpochDay() - startEpochDay, step.toLong()) == 0L
    }

    /**
     * The first moment strictly after [after] at which an intake at [time] is due.
     *
     * Wall-clock times that do not exist (spring-forward gap) are moved forward by the zone rules, and
     * ambiguous ones (fall-back overlap) use the earlier offset, which is how [ZonedDateTime.of] behaves.
     */
    fun nextOccurrence(
        after: ZonedDateTime,
        time: ReminderTime,
        frequencyDays: Int,
        startEpochDay: Long,
    ): ZonedDateTime {
        val zone: ZoneId = after.zone
        var day = after.toLocalDate()
        // The pattern repeats within `frequencyDays` days, so at most one extra day is needed for "today
        // already passed"; the bound keeps a corrupt value from looping forever.
        repeat(frequencyDays.coerceAtLeast(1) + 1) {
            if (isIntakeDay(day, frequencyDays, startEpochDay)) {
                val candidate = ZonedDateTime.of(LocalDateTime.of(day, LocalTime.of(time.hour, time.minute)), zone)
                if (candidate.isAfter(after)) return candidate
            }
            day = day.plusDays(1)
        }
        // Unreachable for valid input; fall back to the next calendar day rather than failing.
        return ZonedDateTime.of(
            LocalDateTime.of(after.toLocalDate().plusDays(1), LocalTime.of(time.hour, time.minute)),
            zone,
        )
    }
}
