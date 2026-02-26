package com.spela.player.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFileStorageTest {

    @Test
    fun xdgDataHomeIsRespected() {
        val result = resolveLinuxDataDir("/home/user", "/custom/data")
        assertEquals(File("/custom/data", "spela"), result)
    }

    @Test
    fun xdgDataHomeFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", null)
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }

    @Test
    fun xdgDataHomeBlankFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", "")
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }

    @Test
    fun xdgDataHomeWhitespaceFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", "   ")
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }
}
