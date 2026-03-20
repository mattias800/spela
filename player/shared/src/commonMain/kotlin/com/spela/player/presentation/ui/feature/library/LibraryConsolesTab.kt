package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
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

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadConsoles)
    }

    val titleBarInset = LocalTitleBarInset.current
    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.75f),
        SpColor.AccentDark.darken(0.80f),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            },
    ) {
        if (state.isLoading && state.consoles.isEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnsPerRow = if (maxWidth >= 600.dp) 2 else 1
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = SpSpacing.ScreenHorizontal,
                        end = SpSpacing.ScreenHorizontal,
                        top = titleBarInset + SpSpacing.Default,
                        bottom = SpSpacing.Default,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    item {
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
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("consoles-list"),
                        contentPadding = PaddingValues(
                            start = SpSpacing.ScreenHorizontal,
                            end = SpSpacing.ScreenHorizontal,
                            top = titleBarInset + SpSpacing.Default,
                            bottom = SpSpacing.Default,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        item {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val columnsPerRow = if (maxWidth >= 600.dp) 2 else 1
                                ConsolesGrid(
                                    consoles = state.consoles,
                                    onConsoleSelected = onConsoleSelected,
                                    consolesWithMissingBios = state.consolesWithMissingBios,
                                    columnsPerRow = columnsPerRow,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
