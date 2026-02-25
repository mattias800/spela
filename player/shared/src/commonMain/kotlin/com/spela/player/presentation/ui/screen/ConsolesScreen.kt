package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.feature.library.LibraryConsolesTab
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.viewmodel.GameListViewModel

@Composable
fun ConsolesScreen(
    viewModel: GameListViewModel,
    onConsoleSelected: (String) -> Unit,
) {
    val titleBarInset = LocalTitleBarInset.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(titleBarInset))

        LibraryConsolesTab(
            viewModel = viewModel,
            onConsoleSelected = onConsoleSelected,
        )
    }
}
