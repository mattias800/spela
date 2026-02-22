package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
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

    if (state.isLoading && state.consoles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            SpLoadingIndicator(message = "Loading consoles...")
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = SpSpacing.ScreenHorizontal,
                        vertical = SpSpacing.Default,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    item {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columnsPerRow = if (maxWidth > 600.dp) 3 else 2
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
