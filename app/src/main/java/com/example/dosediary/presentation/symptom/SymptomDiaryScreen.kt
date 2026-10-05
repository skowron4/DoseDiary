package com.example.dosediary.presentation.symptom

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.presentation.common.EmptyState
import com.example.dosediary.presentation.common.ErrorState
import com.example.dosediary.presentation.common.ListItemIconAction
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.TopLevelContentInsets
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
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    // Item texts are formatted in the ViewModel; tell it when the language may have changed.
    val locales = LocalConfiguration.current.locales.toLanguageTags()
    LaunchedEffect(locales) { viewModel.onLocaleMaybeChanged() }

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
                    onDelete = { pendingDeleteId = it },
                )
            }
        }
    }

    pendingDeleteId?.let { symptomId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete_symptom_title)) },
            text = { Text(stringResource(R.string.delete_symptom_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSymptom(symptomId)
                        pendingDeleteId = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SymptomList(
    symptoms: List<SymptomItemUi>,
    onEdit: (symptomId: Long) -> Unit,
    onDelete: (symptomId: Long) -> Unit,
) {
    // Resolved once for the whole list, not once per card.
    val deleteDescription = stringResource(R.string.action_delete)

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
                    item = symptom,
                    deleteDescription = deleteDescription,
                    onClick = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

private val CardShape = RoundedCornerShape(12.dp)

/**
 * One logged symptom, built only from plain boxes, rows and text (no Material `Card`, `Surface` or
 * `IconButton`, no flow layouts). Receives final strings in [SymptomItemUi]; nothing is formatted here.
 */
@Composable
private fun SymptomCard(
    item: SymptomItemUi,
    deleteDescription: String,
    onClick: (symptomId: Long) -> Unit,
    onDelete: (symptomId: Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainerHigh)
            .clickable { onClick(item.id) }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeverityBadge(text = item.severityText, color = item.severityColor)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = typography.titleSmall,
                color = if (item.isLinkedToMedication) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.dateText,
                style = typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SymptomTagRow(
                tags = item.tags,
                hiddenText = item.hiddenTagsText,
                modifier = Modifier.padding(top = 6.dp),
            )
            item.notes?.let {
                Text(
                    text = it,
                    style = typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        ListItemIconAction(Icons.Default.Delete, deleteDescription, onClick = { onDelete(item.id) })
    }
}

/** A colored disc with the severity number (a `Box` with a background instead of a Material `Surface`). */
@Composable
private fun SeverityBadge(text: String, color: Color) {
    // Same green -> yellow -> red scale as the severity slider; every stop is light enough for dark text.
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}
