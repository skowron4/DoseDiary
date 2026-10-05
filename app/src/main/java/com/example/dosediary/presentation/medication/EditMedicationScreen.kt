package com.example.dosediary.presentation.medication

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.format
import com.example.dosediary.presentation.interaction.InteractionBanner

/** Edit form for a saved medication: nickname, dose, dosing interval and any number of daily reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMedicationScreen(
    onNavigateUp: () -> Unit,
    viewModel: EditMedicationViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)

    fun notificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Save either way: a denied permission only means the reminders will not be shown.
        viewModel.save()
    }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            val silent = currentState.reminderTimes.isNotEmpty() && !notificationsEnabled()
            val message = if (silent) R.string.reminders_saved_no_permission else R.string.medication_updated
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onNavigateUp()
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_medication_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.loadFailed -> ErrorState(message = stringResource(R.string.error_medication_load_failed))
                else -> EditMedicationForm(
                    state = state,
                    onNicknameChange = viewModel::onNicknameChange,
                    onDoseAmountChange = viewModel::onDoseAmountChange,
                    onIntervalChange = viewModel::onIntervalChange,
                    onAddReminder = { showTimePicker = true },
                    onRemoveReminder = viewModel::onRemoveReminder,
                    onRetryInteractions = viewModel::retryInteractionCheck,
                    onSave = {
                        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            state.reminderTimes.isNotEmpty() &&
                            !notificationsEnabled()
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.save()
                        }
                    },
                )
            }
        }
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                viewModel.onAddReminder(time)
            },
        )
    }
}

@Composable
private fun EditMedicationForm(
    state: EditMedicationUiState,
    onNicknameChange: (String) -> Unit,
    onDoseAmountChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onAddReminder: () -> Unit,
    onRemoveReminder: (ReminderTime) -> Unit,
    onRetryInteractions: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(state.commercialName, style = MaterialTheme.typography.titleLarge)
            state.activeSubstance?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        InteractionBanner(state = state.interactions, onRetry = onRetryInteractions)

        OutlinedTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.nickname_label)) },
            supportingText = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.nickname_hint_clear), Modifier.weight(1f))
                    Text("${state.nickname.length} / ${Medication.MAX_NICKNAME_LENGTH}", textAlign = TextAlign.End)
                }
            },
        )

        SectionTitle(stringResource(R.string.dose_section_title))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.doseAmount,
                onValueChange = onDoseAmountChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.dose_amount_label)) },
                placeholder = { Text(stringResource(R.string.dose_amount_placeholder)) },
            )
            OutlinedTextField(
                value = state.intervalText,
                onValueChange = onIntervalChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.dose_interval_label)) },
                suffix = { Text(stringResource(R.string.dose_interval_suffix)) },
            )
        }

        SectionTitle(stringResource(R.string.reminders_section_title))
        if (state.reminderTimes.isEmpty()) {
            Text(
                text = stringResource(R.string.reminders_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.reminderTimes.forEach { time ->
                ReminderRow(time = time, onRemove = { onRemoveReminder(time) })
            }
        }
        OutlinedButton(
            onClick = onAddReminder,
            enabled = state.reminderTimes.size < Medication.MAX_REMINDERS,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.action_add_reminder), Modifier.padding(start = 8.dp))
        }

        state.errorMessage?.let { message ->
            Text(
                text = message.resolve(LocalContext.current),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ReminderRow(time: ReminderTime, onRemove: () -> Unit) {
    val formatted = time.format()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(formatted, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.action_remove_reminder, formatted),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    onConfirm: (ReminderTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = 8,
        initialMinute = 0,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = pickerState) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderTime(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
