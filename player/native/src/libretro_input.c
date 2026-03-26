/*
 * Spela libretro bridge - Input subsystem.
 *
 * Maps platform controller state (set from Kotlin) to
 * the libretro input callbacks the core queries each frame.
 */

#include "libretro_bridge.h"

#include <string.h>

#define MAX_PORTS 8
#define MAX_BUTTONS 16
#define MAX_KEYBOARD_KEYS 322  /* RETROK_LAST + 1 */

static struct {
    /* Digital button state per port */
    bool buttons[MAX_PORTS][MAX_BUTTONS];

    /* Analog axis state per port, per stick (left/right), per axis (x/y) */
    int16_t analog[MAX_PORTS][2][2];

    /* Pointer/touch state per port (for RETRO_DEVICE_POINTER) */
    struct {
        int16_t x;       /* -0x7FFF to 0x7FFF */
        int16_t y;       /* -0x7FFF to 0x7FFF */
        bool pressed;
    } pointer[MAX_PORTS];

    /* Mouse state per port (relative deltas, consumed each frame) */
    struct {
        int16_t dx;       /* Accumulated X delta since last poll */
        int16_t dy;       /* Accumulated Y delta since last poll */
        bool left;
        bool right;
    } mouse[MAX_PORTS];

    /* Keyboard state (global, not per-port) */
    bool keyboard[MAX_KEYBOARD_KEYS];

    bool initialized;
} input_state = {0};

void input_init(void) {
    memset(&input_state, 0, sizeof(input_state));
    input_state.initialized = true;
}

void input_deinit(void) {
    memset(&input_state, 0, sizeof(input_state));
}

/*
 * Called by the core before querying input state each frame.
 * In our case, state is already set by Kotlin, so this is a no-op.
 */
void input_poll_callback(void) {
    /* State is pushed from Kotlin side, nothing to poll */
}

/*
 * Called by the core to read the current state of a button or axis.
 */
int16_t input_state_callback(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= MAX_PORTS) return 0;

    switch (device & 0xFF) {
        case RETRO_DEVICE_JOYPAD:
            if (id < MAX_BUTTONS) {
                return input_state.buttons[port][id] ? 1 : 0;
            }
            break;

        case RETRO_DEVICE_ANALOG:
            if (index < 2 && id < 2) {
                return input_state.analog[port][index][id];
            }
            break;

        case RETRO_DEVICE_MOUSE: {
            /* Mouse input with fallback to pointer state.
             * Some cores (e.g. DeSmuME with pointer_mouse=enabled) query
             * RETRO_DEVICE_MOUSE for touch input. When no real mouse input
             * has been provided (mouse_active is false), fall back to the
             * pointer/touch state for backward compatibility. */
            bool mouse_active = input_state.mouse[port].left ||
                                input_state.mouse[port].right ||
                                input_state.mouse[port].dx != 0 ||
                                input_state.mouse[port].dy != 0;
            if (mouse_active) {
                switch (id) {
                    case RETRO_DEVICE_ID_MOUSE_X: {
                        int16_t dx = input_state.mouse[port].dx;
                        input_state.mouse[port].dx = 0;
                        return dx;
                    }
                    case RETRO_DEVICE_ID_MOUSE_Y: {
                        int16_t dy = input_state.mouse[port].dy;
                        input_state.mouse[port].dy = 0;
                        return dy;
                    }
                    case RETRO_DEVICE_ID_MOUSE_LEFT:
                        return input_state.mouse[port].left ? 1 : 0;
                    case RETRO_DEVICE_ID_MOUSE_RIGHT:
                        return input_state.mouse[port].right ? 1 : 0;
                }
            } else {
                /* Fallback: map pointer/touch state to mouse queries */
                switch (id) {
                    case RETRO_DEVICE_ID_MOUSE_X:
                        return input_state.pointer[port].x;
                    case RETRO_DEVICE_ID_MOUSE_Y:
                        return input_state.pointer[port].y;
                    case RETRO_DEVICE_ID_MOUSE_LEFT:
                        return input_state.pointer[port].pressed ? 1 : 0;
                    case RETRO_DEVICE_ID_MOUSE_RIGHT:
                        return 0;
                }
            }
            break;
        }

        case RETRO_DEVICE_POINTER:
            switch (id) {
                case RETRO_DEVICE_ID_POINTER_X:
                    return input_state.pointer[port].x;
                case RETRO_DEVICE_ID_POINTER_Y:
                    return input_state.pointer[port].y;
                case RETRO_DEVICE_ID_POINTER_PRESSED:
                    return input_state.pointer[port].pressed ? 1 : 0;
            }
            break;

        case RETRO_DEVICE_KEYBOARD:
            if (id < MAX_KEYBOARD_KEYS) {
                return input_state.keyboard[id] ? 1 : 0;
            }
            break;

        default:
            break;
    }

    return 0;
}

/* Called from Kotlin/JNI to read button state */
bool input_get_button(unsigned port, unsigned id) {
    if (port < MAX_PORTS && id < MAX_BUTTONS) {
        return input_state.buttons[port][id];
    }
    return false;
}

/* Called from Kotlin/JNI to read analog axis state */
int16_t input_get_analog(unsigned port, unsigned index, unsigned id) {
    if (port < MAX_PORTS && index < 2 && id < 2) {
        return input_state.analog[port][index][id];
    }
    return 0;
}

/* Called from Kotlin/JNI to set button state */
void input_set_button(unsigned port, unsigned id, bool pressed) {
    if (port < MAX_PORTS && id < MAX_BUTTONS) {
        input_state.buttons[port][id] = pressed;
    }
}

/* Called from Kotlin/JNI to set analog axis state */
void input_set_analog(unsigned port, unsigned index, unsigned id, int16_t value) {
    if (port < MAX_PORTS && index < 2 && id < 2) {
        input_state.analog[port][index][id] = value;
    }
}

/* Called from Kotlin/JNI to set pointer/touch state (for DS touch screen) */
void input_set_pointer(unsigned port, int16_t x, int16_t y, bool pressed) {
    if (port < MAX_PORTS) {
        input_state.pointer[port].x = x;
        input_state.pointer[port].y = y;
        input_state.pointer[port].pressed = pressed;
    }
}

/* Called from Kotlin/JNI to set mouse relative movement and button state */
void input_set_mouse(unsigned port, int16_t dx, int16_t dy, bool left, bool right) {
    if (port < MAX_PORTS) {
        input_state.mouse[port].dx += dx;   /* Accumulate deltas */
        input_state.mouse[port].dy += dy;
        input_state.mouse[port].left = left;
        input_state.mouse[port].right = right;
    }
}

/* Called from Kotlin/JNI to set keyboard key state */
void input_set_keyboard(unsigned key, bool pressed) {
    if (key < MAX_KEYBOARD_KEYS) {
        input_state.keyboard[key] = pressed;
    }
}
