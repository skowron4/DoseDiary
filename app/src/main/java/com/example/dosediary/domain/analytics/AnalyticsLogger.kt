package com.example.dosediary.domain.analytics

/**
 * Records product events (what the user did), independent of where they end up.
 *
 * Implementations must never throw and must be cheap: callers log from use cases and do not guard
 * against failures. Do not put health data (medication names, symptom notes) in [params]; use
 * counts, flags and enum-like values instead.
 */
interface AnalyticsLogger {
    fun logEvent(eventName: String, params: Map<String, String> = emptyMap())
}

/** Stable event names and parameter keys, so call sites and tests do not repeat string literals. */
object AnalyticsEvents {
    const val MEDICATION_ADDED = "medication_added"
    const val SYMPTOM_LOGGED = "symptom_logged"
    const val INTERACTION_WARNING_SHOWN = "interaction_warning_shown"

    const val PARAM_INTAKE_COUNT = "intake_count"
    const val PARAM_SEVERITY = "severity"
    const val PARAM_LINKED_TO_MEDICATION = "linked_to_medication"
    const val PARAM_TAG_COUNT = "tag_count"
    const val PARAM_IS_UPDATE = "is_update"
    const val PARAM_WARNING_COUNT = "warning_count"
    const val PARAM_HIGHEST_SEVERITY = "highest_severity"
}

/** Drops every event. Used by release builds and as the default in tests. */
object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(eventName: String, params: Map<String, String>) = Unit
}
