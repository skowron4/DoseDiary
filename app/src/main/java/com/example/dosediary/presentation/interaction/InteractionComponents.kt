package com.example.dosediary.presentation.interaction

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.interaction.InteractionSeverity
import com.example.dosediary.domain.interaction.InteractionWarning
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.ui.theme.DoseDiaryTheme

/**
 * Interaction status of a medication: a warning card when the label mentions another saved
 * medication, and quiet one-line notes for "checking", "nothing found" and "could not check".
 */
@Composable
fun InteractionBanner(
    state: InteractionUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        InteractionUiState.Idle -> Unit

        InteractionUiState.Checking -> InteractionNote(
            text = stringResource(R.string.interaction_checking),
            modifier = modifier,
            leading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
        )

        InteractionUiState.NothingFound -> InteractionNote(
            text = stringResource(R.string.interaction_nothing_found),
            modifier = modifier,
            leading = { Icon(Icons.Default.Info, contentDescription = null, Modifier.size(18.dp)) },
        )

        is InteractionUiState.Unavailable -> InteractionNote(
            text = stringResource(
                if (state.error == null) R.string.interaction_no_data else R.string.interaction_unavailable,
            ),
            modifier = modifier,
            leading = { Icon(Icons.Default.Info, contentDescription = null, Modifier.size(18.dp)) },
            action = if (state.error != null) {
                { TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
            } else {
                null
            },
        )

        is InteractionUiState.Warnings -> InteractionWarningCard(state, modifier)
    }
}

/** Prominent card listing every potential interaction, tinted by the most serious one. */
@Composable
fun InteractionWarningCard(
    state: InteractionUiState.Warnings,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = state.highestSeverity.containerColor(),
            contentColor = state.highestSeverity.contentColor(),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text(stringResource(R.string.interaction_title), style = MaterialTheme.typography.titleMedium)
            }
            InteractionWarningList(state.items)
            Text(
                text = stringResource(R.string.interaction_disclaimer),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The individual warnings (medication, severity, quoted label sentence), without any card chrome. */
@Composable
fun InteractionWarningList(items: List<InteractionWarning>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { warning ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        R.string.interaction_item_title,
                        warning.savedMedicationName,
                        stringResource(warning.severity.labelRes()),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "\u201C${warning.excerpt}\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun InteractionNote(
    text: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@StringRes
fun InteractionSeverity.labelRes(): Int = when (this) {
    InteractionSeverity.HIGH -> R.string.interaction_severity_high
    InteractionSeverity.MODERATE -> R.string.interaction_severity_moderate
    InteractionSeverity.INFO -> R.string.interaction_severity_info
}

@Composable
private fun InteractionSeverity.containerColor() = when (this) {
    InteractionSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
    InteractionSeverity.MODERATE -> MaterialTheme.colorScheme.tertiaryContainer
    InteractionSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun InteractionSeverity.contentColor() = when (this) {
    InteractionSeverity.HIGH -> MaterialTheme.colorScheme.onErrorContainer
    InteractionSeverity.MODERATE -> MaterialTheme.colorScheme.onTertiaryContainer
    InteractionSeverity.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Preview(name = "High and info warnings", showBackground = true)
@Composable
private fun InteractionBannerWarningPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        InteractionBanner(
            state = InteractionUiState.Warnings(
                listOf(
                    InteractionWarning(
                        savedMedicationId = "w",
                        savedMedicationName = "Coumadin",
                        matchedTerm = "warfarin",
                        excerpt = "Concomitant use with warfarin increases the risk of serious bleeding.",
                        severity = InteractionSeverity.HIGH,
                    ),
                    InteractionWarning(
                        savedMedicationId = "a",
                        savedMedicationName = "Aspirin",
                        matchedTerm = "aspirin",
                        excerpt = "Aspirin was studied in combination.",
                    ),
                ),
            ),
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Offline", showBackground = true)
@Composable
private fun InteractionBannerOfflinePreview() {
    DoseDiaryTheme(dynamicColor = false) {
        InteractionBanner(
            state = InteractionUiState.Unavailable(DomainError.NoInternet),
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
