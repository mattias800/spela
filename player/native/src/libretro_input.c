/*
 * Spela libretro bridge - Input subsystem.
 *
 * Maps platform controller state (set from Kotlin) to
 * the libretro input callbacks the core queries each frame.
 */

#include "libretro_bridge.h"

#include <string.h>
#include <stdio.h>
#ifdef __ANDROID__
#include <android/log.h>
#define INPUT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SpelaLibretro", __VA_ARGS__)
#else
#define INPUT_LOGI(...) do { printf(__VA_ARGS__); printf("\n"); } while(0)
#endif

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

        case RETRO_DEVICE_POINTER: {
            static int ptr_query_log = 0;
            int16_t result = 0;
            switch (id) {
                case RETRO_DEVICE_ID_POINTER_X:
                    result = input_state.pointer[port].x;
                    break;
                case RETRO_DEVICE_ID_POINTER_Y:
                    result = input_state.pointer[port].y;
                    break;
                case RETRO_DEVICE_ID_POINTER_PRESSED:
                    result = input_state.pointer[port].pressed ? 1 : 0;
                    break;
            }
            if (input_state.pointer[port].pressed && id == RETRO_DEVICE_ID_POINTER_PRESSED && (ptr_query_log++ % 60 == 0)) {
                INPUT_LOGI("[Input] core query POINTER port=%d pressed=%d x=%d y=%d",
                     port, input_state.pointer[port].pressed,
                     input_state.pointer[port].x, input_state.pointer[port].y);
            }
            return result;
        }

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
        static int log_count = 0;
        if (pressed && (log_count++ % 30 == 0)) {
            INPUT_LOGI("[Input] pointer port=%d x=%d y=%d pressed=%d", port, x, y, pressed);
        }
        input_state.pointer[port].x = x;
        input_state.pointer[port].y = y;
        input_state.pointer[port].pressed = pressed;
    }
}
