package com.spela.player.presentation.viewmodel

/**
 * Libretro joypad button constants, matching the C header definitions.
 * Used to map platform controller buttons to libretro input IDs.
 */
object LibretroButtons {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15
}

/**
 * Virtual button IDs for analog stick directions.
 * IDs start at 100 to avoid collision with the 16 real libretro button IDs (0-15).
 * These flow through the same key mapping pipeline as regular buttons.
 */
object LibretroAnalog {
    const val LEFT_STICK_UP = 100
    const val LEFT_STICK_DOWN = 101
    const val LEFT_STICK_LEFT = 102
    const val LEFT_STICK_RIGHT = 103
    const val RIGHT_STICK_UP = 104
    const val RIGHT_STICK_DOWN = 105
    const val RIGHT_STICK_LEFT = 106
    const val RIGHT_STICK_RIGHT = 107

    const val STICK_LEFT = 0
    const val STICK_RIGHT = 1
    const val AXIS_X = 0
    const val AXIS_Y = 1

    fun isAnalog(buttonId: Int): Boolean = buttonId in LEFT_STICK_UP..RIGHT_STICK_RIGHT

    /**
     * Decomposes a virtual analog button ID into (stickIndex, axisId, sign).
     * Sign: -1 for up/left, +1 for down/right.
     * Returns null if the ID is not an analog virtual button.
     */
    fun decompose(buttonId: Int): Triple<Int, Int, Int>? = when (buttonId) {
        LEFT_STICK_UP -> Triple(STICK_LEFT, AXIS_Y, -1)
        LEFT_STICK_DOWN -> Triple(STICK_LEFT, AXIS_Y, 1)
        LEFT_STICK_LEFT -> Triple(STICK_LEFT, AXIS_X, -1)
        LEFT_STICK_RIGHT -> Triple(STICK_LEFT, AXIS_X, 1)
        RIGHT_STICK_UP -> Triple(STICK_RIGHT, AXIS_Y, -1)
        RIGHT_STICK_DOWN -> Triple(STICK_RIGHT, AXIS_Y, 1)
        RIGHT_STICK_LEFT -> Triple(STICK_RIGHT, AXIS_X, -1)
        RIGHT_STICK_RIGHT -> Triple(STICK_RIGHT, AXIS_X, 1)
        else -> null
    }
}

object LibretroPixelFormat {
    const val RGB1555 = 0
    const val XRGB8888 = 1
    const val RGB565 = 2
}
