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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.DeveloperAtAGlance
import com.spela.player.presentation.ui.feature.explore.DeveloperCompanyDescription
import com.spela.player.presentation.ui.feature.explore.DeveloperGameItem
import com.spela.player.presentation.ui.feature.explore.DeveloperGenreBreakdown
import com.spela.player.presentation.ui.feature.explore.DeveloperHeroBanner
import com.spela.player.presentation.ui.feature.explore.DeveloperPublishersSection
import com.spela.player.presentation.ui.feature.explore.DeveloperRatingDistribution
import com.spela.player.presentation.ui.feature.explore.DeveloperTimeline
import com.spela.player.presentation.ui.feature.explore.DeveloperTopRatedRow
import com.spela.player.presentation.ui.feature.explore.DeveloperUserStatsCard
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import androidx.compose.material3.Text

@Composable
fun ExploreDeveloperScreen(
    name: String,
    isDeveloper: Boolean = true,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.developerDetailState.collectAsState()

    LaunchedEffect(name, isDeveloper) {
        if (isDeveloper) {
            viewModel.loadDeveloperDetail(name)
        } else {
            viewModel.loadPublisherDetail(name)
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("developer_detail_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = name,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.detail == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("developer_detail_loading"),
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
                        modifier = Modifier.fillMaxSize().testTag("developer_detail_content"),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // 1. Hero Banner
                        item {
                            DeveloperHeroBanner(
                                detail = detail,
                                modifier = Modifier.testTag("developer_hero_banner"),
                            )
                        }

                        // 2. Company Description (below hero banner)
                        val companyInfo = detail.companyInfo
                        if (companyInfo?.description != null) {
                            item {
                                Spacer(Modifier.height(SpSpacing.Large))
                                DeveloperCompanyDescription(
                                    companyInfo = companyInfo,
                                    modifier = Modifier.testTag("developer_company_description_section"),
                                )
                            }
                        }

                        // 3. At a Glance stats row
                        item {
                            DeveloperAtAGlance(
                                detail = detail,
                                modifier = Modifier.testTag("developer_at_a_glance_section"),
                            )
                        }

                        // 4. Top Rated Row
                        if (detail.topGames.isNotEmpty() && detail.gameCount >= 5) {
                            item {
                                DeveloperTopRatedRow(
                                    topGames = detail.topGames,
                                    onGameSelected = onGameSelected,
                                    modifier = Modifier.testTag("developer_top_rated_section"),
                                )
                            }
                        }

                        // 5. Release Timeline
                        if (detail.timeline.isNotEmpty()) {
                            item {
                                DeveloperTimeline(
                                    timeline = detail.timeline,
                                    onGameSelected = onGameSelected,
                                    modifier = Modifier.testTag("developer_timeline_section"),
                                )
                            }
                        }

                        // 6. Rating Distribution (only shown when 5+ rated games)
                        val ratingDist = detail.ratingDistribution
                        if (ratingDist != null && ratingDist.totalRated >= 5) {
                            item {
                                DeveloperRatingDistribution(
                                    distribution = ratingDist,
                                    modifier = Modifier.testTag("developer_rating_distribution_section"),
                                )
                            }
                        }

                        // 7. Genre Breakdown
                        if (detail.genreBreakdown.size >= 2) {
                            item {
                                DeveloperGenreBreakdown(
                                    genres = detail.genreBreakdown.map { it.name to it.gameCount },
                                    totalGames = detail.gameCount,
                                    selectedGenre = state.genreFilter,
                                    onGenreSelected = { genre ->
                                        viewModel.setDeveloperGenreFilter(
                                            if (genre != null && state.genreFilter == genre) null else genre,
                                        )
                                    },
                                    modifier = Modifier.testTag("developer_genre_breakdown"),
                                )
                            }
                        }

                        // 8. User Stats Card
                        if (detail.userStats != null) {
                            item {
                                DeveloperUserStatsCard(
                                    userStats = detail.userStats,
                                    totalGames = detail.gameCount,
                                    onGameSelected = onGameSelected,
                                    modifier = Modifier.testTag("developer_user_stats"),
                                )
                            }
                        }

                        // 9. Games Grouped by Platform
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
                                        icon = Icons.Filled.Code,
                                        title = "No games found",
                                        message = "No games match the selected filter.",
                                        modifier = Modifier.testTag("developer_empty_state"),
                                    )
                                }
                            }
                        } else {
                            // Group games by platform, ordered by count descending
                            val platformBreakdown = detail.platformBreakdown
                                .sortedByDescending { it.count }

                            if (platformBreakdown.isNotEmpty()) {
                                platformBreakdown.forEach { platform ->
                                    val platformGames = filteredGames.filter {
                                        it.consoleName.equals(platform.consoleName, ignoreCase = true)
                                    }
                                    if (platformGames.isNotEmpty()) {
                                        item(key = "platform_header_${platform.consoleId}") {
                                            DeveloperPlatformHeader(
                                                consoleName = platform.consoleName,
                                                gameCount = platformGames.size,
                                                modifier = Modifier.testTag("developer_platform_header_${platform.consoleId}"),
                                            )
                                        }
                                        items(
                                            items = platformGames,
                                            key = { it.id },
                                        ) { game ->
                                            DeveloperGameItem(
                                                game = game,
                                                onClick = { onGameSelected(game.id) },
                                            )
                                        }
                                    }
                                }
                                // Also show games that don't match any platform in breakdown
                                val knownPlatforms = platformBreakdown.map { it.consoleName.lowercase() }.toSet()
                                val uncategorized = filteredGames.filter {
                                    it.consoleName.lowercase() !in knownPlatforms
                                }
                                if (uncategorized.isNotEmpty()) {
                                    item(key = "platform_header_other") {
                                        DeveloperPlatformHeader(
                                            consoleName = "Other",
                                            gameCount = uncategorized.size,
                                            modifier = Modifier.testTag("developer_platform_header_other"),
                                        )
                                    }
                                    items(
                                        items = uncategorized,
                                        key = { "other_${it.id}" },
                                    ) { game ->
                                        DeveloperGameItem(
                                            game = game,
                                            onClick = { onGameSelected(game.id) },
                                        )
                                    }
                                }
                            } else {
                                // Fallback: no platform breakdown, show flat list with console filter
                                if (detail.consoles.isNotEmpty()) {
                                    item {
                                        DeveloperConsoleFilterRow(
                                            consoles = detail.consoles,
                                            totalGames = detail.gameCount,
                                            selectedConsole = state.consoleFilter,
                                            onConsoleSelected = { console ->
                                                viewModel.setDeveloperConsoleFilter(
                                                    if (console != null && state.consoleFilter == console) null else console,
                                                )
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = SpSpacing.ScreenHorizontal)
                                                .testTag("developer_console_filters"),
                                        )
                                        Spacer(Modifier.height(SpSpacing.Large))
                                    }
                                }
                                items(
                                    items = filteredGames,
                                    key = { it.id },
                                ) { game ->
                                    DeveloperGameItem(
                                        game = game,
                                        onClick = { onGameSelected(game.id) },
                                    )
                                }
                            }
                        }

                        // 10. Publishers Section
                        if (detail.publishers.isNotEmpty()) {
                            item {
                                DeveloperPublishersSection(
                                    publishers = detail.publishers,
                                    onPublisherSelected = onPublisherSelected,
                                    modifier = Modifier.testTag("developer_publishers_section"),
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
                            icon = Icons.Filled.Code,
                            title = if (isDeveloper) "Developer not found" else "Publisher not found",
                            message = "Could not load details.",
                            modifier = Modifier.testTag("developer_error_state"),
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
                    onAction = { viewModel.dismissDeveloperDetailError() },
                )
            },
            onDismiss = { viewModel.dismissDeveloperDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// --- (e) Platform Group Header ---

@Composable
private fun DeveloperPlatformHeader(
    consoleName: String,
    gameCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = consoleName,
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
        )
        Text(
            text = "$gameCount games",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

// --- Console Filter Row (fallback when no platform breakdown) ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperConsoleFilterRow(
    consoles: List<String>,
    totalGames: Int,
    selectedConsole: String?,
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
            isSelected = selectedConsole == null,
            modifier = Modifier
                .testTag("developer_console_chip_all")
                .semantics {
                    contentDescription = "All, $totalGames games"
                    role = Role.Button
                },
        )

        consoles.forEach { console ->
            val isSelected = console.equals(selectedConsole, ignoreCase = true)

            SpChip(
                text = console,
                onClick = { onConsoleSelected(console) },
                isSelected = isSelected,
                modifier = Modifier
                    .testTag("developer_console_chip_$console")
                    .semantics {
                        contentDescription = console
                        role = Role.Button
                    },
            )
        }
    }
}
