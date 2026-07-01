package com.spela.player.desktop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalRightStickScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Right-stick continuous scrolling (#1362) must work in every game grid, not
 * just SpScreen's column-scroll. SpLazyVerticalGrid is the shared grid used by
 * the console games list, All Games, Favorites, etc.; wiring RightStickScroll
 * into it makes the behavior actually global (the reported gap was the console
 * games list not scrolling from the stick).
 */
@OptIn(ExperimentalTestApi::class)
class SpLazyVerticalGridScrollTest {

    @Test
    fun rightStickDeflectionScrollsTheGrid() = runComposeUiTest {
        val stick = MutableStateFlow(0f)
        lateinit var gridState: LazyGridState

        setContent {
            gridState = rememberLazyGridState()
            CompositionLocalProvider(
                LocalInputMode provides InputMode.GAMEPAD,
                LocalRightStickScroll provides stick,
            ) {
                SpLazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.size(240.dp),
                ) {
                    items(200) {
                        Box(Modifier.size(80.dp))
                    }
                }
            }
        }

        waitForIdle()
        assertEquals(0, gridState.firstVisibleItemIndex, "grid starts at the top")

        // Deflect the stick fully downward — the grid should scroll toward the end.
        stick.value = 1f
        waitUntil(timeoutMillis = 3_000) { gridState.firstVisibleItemIndex > 0 }
        stick.value = 0f

        assertTrue(
            gridState.firstVisibleItemIndex > 0,
            "right-stick deflection should have scrolled the grid down",
        )
    }
}
