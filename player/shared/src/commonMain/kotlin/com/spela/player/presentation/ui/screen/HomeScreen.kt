package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpGradientCard
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.social.ActivityEventItem
import com.spela.player.presentation.ui.components.social.OnlineUsersRow
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GameListViewModel,
    socialViewModel: SocialViewModel,
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToRelays: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNetplay: () -> Unit = {},
    onNetplaySessionSelected: (String) -> Unit = {},
    onUserSelected: (String) -> Unit = {},
    hasActiveDownloads: Boolean = false,
    activeNetplaySessions: List<NetplaySession> = emptyList(),
) {
    val state by viewModel.state.collectAsState()
    val socialState by socialViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadDashboard)
        socialViewModel.onIntent(SocialIntent.RefreshAll)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Spela") {
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .spFocusRing(shape = CircleShape)
                        .clip(CircleShape)
                        .background(SpColor.SurfaceVariant)
                        .clickable(onClick = onNavigateToDownloads)
                        .focusable()
                        .semantics {
                            contentDescription = "Downloads"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = SpColor.OnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (hasActiveDownloads) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(SpColor.Primary),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .spFocusRing(shape = CircleShape)
                    .clip(CircleShape)
                    .background(SpColor.SurfaceVariant)
                    .clickable(onClick = onNavigateToRelays)
                    .focusable()
                    .semantics {
                        contentDescription = "Relays"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SyncAlt,
                    contentDescription = null,
                    tint = SpColor.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .spFocusRing(shape = CircleShape)
                    .clip(CircleShape)
                    .background(SpColor.SurfaceVariant)
                    .clickable(onClick = onNavigateToNetplay)
                    .focusable()
                    .semantics {
                        contentDescription = "Netplay"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = SpColor.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .spFocusRing(shape = CircleShape)
                    .clip(CircleShape)
                    .background(SpColor.SurfaceVariant)
                    .clickable(onClick = onNavigateToSettings)
                    .focusable()
                    .semantics {
                        contentDescription = "Settings"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = SpColor.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (state.isLoading && state.recentGames.isEmpty() && state.consoles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading your library...")
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = {
                    viewModel.onIntent(GameListIntent.LoadDashboard)
                    socialViewModel.onIntent(SocialIntent.RefreshAll)
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val isEmpty = state.recentGames.isEmpty() &&
                        state.favoriteGames.isEmpty() &&
                        state.playLaterGames.isEmpty() &&
                        state.consoles.isEmpty()

                if (isEmpty && !state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyStates.EmptyLibrary()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Netplay section (AC-15)
                        if (activeNetplaySessions.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Netplay",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                            }
                            items(
                                activeNetplaySessions,
                                key = { "netplay-${it.id}" },
                            ) { session ->
                                NetplaySessionCard(
                                    session = session,
                                    onClick = { onNetplaySessionSelected(session.id) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Continue Playing section
                        if (state.recentGames.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Continue Playing",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                ContinuePlayingRow(
                                    games = state.recentGames.take(10),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Play Later section
                        if (state.playLaterGames.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Play Later",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                GameCarouselRow(
                                    games = state.playLaterGames.take(10),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Online Now section
                        if (socialState.onlineUsers.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Online Now",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                OnlineUsersRow(users = socialState.onlineUsers, onUserSelected = onUserSelected)
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Recent Activity section
                        if (socialState.activityEvents.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recent Activity",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Small))
                            }
                            items(
                                socialState.activityEvents.take(5),
                                key = { "activity-${it.id}" },
                            ) { event ->
                                ActivityEventItem(event = event)
                            }
                            item {
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Favorites section
                        if (state.favoriteGames.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Favorites",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                GameCarouselRow(
                                    games = state.favoriteGames.take(10),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Consoles Grid
                        if (state.consoles.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Consoles",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                                ) {
                                    val columnsPerRow = if (maxWidth > 600.dp) 3 else 2
                                    ConsolesGrid(
                                        consoles = state.consoles,
                                        onConsoleSelected = onConsoleSelected,
                                        columnsPerRow = columnsPerRow,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Error snackbar
    SpSnackbar(
        data = state.error?.let {
            SpSnackbarData(
                message = it,
                type = SpSnackbarType.Error,
                actionLabel = "Dismiss",
                onAction = { viewModel.onIntent(GameListIntent.DismissError) },
            )
        },
        onDismiss = { viewModel.onIntent(GameListIntent.DismissError) },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    } // outer Box
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = SpTypography.HeadlineLarge,
        color = SpColor.OnBackground,
        modifier = modifier.semantics { contentDescription = "$title section" },
    )
}

@Composable
private fun ContinuePlayingRow(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            ContinuePlayingCard(
                game = game,
                onClick = { onGameSelected(game.id) },
            )
        }
    }
}

@Composable
private fun ContinuePlayingCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(280.dp)
            .semantics {
                contentDescription = "Continue playing ${game.title} on ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.size(width = 60.dp, height = 84.dp),
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = game.consoleName,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            Spacer(Modifier.width(SpSpacing.Small))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpColor.Primary)
                    .semantics {
                        contentDescription = "Play ${game.title}"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = SpColor.OnPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun GameCarouselRow(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            GameCoverCard(
                game = game,
                onClick = { onGameSelected(game.id) },
            )
        }
    }
}

@Composable
private fun GameCoverCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = SpSpacing.Small,
                    vertical = SpSpacing.Small,
                ),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = game.consoleName,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ConsolesGrid(
    consoles: List<Console>,
    onConsoleSelected: (String) -> Unit,
    columnsPerRow: Int = 2,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        consoles.chunked(columnsPerRow).forEach { rowConsoles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                rowConsoles.forEach { console ->
                    ConsoleCard(
                        console = console,
                        onClick = { onConsoleSelected(console.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnsPerRow - rowConsoles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConsoleCard(
    console: Console,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val consoleColor = getConsoleColor(console.colorTheme)

    SpGradientCard(
        modifier = modifier
            .height(100.dp)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
                role = Role.Button
            },
        onClick = onClick,
        gradientColors = listOf(
            consoleColor.copy(alpha = 0.3f),
            consoleColor.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpSpacing.Default),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = console.name,
                style = SpTypography.TitleLarge,
                color = SpColor.OnBackground,
            )
            Text(
                text = "${console.gameCount} games",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}

@Composable
private fun NetplaySessionCard(
    session: NetplaySession,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = when (session.status) {
                    NetplaySessionStatus.WAITING -> "${session.gameTitle} netplay session, waiting for player"
                    NetplaySessionStatus.IN_PROGRESS -> "${session.gameTitle} with ${session.clientUsername ?: "player"}, tap to rejoin"
                    NetplaySessionStatus.ENDED -> "${session.gameTitle} session ended"
                }
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = session.gameCoverUrl,
                contentDescription = "${session.gameTitle} cover",
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.gameTitle,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = when (session.status) {
                        NetplaySessionStatus.WAITING -> "Waiting for player..."
                        NetplaySessionStatus.IN_PROGRESS -> "With ${session.clientUsername ?: session.hostUsername} -- Tap to rejoin"
                        NetplaySessionStatus.ENDED -> "Session ended"
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun getConsoleColor(colorTheme: String?): Color {
    if (colorTheme == null) return SpColor.Primary
    // Try parsing as hex color first (backend sends "#e53e3e" format)
    if (colorTheme.startsWith("#")) {
        return try {
            val hex = colorTheme.removePrefix("#")
            val colorLong = when (hex.length) {
                6 -> (0xFF000000 or hex.toLong(16))
                8 -> hex.toLong(16)
                else -> null
            }
            if (colorLong != null) Color(colorLong.toInt()) else SpColor.Primary
        } catch (_: NumberFormatException) {
            SpColor.Primary
        }
    }
    // Fallback to name matching for backwards compatibility
    return when (colorTheme.lowercase()) {
        "nes" -> SpColor.ConsoleNes
        "snes" -> SpColor.ConsoleSnes
        "gameboy", "gb", "gbc" -> SpColor.ConsoleGameBoy
        "gba" -> SpColor.ConsoleGba
        "n64" -> SpColor.ConsoleN64
        "nds" -> SpColor.ConsoleNds
        "sega", "genesis", "megadrive" -> SpColor.ConsoleSega
        "psx", "playstation" -> SpColor.ConsolePsx
        "psp" -> SpColor.ConsolePsp
        "arcade", "mame" -> SpColor.ConsoleArcade
        else -> SpColor.Primary
    }
}
