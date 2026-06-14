package com.spela.player.domain.model

/**
 * Maps an SDL_GamepadType integer (from SDL_GetRealGamepadType, carried on
 * GamepadState.type) to a ControllerStyle. Values per SDL3 3.2.x SDL_gamepad.h:
 *   0 UNKNOWN, 1 STANDARD, 2 XBOX360, 3 XBOXONE, 4 PS3, 5 PS4, 6 PS5,
 *   7 NINTENDO_SWITCH_PRO, 8 JOYCON_LEFT, 9 JOYCON_RIGHT, 10 JOYCON_PAIR.
 * Verify these constants against the pinned SDL3 header if SDL is bumped.
 */
fun controllerStyleFromSdlType(sdlType: Int): ControllerStyle = when (sdlType) {
    2, 3 -> ControllerStyle.Xbox        // also covers XBOXSERIES if a later SDL adds it (extend then)
    4, 5, 6 -> ControllerStyle.PlayStation
    7, 8, 9, 10 -> ControllerStyle.Nintendo
    else -> ControllerStyle.Generic     // 0 UNKNOWN, 1 STANDARD, and anything unmapped
}
