package com.spela.player.presentation.ui.components.keymapping

import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.viewmodel.LibretroAnalog
import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * Per-controller button positions for overlay on controller icons.
 * Coordinates are fractional (0..1) relative to the icon/visual bounds.
 *
 * For consoles with a controllercons icon, positions are tuned to match
 * the SVG layout. For unknown consoles, a generic layout is used.
 */
object ControllerButtonPositions {

    fun getRegions(layout: ConsoleButtonLayout): List<ButtonRegion> {
        val positions = specificPositions[layout.consoleId.lowercase()]
        return if (positions != null) {
            buildRegionsFromPositions(layout, positions)
        } else {
            genericButtonRegions(layout)
        }
    }

    private fun buildRegionsFromPositions(
        layout: ConsoleButtonLayout,
        positions: Map<Int, ButtonPositionSpec>,
    ): List<ButtonRegion> {
        val buttonMap = layout.buttons.associateBy { it.retroButtonId }
        return positions.mapNotNull { (retroId, spec) ->
            val info = buttonMap[retroId] ?: return@mapNotNull null
            ButtonRegion(
                info = info,
                cx = spec.cx,
                cy = spec.cy,
                isRound = spec.isRound,
                widthFraction = spec.widthFraction,
                heightFraction = spec.heightFraction,
            )
        }
    }

    private val specificPositions = mapOf(
        "nes" to nesPositions(),
        "snes" to snesPositions(),
        "n64" to n64Positions(),
        "genesis" to genesisPositions(),
        "psx" to psxPositions(),
        "psp" to pspPositions(),
        "3ds" to threedsPositions(),
        "dc" to dreamcastPositions(),
        "sat" to saturnPositions(),
    )

    // ─── NES ────────────────────────────────────────────────────────────
    // Rectangular controller with D-pad left, two round buttons right,
    // Start/Select as small bars in the center.
    private fun nesPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.17f, 0.42f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.17f, 0.62f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.10f, 0.52f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.24f, 0.52f),
        LibretroButtons.B to ButtonPositionSpec(0.67f, 0.52f),
        LibretroButtons.A to ButtonPositionSpec(0.83f, 0.52f),
        LibretroButtons.SELECT to ButtonPositionSpec(
            0.40f, 0.58f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.55f, 0.58f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
    )

    // ─── SNES ───────────────────────────────────────────────────────────
    // Rounded controller with D-pad left, 4 face buttons (diamond) right,
    // L/R shoulder buttons, Select/Start center.
    private fun snesPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.22f, 0.38f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.22f, 0.58f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.14f, 0.48f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.30f, 0.48f),
        LibretroButtons.B to ButtonPositionSpec(0.71f, 0.56f),
        LibretroButtons.A to ButtonPositionSpec(0.80f, 0.46f),
        LibretroButtons.Y to ButtonPositionSpec(0.71f, 0.36f),
        LibretroButtons.X to ButtonPositionSpec(0.80f, 0.26f),
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.SELECT to ButtonPositionSpec(
            0.40f, 0.52f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.53f, 0.52f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
    )

    // ─── N64 ────────────────────────────────────────────────────────────
    // Three-pronged controller with D-pad left, A/B on right prong,
    // C-buttons cluster upper-right, analog stick center, L/R/Z triggers.
    private fun n64Positions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.14f, 0.27f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.14f, 0.42f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.07f, 0.34f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.21f, 0.34f),
        LibretroButtons.A to ButtonPositionSpec(0.70f, 0.35f),
        LibretroButtons.B to ButtonPositionSpec(0.70f, 0.48f),
        LibretroButtons.START to ButtonPositionSpec(0.50f, 0.34f, widthFraction = 0.06f, heightFraction = 0.06f),
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.14f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.14f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.L2 to ButtonPositionSpec(
            0.50f, 0.18f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ), // Z trigger
        // C-buttons (small cluster upper-right)
        LibretroButtons.X to ButtonPositionSpec(0.82f, 0.28f, widthFraction = 0.05f, heightFraction = 0.05f),  // C-Left
        LibretroButtons.Y to ButtonPositionSpec(0.88f, 0.35f, widthFraction = 0.05f, heightFraction = 0.05f),  // C-Down
        LibretroButtons.L3 to ButtonPositionSpec(0.88f, 0.22f, widthFraction = 0.05f, heightFraction = 0.05f), // C-Up
        LibretroButtons.R3 to ButtonPositionSpec(0.94f, 0.28f, widthFraction = 0.05f, heightFraction = 0.05f), // C-Right
        // Analog stick (center prong)
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.38f, 0.38f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.38f, 0.50f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.32f, 0.44f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.44f, 0.44f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    // ─── Genesis / Mega Drive ───────────────────────────────────────────
    // Rounded bean-shaped controller with D-pad left, 3 face buttons (A/B/C) right,
    // Start in center. 6-button mode adds X/Y/Z above.
    private fun genesisPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.22f, 0.40f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.22f, 0.60f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.14f, 0.50f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.30f, 0.50f),
        LibretroButtons.A to ButtonPositionSpec(0.67f, 0.55f),
        LibretroButtons.B to ButtonPositionSpec(0.77f, 0.48f),
        LibretroButtons.Y to ButtonPositionSpec(0.87f, 0.41f), // C button
        LibretroButtons.START to ButtonPositionSpec(
            0.50f, 0.48f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        // 6-button mode
        LibretroButtons.X to ButtonPositionSpec(0.67f, 0.38f, widthFraction = 0.05f, heightFraction = 0.05f),
        LibretroButtons.L to ButtonPositionSpec(0.77f, 0.31f, widthFraction = 0.05f, heightFraction = 0.05f), // Y
        LibretroButtons.R to ButtonPositionSpec(0.87f, 0.24f, widthFraction = 0.05f, heightFraction = 0.05f), // Z
    )

    // ─── PlayStation ────────────────────────────────────────────────────
    // Classic PSX controller with D-pad left, 4 face buttons right (diamond),
    // L1/R1/L2/R2 shoulders, Select/Start center, L3/R3 sticks.
    private fun psxPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.20f, 0.34f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.20f, 0.50f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.13f, 0.42f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.27f, 0.42f),
        LibretroButtons.B to ButtonPositionSpec(0.81f, 0.50f),    // Cross
        LibretroButtons.A to ButtonPositionSpec(0.89f, 0.42f),    // Circle
        LibretroButtons.Y to ButtonPositionSpec(0.73f, 0.42f),    // Square
        LibretroButtons.X to ButtonPositionSpec(0.81f, 0.34f),    // Triangle
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.05f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.05f,
        ),
        LibretroButtons.L2 to ButtonPositionSpec(
            0.14f, 0.12f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        LibretroButtons.R2 to ButtonPositionSpec(
            0.86f, 0.12f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        LibretroButtons.SELECT to ButtonPositionSpec(
            0.42f, 0.42f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.58f, 0.42f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        LibretroButtons.L3 to ButtonPositionSpec(0.35f, 0.62f, widthFraction = 0.06f, heightFraction = 0.06f),
        LibretroButtons.R3 to ButtonPositionSpec(0.65f, 0.62f, widthFraction = 0.06f, heightFraction = 0.06f),
        // Left analog stick cluster
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.35f, 0.70f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.35f, 0.82f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.29f, 0.76f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.41f, 0.76f, widthFraction = 0.04f, heightFraction = 0.04f),
        // Right analog stick cluster
        LibretroAnalog.RIGHT_STICK_UP to ButtonPositionSpec(0.65f, 0.70f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.RIGHT_STICK_DOWN to ButtonPositionSpec(0.65f, 0.82f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.RIGHT_STICK_LEFT to ButtonPositionSpec(0.59f, 0.76f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.RIGHT_STICK_RIGHT to ButtonPositionSpec(0.71f, 0.76f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    // ─── PSP ────────────────────────────────────────────────────────────
    // Similar to PSX but without R-stick, L3/R3. Left stick on the left side.
    private fun pspPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.20f, 0.34f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.20f, 0.50f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.13f, 0.42f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.27f, 0.42f),
        LibretroButtons.B to ButtonPositionSpec(0.81f, 0.50f),    // Cross
        LibretroButtons.A to ButtonPositionSpec(0.89f, 0.42f),    // Circle
        LibretroButtons.Y to ButtonPositionSpec(0.73f, 0.42f),    // Square
        LibretroButtons.X to ButtonPositionSpec(0.81f, 0.34f),    // Triangle
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.05f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.05f,
        ),
        LibretroButtons.SELECT to ButtonPositionSpec(
            0.42f, 0.42f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.58f, 0.42f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        // Left analog stick cluster
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.25f, 0.57f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.25f, 0.69f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.19f, 0.63f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.31f, 0.63f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    // ─── 3DS ────────────────────────────────────────────────────────────
    // Clamshell handheld. D-pad left, face buttons right, circle pad (left stick).
    private fun threedsPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.20f, 0.34f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.20f, 0.50f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.13f, 0.42f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.27f, 0.42f),
        LibretroButtons.A to ButtonPositionSpec(0.83f, 0.42f),
        LibretroButtons.B to ButtonPositionSpec(0.75f, 0.50f),
        LibretroButtons.X to ButtonPositionSpec(0.83f, 0.34f),
        LibretroButtons.Y to ButtonPositionSpec(0.75f, 0.34f),
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.SELECT to ButtonPositionSpec(
            0.42f, 0.55f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.58f, 0.55f, isRound = false,
            widthFraction = 0.08f, heightFraction = 0.04f,
        ),
        // Circle pad (left stick)
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.35f, 0.32f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.35f, 0.44f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.29f, 0.38f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.41f, 0.38f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    // ─── Dreamcast ──────────────────────────────────────────────────────
    // Winged controller with D-pad left, face buttons right, analog stick left side.
    private fun dreamcastPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.22f, 0.38f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.22f, 0.58f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.14f, 0.48f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.30f, 0.48f),
        LibretroButtons.A to ButtonPositionSpec(0.75f, 0.50f),
        LibretroButtons.B to ButtonPositionSpec(0.83f, 0.42f),
        LibretroButtons.X to ButtonPositionSpec(0.75f, 0.34f),
        LibretroButtons.Y to ButtonPositionSpec(0.83f, 0.26f),
        LibretroButtons.L to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.R to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.50f, 0.48f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        // Analog stick
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.35f, 0.62f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.35f, 0.74f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.29f, 0.68f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.41f, 0.68f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    // ─── Saturn ─────────────────────────────────────────────────────────
    // Rounded controller with D-pad left, A/B/C + X/Y/Z right, L/R triggers.
    // 3D pad variant has analog stick.
    private fun saturnPositions() = mapOf(
        LibretroButtons.UP to ButtonPositionSpec(0.22f, 0.38f),
        LibretroButtons.DOWN to ButtonPositionSpec(0.22f, 0.58f),
        LibretroButtons.LEFT to ButtonPositionSpec(0.14f, 0.48f),
        LibretroButtons.RIGHT to ButtonPositionSpec(0.30f, 0.48f),
        LibretroButtons.A to ButtonPositionSpec(0.67f, 0.55f),
        LibretroButtons.B to ButtonPositionSpec(0.77f, 0.48f),
        LibretroButtons.Y to ButtonPositionSpec(0.87f, 0.41f), // C
        LibretroButtons.X to ButtonPositionSpec(0.67f, 0.38f, widthFraction = 0.05f, heightFraction = 0.05f),
        LibretroButtons.L to ButtonPositionSpec(0.77f, 0.31f, widthFraction = 0.05f, heightFraction = 0.05f), // Y
        LibretroButtons.R to ButtonPositionSpec(0.87f, 0.24f, widthFraction = 0.05f, heightFraction = 0.05f), // Z
        LibretroButtons.L2 to ButtonPositionSpec(
            0.14f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.R2 to ButtonPositionSpec(
            0.86f, 0.20f, isRound = false,
            widthFraction = 0.12f, heightFraction = 0.06f,
        ),
        LibretroButtons.START to ButtonPositionSpec(
            0.50f, 0.48f, isRound = false,
            widthFraction = 0.10f, heightFraction = 0.05f,
        ),
        // Analog stick (3D pad)
        LibretroAnalog.LEFT_STICK_UP to ButtonPositionSpec(0.35f, 0.62f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_DOWN to ButtonPositionSpec(0.35f, 0.74f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_LEFT to ButtonPositionSpec(0.29f, 0.68f, widthFraction = 0.04f, heightFraction = 0.04f),
        LibretroAnalog.LEFT_STICK_RIGHT to ButtonPositionSpec(0.41f, 0.68f, widthFraction = 0.04f, heightFraction = 0.04f),
    )

    /**
     * Generic button layout for consoles without a specific controller icon.
     * Extracted from the original getButtonRegions() in ConsoleControllerVisual.
     */
    internal fun genericButtonRegions(layout: ConsoleButtonLayout): List<ButtonRegion> {
        val buttonIds = layout.buttons.map { it.retroButtonId }.toSet()
        val buttonMap = layout.buttons.associateBy { it.retroButtonId }

        return buildList {
            // D-pad: left side of controller
            if (LibretroButtons.UP in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.UP]!!, 0.25f, 0.30f))
            if (LibretroButtons.DOWN in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.DOWN]!!, 0.25f, 0.50f))
            if (LibretroButtons.LEFT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.LEFT]!!, 0.17f, 0.40f))
            if (LibretroButtons.RIGHT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.RIGHT]!!, 0.33f, 0.40f))

            // Face buttons: right side (diamond pattern)
            if (LibretroButtons.A in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.A]!!, 0.83f, 0.40f))
            if (LibretroButtons.B in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.B]!!, 0.75f, 0.50f))
            if (LibretroButtons.X in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.X]!!, 0.75f, 0.30f))
            if (LibretroButtons.Y in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.Y]!!, 0.67f, 0.40f))

            // Start/Select: center
            if (LibretroButtons.START in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.START]!!,
                    0.55f, 0.55f,
                    isRound = false,
                    widthFraction = 0.10f,
                    heightFraction = 0.05f,
                ))
            if (LibretroButtons.SELECT in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.SELECT]!!,
                    0.42f, 0.55f,
                    isRound = false,
                    widthFraction = 0.10f,
                    heightFraction = 0.05f,
                ))

            // Shoulder buttons
            if (LibretroButtons.L in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.L]!!,
                    0.20f, 0.18f,
                    isRound = false,
                    widthFraction = 0.12f,
                    heightFraction = 0.06f,
                ))
            if (LibretroButtons.R in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.R]!!,
                    0.80f, 0.18f,
                    isRound = false,
                    widthFraction = 0.12f,
                    heightFraction = 0.06f,
                ))

            // L2/R2 triggers
            if (LibretroButtons.L2 in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.L2]!!,
                    0.20f, 0.10f,
                    isRound = false,
                    widthFraction = 0.10f,
                    heightFraction = 0.05f,
                ))
            if (LibretroButtons.R2 in buttonIds)
                add(ButtonRegion(
                    buttonMap[LibretroButtons.R2]!!,
                    0.80f, 0.10f,
                    isRound = false,
                    widthFraction = 0.10f,
                    heightFraction = 0.05f,
                ))

            // L3/R3 sticks
            if (LibretroButtons.L3 in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.L3]!!, 0.35f, 0.65f))
            if (LibretroButtons.R3 in buttonIds)
                add(ButtonRegion(buttonMap[LibretroButtons.R3]!!, 0.65f, 0.65f))

            // Left analog stick directions
            if (LibretroAnalog.LEFT_STICK_UP in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.LEFT_STICK_UP]!!, 0.35f, 0.73f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.LEFT_STICK_DOWN in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.LEFT_STICK_DOWN]!!, 0.35f, 0.85f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.LEFT_STICK_LEFT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.LEFT_STICK_LEFT]!!, 0.29f, 0.79f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.LEFT_STICK_RIGHT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.LEFT_STICK_RIGHT]!!, 0.41f, 0.79f, widthFraction = 0.04f, heightFraction = 0.04f))

            // Right analog stick directions
            if (LibretroAnalog.RIGHT_STICK_UP in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.RIGHT_STICK_UP]!!, 0.65f, 0.73f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.RIGHT_STICK_DOWN in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.RIGHT_STICK_DOWN]!!, 0.65f, 0.85f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.RIGHT_STICK_LEFT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.RIGHT_STICK_LEFT]!!, 0.59f, 0.79f, widthFraction = 0.04f, heightFraction = 0.04f))
            if (LibretroAnalog.RIGHT_STICK_RIGHT in buttonIds)
                add(ButtonRegion(buttonMap[LibretroAnalog.RIGHT_STICK_RIGHT]!!, 0.71f, 0.79f, widthFraction = 0.04f, heightFraction = 0.04f))
        }
    }
}

/**
 * Specification for a button's position on a controller visual.
 */
data class ButtonPositionSpec(
    val cx: Float,
    val cy: Float,
    val isRound: Boolean = true,
    val widthFraction: Float = 0.07f,
    val heightFraction: Float = 0.07f,
)
