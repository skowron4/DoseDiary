package com.example.dosediary.presentation.dashboard

import androidx.compose.runtime.Immutable
import com.example.dosediary.R
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
    /** "2 pills, every 8h"; `null` when neither part is set. */
    val dosage: String?,
    val substance: String?,
    /** "Daily at 8:00 AM, 4:00 PM", or the "set a reminder" prompt when there are none. */
    val reminderText: String,
) {
    companion object {
        fun from(medication: Medication, text: UiTextFormatter): MedicationItemUi {
            val dose = medication.doseAmount?.takeIf { it.isNotBlank() }
            val interval = medication.intervalHours?.let { text.string(R.string.dosage_every_hours, it) }
            val reminderText = if (medication.reminderTimes.isEmpty()) {
                text.string(R.string.action_set_reminder)
            } else {
                text.string(
                    R.string.reminder_daily_at,
                    medication.reminderTimes.joinToString(", ") { text.time(it) },
                )
            }
            return MedicationItemUi(
                id = medication.id,
                title = medication.commercialName,
                displayName = medication.displayName,
                nickname = medication.customUserNickname?.takeIf { it.isNotBlank() },
                dosage = listOfNotNull(dose, interval).takeIf { it.isNotEmpty() }?.joinToString(", "),
                substance = medication.displaySubtitle,
                reminderText = reminderText,
            )
        }
    }
}
