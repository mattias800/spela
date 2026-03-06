package com.spela.player.presentation.ui.feature.sessiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SessionCheatsSection(
    cheatsEnabled: Boolean,
    enabledCheatIndices: List<Int>,
    availableCheats: List<Cheat>,
    isLoadingCheats: Boolean,
    cheatsLoadAttempted: Boolean,
    onToggleCheatsEnabled: (Boolean) -> Unit,
    onToggleCheatAtIndex: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    // Only treat cheats as unavailable once we've actually tried to load them
    val cheatsUnavailable = cheatsLoadAttempted && !isLoadingCheats && availableCheats.isEmpty()

    Column(modifier = modifier) {
        SpSectionHeader(
            title = "Cheats",
            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
        )
        Spacer(Modifier.height(SpSpacing.Small))

        // Master toggle
        SpInnerCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal)
                .testTag("session_cheats_section"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Default),
            ) {
                if (cheatsUnavailable) {
                    Text(
                        text = "Cheats are not available for this game. Cheats require a verified ROM (checksum match).",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        modifier = Modifier
                            .padding(bottom = SpSpacing.Small)
                            .testTag("session_cheats_unavailable"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Cheats",
                            style = SpTypography.BodyMedium,
                            color = if (cheatsUnavailable) SpColor.OnBackgroundTertiary else SpColor.OnCard,
                        )
                        if (cheatsEnabled && enabledCheatIndices.isNotEmpty()) {
                            Text(
                                text = "${enabledCheatIndices.size} cheat(s) active",
                                style = SpTypography.LabelSmall,
                                color = SpColor.OnBackgroundTertiary,
                                modifier = Modifier.testTag("session_cheats_count"),
                            )
                        } else if (!cheatsEnabled && availableCheats.isNotEmpty()) {
                            Text(
                                text = "${availableCheats.size} cheats available",
                                style = SpTypography.LabelSmall,
                                color = SpColor.OnBackgroundTertiary,
                                modifier = Modifier.testTag("session_cheats_available_count"),
                            )
                        }
                    }
                    Switch(
                        checked = cheatsEnabled,
                        onCheckedChange = onToggleCheatsEnabled,
                        enabled = !cheatsUnavailable,
                        modifier = Modifier
                            .testTag("session_cheats_toggle")
                            .semantics { contentDescription = "Toggle cheats" },
                    )
                }
            }
        }

        // Per-cheat list (only when master toggle is on and cheats are available)
        if (cheatsEnabled && !cheatsUnavailable) {
            Spacer(Modifier.height(SpSpacing.Default))

            if (isLoadingCheats) {
                Text(
                    text = "Loading cheats\u2026",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier
                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                        .testTag("session_cheats_loading"),
                )
            } else if (availableCheats.isEmpty()) {
                Text(
                    text = "No cheats available for this game",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier
                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                        .testTag("session_cheats_empty"),
                )
            } else {
                // Select All / Deselect All actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onSelectAll,
                        modifier = Modifier.testTag("session_cheats_select_all"),
                    ) {
                        Text("Select All", color = SpColor.Primary)
                    }
                    TextButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.testTag("session_cheats_deselect_all"),
                    ) {
                        Text("Deselect All", color = SpColor.Primary)
                    }
                }

                // Search filter for large cheat lists
                if (availableCheats.size > 10) {
                    SpTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Search cheats",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpSpacing.ScreenHorizontal)
                            .testTag("session_cheats_search"),
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                }

                val filteredCheats = if (searchQuery.isBlank()) {
                    availableCheats
                } else {
                    availableCheats.filter {
                        it.description.contains(searchQuery, ignoreCase = true) ||
                            it.code.contains(searchQuery, ignoreCase = true)
                    }
                }

                // Cheat list (rendered inline to avoid nested scrollable containers)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_cheats_list"),
                ) {
                    filteredCheats.forEach { cheat ->
                        SessionCheatItem(
                            cheat = cheat,
                            isEnabled = cheat.index in enabledCheatIndices,
                            onToggle = { onToggleCheatAtIndex(cheat.index) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                    vertical = SpSpacing.XXSmall,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCheatItem(
    cheat: Cheat,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(
        modifier = modifier.testTag("session_cheat_item_${cheat.index}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cheat.description,
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnCard,
                    modifier = Modifier.semantics {
                        contentDescription = "Cheat: ${cheat.description}"
                    },
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = cheat.code,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .testTag("session_cheat_toggle_${cheat.index}")
                    .semantics {
                        contentDescription = if (isEnabled) {
                            "Disable ${cheat.description}"
                        } else {
                            "Enable ${cheat.description}"
                        }
                    },
            )
        }
    }
}
