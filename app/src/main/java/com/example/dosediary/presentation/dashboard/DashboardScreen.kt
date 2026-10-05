package com.example.dosediary.presentation.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.TopLevelContentInsets
import com.example.dosediary.presentation.search.SearchEntryBar
import org.koin.androidx.compose.koinViewModel

/**
 * The Medications tab: the user's saved medications, with a sticky search entry bar on top.
 *
 * The bar is only an entry point: tapping it opens the full-screen search overlay
 * ([onOpenSearch]), so search results never reflow this list.
 */
@Composable
fun DashboardScreen(
    onLogSymptom: (medicationId: String?) -> Unit,
    onEditMedication: (medicationId: String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Scaffold(
        contentWindowInsets = TopLevelContentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = uiState,
            onOpenSearch = onOpenSearch,
            onLogSymptomFor = { onLogSymptom(it.id) },
            onEditMedication = { onEditMedication(it.id) },
            onDeleteMedication = { pendingDelete = it },
        )
    }

    pendingDelete?.let { medication ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_medication_title)) },
            text = { Text(stringResource(R.string.delete_medication_text, medication.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMedication(medication)
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
    onEditMedication: (Medication) -> Unit,
    onDeleteMedication: (Medication) -> Unit,
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
            onLogSymptom = onLogSymptomFor,
            onEdit = onEditMedication,
            onDelete = onDeleteMedication,
            onOpenSearch = onOpenSearch,
        )
    }
}

/** Default content: the saved medications. */
private fun LazyListScope.browseSections(
    state: DashboardUiState,
    onLogSymptom: (Medication) -> Unit,
    onEdit: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
    onOpenSearch: () -> Unit,
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
                medicationItems(state.medications, onLogSymptom, onEdit, onDelete)
            }
        }
    }
}

private fun LazyListScope.medicationItems(
    medications: List<Medication>,
    onLogSymptom: (Medication) -> Unit,
    onEdit: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
) {
    items(medications, key = { "med-${it.id}" }, contentType = { "medication" }) { medication ->
        MedicationCard(
            medication = medication,
            onLogSymptom = { onLogSymptom(medication) },
            onEdit = { onEdit(medication) },
            onDelete = { onDelete(medication) },
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
