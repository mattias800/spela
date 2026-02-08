package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpGradientCard
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel

@Composable
fun HomeScreen(
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadDashboard)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Spela")

        if (state.isLoading && state.recentGames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading your library...")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = SpSpacing.Default),
            ) {
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

                // Recently Added (using favorites as proxy)
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
                        ConsolesGrid(
                            consoles = state.consoles,
                            onConsoleSelected = onConsoleSelected,
                        )
                    }
                }
            }
        }
    }
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
        modifier = modifier,
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
        items(games) { game ->
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
        modifier = Modifier.width(280.dp),
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
                contentDescription = game.title,
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
            // Play indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SpColor.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u25B6",
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnPrimary,
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
        items(games) { game ->
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
        modifier = Modifier.width(SpSpacing.CoverMediumWidth),
        onClick = onClick,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = game.title,
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        consoles.chunked(2).forEach { rowConsoles ->
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
                // Fill remaining space if odd number
                if (rowConsoles.size == 1) {
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
        modifier = modifier.height(100.dp),
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

internal fun getConsoleColor(colorTheme: String?): Color {
    return when (colorTheme?.lowercase()) {
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
