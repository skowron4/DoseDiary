package com.example.dosediary.presentation.dashboard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.TopLevelContentInsets
import com.example.dosediary.presentation.common.formatDateTime
import com.example.dosediary.presentation.common.labelRes
import com.example.dosediary.presentation.common.severityColor
import com.example.dosediary.presentation.search.SearchEntryBar
import org.koin.androidx.compose.koinViewModel

/**
 * The Home screen: the user's medications and symptom log, with a sticky search entry bar on top.
 *
 * The bar is only an entry point: tapping it opens the full-screen search overlay
 * ([onOpenSearch]), so search results never reflow this list. Medication cards are minimal and
 * open the medication's details screen ([onOpenMedication]).
 */
@Composable
fun DashboardScreen(
    onLogSymptom: (medicationId: String?) -> Unit,
    onEditSymptom: (symptomId: Long) -> Unit,
    onOpenMedication: (medicationId: String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
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
            onOpenMedication = { onOpenMedication(it.id) },
            onEditSymptom = { onEditSymptom(it.id) },
            onDeleteSymptom = { pendingDelete = it },
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: DashboardUiState,
    onOpenSearch: () -> Unit,
    onOpenMedication: (Medication) -> Unit,
    onEditSymptom: (Symptom) -> Unit,
    onDeleteSymptom: (Symptom) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onOpenMedication = onOpenMedication,
            onOpenSearch = onOpenSearch,
            onEditSymptom = onEditSymptom,
            onDeleteSymptom = onDeleteSymptom,
        )
    }
}

/** Default content: saved medications, then the symptom log. */
private fun LazyListScope.browseSections(
    state: DashboardUiState,
    onOpenMedication: (Medication) -> Unit,
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
                items(state.medications, key = { "med-${it.id}" }) { medication ->
                    MedicationCard(medication = medication, onClick = { onOpenMedication(medication) })
                }
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
                items(state.symptoms, key = { "symptom-${it.id}" }, contentType = { "symptom" }) { symptom ->
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
                if (symptom.tags.isNotEmpty()) {
                    Text(
                        text = symptom.tags.map { stringResource(it.labelRes()) }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
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
    // Same green -> yellow -> red scale as the severity slider; every stop is light enough for dark text.
    Surface(
        shape = CircleShape,
        color = severityColor(severity),
        contentColor = Color.Black,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(severity.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}