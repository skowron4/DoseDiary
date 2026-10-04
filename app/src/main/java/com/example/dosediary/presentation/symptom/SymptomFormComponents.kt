package com.example.dosediary.presentation.symptom

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dosediary.R
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag
import com.example.dosediary.presentation.common.SeverityHigh
import com.example.dosediary.presentation.common.SeverityLow
import com.example.dosediary.presentation.common.SeverityMid
import com.example.dosediary.presentation.common.labelRes
import com.example.dosediary.presentation.common.severityColor
import com.example.dosediary.presentation.common.severityEmoji
import com.example.dosediary.presentation.common.severityLabelRes

private val ThumbSize = 28.dp
private val TrackHeight = 10.dp

/**
 * Severity picker: a stepped (1..10) slider on a green -> yellow -> red track, with a header that
 * shows the matching emoji, level label and number, all tinted with the current severity color.
 */
@Composable
fun SeveritySelector(
    severity: Int,
    onSeverityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(severityColor(severity), label = "severityColor")

    Column(modifier = modifier) {
        Text(stringResource(R.string.severity_label), style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(severityEmoji(severity), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(severityLabelRes(severity)),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$severity / ${Symptom.MAX_SEVERITY}",
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
        }

        SeveritySlider(
            severity = severity,
            thumbColor = color,
            onSeverityChange = onSeverityChange,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.severity_mild),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.severity_severe),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Discrete slider. `steps` makes it snap to whole numbers, while the custom `track` draws no tick
 * marks at all: just a rounded gradient bar that is bright up to the thumb and dimmed after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeveritySlider(
    severity: Int,
    thumbColor: Color,
    onSeverityChange: (Int) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Slider(
        value = severity.toFloat(),
        onValueChange = { onSeverityChange(it.toInt()) },
        valueRange = Symptom.MIN_SEVERITY.toFloat()..Symptom.MAX_SEVERITY.toFloat(),
        steps = Symptom.MAX_SEVERITY - Symptom.MIN_SEVERITY - 1,
        thumb = {
            Box(
                modifier = Modifier
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .border(3.dp, surface, CircleShape),
            )
        },
        track = { state -> SeverityTrack(state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeverityTrack(state: SliderState) {
    val fraction = (state.value - state.valueRange.start) /
        (state.valueRange.endInclusive - state.valueRange.start)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .drawBehind {
                val brush = Brush.horizontalGradient(
                    colors = listOf(SeverityLow, SeverityMid, SeverityHigh),
                    startX = 0f,
                    endX = size.width,
                )
                val radius = CornerRadius(size.height / 2f)
                // Unselected part: dimmed gradient.
                drawRoundRect(brush = brush, size = Size(size.width, size.height), cornerRadius = radius, alpha = 0.3f)
                // Selected part: full-strength gradient up to the thumb centre.
                val thumbRadius = ThumbSize.toPx() / 2f
                val filledX = thumbRadius + fraction * (size.width - 2 * thumbRadius)
                clipRect(right = filledX) {
                    drawRoundRect(brush = brush, size = Size(size.width, size.height), cornerRadius = radius)
                }
            },
    )
}

/**
 * One-tap symptom chips. Tapping a chip toggles it; the free-text notes field below the chips stays
 * available for anything the chips do not cover.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTagSelector(
    selected: Set<SymptomTag>,
    onToggle: (SymptomTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.quick_tags_label), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SymptomTag.entries.forEach { tag ->
                val isSelected = tag in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(tag) },
                    label = { Text(stringResource(tag.labelRes())) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
