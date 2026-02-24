package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.feature.library.LibraryConsolesTab
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
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
    val titleBarInset = LocalTitleBarInset.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(titleBarInset))

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            edgePadding = SpSpacing.ScreenHorizontal,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTabIndex])
                            .height(3.dp)
                            .padding(horizontal = SpSpacing.Medium)
                            .background(SpColor.Primary, RoundedCornerShape(SpSpacing.RadiusPill)),
                    )
                }
            },
            divider = {},
        ) {
            libraryTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = SpTypography.LabelLarge,
                            color = if (selectedTabIndex == index) SpColor.OnBackground
                                    else SpColor.OnBackgroundTertiary,
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
