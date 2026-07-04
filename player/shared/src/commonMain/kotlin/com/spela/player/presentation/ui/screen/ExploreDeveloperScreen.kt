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
import com.spela.player.presentation.ui.components.gamePlatformPillContent
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
fun ExploreDeveloperScreen(
    name: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit = {},
    onDeveloperSelected: (String) -> Unit = {},
    onNavigateToGames: ((name: String, isDeveloper: Boolean) -> Unit)? = null,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.developerDetailState.collectAsState()

    LaunchedEffect(name) {
        viewModel.loadDeveloperDetail(name)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(modifier = Modifier.testTag("developer_detail_screen")) {
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

                // Hero-banner page pattern from player/LAYOUT.md:
                // SpScrollableContent → HeroBanner (edge-to-edge, no padding)
                // → SpMainContentPadding → SpSectionList → sections. Top bar
                // overlays as a sibling so it floats on the banner.
                SpScrollableContent {
                    // Hero banner is decorative — no focusable descendants
                    // — so isDefault=true silently failed here and the
                    // page's default-focus walked to the next candidate
                    // ("All games" first item), which scrolled the page
                    // to the bottom on first entry. Default-focus claim
                    // moved to the first card in Top Rated (see below).
                    DeveloperHeroBanner(
                        detail = detail,
                        modifier = Modifier
                            .focusRestoreItem(key = "explore_developer_hero")
                            .testTag("developer_hero_banner"),
                    )
                    SpMainContentPadding {
                        SpSectionList(
                            modifier = Modifier.testTag("developer_detail_content"),
                        ) {
                        // 1. About — entity description (when IGDB has one).
                        // Heading explicitly names the entity type so a
                        // visitor landing on this page knows whether
                        // they're reading about a developer or publisher
                        // (the hero banner alone doesn't always make that
                        // unambiguous).
                        val companyInfo = detail.companyInfo
                        if (companyInfo?.description != null) {
                            SpTitledSection(title = "About this developer") {
                                DeveloperCompanyDescription(
                                    companyInfo = companyInfo,
                                    modifier = Modifier.testTag("developer_company_description_section"),
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
                                            onClick = { onNavigateToGames(name, true) },
                                            modifier = Modifier.testTag("developer_see_all_games"),
                                        )
                                    }
                                } else {
                                    null
                                },
                            ) {
                                DeveloperTopRatedRow(
                                    topGames = detail.topGames,
                                    onGameSelected = onGameSelected,
                                    modifier = Modifier.testTag("developer_top_rated_section"),
                                    isDefaultFocusGroup = true,
                                )
                            }
                        }

                        // 3. Your Stats
                        if (detail.userStats != null) {
                            SpTitledSection(title = "Your Stats") {
                                DeveloperUserStatsCard(
                                    userStats = detail.userStats,
                                    totalGames = detail.gameCount,
                                    onGameSelected = onGameSelected,
                                    modifier = Modifier.testTag("developer_user_stats"),
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
                                            onClick = { onNavigateToGames(name, true) },
                                        )
                                    }
                                } else null,
                            ) {
                                SpGameGrid(
                                    items = detail.games.take(12).mapIndexed { index, game ->
                                        @Composable {
                                            // Default-focus claim lives on
                                            // Top Rated; falling back to
                                            // the first game here would
                                            // scroll the page on first
                                            // entry past Top Rated +
                                            // Your Stats.
                                            Box(
                                                modifier = Modifier.focusRestoreItem(
                                                    key = "developer_${name}_game_${game.id}",
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
                                                    platformContent = gamePlatformPillContent(game, onGameSelected),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        // 5. Connected companies — moved to the bottom
                        // because the user is mostly here for the games
                        // (sections 2–4), not the supply-chain trivia.
                        // Split into two separately-labelled subsections
                        // so each chip is obviously a publisher OR a
                        // related developer, not an undifferentiated mix.
                        if (detail.publishers.isNotEmpty()) {
                            SpTitledSection(title = "Published by") {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("developer_publisher_chips"),
                                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                ) {
                                    detail.publishers.forEach { publisher ->
                                        SpChip(
                                            text = "${publisher.name} (${publisher.count})",
                                            onClick = { onPublisherSelected(publisher.name) },
                                            modifier = Modifier
                                                .testTag("developer_publisher_chip_${publisher.name}")
                                                .semantics {
                                                    contentDescription = "${publisher.name}, ${publisher.count} games"
                                                    role = Role.Button
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        if (detail.relatedDevelopers.isNotEmpty()) {
                            SpTitledSection(title = "Related developers") {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("developer_related_chips"),
                                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                ) {
                                    detail.relatedDevelopers.forEach { related ->
                                        SpChip(
                                            text = related.name,
                                            onClick = { onDeveloperSelected(related.name) },
                                            modifier = Modifier
                                                .testTag("developer_related_chip_${related.name}")
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
                // Sibling of SpScrollableContent inside SpScreen's Box scope.
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
                            title = "Developer not found",
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
        } // CompositionLocalProvider
    }
}
