package com.spela.player.presentation.ui.feature.library

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1082 regression test: column-count breakpoints for the consoles grid.
 * Pure unit test — no Compose UI runtime required.
 */
class ConsoleColumnsForWidthTest {

    @Test
    fun phonePortrait_oneColumn() {
        assertEquals(1, consoleColumnsForWidth(320.dp))
        assertEquals(1, consoleColumnsForWidth(399.dp))
    }

    @Test
    fun phoneLandscape_twoColumns() {
        assertEquals(2, consoleColumnsForWidth(400.dp))
        assertEquals(2, consoleColumnsForWidth(699.dp))
    }

    @Test
    fun smallTablet_threeColumns() {
        assertEquals(3, consoleColumnsForWidth(700.dp))
        assertEquals(3, consoleColumnsForWidth(1099.dp))
    }

    @Test
    fun laptopWindowed_fourColumns() {
        // The breakpoint that fixes the issue's headline symptom:
        // before #1082 a 1280 dp window stayed at 3 columns and
        // each card ballooned to ~415 dp wide.
        assertEquals(4, consoleColumnsForWidth(1100.dp))
        assertEquals(4, consoleColumnsForWidth(1280.dp))
        assertEquals(4, consoleColumnsForWidth(1499.dp))
    }

    @Test
    fun aynThorLandscapeAndUltrawide_fiveColumns() {
        // AYN Thor in landscape (~1920 dp) lands here. Beyond 1500 dp
        // we cap at 5 columns; the per-card widthIn(max = 340 dp)
        // handles the leftover width by clamping each card.
        assertEquals(5, consoleColumnsForWidth(1500.dp))
        assertEquals(5, consoleColumnsForWidth(1920.dp))
        assertEquals(5, consoleColumnsForWidth(3440.dp))
        assertEquals(5, consoleColumnsForWidth(3840.dp))
    }
}
