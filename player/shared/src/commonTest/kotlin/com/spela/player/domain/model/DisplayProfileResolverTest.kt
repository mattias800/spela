package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayProfileResolverTest {
    @Test
    fun `auto chooses safe original aspect and dolphin 2x for gamecube`() {
        val profile = DisplayProfileResolver.resolve(
            gameId = "metroid-prime",
            gameTitle = "Metroid Prime",
            consoleId = "gc",
            corePath = "/cores/dolphin_libretro.so",
        )

        assertEquals(DisplayAspectChoice.AUTO, profile.aspectChoice)
        assertEquals(WidescreenMode.FOUR_THREE, profile.aspectMode)
        assertEquals("Auto (Original)", profile.aspectLabel)
        assertEquals(RenderScaleChoice.AUTO, profile.renderScaleChoice)
        assertEquals(RenderScale.TWO_X, profile.renderScale)
        assertEquals("Auto (2x)", profile.renderScaleLabel)
    }

    @Test
    fun `same console games can resolve different auto aspect profiles`() {
        val profiles = listOf(
            DisplayGameProfile(
                consoleIds = setOf("wii"),
                titleContains = listOf("zelda", "twilight", "princess"),
                aspectChoice = DisplayAspectChoice.SIXTEEN_NINE,
            ),
        )

        val zelda = DisplayProfileResolver.resolve(
            gameId = "zelda",
            gameTitle = "The Legend of Zelda: Twilight Princess",
            consoleId = "wii",
            corePath = "/cores/dolphin_libretro.so",
            profiles = profiles,
        )
        val metroid = DisplayProfileResolver.resolve(
            gameId = "metroid",
            gameTitle = "Metroid Prime",
            consoleId = "wii",
            corePath = "/cores/dolphin_libretro.so",
            profiles = profiles,
        )

        assertEquals(WidescreenMode.STRETCH, zelda.aspectMode)
        assertEquals("Auto (16:9)", zelda.aspectLabel)
        assertEquals(WidescreenMode.FOUR_THREE, metroid.aspectMode)
        assertEquals("Auto (Original)", metroid.aspectLabel)
    }

    @Test
    fun `manual aspect and render scale choices win when supported`() {
        val profile = DisplayProfileResolver.resolve(
            gameId = "zelda",
            gameTitle = "The Legend of Zelda: Twilight Princess",
            consoleId = "wii",
            corePath = "/cores/dolphin_libretro.so",
            aspectChoice = DisplayAspectChoice.ORIGINAL,
            renderScaleChoice = RenderScaleChoice.THREE_X,
        )

        assertEquals(WidescreenMode.FOUR_THREE, profile.aspectMode)
        assertEquals("Original", profile.aspectLabel)
        assertEquals(RenderScale.THREE_X, profile.renderScale)
        assertEquals("3x", profile.renderScaleLabel)
    }

    @Test
    fun `unsupported manual render scale falls back to native`() {
        val profile = DisplayProfileResolver.resolve(
            gameId = "psx-game",
            gameTitle = "PSX Game",
            consoleId = "psx",
            corePath = "/cores/mednafen_psx_hw_libretro.so",
            renderScaleChoice = RenderScaleChoice.THREE_X,
        )

        assertEquals(RenderScale.NATIVE, profile.renderScale)
        assertEquals("Native", profile.renderScaleLabel)
        assertEquals("3x unavailable, using Native", profile.renderScaleStateDescription)
    }

    @Test
    fun `auto render scale defaults are conservative per core and platform`() {
        assertEquals(
            RenderScale.FOUR_X,
            resolveScale("psx", "/cores/beetle_psx_hw_libretro.so"),
        )
        assertEquals(
            RenderScale.FOUR_X,
            resolveScale("psp", "/cores/ppsspp_libretro.so"),
        )
        assertEquals(
            RenderScale.THREE_X,
            resolveScale("n64", "/cores/mupen64plus_next_gles3_libretro.so", platform = "android"),
        )
        assertEquals(
            RenderScale.NATIVE,
            resolveScale("n64", "/cores/mupen64plus_next_gles3_libretro.dylib", platform = "macos"),
        )
        assertEquals(
            RenderScale.TWO_X,
            resolveScale("wii", "/cores/dolphin_libretro.so"),
        )
        assertEquals(
            RenderScale.NATIVE,
            resolveScale("snes", "/cores/snes9x_libretro.so"),
        )
    }

    @Test
    fun `auto falls back to native when console and core do not support scaling together`() {
        val profile = DisplayProfileResolver.resolve(
            gameId = "gc-game",
            gameTitle = "GameCube Game",
            consoleId = "gc",
            corePath = "/cores/snes9x_libretro.so",
        )

        assertEquals(RenderScale.NATIVE, profile.renderScale)
        assertEquals("Auto (Native)", profile.renderScaleLabel)
    }

    private fun resolveScale(consoleId: String, corePath: String, platform: String = "android"): RenderScale =
        DisplayProfileResolver.resolve(
            gameId = "game",
            gameTitle = "Game",
            consoleId = consoleId,
            corePath = corePath,
            platform = platform,
        ).renderScale
}
