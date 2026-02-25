package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import com.spela.player.presentation.ui.feature.library.LibraryConsolesTab
import com.spela.player.presentation.viewmodel.GameListViewModel

@Composable
fun ConsolesScreen(
    viewModel: GameListViewModel,
    onConsoleSelected: (String) -> Unit,
) {
    LibraryConsolesTab(
        viewModel = viewModel,
        onConsoleSelected = onConsoleSelected,
    )
}
