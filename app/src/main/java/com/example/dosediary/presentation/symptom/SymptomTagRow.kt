package com.example.dosediary.presentation.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.presentation.common.labelRes
import com.example.dosediary.ui.theme.DoseDiaryTheme

/**
 * Read-only chips for the tags of a symptom entry, limited to a single row.
 *
 * Tags that do not fit are not wrapped onto more lines; they are replaced by a "+N" chip, where N is
 * the number of hidden tags. The indicator reserves its own space, so it is always visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomTagRow(
    tags: Collection<SymptomTag>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        maxLines = 1,
        overflow = FlowRowOverflow.expandIndicator {
            TagChip(text = stringResource(R.string.symptom_tags_more, totalItemCount - shownItemCount))
        },
    ) {
        tags.forEach { tag -> TagChip(text = stringResource(tag.labelRes())) }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview(name = "Few tags", showBackground = true, widthDp = 320)
@Composable
private fun SymptomTagRowFewPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        SymptomTagRow(listOf(SymptomTag.HEADACHE, SymptomTag.NAUSEA), Modifier.padding(16.dp))
    }
}

@Preview(name = "Overflow shows +N", showBackground = true, widthDp = 320)
@Composable
private fun SymptomTagRowOverflowPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        SymptomTagRow(SymptomTag.entries, Modifier.padding(16.dp))
    }
}
