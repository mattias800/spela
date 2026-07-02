package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidescreenModeTest {
    @Test
    fun `wii gamecube and ps2 default to original 4 to 3 presentation`() {
        assertEquals(WidescreenMode.FOUR_THREE, defaultWidescreenMode("wii"))
        assertEquals(WidescreenMode.FOUR_THREE, defaultWidescreenMode("gc"))
        assertEquals(WidescreenMode.FOUR_THREE, defaultWidescreenMode("gamecube"))
        assertEquals(WidescreenMode.FOUR_THREE, defaultWidescreenMode("ps2"))
    }

    @Test
    fun `unsupported consoles keep native presentation`() {
        assertEquals(WidescreenMode.NATIVE, defaultWidescreenMode("nes"))
        assertFalse(supportsWidescreenMode("nes"))
    }

    @Test
    fun `supported consoles expose widescreen controls`() {
        assertTrue(supportsWidescreenMode("wii"))
        assertTrue(supportsWidescreenMode("gcn"))
        assertTrue(supportsWidescreenMode("ps2"))
    }
}
