package com.example.dosediary.domain

import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.schedule.IntakeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class IntakeScheduleTest {

    private val utc = ZoneId.of("UTC")
    private val eight = ReminderTime(8, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, zone: ZoneId = utc) =
        ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone)

    private fun next(
        after: ZonedDateTime,
        time: ReminderTime = eight,
        frequencyDays: Int = 1,
        start: LocalDate = after.toLocalDate(),
    ) = IntakeSchedule.nextOccurrence(after, time, frequencyDays, start.toEpochDay())

    // --- Every day ---------------------------------------------------------------------------

    @Test
    fun `every day - later today when the time has not passed`() {
        assertEquals(at(2026, 10, 5, 8), next(at(2026, 10, 5, 7, 59)))
    }

    @Test
    fun `every day - tomorrow when the time has passed`() {
        assertEquals(at(2026, 10, 6, 8), next(at(2026, 10, 5, 8, 1)))
    }

    @Test
    fun `the result is strictly after the reference moment`() {
        // Exactly 08:00 asks for the next occurrence, never the same instant (no reminder loop).
        assertEquals(at(2026, 10, 6, 8), next(at(2026, 10, 5, 8)))
    }

    @Test
    fun `month and year boundaries roll over`() {
        assertEquals(at(2027, 1, 1, 8), next(at(2026, 12, 31, 9)))
    }

    // --- Every N days ------------------------------------------------------------------------

    @Test
    fun `every 3 days - the start day itself counts`() {
        val start = LocalDate.of(2026, 10, 5)
        assertEquals(at(2026, 10, 5, 8), next(at(2026, 10, 5, 6), frequencyDays = 3, start = start))
    }

    @Test
    fun `every 3 days - after the start day's intake the next is three days later`() {
        val start = LocalDate.of(2026, 10, 5)
        assertEquals(at(2026, 10, 8, 8), next(at(2026, 10, 5, 9), frequencyDays = 3, start = start))
    }

    @Test
    fun `every 3 days - skips the days in between`() {
        val start = LocalDate.of(2026, 10, 5)
        assertEquals(at(2026, 10, 8, 8), next(at(2026, 10, 6, 12), frequencyDays = 3, start = start))
        assertEquals(at(2026, 10, 8, 8), next(at(2026, 10, 7, 23, 59), frequencyDays = 3, start = start))
    }

    @Test
    fun `every 3 days - an intake day later in the day is still found`() {
        val start = LocalDate.of(2026, 10, 5)
        // 8 Oct is an intake day and 08:00 has not happened yet.
        assertEquals(at(2026, 10, 8, 8), next(at(2026, 10, 8, 7), frequencyDays = 3, start = start))
    }

    @Test
    fun `the pattern also continues far after the start day`() {
        val start = LocalDate.of(2026, 1, 1)
        // 100 days after the start: 100 % 7 = 2, so the next multiple of 7 is 5 days on.
        val after = at(2026, 1, 1, 12).plusDays(100)
        assertEquals(at(2026, 4, 11, 8).plusDays(5), next(after, frequencyDays = 7, start = start))
    }

    @Test
    fun `a start day in the future is honoured, and the pattern extends backwards from it`() {
        val start = LocalDate.of(2026, 10, 10)
        assertEquals(at(2026, 10, 10, 8), next(at(2026, 10, 5, 12), frequencyDays = 30, start = start))
        // Backwards: days 10 Oct - 4 are intake days too, so 6 Oct is the next one for every 4 days.
        assertEquals(at(2026, 10, 6, 8), next(at(2026, 10, 5, 12), frequencyDays = 4, start = start))
    }

    @Test
    fun `intake day check matches the same rule`() {
        val start = LocalDate.of(2026, 10, 5).toEpochDay()
        assertTrue(IntakeSchedule.isIntakeDay(LocalDate.of(2026, 10, 5), 3, start))
        assertFalse(IntakeSchedule.isIntakeDay(LocalDate.of(2026, 10, 6), 3, start))
        assertFalse(IntakeSchedule.isIntakeDay(LocalDate.of(2026, 10, 7), 3, start))
        assertTrue(IntakeSchedule.isIntakeDay(LocalDate.of(2026, 10, 8), 3, start))
        assertTrue(IntakeSchedule.isIntakeDay(LocalDate.of(2026, 10, 2), 3, start))
        // Every day ignores the start day.
        assertTrue(IntakeSchedule.isIntakeDay(LocalDate.of(2030, 1, 1), 1, start))
    }

    @Test
    fun `a corrupt frequency below one behaves like every day`() {
        assertEquals(at(2026, 10, 6, 8), next(at(2026, 10, 5, 9), frequencyDays = 0))
    }

    // --- Time zones --------------------------------------------------------------------------

    @Test
    fun `the time of day is wall-clock time in the given zone`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val result = next(at(2026, 10, 5, 6, zone = warsaw))
        assertEquals(8, result.hour)
        assertEquals(warsaw, result.zone)
    }

    @Test
    fun `daily reminders keep their wall-clock time across the autumn DST change`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        // DST ends on 25 Oct 2026; 08:00 stays 08:00 although the UTC offset changes by an hour.
        val before = next(at(2026, 10, 23, 9, zone = warsaw))
        val after = next(before)
        assertEquals(at(2026, 10, 24, 8, zone = warsaw), before)
        assertEquals(at(2026, 10, 25, 8, zone = warsaw), after)
        assertEquals(25L * 60 * 60, java.time.Duration.between(before, after).seconds)
    }

    @Test
    fun `a time inside the spring-forward gap is moved forward, not skipped`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        // 29 Mar 2026: 02:00-03:00 does not exist in Warsaw.
        val result = next(at(2026, 3, 28, 12, zone = warsaw), ReminderTime(2, 30))
        assertEquals(LocalDate.of(2026, 3, 29), result.toLocalDate())
        assertEquals(3, result.hour)
    }
}
