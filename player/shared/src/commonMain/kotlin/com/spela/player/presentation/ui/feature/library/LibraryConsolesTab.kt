package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SegmentedOption
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.components.SpSegmentedControl
import com.spela.player.presentation.ui.components.rememberLoadingFlashDebounce
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryConsolesTab(
    viewModel: GameListViewModel,
    preferencesRepository: PreferencesRepository,
    onConsoleSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Persist the grouping choice device-locally — mirrors the web's
    // `localStorage["consoleListGrouping"]` so a user landing on either
    // client sees the same default and their pick survives screen
    // disposal (#1176). The repository's getter falls back to
    // "generation" when unset, which matches the web default.
    var grouping by remember {
        val initial = when (preferencesRepository.getConsoleListGrouping()) {
            "manufacturer" -> ConsoleGrouping.Manufacturer
            else -> ConsoleGrouping.Generation
        }
        mutableStateOf(initial)
    }
    val onGroupingChange: (ConsoleGrouping) -> Unit = { next ->
        grouping = next
        preferencesRepository.setConsoleListGrouping(
            when (next) {
                ConsoleGrouping.Generation -> "generation"
                ConsoleGrouping.Manufacturer -> "manufacturer"
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadConsoles)
    }

    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.75f),
        SpColor.AccentDark.darken(0.80f),
    )

    // Only show consoles the server has games for (#1383) — empty consoles aren't
    // browsable. (Loading uses the raw list so the skeleton still shows on first load.)
    val visibleConsoles = state.consoles.filter { it.gameCount > 0 }
    SpScreen(gradientColors = gradientColors) {
        if (state.isLoading && state.consoles.isEmpty()) {
            SpScrollableContent {
                SpScreenTopSpacer()
                SpMainContentPadding {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // Match the real grid's density (#1446) so the skeleton
                        // doesn't jump column count when consoles finish loading.
                        val columnsPerRow = consoleColumnsForWidth(maxWidth)
                        ConsolesSkeletonGrid(
                            columnsPerRow = columnsPerRow,
                            count = columnsPerRow * 2,
                        )
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = rememberLoadingFlashDebounce(state.isLoading),
                onRefresh = { viewModel.onIntent(GameListIntent.LoadConsoles) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (visibleConsoles.isEmpty()) {
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
                                onGroupingChange = onGroupingChange,
                            )
                            Spacer(Modifier.height(SpSpacing.Medium))
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                ConsolesGrid(
                                    consoles = visibleConsoles,
                                    onConsoleSelected = onConsoleSelected,
                                    consolesWithMissingBios = state.consolesWithMissingBios,
                                    columnsPerRow = consoleColumnsForWidth(maxWidth),
                                    grouping = grouping,
                                )
                            }
                            // Bottom breathing room — without this the last
                            // console card sits flush against the screen edge.
                            Spacer(Modifier.height(SpSpacing.XLarge))
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
    // Migrated from two SpChips to SpSegmentedControl in #1176 so the
    // primary "switch how the entire screen is organised" affordance
    // doesn't look like passive metadata. testTag values are preserved
    // for the existing UI tests.
    SpSegmentedControl(
        options = listOf(
            SegmentedOption(
                value = ConsoleGrouping.Generation,
                label = "Generation",
                testTag = "console-grouping-generation",
            ),
            SegmentedOption(
                value = ConsoleGrouping.Manufacturer,
                label = "Manufacturer",
                testTag = "console-grouping-manufacturer",
            ),
        ),
        selectedValue = grouping,
        onValueChange = onGroupingChange,
        label = "Group by",
        onGradient = true,
        modifier = Modifier.testTag("console-grouping-toggle"),
    )
}
