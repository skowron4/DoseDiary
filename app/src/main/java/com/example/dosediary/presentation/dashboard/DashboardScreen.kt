package com.example.dosediary.presentation.dashboard

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.TopLevelContentInsets
import com.example.dosediary.presentation.common.formatDateTime
import com.example.dosediary.presentation.common.toUiMessage
import com.example.dosediary.presentation.search.SearchEntryBar
import org.koin.androidx.compose.koinViewModel

private sealed interface PendingDelete {
    data class OfMedication(val medication: Medication) : PendingDelete
    data class OfSymptom(val symptom: Symptom) : PendingDelete
}

/**
 * The Home screen: the user's medications and symptom log, with a sticky search entry bar on top.
 *
 * The bar is only an entry point: tapping it opens the full-screen search overlay
 * ([onOpenSearch]), so search results never reflow this list.
 */
@Composable
fun DashboardScreen(
    onLogSymptom: (medicationId: String?) -> Unit,
    onEditSymptom: (symptomId: Long) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var reminderTarget by remember { mutableStateOf<Medication?>(null) }
    var nicknameTarget by remember { mutableStateOf<Medication?>(null) }
    var awaitingPermission by remember { mutableStateOf<Pair<Medication, ReminderTime>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        awaitingPermission?.let { (medication, time) -> viewModel.setReminder(medication, time, granted) }
        awaitingPermission = null
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Scaffold(
        contentWindowInsets = TopLevelContentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onLogSymptom(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_log_symptom)) },
            )
        },
    ) { padding ->
        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = uiState,
            onOpenSearch = onOpenSearch,
            onLogSymptomFor = { onLogSymptom(it.id) },
            onEditSymptom = { onEditSymptom(it.id) },
            onEditNickname = { nicknameTarget = it },
            onSetReminder = { reminderTarget = it },
            onClearReminder = viewModel::clearReminder,
            onDeleteMedication = { pendingDelete = PendingDelete.OfMedication(it) },
            onDeleteSymptom = { pendingDelete = PendingDelete.OfSymptom(it) },
        )
    }

    nicknameTarget?.let { medication ->
        NicknameDialog(
            medication = medication,
            onDismiss = { nicknameTarget = null },
            onConfirm = { nickname ->
                nicknameTarget = null
                viewModel.setNickname(medication, nickname)
            },
        )
    }

    reminderTarget?.let { medication ->
        ReminderTimePickerDialog(
            initial = medication.reminderTime,
            onDismiss = { reminderTarget = null },
            onConfirm = { time ->
                reminderTarget = null
                val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled) {
                    awaitingPermission = medication to time
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setReminder(medication, time, notificationsEnabled)
                }
            },
        )
    }

    pendingDelete?.let { target ->
        val (title, text) = when (target) {
            is PendingDelete.OfMedication -> stringResource(R.string.delete_medication_title) to
                stringResource(R.string.delete_medication_text, target.medication.displayName)
            is PendingDelete.OfSymptom -> stringResource(R.string.delete_symptom_title) to
                stringResource(R.string.delete_symptom_text)
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            is PendingDelete.OfMedication -> viewModel.removeMedication(target.medication)
                            is PendingDelete.OfSymptom -> viewModel.removeSymptom(target.symptom)
                        }
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: DashboardUiState,
    onOpenSearch: () -> Unit,
    onLogSymptomFor: (Medication) -> Unit,
    onEditSymptom: (Symptom) -> Unit,
    onEditNickname: (Medication) -> Unit,
    onSetReminder: (Medication) -> Unit,
    onClearReminder: (Medication) -> Unit,
    onDeleteMedication: (Medication) -> Unit,
    onDeleteSymptom: (Symptom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val medicationActions = MedicationActions(
        onLogSymptom = onLogSymptomFor,
        onEditNickname = onEditNickname,
        onSetReminder = onSetReminder,
        onClearReminder = onClearReminder,
        onDelete = onDeleteMedication,
    )

    LazyColumn(
        modifier = modifier,
        // No top padding: a sticky header would pin *below* it and leave a gap that items scroll through.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Stays pinned to the top while everything below scrolls underneath it.
        stickyHeader(key = "search", contentType = "search") {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                SearchEntryBar(
                    onClick = onOpenSearch,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        browseSections(
            state = state,
            actions = medicationActions,
            onOpenSearch = onOpenSearch,
            onEditSymptom = onEditSymptom,
            onDeleteSymptom = onDeleteSymptom,
        )
    }
}

private class MedicationActions(
    val onLogSymptom: (Medication) -> Unit,
    val onEditNickname: (Medication) -> Unit,
    val onSetReminder: (Medication) -> Unit,
    val onClearReminder: (Medication) -> Unit,
    val onDelete: (Medication) -> Unit,
)

/** Default content: saved medications, then the symptom log. */
private fun LazyListScope.browseSections(
    state: DashboardUiState,
    actions: MedicationActions,
    onOpenSearch: () -> Unit,
    onEditSymptom: (Symptom) -> Unit,
    onDeleteSymptom: (Symptom) -> Unit,
) {
    when (state) {
        DashboardUiState.Loading -> item(key = "loading") { LoadingRow() }

        is DashboardUiState.Error -> item(key = "error") {
            EmptyState(
                icon = Icons.Default.Search,
                message = stringResource(R.string.error_storage),
            )
        }

        is DashboardUiState.Success -> {
            item(key = "meds-header") { SectionHeader(stringResource(R.string.section_medications)) }

            if (state.medications.isEmpty()) {
                item(key = "meds-empty") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = Icons.Default.Search,
                            message = stringResource(R.string.medications_empty),
                        )
                        OutlinedButton(onClick = onOpenSearch) {
                            Text(stringResource(R.string.action_find_medications))
                        }
                    }
                }
            } else {
                medicationItems(state.medications, actions)
            }

            item(key = "symptoms-header") { SectionHeader(stringResource(R.string.section_symptoms)) }

            if (state.symptoms.isEmpty()) {
                item(key = "symptoms-empty") {
                    EmptyState(
                        icon = Icons.Default.Add,
                        message = stringResource(R.string.symptoms_empty),
                    )
                }
            } else {
                items(state.symptoms, key = { "symptom-${it.id}" }) { symptom ->
                    SymptomCard(
                        symptom = symptom,
                        onClick = { onEditSymptom(symptom) },
                        onDelete = { onDeleteSymptom(symptom) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.medicationItems(medications: List<Medication>, actions: MedicationActions) {
    items(medications, key = { "med-${it.id}" }) { medication ->
        MedicationCard(
            medication = medication,
            onLogSymptom = { actions.onLogSymptom(medication) },
            onEditNickname = { actions.onEditNickname(medication) },
            onSetReminder = { actions.onSetReminder(medication) },
            onClearReminder = { actions.onClearReminder(medication) },
            onDelete = { actions.onDelete(medication) },
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SymptomCard(
    symptom: Symptom,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SeverityBadge(symptom.severity)
            Column(Modifier.weight(1f)) {
                Text(
                    text = symptom.medicationName ?: stringResource(R.string.symptom_general),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = formatDateTime(symptom.loggedAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (symptom.notes.isNotBlank()) {
                    Text(
                        text = symptom.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: Int) {
    val (container, content) = when {
        severity <= 3 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        severity <= 7 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(severity.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initial: ReminderTime?,
    onConfirm: (ReminderTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 8,
        initialMinute = initial?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = state) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderTime(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
