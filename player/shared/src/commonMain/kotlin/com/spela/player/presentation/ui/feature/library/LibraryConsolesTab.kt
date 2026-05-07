package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryConsolesTab(
    viewModel: GameListViewModel,
    onConsoleSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // #1106 — toggle state lives at the tab level so the segmented
    // control and the grid render from the same source of truth.
    // `remember` keeps the choice for the lifetime of the tab; cross-
    // restart persistence (and rotation persistence on Android) is
    // filed as a follow-up.
    var grouping by remember { mutableStateOf(ConsoleGrouping.Generation) }

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadConsoles)
    }

    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.75f),
        SpColor.AccentDark.darken(0.80f),
    )

    SpScreen(gradientColors = gradientColors) {
        if (state.isLoading && state.consoles.isEmpty()) {
            SpScrollableContent {
                SpScreenTopSpacer()
                SpMainContentPadding {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columnsPerRow = if (maxWidth >= 600.dp) 2 else 1
                        ConsolesSkeletonGrid(
                            columnsPerRow = columnsPerRow,
                            count = if (columnsPerRow == 2) 4 else 3,
                        )
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.onIntent(GameListIntent.LoadConsoles) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.consoles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyStates.EmptyLibrary()
                    }
                } else {
                    SpScrollableContent(modifier = Modifier.testTag("consoles-list")) {
                        SpScreenTopSpacer()
                        SpMainContentPadding {
                            ConsoleGroupingToggle(
                                grouping = grouping,
                                onGroupingChange = { grouping = it },
                            )
                            Spacer(Modifier.height(SpSpacing.Medium))
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                ConsolesGrid(
                                    consoles = state.consoles,
                                    onConsoleSelected = onConsoleSelected,
                                    consolesWithMissingBios = state.consolesWithMissingBios,
                                    columnsPerRow = consoleColumnsForWidth(maxWidth),
                                    grouping = grouping,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleGroupingToggle(
    grouping: ConsoleGrouping,
    onGroupingChange: (ConsoleGrouping) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("console-grouping-toggle"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        SpChip(
            text = "By generation",
            isSelected = grouping == ConsoleGrouping.Generation,
            onClick = { onGroupingChange(ConsoleGrouping.Generation) },
            onGradient = true,
            modifier = Modifier.testTag("console-grouping-generation"),
        )
        SpChip(
            text = "By manufacturer",
            isSelected = grouping == ConsoleGrouping.Manufacturer,
            onClick = { onGroupingChange(ConsoleGrouping.Manufacturer) },
            onGradient = true,
            modifier = Modifier.testTag("console-grouping-manufacturer"),
        )
    }
}
