package com.example.dosediary.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dosediary.presentation.common.ListItemIconAction
import com.example.dosediary.ui.theme.DoseDiaryTheme

/** Accessibility/action texts shared by every card; resolved once per list instead of once per card. */
@Immutable
data class MedicationCardLabels(
    val edit: String,
    val delete: String,
    val logSymptom: String,
)

private val CardShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(8.dp)

/**
 * A saved medication, built only from plain boxes, rows and text so it is cheap to compose and measure
 * while scrolling:
 *  - no Material `Card`, `AssistChip`, `TextButton` or `IconButton`, and no `FlowRow`;
 *  - every row has a fixed shape: the long reminder text is the only flexible child (`weight` +
 *    one-line ellipsis), everything else keeps its natural size, so no wrapping is ever computed;
 *  - it receives final strings ([MedicationItemUi]); nothing is formatted or looked up here.
 *
 * Text hierarchy: commercial name, then the nickname, then the dosage line, then the active substance.
 * The reminder pill and the "log symptom" action share one row to keep the card short.
 */
@Composable
fun MedicationCard(
    item: MedicationItemUi,
    labels: MedicationCardLabels,
    onLogSymptom: (medicationId: String) -> Unit,
    onEdit: (medicationId: String) -> Unit,
    onDelete: (MedicationItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainerHighest)
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = item.title,
                    style = typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.nickname?.let {
                    Text(
                        text = it,
                        style = typography.bodyMedium,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.dosage?.let {
                    Text(text = it, style = typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.substance?.let {
                    Text(
                        text = it,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ListItemIconAction(Icons.Default.Edit, labels.edit, onClick = { onEdit(item.id) })
            ListItemIconAction(Icons.Default.Delete, labels.delete, onClick = { onDelete(item) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The only flexible child: takes whatever width the action leaves and truncates to one line.
            ReminderPill(
                text = item.reminderText,
                onClick = { onEdit(item.id) },
                modifier = Modifier.weight(1f, fill = false),
            )
            LogSymptomAction(text = labels.logSymptom, onClick = { onLogSymptom(item.id) })
        }
    }
}

/** Replaces `AssistChip`: a rounded tinted row with a bell and one line of text. */
@Composable
private fun ReminderPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(colors.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Replaces `TextButton`: an icon and a label that keep their natural size. */
@Composable
private fun LogSymptomAction(text: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = primary, maxLines = 1)
    }
}

// --- Previews: open this file in Android Studio's Split/Design view to check the card variants. ---

private val previewLabels = MedicationCardLabels(edit = "Edit", delete = "Delete", logSymptom = "Log symptom")

@Preview(name = "Commercial name only", showBackground = true)
@Composable
private fun MedicationCardPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(
            item = MedicationItemUi(
                id = "preview",
                title = "Advil",
                displayName = "Advil",
                nickname = null,
                dosage = null,
                substance = "Ibuprofen",
                reminderText = "Set a reminder",
            ),
            labels = previewLabels,
            onLogSymptom = {},
            onEdit = {},
            onDelete = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Nickname, dosage and reminders", showBackground = true)
@Composable
private fun MedicationCardFullPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        MedicationCard(
            item = MedicationItemUi(
                id = "preview",
                title = "Advil",
                displayName = "Morning headache pill",
                nickname = "Morning headache pill",
                dosage = "2 pills, every 8h",
                substance = "Ibuprofen",
                reminderText = "Daily at 8:00 AM, 4:00 PM, 11:59 PM, 12:30 AM, 1:00 AM",
            ),
            labels = previewLabels,
            onLogSymptom = {},
            onEdit = {},
            onDelete = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
