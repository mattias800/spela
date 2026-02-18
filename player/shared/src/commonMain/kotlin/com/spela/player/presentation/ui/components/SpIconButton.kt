package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor

/**
 * A circular icon button used in top bars and action areas.
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description for the button
 * @param onClick Called when the button is clicked
 * @param modifier Modifier for the root Box
 * @param badge Optional composable rendered as a badge overlay (e.g. notification dot)
 */
@Composable
fun SpIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .spFocusRing(shape = CircleShape)
                .clip(CircleShape)
                .background(SpColor.SurfaceVariant)
                .clickable(onClick = onClick)
                .focusable()
                .semantics {
                    this.contentDescription = contentDescription
                    this.role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SpColor.OnSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge != null) {
            badge()
        }
    }
}
