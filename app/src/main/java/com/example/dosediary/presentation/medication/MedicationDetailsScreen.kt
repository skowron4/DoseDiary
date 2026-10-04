package com.example.dosediary.presentation.medication

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.format
import org.koin.androidx.compose.koinViewModel

/**
 * Details of one saved medication. This is where the actions that used to crowd the Home cards
 * live: daily reminder, nickname, logging a symptom and removing the medication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailsScreen(
    onNavigateUp: () -> Unit,
    onLogSymptom: (medicationId: String) -> Unit,
    viewModel: MedicationDetailsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showReminderPicker by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf<ReminderTime?>(null) }

    val medication = (state as? MedicationDetailsUiState.Success)?.medication

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val time = awaitingPermission
        if (medication != null && time != null) viewModel.setReminder(medication, time, granted)
        awaitingPermission = null
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onNavigateUp() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = medication?.displayName.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (medication != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val current = state) {
            MedicationDetailsUiState.Loading -> LoadingState(contentModifier)
            MedicationDetailsUiState.NotFound ->
                ErrorState(stringResource(R.string.error_medication_not_found), contentModifier)
            is MedicationDetailsUiState.Error ->
                ErrorState(stringResource(R.string.error_storage), contentModifier)
            is MedicationDetailsUiState.Success -> DetailsContent(
                medication = current.medication,
                onSetReminder = { showReminderPicker = true },
                onClearReminder = { viewModel.clearReminder(current.medication) },
                onEditNickname = { showNicknameDialog = true },
                onLogSymptom = { onLogSymptom(current.medication.id) },
                modifier = contentModifier,
            )
        }
    }

    if (medication != null && showNicknameDialog) {
        NicknameDialog(
            medication = medication,
            onDismiss = { showNicknameDialog = false },
            onConfirm = { nickname ->
                showNicknameDialog = false
                viewModel.setNickname(medication, nickname)
            },
        )
    }

    if (medication != null && showReminderPicker) {
        ReminderTimePickerDialog(
            initial = medication.reminderTime,
            onDismiss = { showReminderPicker = false },
            onConfirm = { time ->
                showReminderPicker = false
                val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled) {
                    awaitingPermission = time
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setReminder(medication, time, notificationsEnabled)
                }
            },
        )
    }

    if (medication != null && showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_medication_title)) },
            text = { Text(stringResource(R.string.delete_medication_text, medication.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.removeMedication(medication)
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailsContent(
    medication: Medication,
    onSetReminder: () -> Unit,
    onClearReminder: () -> Unit,
    onEditNickname: () -> Unit,
    onLogSymptom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(medication.displayName, style = MaterialTheme.typography.headlineSmall)
            medication.displaySubtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!medication.customUserNickname.isNullOrBlank()) {
                Text(
                    stringResource(R.string.nickname_original_name, medication.commercialName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            medication.manufacturer?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ReminderCard(
            reminder = medication.reminderTime,
            onSetReminder = onSetReminder,
            onClearReminder = onClearReminder,
        )

        OutlinedButton(onClick = onEditNickname, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.action_edit_nickname), Modifier.padding(start = 8.dp))
        }

        medication.purpose?.takeIf { it.isNotBlank() }?.let { purpose ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.medication_purpose_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(purpose, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = onLogSymptom, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.action_log_symptom), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderTime?,
    onSetReminder: () -> Unit,
    onClearReminder: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.reminder_section_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = if (reminder != null) {
                    stringResource(R.string.reminder_daily_at, reminder.format())
                } else {
                    stringResource(R.string.reminder_none)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSetReminder) {
                    Text(
                        stringResource(
                            if (reminder != null) R.string.action_change_reminder else R.string.action_set_reminder,
                        ),
                    )
                }
                if (reminder != null) {
                    TextButton(onClick = onClearReminder) { Text(stringResource(R.string.action_remove_reminder)) }
                }
            }
        }
    }
}
