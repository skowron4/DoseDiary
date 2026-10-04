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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
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

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
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
        }
    }
}
