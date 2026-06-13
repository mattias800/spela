/**
 * SDL3 Gamepad JNI bridge for desktop gamepad support.
 *
 * Uses SDL3's Gamepad API to enumerate and poll connected gamepads.
 * Returns an array of GamepadState objects containing controller ID, name,
 * button states, and axis values.
 *
 * SDL normalizes physical button position (SDL_GAMEPAD_BUTTON_SOUTH is always
 * the bottom face button regardless of brand). The south->RETRO_B / east->RETRO_A
 * mapping below is the project's established (Nintendo-logical) layout, preserved
 * verbatim from the SDL2 bridge.
 */

#include "gamepad_sdl3.h"

#include <SDL3/SDL.h>
#include <string.h>

#define MAX_CONTROLLERS 8

static SDL_Gamepad *controllers[MAX_CONTROLLERS];
static SDL_JoystickID controller_instance_ids[MAX_CONTROLLERS];
static int num_controllers = 0;
static int initialized = 0;

/* libretro button IDs (matching RETRO_DEVICE_ID_JOYPAD_*) */
#define RETRO_B      0
#define RETRO_Y      1
#define RETRO_SELECT 2
#define RETRO_START  3
#define RETRO_UP     4
#define RETRO_DOWN   5
#define RETRO_LEFT   6
#define RETRO_RIGHT  7
#define RETRO_A      8
#define RETRO_X      9
#define RETRO_L     10
#define RETRO_R     11
#define RETRO_L2    12
#define RETRO_R2    13
#define RETRO_L3    14
#define RETRO_R3    15

/* Number of discrete buttons we track */
#define NUM_BUTTONS 16
/* Number of axes we track: LX, LY, RX, RY, TriggerL, TriggerR */
#define NUM_AXES 6

static int sdl_button_to_retro(SDL_GamepadButton btn) {
    switch (btn) {
        case SDL_GAMEPAD_BUTTON_SOUTH:          return RETRO_B;
        case SDL_GAMEPAD_BUTTON_EAST:           return RETRO_A;
        case SDL_GAMEPAD_BUTTON_WEST:           return RETRO_Y;
        case SDL_GAMEPAD_BUTTON_NORTH:          return RETRO_X;
        case SDL_GAMEPAD_BUTTON_BACK:           return RETRO_SELECT;
        case SDL_GAMEPAD_BUTTON_START:          return RETRO_START;
        case SDL_GAMEPAD_BUTTON_LEFT_STICK:     return RETRO_L3;
        case SDL_GAMEPAD_BUTTON_RIGHT_STICK:    return RETRO_R3;
        case SDL_GAMEPAD_BUTTON_LEFT_SHOULDER:  return RETRO_L;
        case SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER: return RETRO_R;
        case SDL_GAMEPAD_BUTTON_DPAD_UP:        return RETRO_UP;
        case SDL_GAMEPAD_BUTTON_DPAD_DOWN:      return RETRO_DOWN;
        case SDL_GAMEPAD_BUTTON_DPAD_LEFT:      return RETRO_LEFT;
        case SDL_GAMEPAD_BUTTON_DPAD_RIGHT:     return RETRO_RIGHT;
        default:                                return -1;
    }
}

static void open_controllers(void) {
    num_controllers = 0;
    int count = 0;
    SDL_JoystickID *ids = SDL_GetGamepads(&count);
    if (ids) {
        for (int i = 0; i < count && num_controllers < MAX_CONTROLLERS; i++) {
            SDL_Gamepad *gc = SDL_OpenGamepad(ids[i]);
            if (gc) {
                controllers[num_controllers] = gc;
                controller_instance_ids[num_controllers] = ids[i];
                num_controllers++;
            }
        }
        SDL_free(ids);
    }
}

JNIEXPORT jboolean JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadInit(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
    if (initialized) return JNI_TRUE;

    /* Run joystick device detection on SDL's own internal thread so hot-plug
     * add/remove events are delivered even though this app never pumps a Win32
     * message loop on the UI thread. Must be set before SDL_Init. */
    SDL_SetHint(SDL_HINT_JOYSTICK_THREAD, "1");

    /* SDL3: SDL_Init returns true on success. */
    if (!SDL_Init(SDL_INIT_GAMEPAD)) {
        return JNI_FALSE;
    }

    memset(controllers, 0, sizeof(controllers));
    memset(controller_instance_ids, 0, sizeof(controller_instance_ids));
    open_controllers();
    initialized = 1;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadShutdown(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
    if (!initialized) return;

    for (int i = 0; i < num_controllers; i++) {
        if (controllers[i]) {
            SDL_CloseGamepad(controllers[i]);
            controllers[i] = NULL;
        }
    }
    num_controllers = 0;
    SDL_Quit();
    initialized = 0;
}

JNIEXPORT jobjectArray JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadPoll(
    JNIEnv *env, jobject obj
) {
    (void)obj;
    if (!initialized) return NULL;

    /* Process events to detect connect/disconnect. In SDL3 the device event's
     * `which` field is the joystick instance ID (not a device index). */
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_EVENT_GAMEPAD_ADDED: {
                SDL_JoystickID new_id = event.gdevice.which;
                /* Dedup: SDL also posts ADDED for controllers already opened at
                 * init (open_controllers), which would otherwise open the same
                 * physical device twice and assign it two ports. */
                int already_open = 0;
                for (int k = 0; k < num_controllers; k++) {
                    if (controller_instance_ids[k] == new_id) { already_open = 1; break; }
                }
                if (!already_open && num_controllers < MAX_CONTROLLERS) {
                    SDL_Gamepad *gc = SDL_OpenGamepad(new_id);
                    if (gc) {
                        controllers[num_controllers] = gc;
                        controller_instance_ids[num_controllers] = new_id;
                        num_controllers++;
                    }
                }
                break;
            }
            case SDL_EVENT_GAMEPAD_REMOVED: {
                SDL_JoystickID instance_id = event.gdevice.which;
                for (int i = 0; i < num_controllers; i++) {
                    if (controller_instance_ids[i] == instance_id) {
                        SDL_CloseGamepad(controllers[i]);
                        for (int j = i; j < num_controllers - 1; j++) {
                            controllers[j] = controllers[j + 1];
                            controller_instance_ids[j] = controller_instance_ids[j + 1];
                        }
                        num_controllers--;
                        controllers[num_controllers] = NULL;
                        break;
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    SDL_UpdateGamepads();

    jclass stateClass = (*env)->FindClass(env, "com/spela/player/libretro/GamepadState");
    if (!stateClass) return NULL;

    jmethodID ctor = (*env)->GetMethodID(env, stateClass, "<init>", "(ILjava/lang/String;[Z[I)V");
    if (!ctor) return NULL;

    int attached_count = 0;
    for (int i = 0; i < num_controllers; i++) {
        if (controllers[i] && SDL_GamepadConnected(controllers[i])) attached_count++;
    }

    jobjectArray result = (*env)->NewObjectArray(env, attached_count, stateClass, NULL);
    if (!result) return NULL;

    int out_index = 0;
    for (int i = 0; i < num_controllers; i++) {
        SDL_Gamepad *gc = controllers[i];
        if (!gc || !SDL_GamepadConnected(gc)) continue;

        jint controllerId = (jint)controller_instance_ids[i];

        const char *name = SDL_GetGamepadName(gc);
        jstring jname = (*env)->NewStringUTF(env, name ? name : "Unknown Controller");

        jbooleanArray buttons = (*env)->NewBooleanArray(env, NUM_BUTTONS);
        jboolean btnValues[NUM_BUTTONS];
        memset(btnValues, 0, sizeof(btnValues));

        for (int b = SDL_GAMEPAD_BUTTON_SOUTH; b <= SDL_GAMEPAD_BUTTON_DPAD_RIGHT; b++) {
            int retro_id = sdl_button_to_retro((SDL_GamepadButton)b);
            if (retro_id >= 0 && retro_id < NUM_BUTTONS) {
                btnValues[retro_id] = SDL_GetGamepadButton(gc, (SDL_GamepadButton)b) ? JNI_TRUE : JNI_FALSE;
            }
        }
        (*env)->SetBooleanArrayRegion(env, buttons, 0, NUM_BUTTONS, btnValues);

        jintArray axes = (*env)->NewIntArray(env, NUM_AXES);
        jint axisValues[NUM_AXES];
        axisValues[0] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_LEFTX);
        axisValues[1] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_LEFTY);
        axisValues[2] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_RIGHTX);
        axisValues[3] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_RIGHTY);
        axisValues[4] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_LEFT_TRIGGER);
        axisValues[5] = SDL_GetGamepadAxis(gc, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);
        (*env)->SetIntArrayRegion(env, axes, 0, NUM_AXES, axisValues);

        jobject stateObj = (*env)->NewObject(env, stateClass, ctor, controllerId, jname, buttons, axes);
        (*env)->SetObjectArrayElement(env, result, out_index, stateObj);
        out_index++;

        (*env)->DeleteLocalRef(env, jname);
        (*env)->DeleteLocalRef(env, buttons);
        (*env)->DeleteLocalRef(env, axes);
        (*env)->DeleteLocalRef(env, stateObj);
    }

    return result;
}
