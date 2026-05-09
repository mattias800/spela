package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.layout
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
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.DeveloperCompanyDescription
import com.spela.player.presentation.ui.feature.explore.DeveloperDetailSkeleton
import com.spela.player.presentation.ui.feature.explore.DeveloperHeroBanner
import com.spela.player.presentation.ui.feature.explore.DeveloperTopRatedRow
import com.spela.player.presentation.ui.feature.explore.DeveloperUserStatsCard
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
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
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.detail == null -> {
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

                state.detail != null -> {
                    val detail = state.detail!!
                    val horizontalPadding = SpSpacing.ScreenHorizontal

                    Box(modifier = Modifier.fillMaxSize()) {
                        SpSectionList(
                            modifier = Modifier.fillMaxSize().testTag("publisher_detail_content"),
                        ) {
                            // 0. Hero Banner — full-width edge-to-edge
                            DeveloperHeroBanner(
                                detail = detail,
                                modifier = Modifier
                                    .autoFocus()
                                    .layout { measurable, constraints ->
                                        val extraWidth = (horizontalPadding * 2).roundToPx()
                                        val newConstraints = constraints.copy(
                                            maxWidth = constraints.maxWidth + extraWidth,
                                            minWidth = if (constraints.minWidth > 0) constraints.minWidth + extraWidth else 0,
                                        )
                                        val placeable = measurable.measure(newConstraints)
                                        layout(placeable.width, placeable.height) {
                                            placeable.place(-horizontalPadding.roundToPx(), 0)
                                        }
                                    }
                                    .testTag("publisher_hero_banner"),
                            )
                            // 1. About (company description)
                            val companyInfo = detail.companyInfo
                            if (companyInfo?.description != null) {
                                SpTitledSection(title = "About") {
                                    DeveloperCompanyDescription(
                                        companyInfo = companyInfo,
                                        modifier = Modifier.testTag("publisher_company_description_section"),
                                    )
                                }
                            }

                            // 2. Related chips — developers who worked with this publisher,
                            // and related publishers with similar portfolios.
                            val hasDevelopers = detail.developers.isNotEmpty()
                            val hasRelated = detail.relatedPublishers.isNotEmpty()
                            if (hasDevelopers || hasRelated) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("publisher_related_chips"),
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

                            // 3. Top Rated
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
                                    )
                                }
                            }

                            // 4. Games grid
                            if (detail.games.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Games",
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
                                                Box(
                                                    modifier = Modifier.focusRestoreItem(
                                                        key = "publisher_${name}_game_${game.id}",
                                                        isDefault = index == 0,
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

                            // 5. Your Stats
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
                        }
                        // Top bar overlaid on everything — floats over the banner
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
                    } // Box
                }

                else -> {
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
