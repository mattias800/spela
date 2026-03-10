package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.ConsoleEssentials
import com.spela.player.presentation.ui.feature.explore.ConsoleGenreBreakdown
import com.spela.player.presentation.ui.feature.explore.ConsoleHiddenGems
import com.spela.player.presentation.ui.feature.explore.ConsoleRecentlyPlayed
import com.spela.player.presentation.ui.feature.explore.ConsoleTopDevelopers
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.parseHexColor

@Composable
fun ExploreConsoleShowcaseScreen(
    consoleId: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.consoleShowcaseState.collectAsState()

    LaunchedEffect(consoleId) {
        viewModel.loadConsoleShowcase(consoleId)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("console_showcase_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.showcase?.console?.name ?: consoleId,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.showcase == null -> {
                    // Loading skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("console_showcase_loading"),
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

                state.showcase != null -> {
                    val showcase = state.showcase!!
                    val accentColor = parseHexColor(showcase.console.colorTheme, SpColor.Primary)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("console_showcase_list"),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // Hero banner with console color accent
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                accentColor.copy(alpha = 0.3f),
                                                SpColor.Background,
                                            ),
                                        ),
                                    )
                                    .testTag("console_showcase_banner"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = showcase.console.name,
                                        style = SpTypography.DisplaySmall,
                                        color = SpColor.OnBackground,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                    Text(
                                        text = "${showcase.console.gameCount} games in library",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.OnBackgroundSecondary,
                                        modifier = Modifier.testTag("console_showcase_game_count"),
                                    )
                                }
                            }
                        }

                        item {
                            ConsoleEssentials(viewModel, onGameSelected)
                        }

                        item {
                            ConsoleHiddenGems(viewModel, onGameSelected)
                        }

                        item {
                            ConsoleGenreBreakdown(viewModel)
                        }

                        item {
                            ConsoleTopDevelopers(viewModel, onDeveloperSelected)
                        }

                        item {
                            ConsoleRecentlyPlayed(viewModel, onGameSelected)
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.SportsEsports,
                            title = "Console not found",
                            message = "Could not load console details.",
                            modifier = Modifier.testTag("console_showcase_error_state"),
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
                    onAction = { viewModel.dismissConsoleShowcaseError() },
                )
            },
            onDismiss = { viewModel.dismissConsoleShowcaseError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
