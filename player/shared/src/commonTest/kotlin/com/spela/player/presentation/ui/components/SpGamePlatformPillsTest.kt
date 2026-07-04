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
            listOf(GamePlatform("game-1", "nes", "NES", isPreferred = true)),
            platformTargetsForCard(game),
        )
    }

    @Test
    fun platformTargetsMarkCurrentGamePreferredAndDedupeByGameId() {
        val game = game(
            id = "game-nes",
            platforms = listOf(
                GamePlatform("game-nes", "nes", "NES", isPreferred = false),
                GamePlatform("game-snes", "snes", "SNES", isPreferred = false),
                GamePlatform("game-snes", "snes", "SNES", isPreferred = false),
            ),
        )

        val targets = platformTargetsForCard(game)

        assertEquals(listOf("game-nes", "game-snes"), targets.map { it.gameId })
        assertTrue(targets.first().isPreferred)
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
