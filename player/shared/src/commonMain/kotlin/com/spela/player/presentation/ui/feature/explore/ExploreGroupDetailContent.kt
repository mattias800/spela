package com.spela.player.presentation.ui.feature.explore

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SeriesConsole
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.SeriesGame
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpGameGrid
import com.spela.player.presentation.ui.components.SpGridGameCard
import com.spela.player.presentation.ui.components.SpHeroBanner
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
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.util.formatRating

/**
 * Shared content composable for series and franchise detail screens.
 *
 * @param detail The loaded detail data, or null if not yet loaded.
 * @param filteredGames The list of games after applying the console filter.
 * @param consoleFilter The currently selected console filter abbreviation, or null for "All".
 * @param isLoading Whether data is currently loading.
 * @param error An error message to display, or null.
 * @param title The display name shown in the top bar.
 * @param groupLabel A label used for test tag prefixes and display text (e.g., "series" or "franchise").
 * @param onGameSelected Called when a game is tapped.
 * @param onBack Called when the back button is tapped.
 * @param onConsoleFilterSelected Called when a console filter chip is tapped.
 * @param onDismissError Called when the error snackbar is dismissed.
 */
@Composable
fun ExploreGroupDetailContent(
    detail: SeriesDetail?,
    filteredGames: List<SeriesGame>,
    consoleFilter: String?,
    isLoading: Boolean,
    error: String?,
    title: String,
    groupLabel: String,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
    onConsoleFilterSelected: (String?) -> Unit,
    onDismissError: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().spScreenBackground().testTag("explore_${groupLabel}_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            when {
                isLoading && detail == null -> {
                    SpTopBar(
                        title = title,
                        showBack = true,
                        onBack = onBack,
                    )
                    // Loading skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("${groupLabel}_detail_loading"),
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

                detail != null -> {
                    // Use the series/franchise hero URL (best-rated game's hero art)
                    val heroUrl = detail.heroUrl

                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // Hero banner
                        item {
                            SpHeroBanner(
                                heroUrl = heroUrl,
                                height = (260 + 64 + 24).dp,
                                modifier = Modifier.testTag("${groupLabel}_hero_banner"),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                ) {
                                    // Franchise/series logo image or name text fallback
                                    val logoUrl = detail.logoUrl
                                    if (logoUrl != null) {
                                        SubcomposeAsyncImage(
                                            model = logoUrl,
                                            contentDescription = detail.name,
                                            modifier = Modifier
                                                .heightIn(max = 120.dp),
                                            contentScale = ContentScale.Fit,
                                            error = {
                                                Text(
                                                    text = detail.name,
                                                    style = SpTypography.DisplaySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                )
                                            },
                                        )
                                    } else {
                                        Text(
                                            text = detail.name,
                                            style = SpTypography.DisplaySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                    Text(
                                        text = "${detail.totalGames} games",
                                        style = SpTypography.BodyMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }

                        // Console filter badges
                        if (detail.consoles.isNotEmpty()) {
                            item {
                                ConsoleFilterRow(
                                    consoles = detail.consoles,
                                    totalGames = detail.totalGames,
                                    selectedAbbreviation = consoleFilter,
                                    onConsoleSelected = onConsoleFilterSelected,
                                    testTagPrefix = groupLabel,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("${groupLabel}_console_filters"),
                                )
                                Spacer(Modifier.height(SpSpacing.Large))
                            }
                        }

                        // Timeline of games
                        if (filteredGames.isEmpty() && !isLoading) {
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
                                        modifier = Modifier.testTag("${groupLabel}_empty_state"),
                                    )
                                }
                            }
                        } else {
                            item {
                                SpGameGrid(
                                    items = filteredGames.map { game ->
                                        @Composable {
                                            Box(modifier = Modifier.alpha(if (game.inLibrary) 1f else 0.5f)) {
                                                SpGridGameCard(
                                                    title = game.name,
                                                    subtitle = game.consoleName ?: "",
                                                    coverUrl = game.coverUrl,
                                                    onClick = if (game.inLibrary && game.localGameId != null) {
                                                        { onGameSelected(game.localGameId) }
                                                    } else ({}),
                                                    rating = game.rating,
                                                    testTag = "${groupLabel}_game_${game.igdbGameId}",
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                            }
                        }
                    }
                    // Floating top bar over the banner
                    SpTopBar(
                        title = "",
                        showBack = true,
                        onBack = onBack,
                        onGradient = true,
                    )
                    } // Box
                }

                else -> {
                    SpTopBar(
                        title = title,
                        showBack = true,
                        onBack = onBack,
                    )
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.VideoLibrary,
                            title = "${groupLabel.replaceFirstChar { it.uppercase() }} not found",
                            message = "Could not load ${groupLabel} details.",
                            modifier = Modifier.testTag("${groupLabel}_error_state"),
                        )
                    }
                }
            }
        }

        SpSnackbar(
            data = error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = onDismissError,
                )
            },
            onDismiss = onDismissError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsoleFilterRow(
    consoles: List<SeriesConsole>,
    totalGames: Int,
    selectedAbbreviation: String?,
    onConsoleSelected: (String?) -> Unit,
    testTagPrefix: String,
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
                .testTag("${testTagPrefix}_console_chip_all")
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
                    .testTag("${testTagPrefix}_console_chip_${console.abbreviation}")
                    .semantics {
                        contentDescription = "${console.name}, ${console.gameCount} games"
                        role = Role.Button
                    },
            )
        }
    }
}

@Composable
internal fun TimelineItem(
    game: SeriesGame,
    testTagPrefix: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val itemAlpha = if (game.inLibrary) 1f else 0.5f

    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XSmall)
            .alpha(itemAlpha)
            .testTag("${testTagPrefix}_game_${game.igdbGameId}")
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
                    game.consoleAbbreviation?.let { abbr ->
                        Text(
                            text = abbr.uppercase(),
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
                            modifier = Modifier.size(SpSpacing.IconXSmall),
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
