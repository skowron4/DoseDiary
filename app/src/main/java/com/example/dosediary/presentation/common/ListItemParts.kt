package com.example.dosediary.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A 48dp tappable icon for list items.
 *
 * Replaces Material's `IconButton` inside lazy list cards: it is a single clipped, clickable `Box`
 * instead of the full button (extra surface, minimum-interactive-size wrapper and composition locals),
 * and it is created for every card that scrolls into view.
 *
 * @param description Already-resolved accessibility text; resolve it once per list, not per item.
 */
@Composable
fun ListItemIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description)
    }
}
