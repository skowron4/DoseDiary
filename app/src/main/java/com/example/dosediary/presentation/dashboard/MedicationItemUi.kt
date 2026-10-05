package com.example.dosediary.presentation.dashboard

import androidx.compose.runtime.Immutable
import com.example.dosediary.R
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.presentation.common.UiTextFormatter

/**
 * Everything a medication card shows, as final strings. Built off the main thread by
 * [MedicationItemUi.from]; the card itself only places text.
 *
 * @property title Primary text: the full commercial name.
 * @property displayName Nickname when set, otherwise the commercial name (used in dialogs/messages).
 */
@Immutable
data class MedicationItemUi(
    val id: String,
    val title: String,
    val displayName: String,
    val nickname: String?,
    /** "Every day" / "Every 3 days"; `null` while no intake is planned. */
    val frequency: String?,
    val substance: String?,
    /** "8:00 AM · 2 pills, 8:00 PM · 1 pill", or the "set up schedule" prompt when there are no intakes. */
    val reminderText: String,
) {
    companion object {
        fun from(medication: Medication, text: UiTextFormatter): MedicationItemUi {
            val frequency = if (medication.intakes.isEmpty()) {
                null
            } else if (medication.frequencyDays > 1) {
                text.string(R.string.frequency_every_n_days, medication.frequencyDays)
            } else {
                text.string(R.string.frequency_every_day)
            }
            val reminderText = if (medication.intakes.isEmpty()) {
                text.string(R.string.action_set_reminder)
            } else {
                medication.intakes.joinToString(", ") { intakeText(it, text) }
            }
            return MedicationItemUi(
                id = medication.id,
                title = medication.commercialName,
                displayName = medication.displayName,
                nickname = medication.customUserNickname?.takeIf { it.isNotBlank() },
                frequency = frequency,
                substance = medication.displaySubtitle,
                reminderText = reminderText,
            )
        }

        private fun intakeText(intake: Intake, text: UiTextFormatter): String {
            val time = text.time(intake.time)
            val dose = intake.doseAmount?.takeIf { it.isNotBlank() } ?: return time
            return text.string(R.string.intake_summary, time, dose)
        }
    }
}
