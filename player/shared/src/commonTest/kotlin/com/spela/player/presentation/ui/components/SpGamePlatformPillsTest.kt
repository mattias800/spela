package com.spela.player.presentation.ui.components

import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpGamePlatformPillsTest {

    @Test
    fun compactLabelUsesShortNameWhenReadable() {
        assertEquals("SNES", compactPlatformLabel(consoleId = "snes", consoleName = "SNES"))
    }

    @Test
    fun compactLabelFallsBackToConsoleIdForLongNames() {
        assertEquals(
            "NES",
            compactPlatformLabel(
                consoleId = "nes",
                consoleName = "Nintendo Entertainment System",
            ),
        )
    }

    @Test
    fun platformTargetsFallbackToCurrentGame() {
        val game = game(platforms = emptyList())

        assertEquals(
            listOf(
                GamePlatformTarget(
                    gameId = "game-1",
                    consoleId = "nes",
                    consoleName = "NES",
                    isPreferred = true,
                    isCurrent = true,
                ),
            ),
            platformTargetsForGame(game),
        )
    }

    @Test
    fun platformTargetsKeepBackendPreferredAndDedupeByGameId() {
        val game = game(
            id = "game-nes",
            platforms = listOf(
                GamePlatform("game-nes", "nes", "NES", isPreferred = false),
                GamePlatform("game-snes", "snes", "SNES", isPreferred = true),
                GamePlatform("game-snes", "snes", "SNES", isPreferred = false),
            ),
        )

        val targets = platformTargetsForGame(game)

        assertEquals(listOf("game-nes", "game-snes"), targets.map { it.gameId })
        assertTrue(targets.first().isCurrent)
        assertTrue(targets[1].isPreferred)
        assertEquals("game-snes", preferredGameIdForGame(game))
    }

    @Test
    fun platformTargetsIncludeCurrentGameWhenBackendListOmitsIt() {
        val game = game(
            id = "game-nes",
            platforms = listOf(
                GamePlatform("game-snes", "snes", "SNES", isPreferred = true),
            ),
        )

        val targets = platformTargetsForGame(game)

        assertEquals(listOf("game-nes", "game-snes"), targets.map { it.gameId })
        assertEquals("NES", targets.first().consoleName)
        assertTrue(targets.first().isCurrent)
        assertTrue(targets[1].isPreferred)
    }

    private fun game(
        id: String = "game-1",
        platforms: List<GamePlatform> = emptyList(),
    ): Game = Game(
        id = id,
        title = "Test Game",
        consoleId = "nes",
        consoleName = "NES",
        platforms = platforms,
    )
}
