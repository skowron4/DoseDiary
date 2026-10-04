package com.example.dosediary.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.toUiMessage

/**
 * Material 3 search bar used at the top of the Home screen.
 *
 * It stays collapsed (`expanded = false`) on purpose: results are rendered inline in the Home list
 * rather than in the M3 full-screen search overlay, so the list below keeps scrolling normally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val focusManager = LocalFocusManager.current
    DockedSearchBar(
        modifier = modifier.fillMaxWidth(),
        expanded = false,
        onExpandedChange = {},
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.focusRequester(focusRequester),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { focusManager.clearFocus() },
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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

/**
 * Emits the OpenFDA search outcome (idle / loading / error / empty / results) as lazy list items.
 * Written as a [LazyListScope] extension so it can live inside the Home screen's single list.
 */
fun LazyListScope.openFdaResultItems(
    state: SearchUiState,
    onRetry: () -> Unit,
    onSave: (Medication) -> Unit,
) {
    when (val result = state.result) {
        SearchResultState.Idle -> item(key = "fda-idle") {
            EmptyState(
                icon = Icons.Default.Search,
                message = stringResource(R.string.search_idle),
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
