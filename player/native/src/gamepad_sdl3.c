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
#include <stdio.h>
#include <string.h>

#define GP_LOGI(...) do { fprintf(stdout, "[GamepadSDL] "); fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n"); fflush(stdout); } while(0)
#define GP_LOGE(...) do { fprintf(stderr, "[GamepadSDL ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while(0)

#define MAX_CONTROLLERS 8

static SDL_Gamepad *controllers[MAX_CONTROLLERS];
static SDL_JoystickID controller_instance_ids[MAX_CONTROLLERS];
static int num_controllers = 0;
static int initialized = 0;

/* Canonical GamepadPosition ordinals — the INPUT layer of the two-layer model
 * (#1334). These MUST match the Kotlin enum order in
 * shared/src/commonMain/kotlin/com/spela/player/domain/model/GamepadPosition.kt
 * (its "ordinal contract"). The poller reports the per-frame button array
 * indexed by these ordinals; Kotlin then applies the configurable
 * GamepadPosition -> RetroPad mapping. Changing one side without the other
 * silently corrupts desktop input. POS_L2 / POS_R2 are analog triggers, filled
 * Kotlin-side from the axis values, never here. */
#define POS_SOUTH       0
#define POS_EAST        1
#define POS_WEST        2
#define POS_NORTH       3
#define POS_DPAD_UP     4
#define POS_DPAD_DOWN   5
#define POS_DPAD_LEFT   6
#define POS_DPAD_RIGHT  7
#define POS_L1          8
#define POS_R1          9
#define POS_L2         10  /* trigger (axis) — filled in Kotlin */
#define POS_R2         11  /* trigger (axis) — filled in Kotlin */
#define POS_L3         12
#define POS_R3         13
#define POS_START      14
#define POS_SELECT     15

/* Number of discrete positions we track */
#define NUM_BUTTONS 16
/* Number of axes we track: LX, LY, RX, RY, TriggerL, TriggerR */
#define NUM_AXES 6

static int sdl_button_to_position(SDL_GamepadButton btn) {
    switch (btn) {
        case SDL_GAMEPAD_BUTTON_SOUTH:          return POS_SOUTH;
        case SDL_GAMEPAD_BUTTON_EAST:           return POS_EAST;
        case SDL_GAMEPAD_BUTTON_WEST:           return POS_WEST;
        case SDL_GAMEPAD_BUTTON_NORTH:          return POS_NORTH;
        case SDL_GAMEPAD_BUTTON_BACK:           return POS_SELECT;
        case SDL_GAMEPAD_BUTTON_START:          return POS_START;
        case SDL_GAMEPAD_BUTTON_LEFT_STICK:     return POS_L3;
        case SDL_GAMEPAD_BUTTON_RIGHT_STICK:    return POS_R3;
        case SDL_GAMEPAD_BUTTON_LEFT_SHOULDER:  return POS_L1;
        case SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER: return POS_R1;
        case SDL_GAMEPAD_BUTTON_DPAD_UP:        return POS_DPAD_UP;
        case SDL_GAMEPAD_BUTTON_DPAD_DOWN:      return POS_DPAD_DOWN;
        case SDL_GAMEPAD_BUTTON_DPAD_LEFT:      return POS_DPAD_LEFT;
        case SDL_GAMEPAD_BUTTON_DPAD_RIGHT:     return POS_DPAD_RIGHT;
        default:                                return -1;
    }
}

/* One-shot enumeration snapshot for debugging "controller not detected"
 * reports (#1680). A joystick that is not also a gamepad means SDL found the
 * device but has no button mapping for it; zero joysticks means SDL's device
 * discovery (udev/inotify/hidraw) never saw it at all — different bugs, and
 * user reports can't distinguish them without this. Logged at init and on
 * hot-plug only, never per-frame. */
static void log_device_snapshot(const char *when) {
    int num_js = 0;
    SDL_JoystickID *js = SDL_GetJoysticks(&num_js);
    int num_gp = 0;
    SDL_JoystickID *gp = SDL_GetGamepads(&num_gp);
    GP_LOGI("%s: %d joystick(s), %d gamepad(s)", when, num_js, num_gp);
    if (js) {
        for (int i = 0; i < num_js; i++) {
            char guid[64];
            SDL_GUIDToString(SDL_GetJoystickGUIDForID(js[i]), guid, sizeof(guid));
            const char *name = SDL_GetJoystickNameForID(js[i]);
            GP_LOGI("  id=%d name=\"%s\" vid=0x%04x pid=0x%04x guid=%s gamepad=%s",
                    (int)js[i],
                    name ? name : "(null)",
                    SDL_GetJoystickVendorForID(js[i]),
                    SDL_GetJoystickProductForID(js[i]),
                    guid,
                    SDL_IsGamepad(js[i]) ? "yes" : "NO MAPPING");
        }
        SDL_free(js);
    }
    if (gp) SDL_free(gp);

    /* Raw HID layer visibility — on Steam Deck the built-in controller is
     * only reachable through hidraw (SDL's hidapi steamdeck driver), so
     * "0 raw HID devices" here means enumeration/permissions, not mapping. */
    SDL_hid_device_info *hid = SDL_hid_enumerate(0, 0);
    int hid_count = 0;
    for (SDL_hid_device_info *cur = hid; cur; cur = cur->next) hid_count++;
    if (hid) SDL_hid_free_enumeration(hid);
    GP_LOGI("%s: %d raw HID device(s) visible", when, hid_count);
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
            } else {
                GP_LOGE("open failed for gamepad id=%d: %s", (int)ids[i], SDL_GetError());
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
        GP_LOGE("SDL_Init(SDL_INIT_GAMEPAD) failed: %s", SDL_GetError());
        return JNI_FALSE;
    }

    int v = SDL_GetVersion();
    GP_LOGI("SDL %d.%d.%d initialized",
            SDL_VERSIONNUM_MAJOR(v), SDL_VERSIONNUM_MINOR(v), SDL_VERSIONNUM_MICRO(v));

    memset(controllers, 0, sizeof(controllers));
    memset(controller_instance_ids, 0, sizeof(controller_instance_ids));
    open_controllers();
    log_device_snapshot("init");
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
                        GP_LOGI("connected id=%d name=\"%s\"", (int)new_id, SDL_GetGamepadName(gc));
                    } else {
                        GP_LOGE("open failed for added gamepad id=%d: %s", (int)new_id, SDL_GetError());
                    }
                    log_device_snapshot("hotplug");
                }
                break;
            }
            case SDL_EVENT_GAMEPAD_REMOVED: {
                SDL_JoystickID instance_id = event.gdevice.which;
                for (int i = 0; i < num_controllers; i++) {
                    if (controller_instance_ids[i] == instance_id) {
                        GP_LOGI("disconnected id=%d", (int)instance_id);
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

    jmethodID ctor = (*env)->GetMethodID(env, stateClass, "<init>", "(ILjava/lang/String;[Z[IILjava/lang/String;)V");
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

        /* Per-unit serial when the pad exposes one (#1361). Often NULL — Kotlin
         * falls back to the name as the persistence key in that case. */
        const char *serial = SDL_GetGamepadSerial(gc);
        jstring jserial = (*env)->NewStringUTF(env, serial ? serial : "");

        jbooleanArray buttons = (*env)->NewBooleanArray(env, NUM_BUTTONS);
        jboolean btnValues[NUM_BUTTONS];
        memset(btnValues, 0, sizeof(btnValues));

        for (int b = SDL_GAMEPAD_BUTTON_SOUTH; b <= SDL_GAMEPAD_BUTTON_DPAD_RIGHT; b++) {
            int position = sdl_button_to_position((SDL_GamepadButton)b);
            if (position >= 0 && position < NUM_BUTTONS) {
                btnValues[position] = SDL_GetGamepadButton(gc, (SDL_GamepadButton)b) ? JNI_TRUE : JNI_FALSE;
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

        jint gpType = (jint)SDL_GetRealGamepadType(gc);
        jobject stateObj = (*env)->NewObject(env, stateClass, ctor, controllerId, jname, buttons, axes, gpType, jserial);
        (*env)->SetObjectArrayElement(env, result, out_index, stateObj);
        out_index++;

        (*env)->DeleteLocalRef(env, jname);
        (*env)->DeleteLocalRef(env, jserial);
        (*env)->DeleteLocalRef(env, buttons);
        (*env)->DeleteLocalRef(env, axes);
        (*env)->DeleteLocalRef(env, stateObj);
    }

    return result;
}
