/**
 * SDL2 GameController JNI bridge for desktop gamepad support.
 *
 * Uses SDL2's GameController API to enumerate and poll connected gamepads.
 * Returns an array of GamepadState objects containing controller ID, name,
 * button states, and axis values.
 *
 * SDL GameController buttons map directly to libretro button IDs since both
 * use the same standard gamepad layout (A, B, X, Y, Start, Select, etc.).
 */

#include "gamepad_sdl2.h"

#ifdef HAS_SDL2

#include <SDL.h>
#include <string.h>

#define MAX_CONTROLLERS 8

static SDL_GameController *controllers[MAX_CONTROLLERS];
static int controller_instance_ids[MAX_CONTROLLERS];
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

static int sdl_button_to_retro(SDL_GameControllerButton btn) {
    switch (btn) {
        case SDL_CONTROLLER_BUTTON_A:             return RETRO_B;
        case SDL_CONTROLLER_BUTTON_B:             return RETRO_A;
        case SDL_CONTROLLER_BUTTON_X:             return RETRO_Y;
        case SDL_CONTROLLER_BUTTON_Y:             return RETRO_X;
        case SDL_CONTROLLER_BUTTON_BACK:          return RETRO_SELECT;
        case SDL_CONTROLLER_BUTTON_START:         return RETRO_START;
        case SDL_CONTROLLER_BUTTON_LEFTSTICK:     return RETRO_L3;
        case SDL_CONTROLLER_BUTTON_RIGHTSTICK:     return RETRO_R3;
        case SDL_CONTROLLER_BUTTON_LEFTSHOULDER:  return RETRO_L;
        case SDL_CONTROLLER_BUTTON_RIGHTSHOULDER:  return RETRO_R;
        case SDL_CONTROLLER_BUTTON_DPAD_UP:       return RETRO_UP;
        case SDL_CONTROLLER_BUTTON_DPAD_DOWN:     return RETRO_DOWN;
        case SDL_CONTROLLER_BUTTON_DPAD_LEFT:     return RETRO_LEFT;
        case SDL_CONTROLLER_BUTTON_DPAD_RIGHT:    return RETRO_RIGHT;
        default:                                   return -1;
    }
}

static void open_controllers(void) {
    num_controllers = 0;
    int joy_count = SDL_NumJoysticks();
    for (int i = 0; i < joy_count && num_controllers < MAX_CONTROLLERS; i++) {
        if (SDL_IsGameController(i)) {
            SDL_GameController *gc = SDL_GameControllerOpen(i);
            if (gc) {
                controllers[num_controllers] = gc;
                SDL_Joystick *joy = SDL_GameControllerGetJoystick(gc);
                controller_instance_ids[num_controllers] = SDL_JoystickInstanceID(joy);
                num_controllers++;
            }
        }
    }
}

JNIEXPORT jboolean JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadInit(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
    if (initialized) return JNI_TRUE;

    if (SDL_Init(SDL_INIT_GAMECONTROLLER) < 0) {
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
            SDL_GameControllerClose(controllers[i]);
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

    /* Process events to detect connect/disconnect */
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_CONTROLLERDEVICEADDED: {
                int device_index = event.cdevice.which;
                if (SDL_IsGameController(device_index) && num_controllers < MAX_CONTROLLERS) {
                    SDL_GameController *gc = SDL_GameControllerOpen(device_index);
                    if (gc) {
                        controllers[num_controllers] = gc;
                        SDL_Joystick *joy = SDL_GameControllerGetJoystick(gc);
                        controller_instance_ids[num_controllers] = SDL_JoystickInstanceID(joy);
                        num_controllers++;
                    }
                }
                break;
            }
            case SDL_CONTROLLERDEVICEREMOVED: {
                int instance_id = event.cdevice.which;
                for (int i = 0; i < num_controllers; i++) {
                    if (controller_instance_ids[i] == instance_id) {
                        SDL_GameControllerClose(controllers[i]);
                        /* Shift remaining controllers down */
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
        }
    }

    SDL_GameControllerUpdate();

    /* Find the GamepadState class and fields */
    jclass stateClass = (*env)->FindClass(env, "com/spela/player/libretro/GamepadState");
    if (!stateClass) return NULL;

    jmethodID ctor = (*env)->GetMethodID(env, stateClass, "<init>", "(ILjava/lang/String;[Z[I)V");
    if (!ctor) return NULL;

    jobjectArray result = (*env)->NewObjectArray(env, num_controllers, stateClass, NULL);
    if (!result) return NULL;

    for (int i = 0; i < num_controllers; i++) {
        SDL_GameController *gc = controllers[i];
        if (!gc || !SDL_GameControllerGetAttached(gc)) continue;

        /* Controller ID = instance ID */
        jint controllerId = controller_instance_ids[i];

        /* Name */
        const char *name = SDL_GameControllerName(gc);
        jstring jname = (*env)->NewStringUTF(env, name ? name : "Unknown Controller");

        /* Button states */
        jbooleanArray buttons = (*env)->NewBooleanArray(env, NUM_BUTTONS);
        jboolean btnValues[NUM_BUTTONS];
        memset(btnValues, 0, sizeof(btnValues));

        for (int b = SDL_CONTROLLER_BUTTON_A; b <= SDL_CONTROLLER_BUTTON_DPAD_RIGHT; b++) {
            int retro_id = sdl_button_to_retro((SDL_GameControllerButton)b);
            if (retro_id >= 0 && retro_id < NUM_BUTTONS) {
                btnValues[retro_id] = SDL_GameControllerGetButton(gc, (SDL_GameControllerButton)b) ? JNI_TRUE : JNI_FALSE;
            }
        }
        (*env)->SetBooleanArrayRegion(env, buttons, 0, NUM_BUTTONS, btnValues);

        /* Axis values: LX, LY, RX, RY, TriggerL, TriggerR */
        jintArray axes = (*env)->NewIntArray(env, NUM_AXES);
        jint axisValues[NUM_AXES];
        axisValues[0] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_LEFTX);
        axisValues[1] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_LEFTY);
        axisValues[2] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_RIGHTX);
        axisValues[3] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_RIGHTY);
        axisValues[4] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_TRIGGERLEFT);
        axisValues[5] = SDL_GameControllerGetAxis(gc, SDL_CONTROLLER_AXIS_TRIGGERRIGHT);
        (*env)->SetIntArrayRegion(env, axes, 0, NUM_AXES, axisValues);

        /* Create GamepadState object */
        jobject stateObj = (*env)->NewObject(env, stateClass, ctor, controllerId, jname, buttons, axes);
        (*env)->SetObjectArrayElement(env, result, i, stateObj);

        /* Clean up local refs */
        (*env)->DeleteLocalRef(env, jname);
        (*env)->DeleteLocalRef(env, buttons);
        (*env)->DeleteLocalRef(env, axes);
        (*env)->DeleteLocalRef(env, stateObj);
    }

    return result;
}

#else /* !HAS_SDL2 */

/* Stub implementations when SDL2 is not available */
JNIEXPORT jboolean JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadInit(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadShutdown(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
}

JNIEXPORT jobjectArray JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadPoll(
    JNIEnv *env, jobject obj
) {
    (void)env; (void)obj;
    return NULL;
}

#endif /* HAS_SDL2 */
