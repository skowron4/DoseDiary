package com.example.dosediary.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.presentation.common.format
import com.example.dosediary.ui.theme.DoseDiaryTheme

/**
 * A saved medication.
 *
 * Text hierarchy: the full commercial name is the primary text, the user's nickname (when set)
 * sits directly below it, then the quick dosage line ("2 pills, every 8h") and the active substance.
 * The reminder chip and the "log symptom" action share one row to keep the card short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationCard(
    medication: Medication,
    onLogSymptom: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = medication.commercialName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    medication.nicknameOrNull?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    dosageSummary(medication)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    medication.displaySubtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit_medication))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = onEdit,
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp)) },
                    label = {
                        Text(
                            text = if (medication.reminderTimes.isNotEmpty()) {
                                stringResource(
                                    R.string.reminder_daily_at,
                                    medication.reminderTimes.joinToString(", ") { it.format() },
                                )
                            } else {
                                stringResource(R.string.action_set_reminder)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                TextButton(onClick = onLogSymptom) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.action_log_symptom),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private val Medication.nicknameOrNull: String?
    get() = customUserNickname?.takeIf { it.isNotBlank() }

/** Quick dosage text such as "2 pills, every 8h"; either part may be missing, both missing gives `null`. */
@Composable
private fun dosageSummary(medication: Medication): String? {
    val dose = medication.doseAmount?.takeIf { it.isNotBlank() }
    val interval = medication.intervalHours?.let { stringResource(R.string.dosage_every_hours, it) }
    return listOfNotNull(dose, interval).takeIf { it.isNotEmpty() }?.joinToString(", ")
}

// --- Previews: open this file in Android Studio's Split/Design view to check the card variants. ---

private val previewMedication = Medication(
    id = "preview",
    commercialName = "Advil",
    activeSubstance = "Ibuprofen",
    manufacturer = "Haleon",
)

@Preview(name = "Commercial name only", showBackground = true)
@Composable
private fun MedicationCardPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(previewMedication, {}, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "Nickname, dosage and reminders", showBackground = true)
@Composable
private fun MedicationCardFullPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(
            previewMedication.copy(
                customUserNickname = "Morning headache pill",
                doseAmount = "2 pills",
                intervalHours = 8,
                reminderTimes = listOf(ReminderTime(8, 0), ReminderTime(16, 0), ReminderTime(23, 59)),
            ),
            {}, {}, {},
            Modifier.padding(16.dp),
        )
    }
}
