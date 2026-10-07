package com.example.dosediary.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.presentation.interaction.InteractionWarningList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.koin.androidx.compose.koinViewModel

/**
 * Full-screen search overlay. It is a separate navigation destination, so the Home screen (and the
 * bottom bar) are not composed underneath while the user searches.
 *
 * - Query blank: recent searches.
 * - Query typed: matching saved medications, followed by OpenFDA results (with "Save" actions).
 */
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onLogSymptom: (medicationId: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    state.pendingInteraction?.let { pending ->
        InteractionConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmSaveDespiteInteractions,
            onDismiss = viewModel::dismissInteractionWarning,
        )
    }

    if (showCustomDialog) {
        CustomMedicationDialog(
            initialName = query.trim(),
            onSave = { name, substance, manufacturer, purpose, route ->
                showCustomDialog = false
                viewModel.saveCustom(name, substance, manufacturer, purpose, route)
            },
            onDismiss = { showCustomDialog = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SearchInputBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    viewModel.onSearchSubmitted()
                    focusManager.clearFocus()
                },
                onBack = onNavigateUp,
                // Scaffold does not inset its topBar slot (TopAppBar does that itself), so this
                // custom bar must keep clear of the status bar / display cutout on its own.
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    ) { padding ->
        SearchContent(
            query = query,
            state = state,
            onRetry = viewModel::retry,
            onSave = viewModel::save,
            onAddCustom = { showCustomDialog = true },
            onLogSymptom = { onLogSymptom(it.id) },
            onSelectHistory = {
                viewModel.onHistorySelected(it)
                focusManager.clearFocus()
            },
            onRemoveHistory = viewModel::removeHistoryEntry,
            onClearHistory = viewModel::clearAllHistory,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        )
    }
}

@Composable
private fun SearchContent(
    query: String,
    state: SearchUiState,
    onRetry: () -> Unit,
    onSave: (Medication) -> Unit,
    onAddCustom: () -> Unit,
    onLogSymptom: (Medication) -> Unit,
    onSelectHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank()) {
            searchHistoryItems(
                history = state.history,
                onSelect = onSelectHistory,
                onRemove = onRemoveHistory,
                onClearAll = onClearHistory,
            )
        } else {
            localMatchItems(state.localMatches, onLogSymptom)
            item(key = "fda-header") {
                SearchSectionHeader(stringResource(R.string.section_openfda))
            }
            openFdaResultItems(state, onRetry, onSave)
            addCustomMedicationItem(query.trim(), onAddCustom)
        }
    }
}

/** Shown when saving a search result would add a medication whose label mentions one the user already takes. */
@Composable
private fun InteractionConfirmDialog(
    pending: PendingInteraction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interaction_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pending.medication.displayName, style = MaterialTheme.typography.titleMedium)
                InteractionWarningList(pending.warnings)
                Text(
                    text = stringResource(R.string.interaction_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save_anyway)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
