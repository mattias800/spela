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

object LibretroPixelFormat {
    const val RGB1555 = 0
    const val XRGB8888 = 1
    const val RGB565 = 2
}
