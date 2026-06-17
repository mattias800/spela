package com.spela.player.desktop.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.ui.components.gamepad.GamepadSchematic
import kotlin.test.Test

/** Coverage for the #1366 positional schematic: every canonical position renders
 *  as a pip, and only the highlighted ones report Active. */
@OptIn(ExperimentalTestApi::class)
class GamepadSchematicTest {

    @Test
    fun rendersEveryCanonicalPositionAndHighlightsOnlyActive() = runComposeUiTest {
        setContent {
            GamepadSchematic(highlighted = setOf(GamepadPosition.SOUTH, GamepadPosition.R1))
        }

        // All 16 canonical positions are drawn.
        GamepadPosition.entries.forEach { pos ->
            onNodeWithTag("schematic_${pos.name}").assertExists()
        }

        // Highlighted → Active; everything else → Inactive.
        onNodeWithTag("schematic_SOUTH")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))
        onNodeWithTag("schematic_R1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active"))
        onNodeWithTag("schematic_NORTH")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inactive"))
        onNodeWithTag("schematic_DPAD_LEFT")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Inactive"))
    }
}
