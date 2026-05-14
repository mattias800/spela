package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpGameGrid
import com.spela.player.presentation.ui.components.SpGridGameCard
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.DeveloperCompanyDescription
import com.spela.player.presentation.ui.feature.explore.DeveloperDetailSkeleton
import com.spela.player.presentation.ui.feature.explore.DeveloperHeroBanner
import com.spela.player.presentation.ui.feature.explore.DeveloperTopRatedRow
import com.spela.player.presentation.ui.feature.explore.DeveloperUserStatsCard
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ExploreViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExplorePublisherScreen(
    name: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit = {},
    onPublisherSelected: (String) -> Unit = {},
    onNavigateToGames: ((name: String, isDeveloper: Boolean) -> Unit)? = null,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.publisherDetailState.collectAsState()

    LaunchedEffect(name) {
        viewModel.loadPublisherDetail(name)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(modifier = Modifier.testTag("publisher_detail_screen")) {
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        when {
            state.isLoading && state.detail == null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isGamepad) {
                        SpScreenTopSpacer()
                    } else {
                        SpTopBar(
                            title = name,
                            showBack = true,
                            onBack = onBack,
                        )
                    }
                    DeveloperDetailSkeleton()
                }
            }

            state.detail != null -> {
                val detail = state.detail!!

                // Hero-banner page pattern from player/LAYOUT.md.
                // See matching comment in ExploreDeveloperScreen.
                SpScrollableContent {
                    // Hero banner is decorative — no focusable descendants
                    // — see matching comment in ExploreDeveloperScreen.
                    // Default-focus claim moved to Top Rated.
                    DeveloperHeroBanner(
                        detail = detail,
                        modifier = Modifier
                            .focusRestoreItem(key = "explore_publisher_hero")
                            .testTag("publisher_hero_banner"),
                    )
                    SpMainContentPadding {
                        SpSectionList(
                            modifier = Modifier.testTag("publisher_detail_content"),
                        ) {
                            // 1. About — entity description (when IGDB has
                            // one). Heading names the entity type so the
                            // page is self-identifying. See matching
                            // section in ExploreDeveloperScreen.
                            val companyInfo = detail.companyInfo
                            if (companyInfo?.description != null) {
                                SpTitledSection(title = "About this publisher") {
                                    DeveloperCompanyDescription(
                                        companyInfo = companyInfo,
                                        modifier = Modifier.testTag("publisher_company_description_section"),
                                    )
                                }
                            }

                            // 2. Top Rated
                            if (detail.topGames.size >= 3) {
                                SpTitledSection(
                                    title = "Top Rated",
                                    edgeToEdgeContent = true,
                                    titleTrailing = if (detail.games.isNotEmpty() && onNavigateToGames != null) {
                                        {
                                            SpButton(
                                                text = "See all games",
                                                style = SpButtonStyle.Ghost,
                                                onClick = { onNavigateToGames(name, false) },
                                                modifier = Modifier.testTag("publisher_see_all_games"),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                ) {
                                    DeveloperTopRatedRow(
                                        topGames = detail.topGames,
                                        onGameSelected = onGameSelected,
                                        modifier = Modifier.testTag("publisher_top_rated_section"),
                                        isDefaultFocusGroup = true,
                                    )
                                }
                            }

                            // 3. Your Stats
                            val userStats = detail.userStats
                            if (userStats != null) {
                                SpTitledSection(title = "Your Stats") {
                                    DeveloperUserStatsCard(
                                        userStats = userStats,
                                        totalGames = detail.gameCount,
                                        onGameSelected = onGameSelected,
                                        modifier = Modifier.testTag("publisher_user_stats"),
                                    )
                                }
                            }

                            // 4. All games — the full library entry-point.
                            if (detail.games.isNotEmpty()) {
                                SpTitledSection(
                                    title = "All games",
                                    titleTrailing = if (detail.games.size > 12 && onNavigateToGames != null) {
                                        {
                                            SpButton(
                                                text = "See all",
                                                style = SpButtonStyle.Ghost,
                                                onClick = { onNavigateToGames(name, false) },
                                            )
                                        }
                                    } else null,
                                ) {
                                    SpGameGrid(
                                        items = detail.games.take(12).mapIndexed { index, game ->
                                            @Composable {
                                                // Default-focus claim
                                                // lives on Top Rated.
                                                Box(
                                                    modifier = Modifier.focusRestoreItem(
                                                        key = "publisher_${name}_game_${game.id}",
                                                    ),
                                                ) {
                                                    SpGridGameCard(
                                                        title = game.title,
                                                        subtitle = game.consoleName,
                                                        coverUrl = game.coverUrl,
                                                        onClick = { onGameSelected(game.id) },
                                                        rating = game.communityRating,
                                                        isFavorite = game.isFavorite,
                                                        isInPlayLater = game.isInPlayLater,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            // 5. Connected companies — moved to the bottom
                            // (visitors are mostly here for games) and
                            // split into two separately-labelled
                            // subsections so each chip is obviously a
                            // developer that this publisher released
                            // games for OR a publisher with a similar
                            // portfolio.
                            if (detail.developers.isNotEmpty()) {
                                SpTitledSection(title = "Developers") {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("publisher_developer_chips"),
                                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                    ) {
                                        detail.developers.forEach { developer ->
                                            SpChip(
                                                text = "${developer.name} (${developer.count})",
                                                onClick = { onDeveloperSelected(developer.name) },
                                                modifier = Modifier
                                                    .testTag("publisher_developer_chip_${developer.name}")
                                                    .semantics {
                                                        contentDescription = "${developer.name}, ${developer.count} games"
                                                        role = Role.Button
                                                    },
                                            )
                                        }
                                    }
                                }
                            }

                            if (detail.relatedPublishers.isNotEmpty()) {
                                SpTitledSection(title = "Related publishers") {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("publisher_related_chips"),
                                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                    ) {
                                        detail.relatedPublishers.forEach { related ->
                                            SpChip(
                                                text = related.name,
                                                onClick = { onPublisherSelected(related.name) },
                                                modifier = Modifier
                                                    .testTag("publisher_related_chip_${related.name}")
                                                    .semantics {
                                                        contentDescription = "${related.name}, ${related.gameCount} games"
                                                        role = Role.Button
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        } // SpSectionList
                    } // SpMainContentPadding
                } // SpScrollableContent

                // Top bar overlaid on everything — floats over the banner.
                if (isGamepad) {
                    SpScreenTopSpacer()
                } else {
                    SpTopBar(
                        title = "",
                        showBack = true,
                        onBack = onBack,
                        onGradient = true,
                    )
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isGamepad) {
                        SpScreenTopSpacer()
                    } else {
                        SpTopBar(
                            title = name,
                            showBack = true,
                            onBack = onBack,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Code,
                            title = "Publisher not found",
                            message = "Could not load details.",
                            modifier = Modifier.testTag("publisher_error_state"),
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
                    onAction = { viewModel.dismissPublisherDetailError() },
                )
            },
            onDismiss = { viewModel.dismissPublisherDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // CompositionLocalProvider
    }
}
