package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsState
import com.spela.player.presentation.viewmodel.SettingsViewModel

/**
 * Lists the user's per-console save-state opt-out overrides with a way
 * to clear each one. See #804 phase 4b spec point (a) — the primary
 * surface for the user to revisit a deliberate choice.
 *
 * Adding a new override happens via the in-game first-launch prompt
 * (#820); this section is for managing what already exists. When the
 * map is empty, the section hides itself entirely so it doesn't clutter
 * the Emulation tab for the common-case user who never opted out.
 */
internal fun LazyListScope.saveStatePoliciesSectionItems(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    if (state.consoleSaveStatePolicies.isEmpty()) return

    item {
        SpCard(
            onGradient = true,
            modifier = Modifier.testTag("settings_save_state_policies_card"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Small),
            ) {
                val abbrToName = state.consoles
                    .associateBy { it.abbreviation.lowercase() }
                state.consoleSaveStatePolicies.entries
                    // Sorted by display name so the list is stable
                    // across renders even though the map iteration
                    // order isn't.
                    .sortedBy { (abbr, _) -> abbrToName[abbr]?.name ?: abbr }
                    .forEachIndexed { index, (abbr, choice) ->
                        SaveStatePolicyRow(
                            consoleName = abbrToName[abbr]?.name
                                ?: abbr.uppercase(),
                            choice = choice,
                            onClear = {
                                viewModel.onIntent(
                                    SettingsIntent.SetConsoleSaveStatePolicy(
                                        consoleId = abbr,
                                        choice = null,
                                    ),
                                )
                            },
                        )
                        if (index < state.consoleSaveStatePolicies.size - 1) {
                            SettingsDivider()
                        }
                    }
            }
        }
    }
}

@Composable
private fun SaveStatePolicyRow(
    consoleName: String,
    choice: SaveStateChoice,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .testTag("save_state_policy_row_${consoleName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = consoleName,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackground,
            )
            Text(
                text = "Save states: ${choice.displayLabel}",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
        SpSecondaryButton(
            text = "Clear",
            onClick = onClear,
        )
    }
}

/**
 * Human-readable label for the save-state choice column on the
 * Settings row. Kept here rather than on the enum itself because the
 * domain model is consumed by both the prompt copy ("Yes, enable")
 * and the Settings list ("Enabled") — different surfaces, different
 * tone. The enum stays UI-agnostic.
 */
private val SaveStateChoice.displayLabel: String
    get() = when (this) {
        SaveStateChoice.Enabled -> "Enabled"
        SaveStateChoice.Disabled -> "Disabled (battery saves only)"
        SaveStateChoice.AskOnce -> "Ask each time"
    }
