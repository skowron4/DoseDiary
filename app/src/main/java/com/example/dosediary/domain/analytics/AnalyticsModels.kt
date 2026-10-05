package com.example.dosediary.domain.analytics

import java.time.Duration

/*
 * DRAFT (Phase 5): domain model for the upcoming "Analytics" timeline, a chart that overlays when
 * the user took each medication with the symptoms they logged, to judge whether a treatment helps.
 *
 * Nothing here is wired into navigation, Koin or the database yet. See AnalyticsTimelineBuilder
 * for the (tested) aggregation rules and presentation/analytics for the ViewModel draft.
 */

/**
 * A period during which the user took a medication.
 *
 * This is the one piece of data the app does not record today: [com.example.dosediary.domain.model.Medication]
 * only knows its reminder time, not when treatment started or stopped.
 *
 * @property medicationId The saved medication, or `null` once it has been deleted (history is kept,
 *   mirroring how symptoms behave).
 * @property medicationName Snapshot of the display name at the time, so history stays readable after deletion.
 * @property endMillis `null` while the course is ongoing.
 */
data class MedicationCourse(
    val id: Long = 0,
    val medicationId: String?,
    val medicationName: String,
    val startMillis: Long,
    val endMillis: Long? = null,
) {
    init {
        require(endMillis == null || endMillis >= startMillis) { "course cannot end before it starts" }
    }
}

/** How far back the chart looks. */
enum class TimelineRange(val days: Int?) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),

    /** From the first logged symptom or course until now. */
    ALL(null),
}

/** The visible x-axis span, in epoch milliseconds. */
data class TimeWindow(val startMillis: Long, val endMillis: Long) {
    init {
        require(endMillis >= startMillis) { "window end must not precede its start" }
    }

    operator fun contains(timeMillis: Long): Boolean = timeMillis in startMillis..endMillis

    /** Position of [timeMillis] on the x-axis, `0f` (left edge) .. `1f` (right edge); handy for a Canvas. */
    fun fractionOf(timeMillis: Long): Float {
        val width = endMillis - startMillis
        if (width <= 0L) return 0f
        return ((timeMillis - startMillis).toDouble() / width).toFloat().coerceIn(0f, 1f)
    }
}

/** A horizontal bar on a medication lane. Times are clamped to the visible [TimeWindow]. */
data class MedicationBar(
    val courseId: Long,
    val medicationId: String?,
    val medicationName: String,
    val startMillis: Long,
    val endMillis: Long,
    /** The course began before the window, so the bar should look "open" on the left. */
    val startsBeforeWindow: Boolean,
    /** The course has no end date yet, so the bar should look "open" on the right. */
    val isOngoing: Boolean,
)

/** One logged symptom as a point on the severity axis. */
data class SymptomPoint(
    val symptomId: Long,
    val timeMillis: Long,
    val severity: Int,
    val medicationId: String?,
)

/** All symptoms of one calendar day condensed into a single value for the trend line. */
data class DailySeverity(
    val dayStartMillis: Long,
    val averageSeverity: Float,
    val maxSeverity: Int,
    val entryCount: Int,
)

enum class EffectVerdict {
    /** Average severity during the course is clearly lower than before it. */
    IMPROVED,

    /** Average severity during the course is clearly higher than before it. */
    WORSENED,

    /** No meaningful difference. */
    UNCHANGED,

    /** Too few logged symptoms on either side to say anything. */
    INSUFFICIENT_DATA,
}

/**
 * Before/after comparison for one course.
 *
 * Average severity of all logged symptoms in the period right before the course started
 * ([baselineDays] long) versus during the course. This is an observational hint, NOT proof of
 * effectiveness: other treatments, the illness's natural course and logging habits all confound it,
 * which is why [overlappingCourses] and the sample sizes are exposed so the UI can caveat the result.
 */
data class TreatmentEffect(
    val courseId: Long,
    val medicationName: String,
    val baselineDays: Int,
    val averageBefore: Float?,
    val averageDuring: Float?,
    val entriesBefore: Int,
    val entriesDuring: Int,
    /** Other courses that overlapped the compared periods and could explain the change. */
    val overlappingCourses: Int,
    val verdict: EffectVerdict,
) {
    /** `during - before`; negative means symptoms got milder. `null` without data on both sides. */
    val delta: Float?
        get() = if (averageBefore != null && averageDuring != null) averageDuring - averageBefore else null
}

/** Everything the chart needs, already aggregated. */
data class AnalyticsTimeline(
    val range: TimelineRange,
    val window: TimeWindow,
    /** Medication lanes, ordered by start time. */
    val medicationBars: List<MedicationBar>,
    /** Raw symptom dots, ordered by time. */
    val symptomPoints: List<SymptomPoint>,
    /** Daily trend line, ordered by day. */
    val dailySeverity: List<DailySeverity>,
    /** One entry per course overlapping the window, ordered by course start. */
    val effects: List<TreatmentEffect>,
) {
    val isEmpty: Boolean get() = medicationBars.isEmpty() && symptomPoints.isEmpty()

    companion object {
        /** Length of the "before" period used for [TreatmentEffect]. */
        val MAX_BASELINE: Duration = Duration.ofDays(14)
    }
}
