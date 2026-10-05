package com.example.dosediary.presentation.symptom

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSymptomScreen(
    onNavigateUp: () -> Unit,
    viewModel: AddSymptomViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.saved.collect { onNavigateUp() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.edit_symptom_title else R.string.add_symptom_title,
                        ),
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
                state.loadFailed -> ErrorState(message = stringResource(R.string.error_symptom_not_found))
                else -> SymptomForm(
                    state = state,
                    onMedicationSelected = viewModel::onMedicationSelected,
                    onSeverityChange = viewModel::onSeverityChange,
                    onTagToggled = viewModel::onTagToggled,
                    onNotesChange = viewModel::onNotesChange,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun SymptomForm(
    state: AddSymptomUiState,
    onMedicationSelected: (String?) -> Unit,
    onSeverityChange: (Int) -> Unit,
    onTagToggled: (SymptomTag) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MedicationDropdown(
            medications = state.medications,
            selectedId = state.selectedMedicationId,
            onSelected = onMedicationSelected,
        )

        SeveritySelector(
            severity = state.severity,
            onSeverityChange = onSeverityChange,
        )

        QuickTagSelector(
            selected = state.selectedTags,
            onToggle = onTagToggled,
        )

        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.notes_label)) },
            minLines = 3,
            maxLines = 6,
            supportingText = {
                Text(
                    text = stringResource(R.string.counter_of, state.notes.length, Symptom.MAX_NOTES_LENGTH),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
        )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDropdown(
    medications: List<Medication>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val generalLabel = stringResource(R.string.symptom_general)
    val selectedLabel = medications.firstOrNull { it.id == selectedId }?.displayName ?: generalLabel

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.related_medication_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(generalLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            medications.forEach { medication ->
                DropdownMenuItem(
                    text = { Text(medication.displayName) },
                    onClick = {
                        onSelected(medication.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
