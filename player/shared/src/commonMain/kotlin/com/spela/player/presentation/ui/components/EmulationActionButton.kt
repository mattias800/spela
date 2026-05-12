package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Shared circular action button used by both the secondary screen quick actions
 * and the in-game overlay panel.
 *
 * @param icon The icon to display
 * @param label Accessibility label (also shown as text when [showLabel] is true)
 * @param onClick Click handler
 * @param isActive Whether the button is in an active/toggled state
 * @param buttonSize Diameter of the circular icon container
 * @param iconSize Size of the icon inside the circle
 * @param showLabel Whether to show a text label below the icon
 * @param useFocusRing Whether to show a focus ring (for gamepad navigation)
 */
@Composable
fun EmulationActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    buttonSize: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    showLabel: Boolean = true,
    useFocusRing: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val alpha = when {
        isPressed -> 0.6f
        isActive -> 1f
        else -> 0.85f
    }

    val backgroundColor = when {
        isPressed -> SpColor.Primary.copy(alpha = 0.3f)
        isActive -> SpColor.Primary.copy(alpha = 0.2f)
        else -> SpColor.SurfaceBright
    }

    val iconTint = if (isActive || isPressed) SpColor.Primary else SpColor.OnBackground

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { this.alpha = alpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(if (useFocusRing) Modifier.gamepadFocusable(shape = RoundedCornerShape(16.dp), interactionSource = interactionSource, addFocusable = false) else Modifier)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(SpSpacing.XSmall))
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = if (isActive) SpColor.Primary else SpColor.OnBackgroundSecondary,
            )
        }
    }
}
