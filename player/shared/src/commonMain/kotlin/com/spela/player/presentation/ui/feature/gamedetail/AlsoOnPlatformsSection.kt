package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.GamePlatformTarget
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.platformTargetsForGame
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

object GameDetailAlsoOnTestTags {
    const val SECTION = "game_detail_also_on_section"
    const val PLATFORMS = "game_detail_also_on_platforms"

    fun platform(gameId: String, targetGameId: String) =
        "game_detail_also_on_platform_${gameId}_$targetGameId"

    fun preferPlatform(gameId: String, targetGameId: String) =
        "game_detail_also_on_prefer_${gameId}_$targetGameId"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlsoOnPlatformsSection(
    game: Game,
    onPlatformSelected: (String) -> Unit,
    onSetPreferredPlatform: (String) -> Unit,
    settingPreferredPlatformGameId: String? = null,
) {
    val targets = platformTargetsForGame(game)
    if (targets.size <= 1) return

    SpTitledSection(
        title = "Also on",
        icon = Icons.Outlined.Gamepad,
        modifier = Modifier.testTag(GameDetailAlsoOnTestTags.SECTION),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .testTag(GameDetailAlsoOnTestTags.PLATFORMS),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            targets.forEach { platform ->
                val isCurrent = platform.isCurrent
                val preferenceInFlight = settingPreferredPlatformGameId != null
                val isSavingPreference = settingPreferredPlatformGameId == platform.gameId
                val label = platformDisplayName(platform)
                val chipModifier = Modifier
                    .then(
                        if (isCurrent) {
                            Modifier
                        } else {
                            Modifier.focusRestoreItem(
                                key = "game_detail_also_on_${game.id}_${platform.gameId}",
                            )
                        },
                    )
                    .testTag(GameDetailAlsoOnTestTags.platform(game.id, platform.gameId))
                    .semantics {
                        contentDescription = if (isCurrent) {
                            "Current platform $label"
                        } else {
                            "Open ${game.title} on $label"
                        }
                    }
                val preferModifier = Modifier
                    .focusRestoreItem(
                        key = "game_detail_also_on_prefer_${game.id}_${platform.gameId}",
                    )
                    .testTag(GameDetailAlsoOnTestTags.preferPlatform(game.id, platform.gameId))
                    .semantics {
                        contentDescription = if (isSavingPreference) {
                            "Saving preferred platform $label"
                        } else {
                            "Set $label as preferred platform"
                        }
                    }

                if (isCurrent) {
                    Row(
                        modifier = chipModifier,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpConsoleChip(
                            consoleName = label,
                            consoleColor = SpColor.Primary,
                            onGradient = true,
                            isSelected = true,
                        )
                        SpChip(
                            text = "Current",
                            color = SpColor.Primary,
                            onGradient = true,
                            isSelected = true,
                        )
                        PreferredPlatformChip(platform.isPreferred)
                        PreferPlatformChip(
                            visible = !platform.isPreferred,
                            isSaving = isSavingPreference,
                            enabled = !preferenceInFlight,
                            modifier = preferModifier,
                            onClick = { onSetPreferredPlatform(platform.gameId) },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpConsoleChip(
                            consoleName = label,
                            consoleColor = SpColor.Primary,
                            onGradient = true,
                            isSelected = platform.isPreferred,
                            onClick = { onPlatformSelected(platform.gameId) },
                            modifier = chipModifier,
                        )
                        PreferredPlatformChip(platform.isPreferred)
                        PreferPlatformChip(
                            visible = !platform.isPreferred,
                            isSaving = isSavingPreference,
                            enabled = !preferenceInFlight,
                            modifier = preferModifier,
                            onClick = { onSetPreferredPlatform(platform.gameId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferredPlatformChip(visible: Boolean) {
    if (!visible) return
    SpChip(
        text = "Preferred",
        color = SpColor.Primary,
        onGradient = true,
        isSelected = true,
    )
}

@Composable
private fun PreferPlatformChip(
    visible: Boolean,
    isSaving: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (!visible) return
    SpChip(
        text = if (isSaving) "Saving" else "Prefer",
        color = SpColor.Primary,
        onGradient = true,
        isSelected = false,
        onClick = if (enabled) onClick else null,
        modifier = modifier,
    )
}

private fun platformDisplayName(platform: GamePlatformTarget): String =
    platform.consoleName.ifBlank { platform.consoleId.uppercase() }
