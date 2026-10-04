package com.example.dosediary.domain.model

/**
 * A logged symptom entry.
 *
 * @property id Database id; `0` for entries that have not been persisted yet.
 * @property medicationId Optional link to the medication the symptom relates to.
 * @property medicationName Denormalised name of the linked medication, for display.
 * @property severity Subjective severity, [MIN_SEVERITY]..[MAX_SEVERITY].
 * @property loggedAtMillis Epoch milliseconds at which the symptom was logged.
 */
data class Symptom(
    val id: Long = 0,
    val medicationId: String? = null,
    val medicationName: String? = null,
    val severity: Int,
    val notes: String = "",
    val loggedAtMillis: Long,
) {
    companion object {
        const val MIN_SEVERITY = 1
        const val MAX_SEVERITY = 10
        const val MAX_NOTES_LENGTH = 500
    }
}
