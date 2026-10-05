package com.example.dosediary.presentation.symptom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dosediary.ui.theme.DoseDiaryTheme

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * Read-only tag chips of a symptom entry in one plain [Row].
 *
 * There is no wrapping and no "how many fit?" measurement. [SymptomItemUi] already limits the list to
 * a few tags and supplies the "+N" text, so this only places them: each tag chip may shrink and
 * truncate to one line (`weight(fill = false)`), while the "+N" chip keeps its natural width and is
 * always visible.
 *
 * Chips are a single `Text` with a background; there is no Material `Surface`/`AssistChip` per tag.
 *
 * @param tags Final, localised labels.
 * @param hiddenText "+N" for tags that are not listed, or `null`.
 */
@Composable
fun SymptomTagRow(
    tags: List<String>,
    hiddenText: String?,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tag in tags) {
            TagChip(text = tag, modifier = Modifier.weight(1f, fill = false))
        }
        if (hiddenText != null) TagChip(text = hiddenText)
    }
}

@Composable
private fun TagChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(colors.secondaryContainer, ChipShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Preview(name = "Few tags", showBackground = true, widthDp = 320)
@Composable
private fun SymptomTagRowFewPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        SymptomTagRow(listOf("Headache", "Nausea"), hiddenText = null, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "More tags show +N", showBackground = true, widthDp = 320)
@Composable
private fun SymptomTagRowOverflowPreview() {
    DoseDiaryTheme(dynamicColor = false) {
        SymptomTagRow(
            listOf("Headache", "Nausea", "Stomach pain"),
            hiddenText = "+7",
            modifier = Modifier.padding(16.dp),
        )
    }
}
