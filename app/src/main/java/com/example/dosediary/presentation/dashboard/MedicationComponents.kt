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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
 * sits directly below it, and the active substance is shown as a compact info line.
 * The reminder chip and the "log symptom" action share one row to keep the card short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationCard(
    medication: Medication,
    onLogSymptom: () -> Unit,
    onEditNickname: () -> Unit,
    onSetReminder: () -> Unit,
    onClearReminder: () -> Unit,
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
                IconButton(onClick = onEditNickname) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit_nickname))
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
                val reminder = medication.reminderTime
                AssistChip(
                    onClick = onSetReminder,
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, Modifier.size(18.dp)) },
                    label = {
                        Text(
                            if (reminder != null) {
                                stringResource(R.string.reminder_daily_at, reminder.format())
                            } else {
                                stringResource(R.string.action_set_reminder)
                            },
                        )
                    },
                )
                if (reminder != null) {
                    TextButton(onClick = onClearReminder) { Text(stringResource(R.string.action_clear)) }
                }
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

/** Lets the user set or clear a nickname. Saving an empty field removes it. */
@Composable
fun NicknameDialog(
    medication: Medication,
    onConfirm: (nickname: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(medication.id) { mutableStateOf(medication.customUserNickname.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nickname_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Medication.MAX_NICKNAME_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.nickname_label)) },
                    supportingText = {
                        Text(
                            text = "${text.length} / ${Medication.MAX_NICKNAME_LENGTH}",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.nickname_original_name, medication.commercialName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.nickname_hint_clear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --- Previews: open this file in Android Studio's Split/Design view to check both name cases. ---

private val previewMedication = Medication(
    id = "preview",
    commercialName = "Advil",
    activeSubstance = "Ibuprofen",
    manufacturer = "Haleon",
    reminderTime = ReminderTime(8, 30),
)

@Preview(name = "Commercial name only", showBackground = true)
@Composable
private fun MedicationCardPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(previewMedication, {}, {}, {}, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "With nickname", showBackground = true)
@Composable
private fun MedicationCardNicknamePreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(
            previewMedication.copy(customUserNickname = "Morning headache pill", reminderTime = null),
            {}, {}, {}, {}, {},
            Modifier.padding(16.dp),
        )
    }
}
