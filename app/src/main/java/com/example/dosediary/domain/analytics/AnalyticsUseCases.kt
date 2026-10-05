package com.example.dosediary.domain.analytics

import com.example.dosediary.domain.repository.SymptomRepository
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/**
 * DRAFT (Phase 5). Storage contract for treatment courses.
 *
 * Proposed backing: a `medication_courses` Room table (see `MedicationCourseEntity`), added with
 * `AutoMigration(from = 3, to = 4)`. Courses would be created/ended from the medication details
 * screen ("Started taking" / "Stopped taking") and could be auto-started when a medication is saved.
 * No implementation is registered yet.
 */
interface MedicationCourseRepository {
    fun observeCourses(): Flow<List<MedicationCourse>>
    suspend fun startCourse(medicationId: String, medicationName: String, startMillis: Long)
    suspend fun endCourse(courseId: Long, endMillis: Long)
}

/**
 * Streams the chart data for [TimelineRange], recomputed whenever symptoms or courses change.
 *
 * "Now" is read when the underlying data emits; a screen left open across midnight keeps the old
 * window until the next change or until the range is re-selected (acceptable for a draft).
 */
class ObserveAnalyticsTimelineUseCase(
    private val symptoms: SymptomRepository,
    private val courses: MedicationCourseRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    operator fun invoke(range: TimelineRange): Flow<AnalyticsTimeline> =
        combine(symptoms.observeSymptoms(), courses.observeCourses()) { allSymptoms, allCourses ->
            AnalyticsTimelineBuilder.build(allSymptoms, allCourses, range, clock.nowMillis(), zone())
        }
}
