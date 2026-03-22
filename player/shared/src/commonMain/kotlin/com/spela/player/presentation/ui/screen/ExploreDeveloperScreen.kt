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
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.DeveloperDetailSkeleton
import com.spela.player.presentation.ui.feature.explore.DeveloperHeroBanner
import com.spela.player.presentation.ui.feature.explore.DeveloperTopRatedRow
import com.spela.player.presentation.ui.feature.explore.DeveloperUserStatsCard
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.ExploreViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreDeveloperScreen(
    name: String,
    isDeveloper: Boolean = true,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit = {},
    onDeveloperSelected: (String) -> Unit = {},
    onNavigateToGames: ((name: String, isDeveloper: Boolean) -> Unit)? = null,
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

    Box(modifier = Modifier.fillMaxSize().spScreenBackground().testTag("developer_detail_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            SpTopBar(
                title = name,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.detail == null -> {
                    DeveloperDetailSkeleton()
                }

                state.detail != null -> {
                    val detail = state.detail!!

                    SpSectionList(
                        modifier = Modifier.fillMaxSize().testTag("developer_detail_content"),
                    ) {
                        // 1. Hero Banner — full-width edge-to-edge, counteracts SpSectionList padding
                        item {
                            val horizontalPadding = SpSpacing.ScreenHorizontal
                            DeveloperHeroBanner(
                                detail = detail,
                                modifier = Modifier
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
                                    .testTag("developer_hero_banner"),
                            )
                        }

                        // 2. Related chips (publishers + related developers)
                        val hasPublishers = detail.publishers.isNotEmpty()
                        val hasRelated = detail.relatedDevelopers.isNotEmpty()
                        if (hasPublishers || hasRelated) {
                            item {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("developer_related_chips"),
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

                        // 3. Top Rated
                        if (detail.topGames.size >= 3) {
                            item {
                                SpTitledSection(
                                    title = "Top Rated",
                                    edgeToEdgeContent = true,
                                    titleTrailing = if (detail.games.isNotEmpty() && onNavigateToGames != null) {
                                        {
                                            SpButton(
                                                text = "See all games",
                                                style = SpButtonStyle.Ghost,
                                                onClick = { onNavigateToGames(name, isDeveloper) },
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
                                    )
                                }
                            }
                        }

                        // 4. Your Stats
                        if (detail.userStats != null) {
                            item {
                                SpTitledSection(title = "Your Stats") {
                                    DeveloperUserStatsCard(
                                        userStats = detail.userStats,
                                        totalGames = detail.gameCount,
                                        onGameSelected = onGameSelected,
                                        modifier = Modifier.testTag("developer_user_stats"),
                                    )
                                }
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
