package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GenreCount
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.GameShelf
import com.spela.player.presentation.ui.feature.explore.GameShelfSkeleton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating
import com.spela.player.util.parseHexColor

@Composable
fun ExploreConsoleShowcaseScreen(
    consoleId: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.consoleShowcaseState.collectAsState()

    LaunchedEffect(consoleId) {
        viewModel.loadConsoleShowcase(consoleId)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("console_showcase_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.showcase?.console?.name ?: consoleId,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.showcase == null -> {
                    // Loading skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("console_showcase_loading"),
                    ) {
                        Spacer(Modifier.height(SpSpacing.Large))
                        repeat(4) {
                            SpGameCardSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = SpSpacing.Medium),
                            )
                        }
                    }
                }

                state.showcase != null -> {
                    val showcase = state.showcase!!
                    val accentColor = parseHexColor(showcase.console.colorTheme, SpColor.Primary)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("console_showcase_list"),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // Hero banner with console color accent
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                accentColor.copy(alpha = 0.3f),
                                                SpColor.Background,
                                            ),
                                        ),
                                    )
                                    .testTag("console_showcase_banner"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = showcase.console.name,
                                        style = SpTypography.DisplaySmall,
                                        color = SpColor.OnBackground,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                    Text(
                                        text = "${showcase.console.gameCount} games in library",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.OnBackgroundSecondary,
                                        modifier = Modifier.testTag("console_showcase_game_count"),
                                    )
                                }
                            }
                        }

                        // Essentials section
                        if (showcase.essentials.isNotEmpty()) {
                            item {
                                SpTitledSection(
                                    title = "Essentials",
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("console_essentials_section"),
                                ) {
                                    GameShelf(
                                        games = showcase.essentials,
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }
                        }

                        // Hidden Gems section
                        if (showcase.hiddenGems.isNotEmpty()) {
                            item {
                                SpTitledSection(
                                    title = "Hidden Gems",
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("console_hidden_gems_section"),
                                ) {
                                    GameShelf(
                                        games = showcase.hiddenGems,
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }
                        }

                        // Genre Breakdown section
                        if (showcase.genreBreakdown.isNotEmpty()) {
                            item {
                                SpTitledSection(
                                    title = "Genre Breakdown",
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("console_genre_breakdown_section"),
                                ) {
                                    GenreBreakdownChips(
                                        genres = showcase.genreBreakdown,
                                        accentColor = accentColor,
                                    )
                                }
                            }
                        }

                        // Top Developers section
                        if (showcase.topDevelopers.isNotEmpty()) {
                            item {
                                SpTitledSection(
                                    title = "Top Developers",
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("console_top_developers_section"),
                                ) {
                                    TopDevelopersList(
                                        developers = showcase.topDevelopers,
                                        onDeveloperSelected = onDeveloperSelected,
                                    )
                                }
                            }
                        }

                        // Recently Played section (only if non-empty)
                        if (showcase.recentlyPlayed.isNotEmpty()) {
                            item {
                                SpTitledSection(
                                    title = "Recently Played",
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("console_recently_played_section"),
                                ) {
                                    GameShelf(
                                        games = showcase.recentlyPlayed,
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.SportsEsports,
                            title = "Console not found",
                            message = "Could not load console details.",
                            modifier = Modifier.testTag("console_showcase_error_state"),
                        )
                    }
                }
            }
        }

        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissConsoleShowcaseError() },
                )
            },
            onDismiss = { viewModel.dismissConsoleShowcaseError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreBreakdownChips(
    genres: List<GenreCount>,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .testTag("genre_breakdown_chips"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        genres.forEach { genre ->
            SpChip(
                text = "${genre.name} (${genre.gameCount})",
                color = accentColor,
                modifier = Modifier
                    .testTag("genre_chip_${genre.name}")
                    .semantics {
                        contentDescription = "${genre.name}, ${genre.gameCount} games"
                    },
            )
        }
    }
}

@Composable
private fun TopDevelopersList(
    developers: List<DeveloperSummary>,
    onDeveloperSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .testTag("top_developers_list"),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        developers.forEach { developer ->
            SpCard(
                onClick = { onDeveloperSelected(developer.name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("developer_card_${developer.name}")
                    .semantics {
                        contentDescription = "${developer.name}, ${developer.gameCount} games"
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = developer.name,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(SpSpacing.XXSmall))
                        Text(
                            text = "${developer.gameCount} games",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }

                    if (developer.avgRating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = SpColor.Rating,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = formatRating(developer.avgRating),
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackgroundSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
