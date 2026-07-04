/*
 * Spela libretro bridge - Input subsystem.
 *
 * Maps platform controller state (set from Kotlin) to
 * the libretro input callbacks the core queries each frame.
 *
 * Threading (#1044):
 *   Writers = Kotlin platform threads via input_set_button / _analog /
 *             _analog_button / _pointer / _mouse / _keyboard, called from
 *             input event dispatch.
 *   Reader  = libretro core via input_state_callback, called from inside
 *             retro_run on the emulation thread.
 *   Bool / int16 reads are atomic on aligned types in practice, but the
 *   real race is the mouse dx/dy accumulator: input_set_mouse does a
 *   read-modify-write `dx += dx_in`, racing with input_state_callback's
 *   `dx = mouse.dx; mouse.dx = 0;` read-then-clear. Without sync the
 *   writer's `+=` can clobber a delta the reader was about to consume,
 *   silently dropping mouse motion.
 *
 *   The mutex covers every read and write of input_state. Per-callback
 *   overhead is sub-microsecond and the contention is bounded by the
 *   number of buttons the core polls per frame.
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

    /* Analog button pressure per port/button. A separate "set" bitmap lets
     * digital-only devices keep the full-press fallback from #1570. */
    int16_t analog_buttons[MAX_PORTS][MAX_BUTTONS];
    bool analog_buttons_set[MAX_PORTS][MAX_BUTTONS];

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

    /* #1044: cross-thread state guard. See file-level comment. */
    sp_mutex_t lock;
    bool       lock_initialized;
} input_state = {0};

/* Internal lock helpers — input doesn't expose lock/unlock publicly
 * because every JNI call here is single-shot (set or read) and wraps
 * itself; there is no read+copy+clear triple to span. */
static inline void input_lock(void) {
    if (input_state.lock_initialized) {
        sp_mutex_lock(&input_state.lock);
    }
}

static inline void input_unlock(void) {
    if (input_state.lock_initialized) {
        sp_mutex_unlock(&input_state.lock);
    }
}

void input_init(void) {
    /* Preserve the lock across re-init — see libretro_audio.c for the
     * same pattern. */
    bool had_lock = input_state.lock_initialized;
    sp_mutex_t saved_lock;
    if (had_lock) {
        saved_lock = input_state.lock;
    }

    memset(&input_state, 0, sizeof(input_state));

    if (had_lock) {
        input_state.lock = saved_lock;
        input_state.lock_initialized = true;
    } else if (sp_mutex_init(&input_state.lock) == 0) {
        input_state.lock_initialized = true;
    }

    input_state.initialized = true;
}

void input_deinit(void) {
    if (input_state.lock_initialized) {
        sp_mutex_lock(&input_state.lock);
    }
    /* Zero the data fields without clobbering the mutex — memset over
     * the whole struct would corrupt the pthread_mutex_t. */
    memset(input_state.buttons, 0, sizeof(input_state.buttons));
    memset(input_state.analog, 0, sizeof(input_state.analog));
    memset(input_state.analog_buttons, 0, sizeof(input_state.analog_buttons));
    memset(input_state.analog_buttons_set, 0, sizeof(input_state.analog_buttons_set));
    memset(input_state.pointer, 0, sizeof(input_state.pointer));
    memset(input_state.mouse, 0, sizeof(input_state.mouse));
    memset(input_state.keyboard, 0, sizeof(input_state.keyboard));
    input_state.initialized = false;
    if (input_state.lock_initialized) {
        sp_mutex_unlock(&input_state.lock);
        sp_mutex_destroy(&input_state.lock);
        input_state.lock_initialized = false;
    }
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
 *
 * Holds input_lock for the entire body — for mouse dx/dy this is the
 * key guarantee: the read-and-clear pair must be atomic against
 * input_set_mouse's `dx += dx_in`, otherwise concurrent writes can
 * either be lost (writer adds between read and clear) or visible only
 * partially across a torn 16-bit access.
 */
int16_t input_state_callback(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= MAX_PORTS) return 0;

    int16_t result = 0;
    input_lock();

    switch (device & 0xFF) {
        case RETRO_DEVICE_JOYPAD:
            if (id < MAX_BUTTONS) {
                result = input_state.buttons[port][id] ? 1 : 0;
            }
            break;

        case RETRO_DEVICE_ANALOG:
            if (index < 2 && id < 2) {
                /* Analog sticks: index = LEFT/RIGHT, id = X/Y. */
                result = input_state.analog[port][index][id];
            } else if (index == RETRO_DEVICE_INDEX_ANALOG_BUTTON && id < MAX_BUTTONS) {
                /* Analog button pressure. Prefer the platform-supplied value
                 * when the controller exposes a trigger axis. If no analog
                 * pressure has ever been set for this port/button, keep the
                 * #1570 digital full-press fallback for keyboard/digital-only
                 * inputs and cores such as Dolphin that query L/R triggers via
                 * RETRO_DEVICE_INDEX_ANALOG_BUTTON. */
                result = input_state.analog_buttons_set[port][id]
                    ? input_state.analog_buttons[port][id]
                    : (input_state.buttons[port][id] ? 0x7FFF : 0);
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
                        result = input_state.mouse[port].dx;
                        input_state.mouse[port].dx = 0;
                        break;
                    }
                    case RETRO_DEVICE_ID_MOUSE_Y: {
                        result = input_state.mouse[port].dy;
                        input_state.mouse[port].dy = 0;
                        break;
                    }
                    case RETRO_DEVICE_ID_MOUSE_LEFT:
                        result = input_state.mouse[port].left ? 1 : 0;
                        break;
                    case RETRO_DEVICE_ID_MOUSE_RIGHT:
                        result = input_state.mouse[port].right ? 1 : 0;
                        break;
                }
            } else {
                /* Fallback: map pointer/touch state to mouse queries */
                switch (id) {
                    case RETRO_DEVICE_ID_MOUSE_X:
                        result = input_state.pointer[port].x;
                        break;
                    case RETRO_DEVICE_ID_MOUSE_Y:
                        result = input_state.pointer[port].y;
                        break;
                    case RETRO_DEVICE_ID_MOUSE_LEFT:
                        result = input_state.pointer[port].pressed ? 1 : 0;
                        break;
                    case RETRO_DEVICE_ID_MOUSE_RIGHT:
                        result = 0;
                        break;
                }
            }
            break;
        }

        case RETRO_DEVICE_POINTER:
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
            break;

        case RETRO_DEVICE_KEYBOARD:
            if (id < MAX_KEYBOARD_KEYS) {
                result = input_state.keyboard[id] ? 1 : 0;
            }
            break;

        default:
            break;
    }

    input_unlock();
    return result;
}

/* Called from Kotlin/JNI to read button state */
bool input_get_button(unsigned port, unsigned id) {
    if (port >= MAX_PORTS || id >= MAX_BUTTONS) return false;
    input_lock();
    bool pressed = input_state.buttons[port][id];
    input_unlock();
    return pressed;
}

/* Called from Kotlin/JNI to read analog axis state */
int16_t input_get_analog(unsigned port, unsigned index, unsigned id) {
    if (port >= MAX_PORTS || index >= 2 || id >= 2) return 0;
    input_lock();
    int16_t v = input_state.analog[port][index][id];
    input_unlock();
    return v;
}

/* Called from Kotlin/JNI to set button state */
void input_set_button(unsigned port, unsigned id, bool pressed) {
    if (port >= MAX_PORTS || id >= MAX_BUTTONS) return;
    input_lock();
    input_state.buttons[port][id] = pressed;
    input_unlock();
}

/* Called from Kotlin/JNI to set analog axis state */
void input_set_analog(unsigned port, unsigned index, unsigned id, int16_t value) {
    if (port >= MAX_PORTS || index >= 2 || id >= 2) return;
    input_lock();
    input_state.analog[port][index][id] = value;
    input_unlock();
}

/* Called from Kotlin/JNI to set analog button pressure */
void input_set_analog_button(unsigned port, unsigned id, int16_t value) {
    if (port >= MAX_PORTS || id >= MAX_BUTTONS) return;
    input_lock();
    input_state.analog_buttons[port][id] = value;
    input_state.analog_buttons_set[port][id] = true;
    input_unlock();
}

/* Called from Kotlin/JNI when a mapped analog button no longer receives pressure */
void input_clear_analog_button(unsigned port, unsigned id) {
    if (port >= MAX_PORTS || id >= MAX_BUTTONS) return;
    input_lock();
    input_state.analog_buttons[port][id] = 0;
    input_state.analog_buttons_set[port][id] = false;
    input_unlock();
}

/* Called from Kotlin/JNI to set pointer/touch state (for DS touch screen) */
void input_set_pointer(unsigned port, int16_t x, int16_t y, bool pressed) {
    if (port >= MAX_PORTS) return;
    input_lock();
    input_state.pointer[port].x = x;
    input_state.pointer[port].y = y;
    input_state.pointer[port].pressed = pressed;
    input_unlock();
}

/* Called from Kotlin/JNI to set mouse relative movement and button state */
void input_set_mouse(unsigned port, int16_t dx, int16_t dy, bool left, bool right) {
    if (port >= MAX_PORTS) return;
    input_lock();
    input_state.mouse[port].dx += dx;   /* Accumulate deltas */
    input_state.mouse[port].dy += dy;
    input_state.mouse[port].left = left;
    input_state.mouse[port].right = right;
    input_unlock();
}

/* Called from Kotlin/JNI to set keyboard key state */
void input_set_keyboard(unsigned key, bool pressed) {
    if (key >= MAX_KEYBOARD_KEYS) return;
    input_lock();
    input_state.keyboard[key] = pressed;
    input_unlock();
}
