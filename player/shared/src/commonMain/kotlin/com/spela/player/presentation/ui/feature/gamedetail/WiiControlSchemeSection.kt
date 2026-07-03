package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.WiiControlScheme
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Per-game Wii controller scheme picker on the game detail screen
 * (#1559). Rendered only for Wii games; the selection persists
 * device-locally and applies on the next launch (and live in-game via
 * the overlay's picker).
 */
@Composable
fun WiiControlSchemeSection(
    currentScheme: WiiControlScheme,
    onSelectScheme: (WiiControlScheme) -> Unit,
) {
    SpTitledSection(
        title = "Wii Remote",
        icon = Icons.Filled.SettingsRemote,
    ) {
        Column(
            modifier = Modifier.testTag("wii-control-scheme-section"),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            WiiControlScheme.entries.forEach { scheme ->
                SpRadioOption(
                    title = scheme.displayName,
                    description = scheme.description,
                    isSelected = scheme == currentScheme,
                    onClick = { onSelectScheme(scheme) },
                    modifier = Modifier.testTag("wii-scheme-option-${scheme.storageId}"),
                )
            }
        }
    }
}
