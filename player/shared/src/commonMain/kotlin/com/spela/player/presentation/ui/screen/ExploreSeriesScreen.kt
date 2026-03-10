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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SeriesConsole
import com.spela.player.domain.model.SeriesGame
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating

@Composable
fun ExploreSeriesScreen(
    seriesId: String,
    seriesName: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.seriesDetailState.collectAsState()

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetail(seriesId, seriesName)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("explore_series_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = seriesName,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.detail == null -> {
                    // Loading skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("series_detail_loading"),
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

                state.detail != null -> {
                    val detail = state.detail!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // Hero banner with gradient
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                SpColor.Primary.copy(alpha = 0.3f),
                                                SpColor.Background,
                                            ),
                                        ),
                                    )
                                    .testTag("series_hero_banner"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = detail.name,
                                    style = SpTypography.DisplaySmall,
                                    color = SpColor.OnBackground,
                                )
                            }
                        }

                        // Ownership progress
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("series_ownership_progress"),
                            ) {
                                val progress = if (detail.totalGames > 0) {
                                    detail.libraryGames.toFloat() / detail.totalGames.toFloat()
                                } else 0f

                                Text(
                                    text = "You own ${detail.libraryGames} of ${detail.totalGames} games",
                                    style = SpTypography.BodyMedium,
                                    color = SpColor.OnBackgroundSecondary,
                                    modifier = Modifier.testTag("series_ownership_text"),
                                )
                                Spacer(Modifier.height(SpSpacing.Small))
                                SpProgressBar(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(SpSpacing.Large))
                            }
                        }

                        // Console filter badges
                        if (detail.consoles.isNotEmpty()) {
                            item {
                                ConsoleFilterRow(
                                    consoles = detail.consoles,
                                    totalGames = detail.totalGames,
                                    selectedAbbreviation = state.consoleFilter,
                                    onConsoleSelected = { abbreviation ->
                                        viewModel.setSeriesConsoleFilter(
                                            if (abbreviation != null && state.consoleFilter == abbreviation) null else abbreviation,
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("series_console_filters"),
                                )
                                Spacer(Modifier.height(SpSpacing.Large))
                            }
                        }

                        // Timeline of games
                        val filteredGames = state.filteredGames
                        if (filteredGames.isEmpty() && !state.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpSpacing.XXLarge),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SpEmptyState(
                                        icon = Icons.Filled.VideoLibrary,
                                        title = "No games found",
                                        message = "No games match the selected filter.",
                                        modifier = Modifier.testTag("series_empty_state"),
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filteredGames,
                                key = { it.igdbGameId },
                            ) { game ->
                                SeriesTimelineItem(
                                    game = game,
                                    onClick = if (game.inLibrary && game.localGameId != null) {
                                        { onGameSelected(game.localGameId) }
                                    } else null,
                                )
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
                            icon = Icons.Filled.VideoLibrary,
                            title = "Series not found",
                            message = "Could not load series details.",
                            modifier = Modifier.testTag("series_error_state"),
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
                    onAction = { viewModel.dismissSeriesDetailError() },
                )
            },
            onDismiss = { viewModel.dismissSeriesDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsoleFilterRow(
    consoles: List<SeriesConsole>,
    totalGames: Int,
    selectedAbbreviation: String?,
    onConsoleSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        // "All" chip
        SpChip(
            text = "All ($totalGames)",
            onClick = { onConsoleSelected(null) },
            isSelected = selectedAbbreviation == null,
            modifier = Modifier
                .testTag("series_console_chip_all")
                .semantics {
                    contentDescription = "All, $totalGames games"
                    role = Role.Button
                },
        )

        consoles.forEach { console ->
            val isSelected = console.abbreviation == selectedAbbreviation

            SpChip(
                text = "${console.name} (${console.gameCount})",
                onClick = { onConsoleSelected(console.abbreviation) },
                isSelected = isSelected,
                modifier = Modifier
                    .testTag("series_console_chip_${console.abbreviation}")
                    .semantics {
                        contentDescription = "${console.name}, ${console.gameCount} games"
                        role = Role.Button
                    },
            )
        }
    }
}

@Composable
private fun SeriesTimelineItem(
    game: SeriesGame,
    onClick: (() -> Unit)?,
) {
    val itemAlpha = if (game.inLibrary) 1f else 0.5f

    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.XSmall,
            )
            .alpha(itemAlpha)
            .testTag("series_game_${game.igdbGameId}")
            .semantics {
                contentDescription = buildString {
                    append(game.name)
                    game.consoleName?.let { append(", $it") }
                    game.releaseDate?.let { append(", $it") }
                    if (!game.inLibrary) append(", not in library")
                }
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Cover art
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.name} cover art",
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                aspectRatio = 0.75f,
            )

            // Game info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = game.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(SpSpacing.XXSmall))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    game.consoleName?.let { consoleName ->
                        Text(
                            text = consoleName,
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }

                    game.releaseDate?.take(4)?.let { year ->
                        Text(
                            text = year,
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }

                    if (game.rating > 0) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }

                if (!game.inLibrary) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = "Not in library",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}
