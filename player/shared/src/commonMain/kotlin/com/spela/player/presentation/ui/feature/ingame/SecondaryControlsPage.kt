package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Controls page for the secondary screen companion.
 *
 * Shows a P1/P2 port selector toggle above the platform touch controls,
 * allowing the user to choose which player port the on-screen buttons target.
 */
@Composable
fun SecondaryControlsPage(
    controller: LibretroController,
    touchControlPort: Int,
    onSelectPort: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Port selector toggle row
        PortSelectorRow(
            selectedPort = touchControlPort,
            onSelectPort = onSelectPort,
        )

        // Touch controls filling remaining space
        PlatformTouchControls(
            controller = controller,
            modifier = Modifier.weight(1f),
            port = touchControlPort,
        )
    }
}

/**
 * Segmented button row for selecting the touch control port (P1 / P2).
 */
@Composable
private fun PortSelectorRow(
    selectedPort: Int,
    onSelectPort: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Control port: Player ${selectedPort + 1}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortPillButton(
            label = "P1",
            port = 0,
            isSelected = selectedPort == 0,
            onSelect = onSelectPort,
        )

        Spacer(Modifier.width(SpSpacing.Small))

        PortPillButton(
            label = "P2",
            port = 1,
            isSelected = selectedPort == 1,
            onSelect = onSelectPort,
        )
    }
}

/**
 * A small pill-shaped button for port selection.
 */
@Composable
private fun PortPillButton(
    label: String,
    port: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val backgroundColor = if (isSelected) SpColor.Primary else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.OnBackground else SpColor.OnBackgroundSecondary
    val playerNumber = port + 1

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(SpSpacing.Small))
            .background(backgroundColor)
            .clickable { onSelect(port) }
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Player $playerNumber controls"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = textColor,
        )
    }
}
