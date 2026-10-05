package com.example.dosediary.presentation.medication

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.format
import com.example.dosediary.presentation.interaction.InteractionBanner

/** Which time the picker dialog is editing: a new intake, or the time of an existing one. */
private sealed interface TimeEdit {
    data object New : TimeEdit
    data class Existing(val intakeId: Int, val time: ReminderTime) : TimeEdit
}

/**
 * Edit form for a saved medication: custom name, frequency ("every day" / "every X days") and any
 * number of intakes, each with a time, a dose and a notification switch.
 *
 * Notification permissions are only requested when the user switches a notification on; the flow is
 * driven by [EditMedicationViewModel.uiState]'s `permissionPrompt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMedicationScreen(
    onNavigateUp: () -> Unit,
    viewModel: EditMedicationViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onNotificationPermissionResult,
    )

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { silent ->
            val message = if (silent) R.string.reminders_saved_no_permission else R.string.medication_updated
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onNavigateUp()
        }
    }

    LaunchedEffect(state.permissionPrompt) {
        if (state.permissionPrompt == PermissionPrompt.REQUEST_NOTIFICATIONS) {
            // Clear the prompt first: if the activity is recreated while the system dialog is open, the
            // request must not be started a second time (the result still reaches the ViewModel).
            viewModel.onPermissionPromptHandled()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationPermissionResult(granted = true)
            }
        }
    }

    var timeEdit by remember { mutableStateOf<TimeEdit?>(null) }

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
                    onFrequencyModeChange = viewModel::onFrequencyModeChange,
                    onFrequencyDaysChange = viewModel::onFrequencyDaysChange,
                    onAddIntake = { timeEdit = TimeEdit.New },
                    onChangeIntakeTime = { draft -> timeEdit = TimeEdit.Existing(draft.id, draft.time) },
                    onIntakeDoseChange = viewModel::onIntakeDoseChange,
                    onIntakeNotifyChange = viewModel::onIntakeNotifyChange,
                    onRemoveIntake = viewModel::onRemoveIntake,
                    onRetryInteractions = viewModel::retryInteractionCheck,
                    onSave = viewModel::save,
                )
            }
        }
    }

    timeEdit?.let { edit ->
        IntakeTimePickerDialog(
            initial = (edit as? TimeEdit.Existing)?.time ?: ReminderTime(8, 0),
            confirmText = stringResource(if (edit is TimeEdit.New) R.string.action_add else R.string.action_ok),
            onDismiss = { timeEdit = null },
            onConfirm = { time ->
                timeEdit = null
                when (edit) {
                    TimeEdit.New -> viewModel.onAddIntake(time)
                    is TimeEdit.Existing -> viewModel.onIntakeTimeChange(edit.intakeId, time)
                }
            },
        )
    }

    when (state.permissionPrompt) {
        PermissionPrompt.NOTIFICATIONS_BLOCKED -> PermissionDialog(
            title = stringResource(R.string.permission_notifications_blocked_title),
            text = stringResource(R.string.permission_notifications_blocked_text),
            onOpenSettings = {
                viewModel.onPermissionPromptHandled()
                context.openNotificationSettings()
            },
            onDismiss = viewModel::onPermissionPromptHandled,
        )

        PermissionPrompt.EXPLAIN_EXACT_ALARMS -> PermissionDialog(
            title = stringResource(R.string.permission_exact_alarms_title),
            text = stringResource(R.string.permission_exact_alarms_text),
            onOpenSettings = {
                viewModel.onPermissionPromptHandled()
                context.openExactAlarmSettings()
            },
            onDismiss = viewModel::onPermissionPromptHandled,
        )

        // The system dialog is launched by the effect above; nothing to draw.
        PermissionPrompt.REQUEST_NOTIFICATIONS, null -> Unit
    }
}

@Composable
private fun EditMedicationForm(
    state: EditMedicationUiState,
    onNicknameChange: (String) -> Unit,
    onFrequencyModeChange: (FrequencyMode) -> Unit,
    onFrequencyDaysChange: (String) -> Unit,
    onAddIntake: () -> Unit,
    onChangeIntakeTime: (IntakeDraft) -> Unit,
    onIntakeDoseChange: (id: Int, String) -> Unit,
    onIntakeNotifyChange: (id: Int, Boolean) -> Unit,
    onRemoveIntake: (id: Int) -> Unit,
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

        SectionTitle(stringResource(R.string.frequency_section_title))
        FrequencySection(
            mode = state.frequencyMode,
            daysText = state.frequencyDaysText,
            onModeChange = onFrequencyModeChange,
            onDaysChange = onFrequencyDaysChange,
        )

        SectionTitle(stringResource(R.string.intakes_section_title))
        if (state.intakes.isEmpty()) {
            Text(
                text = stringResource(R.string.intakes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.intakes.forEach { draft ->
                // The id is the stable key: rows keep their text field state when the list is re-sorted.
                key(draft.id) {
                    IntakeRow(
                        draft = draft,
                        onChangeTime = { onChangeIntakeTime(draft) },
                        onDoseChange = { onIntakeDoseChange(draft.id, it) },
                        onNotifyChange = { onIntakeNotifyChange(draft.id, it) },
                        onRemove = { onRemoveIntake(draft.id) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onAddIntake,
            enabled = state.intakes.size < Medication.MAX_INTAKES,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.action_add_intake), Modifier.padding(start = 8.dp))
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
private fun FrequencySection(
    mode: FrequencyMode,
    daysText: String,
    onModeChange: (FrequencyMode) -> Unit,
    onDaysChange: (String) -> Unit,
) {
    Column {
        FrequencyOption(
            text = stringResource(R.string.frequency_every_day),
            selected = mode == FrequencyMode.EVERY_DAY,
            onSelect = { onModeChange(FrequencyMode.EVERY_DAY) },
        )
        FrequencyOption(
            text = stringResource(R.string.frequency_option_every_n_days),
            selected = mode == FrequencyMode.EVERY_N_DAYS,
            onSelect = { onModeChange(FrequencyMode.EVERY_N_DAYS) },
        )
        if (mode == FrequencyMode.EVERY_N_DAYS) {
            OutlinedTextField(
                value = daysText,
                onValueChange = onDaysChange,
                modifier = Modifier
                    .padding(start = 48.dp, top = 4.dp)
                    .width(160.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.frequency_days_label)) },
                supportingText = { Text(stringResource(R.string.frequency_start_hint)) },
            )
        }
    }
}

@Composable
private fun FrequencyOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One intake: tap the time to change it, type the dose, switch the reminder on or off. */
@Composable
private fun IntakeRow(
    draft: IntakeDraft,
    onChangeTime: () -> Unit,
    onDoseChange: (String) -> Unit,
    onNotifyChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val formatted = draft.time.format()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = stringResource(R.string.action_change_intake_time, formatted),
                        onClick = onChangeTime,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(R.string.intake_notify_label), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = draft.notify, onCheckedChange = onNotifyChange)
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove_intake, formatted),
                )
            }
        }
        OutlinedTextField(
            value = draft.dose,
            onValueChange = onDoseChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.dose_amount_label)) },
            placeholder = { Text(stringResource(R.string.dose_amount_placeholder)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntakeTimePickerDialog(
    initial: ReminderTime,
    confirmText: String,
    onConfirm: (ReminderTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.intake_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = pickerState) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderTime(pickerState.hour, pickerState.minute)) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PermissionDialog(
    title: String,
    text: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.permission_not_now)) }
        },
    )
}

/** Opens the app's notification settings (where a permanently denied permission can be re-enabled). */
private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    startSettings(intent, fallback = appDetailsIntent())
}

/** Opens the "alarms & reminders" special access page for this app (Android 12+). */
private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    startSettings(intent, fallback = appDetailsIntent())
}

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

private fun Context.startSettings(intent: Intent, fallback: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(fallback)
        } catch (_: ActivityNotFoundException) {
            // No settings screen available on this device; nothing more to offer.
        }
    }
}
