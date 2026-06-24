package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1082 / #1441 regression test: column-count breakpoints for the consoles
 * grid. The grid runs denser (and the cards smaller) than the original #1082
 * tuning so the 4:5 portrait cards stay compact. Pure unit test — no Compose
 * UI runtime required.
 */
class ConsoleColumnsForWidthTest {

    @Test
    fun phonePortrait_oneColumn() {
        assertEquals(1, consoleColumnsForWidth(280.dp))
        assertEquals(1, consoleColumnsForWidth(319.dp))
    }

    @Test
    fun phone_twoColumns() {
        assertEquals(2, consoleColumnsForWidth(320.dp))
        assertEquals(2, consoleColumnsForWidth(619.dp))
    }

    @Test
    fun smallTablet_threeColumns() {
        assertEquals(3, consoleColumnsForWidth(620.dp))
        assertEquals(3, consoleColumnsForWidth(879.dp))
    }

    @Test
    fun tablet_fourColumns() {
        assertEquals(4, consoleColumnsForWidth(880.dp))
        assertEquals(4, consoleColumnsForWidth(1099.dp))
    }

    @Test
    fun laptopWindowed_fiveColumns() {
        assertEquals(5, consoleColumnsForWidth(1100.dp))
        assertEquals(5, consoleColumnsForWidth(1319.dp))
    }

    @Test
    fun desktop_sixColumns() {
        assertEquals(6, consoleColumnsForWidth(1320.dp))
        assertEquals(6, consoleColumnsForWidth(1539.dp))
    }

    @Test
    fun aynThorLandscapeAndUltrawide_sevenColumns() {
        // AYN Thor in landscape (~1920 dp) and wider lands here. Beyond
        // 1540 dp we cap at 7 columns; the per-card widthIn(max) handles
        // any leftover width by clamping each card.
        assertEquals(7, consoleColumnsForWidth(1540.dp))
        assertEquals(7, consoleColumnsForWidth(1920.dp))
        assertEquals(7, consoleColumnsForWidth(3440.dp))
        assertEquals(7, consoleColumnsForWidth(3840.dp))
    }
}
