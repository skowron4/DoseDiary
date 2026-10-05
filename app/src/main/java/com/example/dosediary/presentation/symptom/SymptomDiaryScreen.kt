package com.example.dosediary.presentation.symptom

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.TopLevelContentInsets
import com.example.dosediary.presentation.common.formatDateTime
import org.koin.androidx.compose.koinViewModel

/**
 * The symptom diary tab: every logged symptom, newest first. Tapping an entry edits it; the
 * floating button logs a new, medication-independent entry.
 */
@Composable
fun SymptomDiaryScreen(
    onLogSymptom: () -> Unit,
    onEditSymptom: (symptomId: Long) -> Unit,
    viewModel: SymptomDiaryViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Symptom?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Scaffold(
        contentWindowInsets = TopLevelContentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLogSymptom,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_log_symptom)) },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                SymptomDiaryUiState.Loading -> LoadingState()
                is SymptomDiaryUiState.Error -> ErrorState(message = stringResource(R.string.error_storage))
                is SymptomDiaryUiState.Success -> SymptomList(
                    symptoms = state.symptoms,
                    onEdit = onEditSymptom,
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }

    pendingDelete?.let { symptom ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_symptom_title)) },
            text = { Text(stringResource(R.string.delete_symptom_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSymptom(symptom)
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

@Composable
private fun SymptomList(
    symptoms: List<Symptom>,
    onEdit: (symptomId: Long) -> Unit,
    onDelete: (Symptom) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last card clear of the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.symptoms_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (symptoms.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Default.Add,
                    message = stringResource(R.string.symptoms_empty),
                )
            }
        } else {
            items(symptoms, key = { "symptom-${it.id}" }, contentType = { "symptom" }) { symptom ->
                SymptomCard(
                    symptom = symptom,
                    onClick = { onEdit(symptom.id) },
                    onDelete = { onDelete(symptom) },
                )
            }
        }
    }
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
