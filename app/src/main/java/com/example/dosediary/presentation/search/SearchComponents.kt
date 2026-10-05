package com.example.dosediary.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.toUiMessage

/**
 * Read-only, search-bar-looking entry point shown on the Home screen. It is deliberately not a
 * text field: tapping it opens the full-screen search overlay, where the real input lives.
 */
@Composable
fun SearchEntryBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The editable search field of the overlay: back arrow on the left, clear button on the right.
 * Requests focus once when it enters the composition so the keyboard opens immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DockedSearchBar(
        modifier = modifier.fillMaxWidth(),
        expanded = false,
        onExpandedChange = {},
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.focusRequester(focusRequester),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch() },
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
        content = {},
    )
}

/** Section title used inside the overlay's list. */
@Composable
fun SearchSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

/** Recent searches: tap a row to re-run it, tap the X to forget it, or clear everything. */
fun LazyListScope.searchHistoryItems(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (history.isEmpty()) {
        item(key = "history-empty") {
            EmptyState(
                icon = Icons.Default.Search,
                message = stringResource(R.string.search_idle),
            )
        }
        return
    }

    item(key = "history-header") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchSectionHeader(
                text = stringResource(R.string.search_recent),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearAll) { Text(stringResource(R.string.search_clear_history)) }
        }
    }
    items(history, key = { "history-$it" }) { query ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(query) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            IconButton(onClick = { onRemove(query) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_remove_history_item, query),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Saved medications that match the query ("In my diary"). */
fun LazyListScope.localMatchItems(
    matches: List<Medication>,
    onLogSymptom: (Medication) -> Unit,
) {
    item(key = "local-header") { SearchSectionHeader(stringResource(R.string.section_in_my_diary)) }
    if (matches.isEmpty()) {
        item(key = "local-empty") {
            Text(
                text = stringResource(R.string.search_no_local_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(matches, key = { "local-${it.id}" }) { medication ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(medication.displayName, style = MaterialTheme.typography.titleMedium)
                        medication.displaySubtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onLogSymptom(medication) }) {
                        Text(stringResource(R.string.action_log_symptom))
                    }
                }
            }
        }
    }
}

/**
 * Emits the OpenFDA search outcome (idle / loading / error / empty / results) as lazy list items.
 * Written as a [LazyListScope] extension so it can live inside the overlay's single list.
 */
fun LazyListScope.openFdaResultItems(
    state: SearchUiState,
    onRetry: () -> Unit,
    onSave: (Medication) -> Unit,
) {
    when (val result = state.result) {
        // Debounce window / query still too short: nothing to show yet.
        SearchResultState.Idle -> item(key = "fda-idle") {
            Text(
                text = stringResource(R.string.search_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SearchResultState.Loading -> item(key = "fda-loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SearchResultState.Error -> item(key = "fda-error") {
            InlineError(
                message = result.error.toUiMessage().resolve(LocalContext.current),
                onRetry = onRetry,
            )
        }

        is SearchResultState.Success -> if (result.medications.isEmpty()) {
            item(key = "fda-empty") {
                EmptyState(
                    icon = Icons.Default.Search,
                    message = stringResource(R.string.search_no_results),
                )
            }
        } else {
            items(count = result.medications.size, key = { "fda-${result.medications[it].id}" }) { index ->
                val medication = result.medications[index]
                OpenFdaResultCard(
                    medication = medication,
                    isSaved = medication.id in state.savedIds,
                    onSave = { onSave(medication) },
                )
            }
        }
    }
}

/** Error block sized for use inside a lazy list (the full-screen `ErrorState` would not fit). */
@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun OpenFdaResultCard(
    medication: Medication,
    isSaved: Boolean,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Search results are not saved yet, so there is never a nickname: commercial name leads.
            Text(medication.displayName, style = MaterialTheme.typography.titleMedium)
            medication.displaySubtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            medication.manufacturer?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            medication.purpose?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isSaved) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                        Text(stringResource(R.string.action_saved), Modifier.padding(start = 8.dp))
                    }
                } else {
                    Button(onClick = onSave) { Text(stringResource(R.string.action_save_medication)) }
                }
            }
        }
    }
}
