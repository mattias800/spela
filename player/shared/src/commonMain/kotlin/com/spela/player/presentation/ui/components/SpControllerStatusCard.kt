package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.libretro.PortStatus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Card showing controller status for the navigation rail.
 *
 * Displays a "CONTROLLERS" label and a [SpControllerStatusRow] with all 4 slots.
 * Clickable — navigates to controller settings. Focusable for gamepad navigation.
 *
 * @param ports The list of port statuses to display.
 * @param onClick Called when the card is clicked or selected.
 */
@Composable
fun SpControllerStatusCard(
    ports: List<PortStatus>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    val bgAlpha = if (isFocused) 0.1f else 0.05f
    val borderAlpha = if (isFocused) 0.15f else 0.08f

    val connectedCount = ports.count { it.connected }

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "$connectedCount controllers connected"
                role = Role.Button
            },
    ) {
        Text(
            text = "CONTROLLERS",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            letterSpacing = SpTypography.LabelSmall.letterSpacing,
        )

        SpControllerStatusRow(
            ports = ports,
            showEmptySlots = true,
            modifier = Modifier.padding(top = SpSpacing.XSmall),
        )
    }
}
