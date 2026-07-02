package com.spela.player.data.remote.dto

import com.spela.client.models.UserPreferencesResponse
import com.spela.player.domain.model.RenderScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserPreferencesMapperTest {
    @Test
    fun mapsConsoleRenderScalesAndDropsUnknownValues() {
        val dto = UserPreferencesResponse(
            autoLoadSaveEnabled = true,
            autoSaveEnabled = true,
            autoUpdateCoresEnabled = true,
            consoleKeyMappings = emptyMap(),
            consoleRenderScales = mapOf(
                "GC" to "3x",
                "PSP" to "4X",
                "snes" to "garbage",
                "psx" to "native",
            ),
            consoleSaveStatePolicies = emptyMap(),
            consoleShaders = emptyMap(),
            customKeyMapping = emptyMap(),
            defaultSecondScreenPage = "art",
            gameSaveStatePolicies = emptyMap(),
            preferredRegions = emptyList(),
            raHardcoreEnabled = false,
            raLinked = false,
            raUsername = "",
            selectedKeyMapping = "arrows-left",
            selectedShader = "none",
            selectedTheme = "default-dark",
            showPerformanceOverlay = false,
        )

        val preferences = dto.toDomain()

        assertEquals(RenderScale.THREE_X, preferences.consoleRenderScales["gc"])
        assertEquals(RenderScale.FOUR_X, preferences.consoleRenderScales["psp"])
        assertFalse(preferences.consoleRenderScales.containsKey("snes"))
        assertFalse(preferences.consoleRenderScales.containsKey("psx"))
    }

    @Test
    fun missingConsoleRenderScalesMapsToEmpty() {
        val dto = UserPreferencesResponse(
            autoLoadSaveEnabled = true,
            autoSaveEnabled = true,
            autoUpdateCoresEnabled = true,
            consoleKeyMappings = emptyMap(),
            consoleSaveStatePolicies = emptyMap(),
            consoleShaders = emptyMap(),
            customKeyMapping = emptyMap(),
            defaultSecondScreenPage = "art",
            gameSaveStatePolicies = emptyMap(),
            preferredRegions = emptyList(),
            raHardcoreEnabled = false,
            raLinked = false,
            raUsername = "",
            selectedKeyMapping = "arrows-left",
            selectedShader = "none",
            selectedTheme = "default-dark",
            showPerformanceOverlay = false,
        )

        assertEquals(emptyMap(), dto.toDomain().consoleRenderScales)
    }
}
