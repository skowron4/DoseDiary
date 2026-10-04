package com.example.dosediary.domain

import com.example.dosediary.domain.analytics.AnalyticsTimelineBuilder
import com.example.dosediary.domain.analytics.EffectVerdict
import com.example.dosediary.domain.analytics.MedicationCourse
import com.example.dosediary.domain.analytics.TimelineRange
import com.example.dosediary.domain.model.Symptom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsTimelineBuilderTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 15)
    private val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun day(offsetFromToday: Int, hour: Int = 10): Long =
        today.plusDays(offsetFromToday.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun symptom(id: Long, dayOffset: Int, severity: Int, hour: Int = 10) =
        Symptom(id = id, severity = severity, loggedAtMillis = day(dayOffset, hour))

    private fun course(start: Int, end: Int?, name: String = "Advil", id: Long = 1) = MedicationCourse(
        id = id,
        medicationId = "m$id",
        medicationName = name,
        startMillis = day(start),
        endMillis = end?.let { day(it) },
    )

    private fun build(
        symptoms: List<Symptom> = emptyList(),
        courses: List<MedicationCourse> = emptyList(),
        range: TimelineRange = TimelineRange.WEEK,
    ) = AnalyticsTimelineBuilder.build(symptoms, courses, range, now, zone)

    @Test
    fun `week window starts at local midnight six days ago and ends now`() {
        val window = build().window
        assertEquals(today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), window.startMillis)
        assertEquals(now, window.endMillis)
    }

    @Test
    fun `all range starts at the earliest data`() {
        val timeline = build(
            symptoms = listOf(symptom(1, -40, 5)),
            courses = listOf(course(start = -60, end = null)),
            range = TimelineRange.ALL,
        )
        assertEquals(today.minusDays(60).atStartOfDay(zone).toInstant().toEpochMilli(), timeline.window.startMillis)
    }

    @Test
    fun `empty data yields an empty timeline`() {
        assertTrue(build().isEmpty)
    }

    @Test
    fun `symptoms outside the window are excluded but sorted ones inside are kept`() {
        val timeline = build(symptoms = listOf(symptom(2, -1, 4), symptom(1, -30, 9), symptom(3, -3, 6)))
        assertEquals(listOf(3L, 2L), timeline.symptomPoints.map { it.symptomId })
    }

    @Test
    fun `daily severity averages entries of the same local day`() {
        val timeline = build(
            symptoms = listOf(symptom(1, -1, 4, hour = 8), symptom(2, -1, 8, hour = 20), symptom(3, 0, 5)),
        )
        val yesterday = timeline.dailySeverity.first()
        assertEquals(6f, yesterday.averageSeverity, 0.001f)
        assertEquals(8, yesterday.maxSeverity)
        assertEquals(2, yesterday.entryCount)
        assertEquals(2, timeline.dailySeverity.size)
    }

    @Test
    fun `medication bars are clamped to the window and flag open edges`() {
        val timeline = build(courses = listOf(course(start = -20, end = null)))
        val bar = timeline.medicationBars.single()
        assertEquals(timeline.window.startMillis, bar.startMillis)
        assertEquals(now, bar.endMillis)
        assertTrue(bar.startsBeforeWindow)
        assertTrue(bar.isOngoing)
    }

    @Test
    fun `courses that ended before the window are omitted`() {
        val timeline = build(courses = listOf(course(start = -40, end = -20)))
        assertTrue(timeline.medicationBars.isEmpty())
        assertTrue(timeline.effects.isEmpty())
    }

    @Test
    fun `window maps times to chart fractions`() {
        val window = build().window
        assertEquals(0f, window.fractionOf(window.startMillis), 0f)
        assertEquals(1f, window.fractionOf(window.endMillis), 0f)
        assertEquals(0f, window.fractionOf(window.startMillis - 1_000), 0f)
    }

    @Test
    fun `milder symptoms during a course are reported as improved`() {
        // Course runs from day -5 to now (5 days); the 5 days before it had severity ~8, during ~3.
        val symptoms = listOf(
            symptom(1, -9, 8), symptom(2, -7, 9),
            symptom(3, -3, 3), symptom(4, -1, 2),
        )
        val effect = build(symptoms, listOf(course(start = -5, end = null))).effects.single()

        assertEquals(EffectVerdict.IMPROVED, effect.verdict)
        assertEquals(8.5f, effect.averageBefore!!, 0.001f)
        assertEquals(2.5f, effect.averageDuring!!, 0.001f)
        assertEquals(-6f, effect.delta!!, 0.001f)
        assertEquals(0, effect.overlappingCourses)
    }

    @Test
    fun `worse symptoms during a course are reported as worsened`() {
        val symptoms = listOf(symptom(1, -9, 2), symptom(2, -7, 3), symptom(3, -3, 7), symptom(4, -1, 8))
        val effect = build(symptoms, listOf(course(start = -5, end = null))).effects.single()
        assertEquals(EffectVerdict.WORSENED, effect.verdict)
    }

    @Test
    fun `small differences count as unchanged`() {
        val symptoms = listOf(symptom(1, -9, 5), symptom(2, -7, 5), symptom(3, -3, 5), symptom(4, -1, 6))
        val effect = build(symptoms, listOf(course(start = -5, end = null))).effects.single()
        assertEquals(EffectVerdict.UNCHANGED, effect.verdict)
    }

    @Test
    fun `too few entries give insufficient data instead of a verdict`() {
        val symptoms = listOf(symptom(1, -7, 9), symptom(2, -3, 2), symptom(3, -1, 2))
        val effect = build(symptoms, listOf(course(start = -5, end = null))).effects.single()
        assertEquals(EffectVerdict.INSUFFICIENT_DATA, effect.verdict)
        assertEquals(1, effect.entriesBefore)
    }

    @Test
    fun `no symptoms before the course leaves the baseline empty`() {
        val effect = build(listOf(symptom(1, -1, 5)), listOf(course(start = -5, end = null))).effects.single()
        assertNull(effect.averageBefore)
        assertNull(effect.delta)
    }

    @Test
    fun `baseline is capped at 14 days for long courses`() {
        val effect = build(courses = listOf(course(start = -60, end = null)), range = TimelineRange.ALL).effects.single()
        assertEquals(14, effect.baselineDays)
        assertEquals(Duration.ofDays(14), com.example.dosediary.domain.analytics.AnalyticsTimeline.MAX_BASELINE)
    }

    @Test
    fun `baseline uses symptoms from before the visible window`() {
        // WEEK window starts at day -6, but the baseline of a course starting at day -5 reaches day -10.
        val symptoms = listOf(symptom(1, -9, 8), symptom(2, -8, 8), symptom(3, -3, 2), symptom(4, -2, 2))
        val timeline = build(symptoms, listOf(course(start = -5, end = null)))
        assertEquals(2, timeline.effects.single().entriesBefore)
        assertEquals(2, timeline.symptomPoints.size) // only the in-window ones are drawn
    }

    @Test
    fun `overlapping courses are counted as confounders`() {
        val courses = listOf(
            course(start = -5, end = null, name = "Advil", id = 1),
            course(start = -4, end = null, name = "Aspirin", id = 2),
            course(start = -40, end = -30, name = "Old", id = 3),
        )
        val effects = build(courses = courses).effects
        assertEquals(1, effects.first { it.medicationName == "Advil" }.overlappingCourses)
        assertEquals(1, effects.first { it.medicationName == "Aspirin" }.overlappingCourses)
    }

    @Test
    fun `symptom logged exactly when a course starts counts as during`() {
        val start = day(-5)
        val s = Symptom(id = 1, severity = 4, loggedAtMillis = start)
        val effect = build(listOf(s), listOf(course(start = -5, end = null))).effects.single()
        assertEquals(1, effect.entriesDuring)
        assertEquals(0, effect.entriesBefore)
        assertFalse(effect.verdict == EffectVerdict.IMPROVED)
    }
}
