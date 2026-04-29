package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke tests for [ControllerIcons]. Confirms each registered console
 * id produces a non-empty path, the SNES fallback fires for unknown
 * ids, and aliases resolve to the correct underlying icon.
 */
class ControllerIconsTest {

    @Test fun knownConsolesRenderNonEmptyPaths() {
        listOf("nes", "snes", "n64", "genesis", "psx").forEach { id ->
            val nodes = ControllerIcons.forConsole(id).firstPathNodes()
            assertTrue(nodes.isNotEmpty(), "expected $id icon to have path nodes")
        }
    }

    @Test fun unknownConsoleFallsBackToSnes() {
        val unknown = ControllerIcons.forConsole("does-not-exist").firstPathNodes()
        val snes = ControllerIcons.forConsole("snes").firstPathNodes()
        assertEquals(snes, unknown, "unknown ids should render the SNES fallback")
    }

    @Test fun consoleAliasesMatchTheirCanonicalIcon() {
        val cases = listOf(
            "gb" to "snes", "gba" to "snes", "gbc" to "snes",
            "gen" to "genesis", "sat" to "genesis",
            "psp" to "psx", "dc" to "psx",
        )
        cases.forEach { (alias, canonical) ->
            assertEquals(
                ControllerIcons.forConsole(canonical).firstPathNodes(),
                ControllerIcons.forConsole(alias).firstPathNodes(),
                "alias '$alias' should resolve to '$canonical'",
            )
        }
    }

    @Test fun consoleIdLookupIsCaseInsensitive() {
        assertEquals(
            ControllerIcons.forConsole("nes").firstPathNodes(),
            ControllerIcons.forConsole("NES").firstPathNodes(),
        )
    }

    private fun androidx.compose.ui.graphics.vector.ImageVector.firstPathNodes(): List<PathNode> {
        val first = root.iterator().asSequence().firstOrNull()
        assertTrue(first is VectorPath, "first child is not VectorPath: $first")
        return first.pathData
    }
}
