package com.example.dosediary.domain.model

/**
 * A medication, either returned from the OpenFDA search or saved by the user.
 *
 * @property id Stable identifier (the OpenFDA label id). Used as the primary key when saved locally.
 * @property commercialName Brand/commercial name as published by the manufacturer.
 * @property activeSubstance Active ingredient(s), e.g. "Ibuprofen".
 * @property customUserNickname Optional name the user gave this medication ("Morning pill").
 * @property doseAmount Free-text amount taken per dose, e.g. "2 pills" or "5 ml".
 * @property intervalHours Hours between doses (8 means "every 8h"), or `null` when not set.
 * @property reminderTimes Daily reminder times, sorted and distinct; empty when no reminder is configured.
 */
data class Medication(
    val id: String,
    val commercialName: String,
    val activeSubstance: String? = null,
    val manufacturer: String? = null,
    val customUserNickname: String? = null,
    val purpose: String? = null,
    val route: String? = null,
    val doseAmount: String? = null,
    val intervalHours: Int? = null,
    val reminderTimes: List<ReminderTime> = emptyList(),
) {
    /** Primary text to show: the user's nickname when set, otherwise the commercial name. */
    val displayName: String
        get() = customUserNickname?.takeIf { it.isNotBlank() } ?: commercialName

    /** Secondary text to show under [displayName]. */
    val displaySubtitle: String?
        get() = activeSubstance?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_NICKNAME_LENGTH = 40
        const val MAX_DOSE_LENGTH = 40
        const val MIN_INTERVAL_HOURS = 1
        const val MAX_INTERVAL_HOURS = 168
        const val MAX_REMINDERS = 10
    }
}

/** The user-editable part of a saved medication, applied in one go by the edit screen. */
data class MedicationDetails(
    val nickname: String?,
    val doseAmount: String?,
    val intervalHours: Int?,
    val reminderTimes: List<ReminderTime>,
)

/** Case-insensitive match against nickname, commercial name, active substance and manufacturer. */
fun Medication.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return listOfNotNull(customUserNickname, commercialName, activeSubstance, manufacturer)
        .any { it.contains(needle, ignoreCase = true) }
}

/** A wall-clock time of day (24h) at which a daily reminder fires. */
data class ReminderTime(val hour: Int, val minute: Int) : Comparable<ReminderTime> {
    init {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }

    override fun compareTo(other: ReminderTime): Int = compareValuesBy(this, other, { it.hour }, { it.minute })
}
