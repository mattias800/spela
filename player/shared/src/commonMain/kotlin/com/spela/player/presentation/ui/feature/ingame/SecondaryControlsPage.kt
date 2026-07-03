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
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.spela.player.presentation.state.ControlTab
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Controls page for the secondary screen companion.
 *
 * Shows a segmented control (Gamepad | Keyboard | Trackpad) above the
 * active input mode content. The Gamepad tab includes the P1/P2 port
 * selector and platform touch controls.
 */
@Composable
fun SecondaryControlsPage(
    controller: LibretroController,
    touchControlPort: Int,
    selectedTab: ControlTab,
    consoleId: String,
    onSelectPort: (Int) -> Unit,
    onSelectTab: (ControlTab) -> Unit,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseButton: (left: Boolean, right: Boolean) -> Unit,
    /** Current core framebuffer size — passed to the trackpad so it can
     *  scale finger deltas to game-pixel deltas without the perceived
     *  cursor speed depending on the core's native resolution (#858). */
    gameWidth: Int = 0,
    gameHeight: Int = 0,
    /** Wii + Touch Pointer session (#1581): surfaces the Pointer tab and
     *  its IR touch surface, sized to [pointerAspectRatio]. */
    showPointerTab: Boolean = false,
    pointerAspectRatio: Float = 4f / 3f,
    modifier: Modifier = Modifier,
) {
    // POINTER is only offered for Wii + Touch Pointer sessions.
    val tabs = ControlTab.entries.filter { it != ControlTab.POINTER || showPointerTab }
    // Guard against a stale persisted "pointer" tab when it isn't available.
    val activeTab = if (selectedTab in tabs) selectedTab else ControlTab.GAMEPAD

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Segmented control tab selector
        ControlTabSelector(
            tabs = tabs,
            selectedTab = activeTab,
            onSelectTab = onSelectTab,
        )

        // Tab content filling remaining space
        when (activeTab) {
            ControlTab.GAMEPAD -> GamepadTabContent(
                controller = controller,
                touchControlPort = touchControlPort,
                onSelectPort = onSelectPort,
                modifier = Modifier.weight(1f),
            )
            ControlTab.KEYBOARD -> SecondaryKeyboardTab(
                consoleId = consoleId,
                onKeyDown = onKeyDown,
                onKeyUp = onKeyUp,
                modifier = Modifier.weight(1f),
            )
            ControlTab.TRACKPAD -> SecondaryTrackpadTab(
                onMouseMove = onMouseMove,
                onMouseButton = onMouseButton,
                gameWidth = gameWidth,
                gameHeight = gameHeight,
                modifier = Modifier.weight(1f),
            )
            ControlTab.POINTER -> SecondaryWiiPointerTab(
                controller = controller,
                aspectRatio = pointerAspectRatio,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Gamepad tab content: P1/P2 port selector + platform touch controls.
 */
@Composable
private fun GamepadTabContent(
    controller: LibretroController,
    touchControlPort: Int,
    onSelectPort: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PortSelectorRow(
            selectedPort = touchControlPort,
            onSelectPort = onSelectPort,
        )
        PlatformTouchControls(
            controller = controller,
            modifier = Modifier.weight(1f),
            port = touchControlPort,
        )
    }
}

/**
 * Pill-style segmented control for switching between input tabs.
 */
@Composable
private fun ControlTabSelector(
    tabs: List<ControlTab>,
    selectedTab: ControlTab,
    onSelectTab: (ControlTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Input mode: ${selectedTab.id}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(4.dp))
            TabPill(
                label = tab.id.replaceFirstChar { it.uppercase() },
                isSelected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                contentDesc = "${tab.id.replaceFirstChar { it.uppercase() }} input mode",
            )
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDesc: String,
) {
    val backgroundColor = if (isSelected) SpColor.Primary else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.OnBackground else SpColor.OnBackgroundSecondary

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(SpSpacing.Small))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = textColor,
        )
    }
}

// --- Port selector ---

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
        PortPillButton(label = "P1", port = 0, isSelected = selectedPort == 0, onSelect = onSelectPort)
        Spacer(Modifier.width(SpSpacing.Small))
        PortPillButton(label = "P2", port = 1, isSelected = selectedPort == 1, onSelect = onSelectPort)
    }
}

@Composable
private fun PortPillButton(
    label: String,
    port: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val backgroundColor = if (isSelected) SpColor.Primary else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.OnBackground else SpColor.OnBackgroundSecondary

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(SpSpacing.Small))
            .background(backgroundColor)
            .clickable { onSelect(port) }
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = "Player ${port + 1} controls" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = SpTypography.LabelMedium, color = textColor)
    }
}
