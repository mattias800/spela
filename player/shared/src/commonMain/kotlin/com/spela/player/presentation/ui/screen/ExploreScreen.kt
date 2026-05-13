package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpSectionLink
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
                    ScreenLoadingIndicator(message = "Loading...")
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
                            .focusRestoreItem(key = "explore_search_bar", isDefault = true)
                            .testTag("explore_search_bar"),
                    )

                    ExploreSection(
                        title = "Browse by Console",
                        testTag = "explore_consoles_section",
                        focusKey = "section_browse_by_console",
                        isLoading = state.isLoadingConsoleHighlights,
                        isEmpty = state.consoleHighlights.isEmpty(),
                        skeleton = { ConsoleQuickJumpSkeleton() },
                    ) {
                        ConsoleQuickJumpSection(
                            consoles = state.consoleHighlights,
                            onConsoleSelected = { onConsoleSelected?.invoke(it) },
                        )
                    }

                    ExploreSection(
                        title = "What are you in the mood for?",
                        testTag = "explore_moods_section",
                        focusKey = "section_moods",
                        isLoading = state.isLoadingMoods,
                        isEmpty = state.moods.isEmpty(),
                        skeleton = { MoodPickerSkeleton() },
                    ) {
                        MoodPicker(
                            moods = state.moods,
                            onMoodSelected = { id, name -> onMoodSelected?.invoke(id, name) },
                        )
                    }

                    // Wild Features — Lucky & Wizard
                    WildFeaturesSection(
                        onSurpriseMe = { onSurpriseMe?.invoke() },
                        onWizardSelected = { onWizardSelected?.invoke() },
                        modifier = Modifier
                            .testTag("explore_wild_features")
                            .rememberFocus("section_wild_features"),
                    )

                    ExploreSection(
                        title = "For You",
                        testTag = "explore_for_you_section",
                        focusKey = "section_for_you",
                        isLoading = state.isLoadingForYou,
                        isEmpty = state.forYouRows.isEmpty(),
                        skeleton = { ForYouSkeleton() },
                    ) {
                        ForYouSection(
                            rows = state.forYouRows,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Browse by Theme",
                        testTag = "explore_themes_section",
                        focusKey = "section_themes",
                        isLoading = state.isLoadingThemes,
                        isEmpty = state.themes.isEmpty(),
                        skeleton = { ThemeGridSkeleton() },
                    ) {
                        ThemeGrid(
                            themes = state.themes,
                            onThemeSelected = { id, name -> onThemeSelected?.invoke(id, name) },
                        )
                    }

                    ExploreSection(
                        title = "Popular Keywords",
                        testTag = "explore_keywords_section",
                        focusKey = "section_keywords",
                        isLoading = state.isLoadingKeywords,
                        isEmpty = state.keywords.isEmpty(),
                        skeleton = { KeywordChipsSkeleton() },
                    ) {
                        KeywordChips(
                            keywords = state.keywords,
                            onKeywordSelected = { id, name -> onKeywordSelected?.invoke(id, name) },
                        )
                    }

                    ExploreSection(
                        title = "Browse by Series",
                        testTag = "explore_series_section",
                        focusKey = "section_series",
                        isLoading = state.isLoadingFeaturedSeries,
                        isEmpty = state.featuredSeries.isEmpty(),
                        skeleton = { SeriesShelfSkeleton() },
                    ) {
                        SeriesShelf(
                            series = state.featuredSeries,
                            onSeriesSelected = { id, name -> onSeriesSelected?.invoke(id, name) },
                        )
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

                    ExploreSection(
                        title = "Visual Discovery",
                        testTag = "explore_artwork_section",
                        focusKey = "section_artwork",
                        isLoading = state.isLoadingArtwork,
                        isEmpty = state.artworkShowcase.isEmpty(),
                        skeleton = { ArtworkShowcaseSkeleton() },
                        titleTrailing = onGallerySelected?.let { onClick ->
                            {
                                SpSectionLink(
                                    text = "Browse Gallery",
                                    onClick = onClick,
                                    modifier = Modifier.testTag("browse_gallery_button"),
                                )
                            }
                        },
                    ) {
                        ArtworkShowcaseSection(
                            artworks = state.artworkShowcase,
                            onGameSelected = onGameSelected,
                        )
                    }

                    // Social & Community Discovery sections — all share the
                    // single state.isLoadingSocial flag.
                    ExploreSection(
                        title = "Trending on Your Server",
                        testTag = "explore_trending_section",
                        focusKey = "section_trending",
                        isLoading = state.isLoadingSocial,
                        isEmpty = state.trendingGames.isEmpty(),
                        skeleton = { SocialSectionSkeleton() },
                    ) {
                        TrendingSection(
                            games = state.trendingGames,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Community Favorites",
                        testTag = "explore_community_top_section",
                        focusKey = "section_community_favorites",
                        isLoading = state.isLoadingSocial,
                        isEmpty = state.communityTopGames.isEmpty(),
                        skeleton = { SocialSectionSkeleton() },
                    ) {
                        CommunityTopSection(
                            games = state.communityTopGames,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Cult Classics",
                        testTag = "explore_cult_classics_section",
                        focusKey = "section_cult_classics",
                        isLoading = state.isLoadingSocial,
                        isEmpty = state.cultClassics.isEmpty(),
                        skeleton = { SocialSectionSkeleton() },
                    ) {
                        CultClassicsSection(
                            games = state.cultClassics,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Active Right Now",
                        testTag = "explore_active_now_section",
                        focusKey = "section_active_now",
                        isLoading = state.isLoadingSocial,
                        isEmpty = state.activeNowGames.isEmpty(),
                        skeleton = { SocialSectionSkeleton() },
                    ) {
                        ActiveNowSection(
                            games = state.activeNowGames,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Recently Reviewed",
                        testTag = "explore_recently_reviewed_section",
                        focusKey = "section_recently_reviewed",
                        isLoading = state.isLoadingSocial,
                        isEmpty = state.recentReviews.isEmpty(),
                        skeleton = { SocialSectionSkeleton() },
                    ) {
                        RecentlyReviewedSection(
                            reviews = state.recentReviews,
                            onGameSelected = onGameSelected,
                        )
                    }

                    // Title includes the date when populated — the skeleton
                    // variant doesn't have that context yet, so both branches
                    // use the static "On This Day" label while loading.
                    val onThisDayTitle = if (state.onThisDayDate.isNotEmpty()) {
                        "On This Day (${state.onThisDayDate})"
                    } else {
                        "On This Day"
                    }
                    ExploreSection(
                        title = onThisDayTitle,
                        testTag = "explore_on_this_day_section",
                        focusKey = "section_on_this_day",
                        isLoading = state.isLoadingTemporal,
                        isEmpty = state.onThisDayGames.isEmpty(),
                        skeleton = { TemporalSectionSkeleton() },
                    ) {
                        OnThisDaySection(
                            games = state.onThisDayGames,
                            onGameSelected = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Your Anniversaries",
                        testTag = "explore_anniversaries_section",
                        focusKey = "section_anniversaries",
                        isLoading = state.isLoadingTemporal,
                        isEmpty = state.anniversaries.isEmpty(),
                        skeleton = { TemporalSectionSkeleton() },
                    ) {
                        AnniversariesSection(
                            anniversaries = state.anniversaries,
                            onGameSelected = onGameSelected,
                        )
                    }

                    // Achievement Discovery — all share state.isLoadingAchievement.
                    ExploreSection(
                        title = "Easy to 100%",
                        testTag = "explore_easy_to_complete_section",
                        focusKey = "section_easy_to_complete",
                        isLoading = state.isLoadingAchievement,
                        isEmpty = state.easyToCompleteGames.isEmpty(),
                        skeleton = { AchievementSectionSkeleton() },
                    ) {
                        EasyToCompleteSection(
                            games = state.easyToCompleteGames,
                            onGameClick = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Mount Everest",
                        testTag = "explore_hardest_games_section",
                        focusKey = "section_hardest_games",
                        isLoading = state.isLoadingAchievement,
                        isEmpty = state.hardestGames.isEmpty(),
                        skeleton = { AchievementSectionSkeleton() },
                    ) {
                        HardestGamesSection(
                            games = state.hardestGames,
                            onGameClick = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Almost Done",
                        testTag = "explore_almost_done_section",
                        focusKey = "section_almost_done",
                        isLoading = state.isLoadingAchievement,
                        isEmpty = state.almostDoneGames.isEmpty(),
                        skeleton = { AchievementSectionSkeleton() },
                    ) {
                        AlmostDoneSection(
                            games = state.almostDoneGames,
                            onGameClick = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Fresh Challenges",
                        testTag = "explore_fresh_challenges_section",
                        focusKey = "section_fresh_challenges",
                        isLoading = state.isLoadingAchievement,
                        isEmpty = state.freshChallengeGames.isEmpty(),
                        skeleton = { AchievementSectionSkeleton() },
                    ) {
                        FreshChallengesSection(
                            games = state.freshChallengeGames,
                            onGameClick = onGameSelected,
                        )
                    }

                    ExploreSection(
                        title = "Active Challenges",
                        testTag = "explore_active_challenges_section",
                        focusKey = "section_active_challenges",
                        isLoading = state.isLoadingAchievement,
                        isEmpty = state.activeChallenges.isEmpty(),
                        skeleton = { AchievementSectionSkeleton() },
                    ) {
                        ActiveChallengesSection(
                            challenges = state.activeChallenges,
                            onChallengeClick = { onChallengeSelected?.invoke(it) },
                        )
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
                                        memoryKey = "explore_row_${row.id}",
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

/**
 * Renders one Explore section with the canonical loading / populated /
 * hidden conditional ladder. Collapses the 15+ copies of this pattern
 * that used to live inline in [ExploreScreen]:
 *
 *   - loading + empty data → skeleton inside [SpTitledSection]
 *   - populated data → content inside [SpTitledSection] with test tag + focus key
 *   - idle + empty → render nothing
 *
 * [isLoading] / [isEmpty] come from the view model. [title] / [testTag] /
 * [focusKey] are the per-section labels. [titleTrailing] matches
 * [SpTitledSection]'s optional trailing slot (used by the Artwork
 * section's "Browse Gallery" link).
 */
@Composable
private fun ExploreSection(
    title: String,
    testTag: String,
    focusKey: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    skeleton: @Composable () -> Unit,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when {
        isLoading && isEmpty -> {
            SpTitledSection(
                title = title,
                edgeToEdgeContent = true,
            ) {
                skeleton()
            }
        }
        !isEmpty -> {
            SpTitledSection(
                title = title,
                edgeToEdgeContent = true,
                modifier = Modifier
                    .testTag(testTag)
                    .rememberFocus(focusKey),
                titleTrailing = titleTrailing,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SearchBarEntryPoint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusLarge)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpColor.SurfaceVariant)
            .border(1.dp, SpColor.Divider, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(
                shape = shape,
                interactionSource = interactionSource,
                addFocusable = false,
            )
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
