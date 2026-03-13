package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.feature.explore.ExploreGroupDetailContent
import com.spela.player.presentation.viewmodel.ExploreViewModel

@Composable
fun ExploreFranchiseScreen(
    franchiseId: String,
    franchiseName: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.franchiseDetailState.collectAsState()

    LaunchedEffect(franchiseId) {
        viewModel.loadFranchiseDetail(franchiseId, franchiseName)
    }

    ExploreGroupDetailContent(
        detail = state.detail,
        filteredGames = state.filteredGames,
        consoleFilter = state.consoleFilter,
        isLoading = state.isLoading,
        error = state.error,
        title = franchiseName,
        groupLabel = "franchise",
        onGameSelected = onGameSelected,
        onBack = onBack,
        onConsoleFilterSelected = { abbreviation ->
            viewModel.setFranchiseConsoleFilter(
                if (abbreviation != null && state.consoleFilter == abbreviation) null else abbreviation,
            )
        },
        onDismissError = { viewModel.dismissFranchiseDetailError() },
    )
}
