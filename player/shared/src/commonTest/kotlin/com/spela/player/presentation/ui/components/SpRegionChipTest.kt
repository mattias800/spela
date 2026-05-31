package com.spela.player.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #1253: country flag emoji render as bare indicator letters ("US") on
 * Windows/Linux desktop. regionChipText drops the flag there but keeps it
 * where it renders (macOS, Android) and keeps globe emoji everywhere.
 */
class SpRegionChipTest {

    @Test
    fun countryFlagShownOnMacAndAndroid() {
        assertEquals("🇺🇸 USA", regionChipText("USA", "macos"))
        assertEquals("🇺🇸 USA", regionChipText("USA", "android"))
        assertEquals("🇯🇵 Japan", regionChipText("Japan", "macos"))
    }

    @Test
    fun countryFlagDroppedOnWindowsAndLinux() {
        assertEquals("USA", regionChipText("USA", "windows"))
        assertEquals("USA", regionChipText("USA", "linux"))
        assertEquals("Japan", regionChipText("Japan", "windows"))
    }

    @Test
    fun globeEmojiKeptEverywhere() {
        // World/Asia use single-codepoint globe emoji that render in the
        // default Windows/Linux fonts — they must not be dropped.
        assertEquals("🌍 World", regionChipText("World", "windows"))
        assertEquals("🌏 Asia", regionChipText("Asia", "linux"))
        assertEquals("🌍 World", regionChipText("World", "macos"))
    }

    @Test
    fun unknownRegionHasNoFlag() {
        assertEquals("Atlantis", regionChipText("Atlantis", "windows"))
        assertEquals("Atlantis", regionChipText("Atlantis", "macos"))
    }

    @Test
    fun regionMatchingIsCaseInsensitiveSubstring() {
        // "USA, Europe" contains "USA" → matched.
        assertEquals("🇺🇸 USA, Europe", regionChipText("USA, Europe", "macos"))
    }

    @Test
    fun flagRendersOnClassifiesRegionalIndicatorVsGlobe() {
        assertFalse(flagRendersOn("🇺🇸", "windows"))
        assertTrue(flagRendersOn("🇺🇸", "macos"))
        assertTrue(flagRendersOn("🌍", "windows")) // globe renders everywhere
        assertTrue(flagRendersOn("🌏", "linux"))
    }
}
