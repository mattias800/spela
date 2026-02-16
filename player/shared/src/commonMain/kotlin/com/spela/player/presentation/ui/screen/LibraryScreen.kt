package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.CollectionsViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel

private val libraryTabs = listOf("Consoles", "Games", "Favorites", "Play Later", "Collections")

@Composable
fun LibraryScreen(
    gameListViewModel: GameListViewModel,
    collectionsViewModel: CollectionsViewModel,
    onConsoleSelected: (String) -> Unit,
    onGameSelected: (String) -> Unit,
    onCollectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Library")

        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = SpColor.Surface,
            contentColor = SpColor.OnSurface,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = SpColor.Primary,
                    )
                }
            },
        ) {
            libraryTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = SpTypography.LabelMedium,
                            color = if (selectedTabIndex == index) SpColor.Primary else SpColor.OnBackgroundTertiary,
                        )
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> LibraryConsolesTab(
                    viewModel = gameListViewModel,
                    onConsoleSelected = onConsoleSelected,
                )
                1 -> AllGamesScreen(
                    viewModel = gameListViewModel,
                    onGameSelected = onGameSelected,
                )
                2 -> FavoritesScreen(
                    viewModel = gameListViewModel,
                    onGameSelected = onGameSelected,
                )
                3 -> PlayLaterScreen(
                    viewModel = gameListViewModel,
                    onGameSelected = onGameSelected,
                )
                4 -> CollectionsScreen(
                    viewModel = collectionsViewModel,
                    onCollectionSelected = onCollectionSelected,
                )
            }
        }
    }
}
