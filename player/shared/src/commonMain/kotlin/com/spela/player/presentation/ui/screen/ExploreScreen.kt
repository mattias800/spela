package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.spela.player.presentation.ui.theme.spScreenBackground
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.feature.explore.ActiveNowSection
import com.spela.player.presentation.ui.feature.explore.ArtworkShowcaseSection
import com.spela.player.presentation.ui.feature.explore.ArtworkShowcaseSkeleton
import com.spela.player.presentation.ui.feature.explore.CommunityTopSection
import com.spela.player.presentation.ui.feature.explore.ConsoleQuickJumpSection
import com.spela.player.presentation.ui.feature.explore.ConsoleQuickJumpSkeleton
import com.spela.player.presentation.ui.feature.explore.CultClassicsSection
import com.spela.player.presentation.ui.feature.explore.DeveloperSpotlightSection
import com.spela.player.presentation.ui.feature.explore.DeveloperSpotlightSkeleton
import com.spela.player.presentation.ui.feature.explore.ForYouSection
import com.spela.player.presentation.ui.feature.explore.ForYouSkeleton
import com.spela.player.presentation.ui.feature.explore.GameShelf
import com.spela.player.presentation.ui.feature.explore.GameShelfSkeleton
import com.spela.player.presentation.ui.feature.explore.HeroCarousel
import com.spela.player.presentation.ui.feature.explore.HeroCarouselSkeleton
import com.spela.player.presentation.ui.feature.explore.KeywordChips
import com.spela.player.presentation.ui.feature.explore.KeywordChipsSkeleton
import com.spela.player.presentation.ui.feature.explore.SeriesShelf
import com.spela.player.presentation.ui.feature.explore.SeriesShelfSkeleton
import com.spela.player.presentation.ui.feature.explore.MoodPicker
import com.spela.player.presentation.ui.feature.explore.MoodPickerSkeleton
import com.spela.player.presentation.ui.feature.explore.RecentlyReviewedSection
import com.spela.player.presentation.ui.feature.explore.SocialSectionSkeleton
import com.spela.player.presentation.ui.feature.explore.ThemeGrid
import com.spela.player.presentation.ui.feature.explore.ThemeGridSkeleton
import com.spela.player.presentation.ui.feature.explore.AchievementSectionSkeleton
import com.spela.player.presentation.ui.feature.explore.ActiveChallengesSection
import com.spela.player.presentation.ui.feature.explore.AlmostDoneSection
import com.spela.player.presentation.ui.feature.explore.AnniversariesSection
import com.spela.player.presentation.ui.feature.explore.EasyToCompleteSection
import com.spela.player.presentation.ui.feature.explore.FreshChallengesSection
import com.spela.player.presentation.ui.feature.explore.HardestGamesSection
import com.spela.player.presentation.ui.feature.explore.OnThisDaySection
import com.spela.player.presentation.ui.feature.explore.TemporalSectionSkeleton
import com.spela.player.presentation.ui.feature.explore.TrendingSection
import com.spela.player.presentation.ui.feature.explore.WildFeaturesSection
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onThemeSelected: ((themeId: String, themeName: String) -> Unit)? = null,
    onKeywordSelected: ((keywordId: String, keywordName: String) -> Unit)? = null,
    onSeriesSelected: ((seriesId: String, seriesName: String) -> Unit)? = null,

    onMoodSelected: ((moodId: String, moodName: String) -> Unit)? = null,
    onDeveloperSelected: ((name: String) -> Unit)? = null,
    onConsoleSelected: ((consoleId: String) -> Unit)? = null,
    onChallengeSelected: ((challengeId: String) -> Unit)? = null,
    onGallerySelected: (() -> Unit)? = null,
    onSurpriseMe: (() -> Unit)? = null,
    onWizardSelected: (() -> Unit)? = null,
    onGlobalSearchSelected: (() -> Unit)? = null,
    onSearchSelected: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val titleBarInset = LocalTitleBarInset.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .spScreenBackground()
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
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                ) {
                    // Hero carousel
                    item {
                        if (state.isLoadingFeatured && state.featuredGames.isEmpty()) {
                            HeroCarouselSkeleton(
                                modifier = Modifier.padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                ),
                            )
                        } else if (state.featuredGames.isNotEmpty()) {
                            HeroCarousel(
                                featuredGames = state.featuredGames,
                                onGameSelected = onGameSelected,
                                modifier = Modifier.padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                ),
                            )
                        }
                    }

                    // Global search entry point — tappable search bar
                    item {
                        SearchBarEntryPoint(
                            onClick = { onGlobalSearchSelected?.invoke() },
                            modifier = Modifier
                                .padding(
                                    horizontal = SpSpacing.ScreenHorizontal,
                                )
                                .testTag("explore_search_bar"),
                        )
                    }

                    // Console quick-jump section
                    item {
                        if (state.isLoadingConsoleHighlights && state.consoleHighlights.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Browse by Console",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                ConsoleQuickJumpSkeleton()
                            }
                        } else if (state.consoleHighlights.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Browse by Console",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_consoles_section"),
                            ) {
                                ConsoleQuickJumpSection(
                                    consoles = state.consoleHighlights,
                                    onConsoleSelected = { consoleId ->
                                        onConsoleSelected?.invoke(consoleId)
                                    },
                                )
                            }
                        }
                    }

                    // Mood picker section
                    item {
                        if (state.isLoadingMoods && state.moods.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "What are you in the mood for?",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                MoodPickerSkeleton()
                            }
                        } else if (state.moods.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
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
                                )
                            }
                        }
                    }

                    // Wild Features — Lucky & Wizard
                    item {
                        WildFeaturesSection(
                            onSurpriseMe = { onSurpriseMe?.invoke() },
                            onWizardSelected = { onWizardSelected?.invoke() },
                            includeTopSpacing = false,
                            modifier = Modifier
                                .padding(horizontal = SpSpacing.ScreenHorizontal)
                                .testTag("explore_wild_features"),
                        )
                    }

                    // For You section (personalized recommendations)
                    item {
                        if (state.isLoadingForYou && state.forYouRows.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "For You",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                ForYouSkeleton()
                            }
                        } else if (state.forYouRows.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "For You",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_for_you_section"),
                            ) {
                                ForYouSection(
                                    rows = state.forYouRows,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Theme grid section
                    item {
                        if (state.isLoadingThemes && state.themes.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Browse by Theme",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                ThemeGridSkeleton()
                            }
                        } else if (state.themes.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
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
                                includeTopSpacing = false,
                                title = "Popular Keywords",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                KeywordChipsSkeleton()
                            }
                        } else if (state.keywords.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
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
                                includeTopSpacing = false,
                                title = "Browse by Series",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SeriesShelfSkeleton()
                            }
                        } else if (state.featuredSeries.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
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


                    // Developer spotlight section
                    item {
                        if (state.isLoadingDeveloperSpotlight && state.developerSpotlight == null) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Developer Spotlight",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                DeveloperSpotlightSkeleton()
                            }
                        } else if (state.developerSpotlight != null) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Developer Spotlight",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_developer_spotlight_section"),
                            ) {
                                DeveloperSpotlightSection(
                                    spotlight = state.developerSpotlight!!,
                                    onDeveloperSelected = { name ->
                                        onDeveloperSelected?.invoke(name)
                                    },
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Artwork showcase section
                    item {
                        if (state.isLoadingArtwork && state.artworkShowcase.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Visual Discovery",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                ArtworkShowcaseSkeleton()
                            }
                        } else if (state.artworkShowcase.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Visual Discovery",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_artwork_section"),
                                titleTrailing = if (onGallerySelected != null) {
                                    {
                                        Text(
                                            text = "Browse Gallery",
                                            style = SpTypography.LabelLarge,
                                            color = SpColor.Link,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(SpSpacing.Small))
                                                .clickable(onClick = onGallerySelected)
                                                .padding(SpSpacing.Small)
                                                .testTag("browse_gallery_button"),
                                        )
                                    }
                                } else null,
                            ) {
                                ArtworkShowcaseSection(
                                    artworks = state.artworkShowcase,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Social & Community Discovery sections
                    // Trending
                    item {
                        if (state.isLoadingSocial && state.trendingGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Trending on Your Server",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.trendingGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Trending on Your Server",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_trending_section"),
                            ) {
                                TrendingSection(
                                    games = state.trendingGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Community Favorites
                    item {
                        if (state.isLoadingSocial && state.communityTopGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Community Favorites",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.communityTopGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Community Favorites",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_community_top_section"),
                            ) {
                                CommunityTopSection(
                                    games = state.communityTopGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Cult Classics
                    item {
                        if (state.isLoadingSocial && state.cultClassics.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Cult Classics",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.cultClassics.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Cult Classics",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_cult_classics_section"),
                            ) {
                                CultClassicsSection(
                                    games = state.cultClassics,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Active Right Now
                    item {
                        if (state.isLoadingSocial && state.activeNowGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Active Right Now",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.activeNowGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Active Right Now",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_active_now_section"),
                            ) {
                                ActiveNowSection(
                                    games = state.activeNowGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Recently Reviewed
                    item {
                        if (state.isLoadingSocial && state.recentReviews.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Recently Reviewed",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.recentReviews.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Recently Reviewed",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_recently_reviewed_section"),
                            ) {
                                RecentlyReviewedSection(
                                    reviews = state.recentReviews,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Temporal Discovery: On This Day
                    item {
                        if (state.isLoadingTemporal && state.onThisDayGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "On This Day",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                TemporalSectionSkeleton()
                            }
                        } else if (state.onThisDayGames.isNotEmpty()) {
                            val title = if (state.onThisDayDate.isNotEmpty()) {
                                "On This Day (${state.onThisDayDate})"
                            } else {
                                "On This Day"
                            }
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = title,
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_on_this_day_section"),
                            ) {
                                OnThisDaySection(
                                    games = state.onThisDayGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Temporal Discovery: Your Anniversaries
                    item {
                        if (state.isLoadingTemporal && state.anniversaries.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Your Anniversaries",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                TemporalSectionSkeleton()
                            }
                        } else if (state.anniversaries.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Your Anniversaries",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_anniversaries_section"),
                            ) {
                                AnniversariesSection(
                                    anniversaries = state.anniversaries,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Easy to 100%
                    item {
                        if (state.isLoadingAchievement && state.easyToCompleteGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Easy to 100%",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.easyToCompleteGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Easy to 100%",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_easy_to_complete_section"),
                            ) {
                                EasyToCompleteSection(
                                    games = state.easyToCompleteGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Mount Everest (Hardest)
                    item {
                        if (state.isLoadingAchievement && state.hardestGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Mount Everest",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.hardestGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Mount Everest",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_hardest_games_section"),
                            ) {
                                HardestGamesSection(
                                    games = state.hardestGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Almost Done
                    item {
                        if (state.isLoadingAchievement && state.almostDoneGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Almost Done",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.almostDoneGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Almost Done",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_almost_done_section"),
                            ) {
                                AlmostDoneSection(
                                    games = state.almostDoneGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Fresh Challenges
                    item {
                        if (state.isLoadingAchievement && state.freshChallengeGames.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Fresh Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.freshChallengeGames.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Fresh Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_fresh_challenges_section"),
                            ) {
                                FreshChallengesSection(
                                    games = state.freshChallengeGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Active Challenges
                    item {
                        if (state.isLoadingAchievement && state.activeChallenges.isEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Active Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.activeChallenges.isNotEmpty()) {
                            SpTitledSection(
                                includeTopSpacing = false,
                                title = "Active Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("explore_active_challenges_section"),
                            ) {
                                ActiveChallengesSection(
                                    challenges = state.activeChallenges,
                                    onChallengeClick = { challengeId ->
                                        onChallengeSelected?.invoke(challengeId)
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
                                includeTopSpacing = false,
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
                                includeTopSpacing = false,
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

@Composable
private fun SearchBarEntryPoint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusLarge)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = shape)
            .clip(shape)
            .background(SpColor.SurfaceVariant)
            .border(1.dp, SpColor.Divider, shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = SpSpacing.Default, vertical = 14.dp)
            .semantics {
                contentDescription = "Search games, consoles, developers"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = SpColor.OnBackgroundTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(SpSpacing.Small))
        Text(
            text = "Search games, consoles, developers...",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
