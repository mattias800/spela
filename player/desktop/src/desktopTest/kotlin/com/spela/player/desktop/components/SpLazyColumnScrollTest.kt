package com.spela.player.desktop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpLazyColumn
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalRightStickScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Right-stick continuous scrolling (#1362) must work on list screens, not just
 * grids. SpLazyColumn is the shared LazyColumn used by Stats, Downloads,
 * Netplay, Explore, etc.; wiring RightStickScroll into it makes the behavior
 * global for lists too (the reported gap was list screens not scrolling).
 */
@OptIn(ExperimentalTestApi::class)
class SpLazyColumnScrollTest {

    @Test
    fun rightStickDeflectionScrollsTheColumn() = runComposeUiTest {
        val stick = MutableStateFlow(0f)
        lateinit var listState: LazyListState

        setContent {
            listState = rememberLazyListState()
            CompositionLocalProvider(
                LocalInputMode provides InputMode.GAMEPAD,
                LocalRightStickScroll provides stick,
            ) {
                SpLazyColumn(
                    state = listState,
                    modifier = Modifier.size(240.dp),
                ) {
                    items(count = 200) {
                        Box(Modifier.size(80.dp))
                    }
                }
            }
        }

        waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "column starts at the top")

        stick.value = 1f
        waitUntil(timeoutMillis = 3_000) { listState.firstVisibleItemIndex > 0 }
        stick.value = 0f

        assertTrue(
            listState.firstVisibleItemIndex > 0,
            "right-stick deflection should have scrolled the column down",
        )
    }
}
