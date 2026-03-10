package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.feature.explore.GameShelf
import com.spela.player.presentation.ui.feature.explore.GameShelfSkeleton
import com.spela.player.presentation.ui.feature.explore.HeroCarousel
import com.spela.player.presentation.ui.feature.explore.HeroCarouselSkeleton
import com.spela.player.presentation.ui.feature.explore.KeywordChips
import com.spela.player.presentation.ui.feature.explore.KeywordChipsSkeleton
import com.spela.player.presentation.ui.feature.explore.MoodPicker
import com.spela.player.presentation.ui.feature.explore.MoodPickerSkeleton
import com.spela.player.presentation.ui.feature.explore.SeriesShelf
import com.spela.player.presentation.ui.feature.explore.SeriesShelfSkeleton
import com.spela.player.presentation.ui.feature.explore.ThemeGrid
import com.spela.player.presentation.ui.feature.explore.ThemeGridSkeleton
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ExploreViewModel

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onThemeSelected: ((themeId: String, themeName: String) -> Unit)? = null,
    onKeywordSelected: ((keywordId: String, keywordName: String) -> Unit)? = null,
    onSeriesSelected: ((seriesId: String, seriesName: String) -> Unit)? = null,
    onMoodSelected: ((moodId: String, moodName: String) -> Unit)? = null,
    onSurpriseMe: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val titleBarInset = LocalTitleBarInset.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explore_screen"),
    ) {
        when {
            // Empty library: no data at all and not loading
            state.isEmpty && !state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpEmptyState(
                        icon = Icons.Filled.Explore,
                        title = "Nothing to explore yet",
                        message = "Games need to be added to the library before you can explore. Contact your server admin to add games.",
                        modifier = Modifier.testTag("explore_empty_state"),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = titleBarInset,
                        bottom = SpSpacing.XLarge,
                    ),
                ) {
                    // Hero carousel
                    item {
                        if (state.isLoadingFeatured && state.featuredGames.isEmpty()) {
                            HeroCarouselSkeleton(
                                modifier = Modifier.padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                    vertical = SpSpacing.Default,
                                ),
                            )
                        } else if (state.featuredGames.isNotEmpty()) {
                            HeroCarousel(
                                featuredGames = state.featuredGames,
                                onGameSelected = onGameSelected,
                                modifier = Modifier.padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                    vertical = SpSpacing.Default,
                                ),
                            )
                        }
                    }

                    // Mood picker section
                    item {
                        if (state.isLoadingMoods && state.moods.isEmpty()) {
                            SpTitledSection(
                                title = "What are you in the mood for?",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                MoodPickerSkeleton()
                            }
                        } else if (state.moods.isNotEmpty()) {
                            SpTitledSection(
                                title = "What are you in the mood for?",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_moods_section"),
                            ) {
                                MoodPicker(
                                    moods = state.moods,
                                    onMoodSelected = { moodId, moodName ->
                                        onMoodSelected?.invoke(moodId, moodName)
                                    },
                                    onSurpriseMe = {
                                        onSurpriseMe?.invoke()
                                    },
                                )
                            }
                        }
                    }

                    // Theme grid section
                    item {
                        if (state.isLoadingThemes && state.themes.isEmpty()) {
                            SpTitledSection(
                                title = "Browse by Theme",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                ThemeGridSkeleton()
                            }
                        } else if (state.themes.isNotEmpty()) {
                            SpTitledSection(
                                title = "Browse by Theme",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_themes_section"),
                            ) {
                                ThemeGrid(
                                    themes = state.themes,
                                    onThemeSelected = { themeId, themeName ->
                                        onThemeSelected?.invoke(themeId, themeName)
                                    },
                                )
                            }
                        }
                    }

                    // Keyword chips section
                    item {
                        if (state.isLoadingKeywords && state.keywords.isEmpty()) {
                            SpTitledSection(
                                title = "Popular Keywords",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                KeywordChipsSkeleton()
                            }
                        } else if (state.keywords.isNotEmpty()) {
                            SpTitledSection(
                                title = "Popular Keywords",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_keywords_section"),
                            ) {
                                KeywordChips(
                                    keywords = state.keywords,
                                    onKeywordSelected = { keywordId, keywordName ->
                                        onKeywordSelected?.invoke(keywordId, keywordName)
                                    },
                                )
                            }
                        }
                    }

                    // Series shelf section
                    item {
                        if (state.isLoadingFeaturedSeries && state.featuredSeries.isEmpty()) {
                            SpTitledSection(
                                title = "Browse by Series",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SeriesShelfSkeleton()
                            }
                        } else if (state.featuredSeries.isNotEmpty()) {
                            SpTitledSection(
                                title = "Browse by Series",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_series_section"),
                            ) {
                                SeriesShelf(
                                    series = state.featuredSeries,
                                    onSeriesSelected = { seriesId, seriesName ->
                                        onSeriesSelected?.invoke(seriesId, seriesName)
                                    },
                                )
                            }
                        }
                    }

                    // Shelf rows
                    if (state.isLoadingRows && state.rows.isEmpty()) {
                        // Loading skeletons for rows
                        val skeletonTitles = listOf("Top Rated", "Recently Added", "Hidden Gems")
                        items(skeletonTitles.size) { index ->
                            SpTitledSection(
                                title = skeletonTitles[index],
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                GameShelfSkeleton()
                            }
                        }
                    } else {
                        items(
                            items = state.rows,
                            key = { it.id },
                        ) { row ->
                            if (row.games.isNotEmpty()) {
                                SpTitledSection(
                                    title = row.title,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("explore_row_${row.id}"),
                                ) {
                                    GameShelf(
                                        games = row.games,
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }
                        }
                    }

                    // Bottom spacer
                    item { Spacer(Modifier.height(SpSpacing.XLarge)) }
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
                    onAction = { viewModel.dismissError() },
                )
            },
            onDismiss = { viewModel.dismissError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
