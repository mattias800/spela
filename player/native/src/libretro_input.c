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

        default:
            break;
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
