package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.RemoteGame
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGridGameCard
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.social.FriendsPlayingNowSection
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ConnectedServersIntent
import com.spela.player.presentation.viewmodel.ConnectedServersViewModel

/**
 * Browse games available on connected federation servers: a console selector
 * row + a grid of that console's games. Each game opens the remote-game detail
 * where it can be imported. Parallel-worlds: separate from the local library.
 */
@Composable
fun ConnectedServersScreen(
    viewModel: ConnectedServersViewModel,
    onGameSelected: (RemoteGame) -> Unit,
    onRemoteGameKeySelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(ConnectedServersIntent.Load)
    }

    val focusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        SpScreen(modifier = Modifier.testTag("connected_servers_screen")) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab root — no back button (the bottom nav owns top-level switching).
                SpTopBar(title = "Connected servers")

                // Live "who's playing now" across connected servers. Self-contained
                // and renders nothing when empty; it loads asynchronously so it's
                // absent during the entry focus-settle window (no focus race with
                // the console chip default below).
                FriendsPlayingNowSection(
                    presence = state.presence,
                    onGameSelected = onRemoteGameKeySelected,
                    modifier = Modifier.padding(
                        horizontal = SpSpacing.ScreenHorizontal,
                        vertical = SpSpacing.Small,
                    ),
                )

                if (state.consoles.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = SpSpacing.ScreenHorizontal,
                            vertical = SpSpacing.Small,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        items(state.consoles, key = { it.console }) { console ->
                            val selected = console.console == state.selectedConsole
                            SpButton(
                                text = "${console.console} (${console.count})",
                                onClick = {
                                    viewModel.onIntent(ConnectedServersIntent.SelectConsole(console.console))
                                },
                                style = if (selected) SpButtonStyle.Primary else SpButtonStyle.Secondary,
                                // The selected chip is the default focus (it's also the
                                // first focusable in composition order, so gamepad
                                // moveFocus(Next) and the default-restore agree — no snap).
                                modifier = Modifier.focusRestoreItem(
                                    key = "connected_console_${console.console}",
                                    isDefault = selected,
                                ),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.isLoadingConsoles && state.consoles.isEmpty() ->
                            ScreenLoadingIndicator(message = "Loading connected servers...")

                        state.consoles.isEmpty() ->
                            SpEmptyState(
                                icon = Icons.Default.Cloud,
                                title = "No connected-server games",
                                message = "Games offered by servers you're connected to will appear here.",
                            )

                        state.isLoadingGames && state.games.isEmpty() ->
                            ScreenLoadingIndicator(message = "Loading games...")

                        state.games.isEmpty() ->
                            SpEmptyState(
                                icon = Icons.Default.Cloud,
                                title = "No games",
                                message = "No games are available for this console.",
                            )

                        else ->
                            SpLazyVerticalGrid(
                                columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                    vertical = SpSpacing.Default,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                                verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                            ) {
                                items(state.games, key = { it.key }) { game ->
                                    val servers = game.originCount
                                    SpGridGameCard(
                                        title = game.title,
                                        subtitle = "on $servers connected ${if (servers == 1) "server" else "servers"}",
                                        coverUrl = game.coverUrl,
                                        onClick = { onGameSelected(game) },
                                        testTag = "remote_game_${game.key}",
                                        // Default focus lives on the selected console
                                        // chip above, so grid items never claim it
                                        // (avoids the chip-vs-grid focus race).
                                        modifier = Modifier.focusRestoreItem(
                                            key = "connected_servers_${game.key}",
                                            isDefault = false,
                                        ),
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
