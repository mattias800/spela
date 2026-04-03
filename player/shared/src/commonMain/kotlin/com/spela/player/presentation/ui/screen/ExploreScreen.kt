package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScrollableContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpSectionList
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

    // Track initial load to avoid flashing empty state.
    // If the ViewModel already has data (revisit), start as loaded immediately.
    val hasDataOnMount = !state.isEmpty || state.isLoading
    var sawLoading by remember { mutableStateOf(false) }
    var hasInitiallyLoaded by remember { mutableStateOf(hasDataOnMount) }
    if (state.isLoading) sawLoading = true
    if (sawLoading && !state.isLoading) hasInitiallyLoaded = true

    SpScreen(modifier = Modifier.testTag("explore_screen")) {
        when {
            // Show loading until first fetch completes
            !hasInitiallyLoaded -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading...")
                }
            }
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
                SpScrollableContent {
                    // Hero carousel — edge-to-edge, outside SpMainContentPadding
                    if (state.isLoadingFeatured || state.featuredGames.isNotEmpty()) {
                        if (state.isLoadingFeatured && state.featuredGames.isEmpty()) {
                            HeroCarouselSkeleton()
                        } else if (state.featuredGames.isNotEmpty()) {
                            HeroCarousel(
                                featuredGames = state.featuredGames,
                                onGameSelected = onGameSelected,
                            )
                        }
                    }

                SpMainContentPadding {
                val focusMemory = rememberFocusMemoryState()
                CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                SpSectionList(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Global search entry point — tappable search bar
                    SearchBarEntryPoint(
                        onClick = { onGlobalSearchSelected?.invoke() },
                        modifier = Modifier
                            .autoFocus()
                            .testTag("explore_search_bar"),
                    )

                    // Console quick-jump section
                    if (state.isLoadingConsoleHighlights || state.consoleHighlights.isNotEmpty()) {
                        if (state.isLoadingConsoleHighlights && state.consoleHighlights.isEmpty()) {
                            SpTitledSection(
                                title = "Browse by Console",
                                edgeToEdgeContent = true,
                            ) {
                                ConsoleQuickJumpSkeleton()
                            }
                        } else if (state.consoleHighlights.isNotEmpty()) {
                            SpTitledSection(
                                title = "Browse by Console",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_consoles_section")
                                    .rememberFocus("section_browse_by_console"),
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
                    if (state.isLoadingMoods || state.moods.isNotEmpty()) {
                        if (state.isLoadingMoods && state.moods.isEmpty()) {
                            SpTitledSection(
                                title = "What are you in the mood for?",
                                edgeToEdgeContent = true,
                            ) {
                                MoodPickerSkeleton()
                            }
                        } else if (state.moods.isNotEmpty()) {
                            SpTitledSection(
                                title = "What are you in the mood for?",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_moods_section")
                                    .rememberFocus("section_moods"),
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
                    WildFeaturesSection(
                        onSurpriseMe = { onSurpriseMe?.invoke() },
                        onWizardSelected = { onWizardSelected?.invoke() },
                        modifier = Modifier
                            .testTag("explore_wild_features")
                            .rememberFocus("section_wild_features"),
                    )

                    // For You section (personalized recommendations)
                    if (state.isLoadingForYou || state.forYouRows.isNotEmpty()) {
                        if (state.isLoadingForYou && state.forYouRows.isEmpty()) {
                            SpTitledSection(
                                title = "For You",
                                edgeToEdgeContent = true,
                            ) {
                                ForYouSkeleton()
                            }
                        } else if (state.forYouRows.isNotEmpty()) {
                            SpTitledSection(
                                title = "For You",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_for_you_section")
                                    .rememberFocus("section_for_you"),
                            ) {
                                ForYouSection(
                                    rows = state.forYouRows,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Theme grid section
                    if (state.isLoadingThemes || state.themes.isNotEmpty()) {
                        if (state.isLoadingThemes && state.themes.isEmpty()) {
                            SpTitledSection(
                                title = "Browse by Theme",
                                edgeToEdgeContent = true,
                            ) {
                                ThemeGridSkeleton()
                            }
                        } else if (state.themes.isNotEmpty()) {
                            SpTitledSection(
                                title = "Browse by Theme",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_themes_section")
                                    .rememberFocus("section_themes"),
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
                    if (state.isLoadingKeywords || state.keywords.isNotEmpty()) {
                        if (state.isLoadingKeywords && state.keywords.isEmpty()) {
                            SpTitledSection(
                                title = "Popular Keywords",
                                edgeToEdgeContent = true,
                            ) {
                                KeywordChipsSkeleton()
                            }
                        } else if (state.keywords.isNotEmpty()) {
                            SpTitledSection(
                                title = "Popular Keywords",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_keywords_section")
                                    .rememberFocus("section_keywords"),
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
                    if (state.isLoadingFeaturedSeries || state.featuredSeries.isNotEmpty()) {
                        if (state.isLoadingFeaturedSeries && state.featuredSeries.isEmpty()) {
                            SpTitledSection(
                                title = "Browse by Series",
                                edgeToEdgeContent = true,
                            ) {
                                SeriesShelfSkeleton()
                            }
                        } else if (state.featuredSeries.isNotEmpty()) {
                            SpTitledSection(
                                title = "Browse by Series",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_series_section")
                                    .rememberFocus("section_series"),
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
                    if (state.isLoadingDeveloperSpotlight && state.developerSpotlight == null) {
                        DeveloperSpotlightSkeleton(
                        )
                    } else if (state.developerSpotlight != null) {
                        DeveloperSpotlightSection(
                            spotlight = state.developerSpotlight!!,
                            onDeveloperSelected = { name ->
                                onDeveloperSelected?.invoke(name)
                            },
                            onGameSelected = onGameSelected,
                            modifier = Modifier.testTag("explore_developer_spotlight_section")
                                .rememberFocus("section_developer_spotlight"),
                        )
                    }

                    // Artwork showcase section
                    if (state.isLoadingArtwork || state.artworkShowcase.isNotEmpty()) {
                        if (state.isLoadingArtwork && state.artworkShowcase.isEmpty()) {
                            SpTitledSection(
                                title = "Visual Discovery",
                                edgeToEdgeContent = true,
                            ) {
                                ArtworkShowcaseSkeleton()
                            }
                        } else if (state.artworkShowcase.isNotEmpty()) {
                            SpTitledSection(
                                title = "Visual Discovery",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_artwork_section")
                                    .rememberFocus("section_artwork"),
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
                    if (state.isLoadingSocial || state.trendingGames.isNotEmpty()) {
                        if (state.isLoadingSocial && state.trendingGames.isEmpty()) {
                            SpTitledSection(
                                title = "Trending on Your Server",
                                edgeToEdgeContent = true,
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.trendingGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Trending on Your Server",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_trending_section")
                                    .rememberFocus("section_trending"),
                            ) {
                                TrendingSection(
                                    games = state.trendingGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Community Favorites
                    if (state.isLoadingSocial || state.communityTopGames.isNotEmpty()) {
                        if (state.isLoadingSocial && state.communityTopGames.isEmpty()) {
                            SpTitledSection(
                                title = "Community Favorites",
                                edgeToEdgeContent = true,
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.communityTopGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Community Favorites",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_community_top_section")
                                    .rememberFocus("section_community_favorites"),
                            ) {
                                CommunityTopSection(
                                    games = state.communityTopGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Cult Classics
                    if (state.isLoadingSocial || state.cultClassics.isNotEmpty()) {
                        if (state.isLoadingSocial && state.cultClassics.isEmpty()) {
                            SpTitledSection(
                                title = "Cult Classics",
                                edgeToEdgeContent = true,
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.cultClassics.isNotEmpty()) {
                            SpTitledSection(
                                title = "Cult Classics",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_cult_classics_section")
                                    .rememberFocus("section_cult_classics"),
                            ) {
                                CultClassicsSection(
                                    games = state.cultClassics,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Active Right Now
                    if (state.isLoadingSocial || state.activeNowGames.isNotEmpty()) {
                        if (state.isLoadingSocial && state.activeNowGames.isEmpty()) {
                            SpTitledSection(
                                title = "Active Right Now",
                                edgeToEdgeContent = true,
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.activeNowGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Active Right Now",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_active_now_section")
                                    .rememberFocus("section_active_now"),
                            ) {
                                ActiveNowSection(
                                    games = state.activeNowGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Recently Reviewed
                    if (state.isLoadingSocial || state.recentReviews.isNotEmpty()) {
                        if (state.isLoadingSocial && state.recentReviews.isEmpty()) {
                            SpTitledSection(
                                title = "Recently Reviewed",
                                edgeToEdgeContent = true,
                            ) {
                                SocialSectionSkeleton()
                            }
                        } else if (state.recentReviews.isNotEmpty()) {
                            SpTitledSection(
                                title = "Recently Reviewed",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_recently_reviewed_section")
                                    .rememberFocus("section_recently_reviewed"),
                            ) {
                                RecentlyReviewedSection(
                                    reviews = state.recentReviews,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Temporal Discovery: On This Day
                    if (state.isLoadingTemporal || state.onThisDayGames.isNotEmpty()) {
                        if (state.isLoadingTemporal && state.onThisDayGames.isEmpty()) {
                            SpTitledSection(
                                title = "On This Day",
                                edgeToEdgeContent = true,
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
                                title = title,
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_on_this_day_section")
                                    .rememberFocus("section_on_this_day"),
                            ) {
                                OnThisDaySection(
                                    games = state.onThisDayGames,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Temporal Discovery: Your Anniversaries
                    if (state.isLoadingTemporal || state.anniversaries.isNotEmpty()) {
                        if (state.isLoadingTemporal && state.anniversaries.isEmpty()) {
                            SpTitledSection(
                                title = "Your Anniversaries",
                                edgeToEdgeContent = true,
                            ) {
                                TemporalSectionSkeleton()
                            }
                        } else if (state.anniversaries.isNotEmpty()) {
                            SpTitledSection(
                                title = "Your Anniversaries",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_anniversaries_section")
                                    .rememberFocus("section_anniversaries"),
                            ) {
                                AnniversariesSection(
                                    anniversaries = state.anniversaries,
                                    onGameSelected = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Easy to 100%
                    if (state.isLoadingAchievement || state.easyToCompleteGames.isNotEmpty()) {
                        if (state.isLoadingAchievement && state.easyToCompleteGames.isEmpty()) {
                            SpTitledSection(
                                title = "Easy to 100%",
                                edgeToEdgeContent = true,
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.easyToCompleteGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Easy to 100%",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_easy_to_complete_section")
                                    .rememberFocus("section_easy_to_complete"),
                            ) {
                                EasyToCompleteSection(
                                    games = state.easyToCompleteGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Mount Everest (Hardest)
                    if (state.isLoadingAchievement || state.hardestGames.isNotEmpty()) {
                        if (state.isLoadingAchievement && state.hardestGames.isEmpty()) {
                            SpTitledSection(
                                title = "Mount Everest",
                                edgeToEdgeContent = true,
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.hardestGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Mount Everest",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_hardest_games_section")
                                    .rememberFocus("section_hardest_games"),
                            ) {
                                HardestGamesSection(
                                    games = state.hardestGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Almost Done
                    if (state.isLoadingAchievement || state.almostDoneGames.isNotEmpty()) {
                        if (state.isLoadingAchievement && state.almostDoneGames.isEmpty()) {
                            SpTitledSection(
                                title = "Almost Done",
                                edgeToEdgeContent = true,
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.almostDoneGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Almost Done",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_almost_done_section")
                                    .rememberFocus("section_almost_done"),
                            ) {
                                AlmostDoneSection(
                                    games = state.almostDoneGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Fresh Challenges
                    if (state.isLoadingAchievement || state.freshChallengeGames.isNotEmpty()) {
                        if (state.isLoadingAchievement && state.freshChallengeGames.isEmpty()) {
                            SpTitledSection(
                                title = "Fresh Challenges",
                                edgeToEdgeContent = true,
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.freshChallengeGames.isNotEmpty()) {
                            SpTitledSection(
                                title = "Fresh Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_fresh_challenges_section")
                                    .rememberFocus("section_fresh_challenges"),
                            ) {
                                FreshChallengesSection(
                                    games = state.freshChallengeGames,
                                    onGameClick = onGameSelected,
                                )
                            }
                        }
                    }

                    // Achievement Discovery: Active Challenges
                    if (state.isLoadingAchievement || state.activeChallenges.isNotEmpty()) {
                        if (state.isLoadingAchievement && state.activeChallenges.isEmpty()) {
                            SpTitledSection(
                                title = "Active Challenges",
                                edgeToEdgeContent = true,
                            ) {
                                AchievementSectionSkeleton()
                            }
                        } else if (state.activeChallenges.isNotEmpty()) {
                            SpTitledSection(
                                title = "Active Challenges",
                                edgeToEdgeContent = true,
                                modifier = Modifier
                                    .testTag("explore_active_challenges_section")
                                    .rememberFocus("section_active_challenges"),
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
                        skeletonTitles.forEach { title ->
                            SpTitledSection(
                                title = title,
                                edgeToEdgeContent = true,
                            ) {
                                GameShelfSkeleton()
                            }
                        }
                    } else {
                        state.rows.forEach { row ->
                            if (row.games.isNotEmpty()) {
                                SpTitledSection(
                                title = row.title,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier
                                        .testTag("explore_row_${row.id}")
                                        .rememberFocus("section_row_${row.id}"),
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
                    Spacer(Modifier.height(SpSpacing.XLarge))
                } // SpSectionList
                } // CompositionLocalProvider
                } // SpMainContentPadding
                } // SpScrollableContent
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
            .clip(shape)
            .background(SpColor.SurfaceVariant)
            .border(1.dp, SpColor.Divider, shape)
            .clickable(onClick = onClick)
            .gamepadFocusable(shape = shape)
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
