package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
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
    onGradient: Boolean = false,
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Merge descendants on the outer Box so a caller-supplied testTag /
    // contentDescription on `modifier` ends up on the same merged node as
    // the inner Box's click action. Without this, a test that finds the
    // outer Box by testTag and calls performClick can't reach the
    // click handler that lives on the inner Box.
    Box(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (onGradient) Color.Black.copy(alpha = 0.30f) else SpColor.SurfaceVariant)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .gamepadFocusable(shape = CircleShape, interactionSource = interactionSource)
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
