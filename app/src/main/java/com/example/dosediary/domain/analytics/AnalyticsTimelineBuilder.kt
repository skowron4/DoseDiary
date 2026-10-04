package com.example.dosediary.domain.analytics

import com.example.dosediary.domain.model.Symptom
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Pure aggregation behind the analytics chart. No Android, no I/O: feed it the full symptom and
 * course history plus "now" and a time zone, get chart-ready data back.
 *
 * Rules:
 * - The window ends at `now`. Fixed ranges start at local midnight `days - 1` days ago (so "WEEK"
 *   is today plus the six previous calendar days); `ALL` starts at the earliest data.
 * - Medication bars are clamped to the window; courses entirely outside it are omitted.
 * - Symptoms are bucketed by local calendar day for the trend line.
 * - Effects compare the average severity during a course with an equally long period just before
 *   it (at most [AnalyticsTimeline.MAX_BASELINE]). They use ALL symptoms, including ones logged
 *   before the visible window, so the baseline does not depend on the zoom level.
 */
object AnalyticsTimelineBuilder {

    /** Minimum logged symptoms on EACH side before a verdict is given. */
    const val MIN_ENTRIES_PER_SIDE = 2

    /** Smallest change (on the 1-10 scale) that counts as better or worse. */
    const val SIGNIFICANT_DELTA = 1.0f

    fun build(
        symptoms: List<Symptom>,
        courses: List<MedicationCourse>,
        range: TimelineRange,
        nowMillis: Long,
        zone: ZoneId,
    ): AnalyticsTimeline {
        val window = windowFor(range, symptoms, courses, nowMillis, zone)

        val bars = courses
            .filter { it.overlaps(window, nowMillis) }
            .sortedBy { it.startMillis }
            .map { it.toBar(window, nowMillis) }

        val visibleSymptoms = symptoms
            .filter { it.loggedAtMillis in window }
            .sortedBy { it.loggedAtMillis }

        return AnalyticsTimeline(
            range = range,
            window = window,
            medicationBars = bars,
            symptomPoints = visibleSymptoms.map {
                SymptomPoint(it.id, it.loggedAtMillis, it.severity, it.medicationId)
            },
            dailySeverity = dailySeverity(visibleSymptoms, zone),
            effects = courses
                .filter { it.overlaps(window, nowMillis) }
                .sortedBy { it.startMillis }
                .map { effectOf(it, symptoms, courses, nowMillis) },
        )
    }

    private fun windowFor(
        range: TimelineRange,
        symptoms: List<Symptom>,
        courses: List<MedicationCourse>,
        nowMillis: Long,
        zone: ZoneId,
    ): TimeWindow {
        val todayStart = startOfDay(nowMillis, zone)
        val start = when (val days = range.days) {
            null -> {
                val earliest = (symptoms.map { it.loggedAtMillis } + courses.map { it.startMillis }).minOrNull()
                earliest?.let { startOfDay(it, zone) } ?: todayStart
            }
            else -> Instant.ofEpochMilli(todayStart).atZone(zone).minusDays((days - 1).toLong()).toInstant().toEpochMilli()
        }
        return TimeWindow(startMillis = minOf(start, nowMillis), endMillis = nowMillis)
    }

    private fun startOfDay(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun MedicationCourse.effectiveEnd(nowMillis: Long): Long = endMillis ?: nowMillis

    private fun MedicationCourse.overlaps(window: TimeWindow, nowMillis: Long): Boolean =
        startMillis <= window.endMillis && effectiveEnd(nowMillis) >= window.startMillis

    private fun MedicationCourse.toBar(window: TimeWindow, nowMillis: Long) = MedicationBar(
        courseId = id,
        medicationId = medicationId,
        medicationName = medicationName,
        startMillis = maxOf(startMillis, window.startMillis),
        endMillis = minOf(effectiveEnd(nowMillis), window.endMillis),
        startsBeforeWindow = startMillis < window.startMillis,
        isOngoing = endMillis == null,
    )

    private fun dailySeverity(symptoms: List<Symptom>, zone: ZoneId): List<DailySeverity> =
        symptoms
            .groupBy { Instant.ofEpochMilli(it.loggedAtMillis).atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (day, entries) ->
                DailySeverity(
                    dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    averageSeverity = entries.map { it.severity }.average().toFloat(),
                    maxSeverity = entries.maxOf { it.severity },
                    entryCount = entries.size,
                )
            }

    private fun effectOf(
        course: MedicationCourse,
        symptoms: List<Symptom>,
        allCourses: List<MedicationCourse>,
        nowMillis: Long,
    ): TreatmentEffect {
        val duringEnd = course.effectiveEnd(nowMillis)
        val duringLength = duringEnd - course.startMillis
        val baseline = minOf(Duration.ofMillis(duringLength), AnalyticsTimeline.MAX_BASELINE)
        val beforeStart = course.startMillis - baseline.toMillis()

        // "before" is half-open so a symptom logged exactly at the start counts as "during".
        val before = symptoms.filter { it.loggedAtMillis >= beforeStart && it.loggedAtMillis < course.startMillis }
        val during = symptoms.filter { it.loggedAtMillis in course.startMillis..duringEnd }

        val averageBefore = before.map { it.severity }.averageOrNull()
        val averageDuring = during.map { it.severity }.averageOrNull()

        val overlapping = allCourses.count { other ->
            other !== course &&
                other.startMillis <= duringEnd &&
                other.effectiveEnd(nowMillis) >= beforeStart
        }

        return TreatmentEffect(
            courseId = course.id,
            medicationName = course.medicationName,
            baselineDays = baseline.toDays().toInt(),
            averageBefore = averageBefore,
            averageDuring = averageDuring,
            entriesBefore = before.size,
            entriesDuring = during.size,
            overlappingCourses = overlapping,
            verdict = verdictOf(averageBefore, averageDuring, before.size, during.size),
        )
    }

    private fun verdictOf(before: Float?, during: Float?, entriesBefore: Int, entriesDuring: Int): EffectVerdict {
        if (before == null || during == null ||
            entriesBefore < MIN_ENTRIES_PER_SIDE || entriesDuring < MIN_ENTRIES_PER_SIDE
        ) {
            return EffectVerdict.INSUFFICIENT_DATA
        }
        val delta = during - before
        return when {
            delta <= -SIGNIFICANT_DELTA -> EffectVerdict.IMPROVED
            delta >= SIGNIFICANT_DELTA -> EffectVerdict.WORSENED
            else -> EffectVerdict.UNCHANGED
        }
    }

    private fun List<Int>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
}
