package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.feature.settings.SettingsSectionHeader
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Per-game save-state opt-out toggle for the game-detail options
 * area. Tri-state radio:
 *
 *   - Console default → no per-game override (current = null)
 *   - Always enabled  → per-game override = Enabled
 *   - Always disabled → per-game override = Disabled
 *
 * The intent dispatched is [com.spela.player.presentation.intent
 * .GameDetailIntent.SetGameSaveStatePolicy] with the corresponding
 * choice or null for "clear override". See #804 phase 4b spec
 * point (c).
 *
 * The "Ask each time" choice from the in-game first-launch prompt
 * is intentionally absent here — that's a console-level state for
 * users who want to be re-prompted, not a per-game one. Per-game
 * is for deliberate "yes for this title, no for that title" calls.
 */
@Composable
fun GameSaveStatePolicyToggle(
    current: SaveStateChoice?,
    onChange: (SaveStateChoice?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(title = "Save states")
        SpCard(
            onGradient = true,
            modifier = Modifier.testTag("game-save-state-policy-toggle"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.Small),
            ) {
                SpRadioOption(
                    title = "Console default",
                    description = "Use the per-console policy from Settings",
                    isSelected = current == null,
                    onClick = { onChange(null) },
                    modifier = Modifier.testTag("game-save-state-default"),
                )
                SettingsDivider()
                SpRadioOption(
                    title = "Always enabled",
                    description = "Allow save states for this game even if the console is opted out",
                    isSelected = current == SaveStateChoice.Enabled,
                    onClick = { onChange(SaveStateChoice.Enabled) },
                    modifier = Modifier.testTag("game-save-state-enabled"),
                )
                SettingsDivider()
                SpRadioOption(
                    title = "Always disabled",
                    description = "Hide save state controls for this game even if the console allows them",
                    isSelected = current == SaveStateChoice.Disabled,
                    onClick = { onChange(SaveStateChoice.Disabled) },
                    modifier = Modifier.testTag("game-save-state-disabled"),
                )
            }
        }
    }
}
