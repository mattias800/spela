package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.presentation.ui.feature.library.LibraryConsolesTab
import com.spela.player.presentation.viewmodel.GameListViewModel

@Composable
fun ConsolesScreen(
    viewModel: GameListViewModel,
    preferencesRepository: PreferencesRepository,
    onConsoleSelected: (String) -> Unit,
    onBrowseConnectedServers: () -> Unit,
) {
    LibraryConsolesTab(
        viewModel = viewModel,
        preferencesRepository = preferencesRepository,
        onConsoleSelected = onConsoleSelected,
        onBrowseConnectedServers = onBrowseConnectedServers,
    )
}
