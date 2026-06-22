package com.spela.player.presentation.ui.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.state.StatScope
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SegmentedOption
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpSegmentedControl
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Modular stats sections. Each is a self-contained LazyListScope extension that
 * emits its full section (header + This-server|Across-servers scope toggle +
 * rows). Reorder or relocate a section by moving its call site. State lives in
 * the StatsViewModel (player convention); these are presentational.
 *
 * The scope toggle is focusable and sits above the rows, so per
 * GAMEPAD_NAVIGATION.md the screen's default-focus element must be the FIRST
 * focusable in composition order. The caller therefore makes the FIRST rendered
 * section's toggle the default (isDefaultFocus = true); rows are never default.
 */
fun LazyListScope.mostPlayedStatsSection(
    games: List<MostPlayedGame>,
    meshStats: List<MeshStat>,
    scope: StatScope,
    isLoadingMesh: Boolean,
    isDefaultFocus: Boolean,
    onScopeChange: (StatScope) -> Unit,
    onGameSelected: (String) -> Unit,
) {
    item(key = "most-played-header") {
        StatsSectionHeaderWithScope(
            title = "Most Played Games",
            scope = scope,
            isDefaultFocus = isDefaultFocus,
            testPrefix = "most-played",
            onScopeChange = onScopeChange,
            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
        )
    }

    when (scope) {
        StatScope.ThisServer -> {
            itemsIndexed(games, key = { _, item -> "game-${item.game.id}" }) { index, item ->
                MostPlayedGameItem(
                    rank = index + 1,
                    item = item,
                    onClick = { onGameSelected(item.game.id) },
                    modifier = Modifier
                        .focusRestoreItem(key = "game_${item.game.id}", isDefault = false)
                        .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
                )
            }
        }
        StatScope.AcrossServers -> meshStatRows(
            stats = meshStats,
            isLoading = isLoadingMesh,
            showPlayers = true,
            emptyMessage = "Most-played games will appear here once connected servers share their stats.",
            keyPrefix = "mesh-game",
        )
    }

    item(key = "most-played-spacer") { Spacer(Modifier.height(SpSpacing.XXLarge)) }
}

fun LazyListScope.mostActivePlayersStatsSection(
    players: List<ActivePlayer>,
    meshStats: List<MeshStat>,
    scope: StatScope,
    isLoadingMesh: Boolean,
    isDefaultFocus: Boolean,
    onScopeChange: (StatScope) -> Unit,
    onUserSelected: (String) -> Unit,
) {
    item(key = "active-header") {
        StatsSectionHeaderWithScope(
            title = "Most Active Players",
            scope = scope,
            isDefaultFocus = isDefaultFocus,
            testPrefix = "most-active",
            onScopeChange = onScopeChange,
            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
        )
    }

    when (scope) {
        StatScope.ThisServer -> {
            itemsIndexed(players, key = { _, item -> "player-${item.userId}" }) { index, item ->
                ActivePlayerItem(
                    rank = index + 1,
                    item = item,
                    onClick = { onUserSelected(item.userId) },
                    modifier = Modifier
                        .focusRestoreItem(key = "player_${item.userId}", isDefault = false)
                        .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
                )
            }
        }
        StatScope.AcrossServers -> meshStatRows(
            stats = meshStats,
            isLoading = isLoadingMesh,
            showPlayers = false,
            emptyMessage = "Active players will appear here once connected servers share their stats.",
            keyPrefix = "mesh-player",
        )
    }

    item(key = "active-spacer") { Spacer(Modifier.height(SpSpacing.XXLarge)) }
}

private fun LazyListScope.meshStatRows(
    stats: List<MeshStat>,
    isLoading: Boolean,
    showPlayers: Boolean,
    emptyMessage: String,
    keyPrefix: String,
) {
    if (stats.isEmpty()) {
        item(key = "$keyPrefix-placeholder") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Large),
            ) {
                if (isLoading) {
                    ScreenLoadingIndicator(message = "Loading…")
                } else {
                    SpEmptyState(
                        icon = Icons.Filled.Public,
                        title = "Nothing across connected servers yet",
                        message = emptyMessage,
                    )
                }
            }
        }
        return
    }

    itemsIndexed(stats, key = { _, s -> "$keyPrefix-${s.key}" }) { index, s ->
        MeshStatItem(
            rank = index + 1,
            label = s.label,
            playTimeSeconds = s.playTimeSeconds,
            players = if (showPlayers) s.players else null,
            modifier = Modifier
                .focusRestoreItem(key = "${keyPrefix}_${s.key}", isDefault = false)
                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
        )
    }
}

@Composable
private fun StatsSectionHeaderWithScope(
    title: String,
    scope: StatScope,
    isDefaultFocus: Boolean,
    testPrefix: String,
    onScopeChange: (StatScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Outer (screen-horizontal) spacing is supplied by the call site, not baked
    // in here — see ComponentOuterSpacingRule.
    Column(modifier = modifier) {
        SpSectionHeader(title = title)
        Spacer(Modifier.height(SpSpacing.Small))
        SpSegmentedControl(
            options = listOf(
                SegmentedOption(StatScope.ThisServer, "This server", "$testPrefix-this-server"),
                SegmentedOption(StatScope.AcrossServers, "Across servers", "$testPrefix-across"),
            ),
            selectedValue = scope,
            onValueChange = onScopeChange,
            onGradient = true,
            modifier = Modifier
                .focusRestoreItem(key = "$testPrefix-scope", isDefault = isDefaultFocus)
                .testTag("$testPrefix-scope-toggle"),
        )
        Spacer(Modifier.height(SpSpacing.Small))
    }
}
