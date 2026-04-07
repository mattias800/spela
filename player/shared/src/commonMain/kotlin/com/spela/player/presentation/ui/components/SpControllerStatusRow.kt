package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.libretro.PortStatus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * A row of controller dots with player labels (P1, P2, etc.).
 *
 * Content-layer component that composes [SpControllerDot] instances.
 *
 * @param ports The list of port statuses to display.
 * @param showEmptySlots If true, shows all ports (connected + disconnected).
 *   If false, only shows connected ports.
 * @param dotSize Size of each dot.
 */
@Composable
fun SpControllerStatusRow(
    ports: List<PortStatus>,
    showEmptySlots: Boolean,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = SpSpacing.Small,
) {
    val visiblePorts = if (showEmptySlots) ports else ports.filter { it.connected }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visiblePorts.forEach { port ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
            ) {
                SpControllerDot(
                    connected = port.connected,
                    active = port.active,
                    port = port.port,
                    size = dotSize,
                )
                Text(
                    text = "P${port.port + 1}",
                    style = SpTypography.LabelSmall,
                    color = if (port.connected) SpColor.OnBackgroundSecondary else SpColor.OnBackgroundTertiary.copy(alpha = 0.5f),
                )
            }
        }
    }
}
