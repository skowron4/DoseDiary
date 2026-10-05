package com.example.dosediary.domain.model

/**
 * A medication, either returned from the OpenFDA search or saved by the user.
 *
 * @property id Stable identifier (the OpenFDA label id). Used as the primary key when saved locally.
 * @property commercialName Brand/commercial name as published by the manufacturer.
 * @property activeSubstance Active ingredient(s), e.g. "Ibuprofen".
 * @property customUserNickname Optional name the user gave this medication ("Morning pill").
 * @property frequencyDays Days between intake days: 1 is every day, 3 is every third day.
 * @property frequencyStartEpochDay Epoch day (UTC-agnostic local date) the interval is counted from. Only
 *   meaningful when [frequencyDays] is greater than 1.
 * @property intakes What is taken on an intake day: one entry per time of day, sorted by time.
 */
data class Medication(
    val id: String,
    val commercialName: String,
    val activeSubstance: String? = null,
    val manufacturer: String? = null,
    val customUserNickname: String? = null,
    val purpose: String? = null,
    val route: String? = null,
    val frequencyDays: Int = DEFAULT_FREQUENCY_DAYS,
    val frequencyStartEpochDay: Long = 0L,
    val intakes: List<Intake> = emptyList(),
) {
    /** Primary text to show: the user's nickname when set, otherwise the commercial name. */
    val displayName: String
        get() = customUserNickname?.takeIf { it.isNotBlank() } ?: commercialName

    /** Secondary text to show under [displayName]. */
    val displaySubtitle: String?
        get() = activeSubstance?.takeIf { it.isNotBlank() }

    /** The intakes the user wants to be notified about. */
    val notifyingIntakes: List<Intake>
        get() = intakes.filter { it.notify }

    companion object {
        const val MAX_NICKNAME_LENGTH = 40
        const val MAX_DOSE_LENGTH = 40
        const val DEFAULT_FREQUENCY_DAYS = 1
        const val MIN_FREQUENCY_DAYS = 1
        const val MAX_FREQUENCY_DAYS = 90
        const val MAX_INTAKES = 10
    }
}

/**
 * One planned intake: a time of day, the amount taken then, and whether to notify.
 *
 * @property doseAmount Free-text amount, e.g. "2 pills" or "5 ml"; `null` when not specified.
 * @property notify Whether a notification should fire at [time] on intake days.
 */
data class Intake(
    val time: ReminderTime,
    val doseAmount: String? = null,
    val notify: Boolean = true,
)

/** The user-editable part of a saved medication, applied in one go by the edit screen. */
data class MedicationDetails(
    val nickname: String?,
    val frequencyDays: Int,
    val frequencyStartEpochDay: Long,
    val intakes: List<Intake>,
)

/** Case-insensitive match against nickname, commercial name, active substance and manufacturer. */
fun Medication.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return listOfNotNull(customUserNickname, commercialName, activeSubstance, manufacturer)
        .any { it.contains(needle, ignoreCase = true) }
}

/** A wall-clock time of day (24h). */
data class ReminderTime(val hour: Int, val minute: Int) : Comparable<ReminderTime> {
    init {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }

    override fun compareTo(other: ReminderTime): Int = compareValuesBy(this, other, { it.hour }, { it.minute })
}
