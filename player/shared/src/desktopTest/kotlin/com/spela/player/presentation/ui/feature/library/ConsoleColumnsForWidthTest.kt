package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1082 / #1441 / #1446 regression test: column-count breakpoints for the
 * consoles grid. #1446 bumped every breakpoint by +2 so the cards render
 * smaller and denser. Pure unit test — no Compose UI runtime required.
 */
class ConsoleColumnsForWidthTest {

    @Test
    fun belowSmallest_threeColumns() {
        assertEquals(3, consoleColumnsForWidth(0.dp))
        assertEquals(3, consoleColumnsForWidth(319.dp))
    }

    @Test
    fun phone_fourColumns() {
        assertEquals(4, consoleColumnsForWidth(320.dp))
        assertEquals(4, consoleColumnsForWidth(619.dp))
    }

    @Test
    fun aynThorLandscape_fiveColumns() {
        // The AYN Thor in landscape is 1920×1080 px but at ~2.3× density that
        // is only ~830 dp wide; the consoles-grid container measures 792.6 dp
        // on-device (after content padding), so it lands in this arm — 5 cards
        // per row (was 3 before #1446). The earlier test's "~1920 dp → 7
        // columns" assumption was wrong (it conflated px with dp).
        assertEquals(5, consoleColumnsForWidth(620.dp))
        assertEquals(5, consoleColumnsForWidth(792.6.dp)) // measured Thor width
        assertEquals(5, consoleColumnsForWidth(879.dp))
    }

    @Test
    fun tablet_sixColumns() {
        assertEquals(6, consoleColumnsForWidth(880.dp))
        assertEquals(6, consoleColumnsForWidth(1099.dp))
    }

    @Test
    fun laptopWindowed_sevenColumns() {
        assertEquals(7, consoleColumnsForWidth(1100.dp))
        assertEquals(7, consoleColumnsForWidth(1319.dp))
    }

    @Test
    fun desktop_eightColumns() {
        assertEquals(8, consoleColumnsForWidth(1320.dp))
        assertEquals(8, consoleColumnsForWidth(1539.dp))
    }

    @Test
    fun ultrawide_nineColumns() {
        // Beyond 1540 dp we cap at 9 columns; the per-card widthIn(max) clamps
        // each card so leftover width doesn't stretch them oversized.
        assertEquals(9, consoleColumnsForWidth(1540.dp))
        assertEquals(9, consoleColumnsForWidth(1920.dp))
        assertEquals(9, consoleColumnsForWidth(3440.dp))
        assertEquals(9, consoleColumnsForWidth(3840.dp))
    }
}
