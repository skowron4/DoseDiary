package com.example.dosediary.presentation.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * A saved medication on the Home screen, kept deliberately minimal: its name and, when a daily
 * reminder is active, a small subtle bell. Everything else (reminder, nickname, removal) lives on
 * the medication's details screen, which opens when the card is tapped.
 *
 * The name is the user's nickname when set, otherwise the commercial name ([Medication.displayName]).
 */
@Composable
fun MedicationCard(
    medication: Medication,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = medication.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            medication.reminderTime?.let { reminder ->
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.reminder_daily_at, reminder.format()),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

// --- Previews: open this file in Android Studio's Split/Design view to check the name cases. ---

private val previewMedication = Medication(
    id = "preview",
    commercialName = "Advil",
    activeSubstance = "Ibuprofen",
    manufacturer = "Haleon",
    reminderTime = ReminderTime(8, 30),
)

@Preview(name = "With reminder", showBackground = true)
@Composable
private fun MedicationCardPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(previewMedication, onClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Nickname, no reminder", showBackground = true)
@Composable
private fun MedicationCardNicknamePreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(
            previewMedication.copy(customUserNickname = "Morning headache pill", reminderTime = null),
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
