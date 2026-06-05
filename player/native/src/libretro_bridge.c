/*
 * Spela libretro bridge - Main core loading and lifecycle management.
 *
 * This implements the libretro frontend API: it loads cores as shared libraries,
 * registers callbacks, and drives the emulation loop.
 *
 * Reference: RetroArch's core_ctl.c and runloop.c
 */

#include "sp_platform.h"      /* must precede vulkan.h on Win32 (provides windows.h) */
#include <vulkan/vulkan.h>

#include "libretro_bridge.h"
#include "libretro_achievements.h"
#include "gpu_renderer.h"
#include "spela_host_api.h"   /* native (non-JNI) host API — implemented at end of file */

#include "hw_render_gl.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifndef _WIN32
#include <unistd.h>
#include <sys/stat.h>
#endif
#include <errno.h>
#ifdef __ANDROID__
#include <android/native_window_jni.h>
#else
/* Desktop: JAWT for extracting native surface from AWT components */
#include <jawt.h>
#endif

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "SpelaLibretro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
static FILE *g_bridge_log_file = NULL;
static FILE *bridge_log_get(void) {
    if (!g_bridge_log_file) {
        char log_path[512];
        snprintf(log_path, sizeof(log_path), "%sspela_bridge.log", sp_get_temp_dir());
        g_bridge_log_file = fopen(log_path, "w");
        if (g_bridge_log_file) setbuf(g_bridge_log_file, NULL);
    }
    return g_bridge_log_file ? g_bridge_log_file : stderr;
}
/* Log to both bridge log file AND stderr so output appears in Gradle console */
#define LOGI(...) do { FILE *f = bridge_log_get(); fprintf(f, __VA_ARGS__); fprintf(f, "\n"); fprintf(stderr, "[SpelaBridge] " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#define LOGW(...) do { FILE *f = bridge_log_get(); fprintf(f, "WARN: " __VA_ARGS__); fprintf(f, "\n"); fprintf(stderr, "[SpelaBridge] WARN: " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#define LOGE(...) do { FILE *f = bridge_log_get(); fprintf(f, "ERROR: " __VA_ARGS__); fprintf(f, "\n"); fprintf(stderr, "[SpelaBridge] ERROR: " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

/* Global core instance */
libretro_core_t g_core = {0};

/* Global JavaVM pointer - needed for JNI thread attachment (e.g. Play! PS2)
 * and for getting a valid JNIEnv on arbitrary threads (achievements deinit).
 * Captured from JNIEnv in nativeLoadCore(). */
JavaVM *g_jvm = NULL;

/* GPU renderer instance - used by both env callbacks and JNI methods */
static gpu_renderer_t *g_gpu_renderer = NULL;
/* Set when nativeGpuDeinit is called while a core is still loaded (emulation
 * screen onDispose racing the stop flow); the deinit then runs at the end of
 * nativeDeinit instead. See nativeGpuDeinit. */
static volatile bool g_gpu_deinit_deferred = false;
static void do_gpu_deinit(void);

/* Core variable storage for RETRO_ENVIRONMENT_GET_VARIABLE */
#define MAX_CORE_VARIABLES 256
#define MAX_VAR_KEY_LEN 128
#define MAX_VAR_VALUE_LEN 256

static struct {
    char key[MAX_VAR_KEY_LEN];
    char value[MAX_VAR_VALUE_LEN];
} core_variables[MAX_CORE_VARIABLES];

static int core_variable_count = 0;
static bool core_variables_dirty = false;

/* Track whether retro_run() has completed at least once.
 * Some cores (e.g. Dolphin) crash if retro_serialize_size() is called
 * before the core has fully initialized via its first retro_run(). */
static bool g_first_frame_run = false;

void core_variables_set(const char *key, const char *value) {
    /* Update existing variable if key matches */
    for (int i = 0; i < core_variable_count; i++) {
        if (strcmp(core_variables[i].key, key) == 0) {
            strncpy(core_variables[i].value, value, MAX_VAR_VALUE_LEN - 1);
            core_variables[i].value[MAX_VAR_VALUE_LEN - 1] = '\0';
            core_variables_dirty = true;
            return;
        }
    }
    /* Add new variable */
    if (core_variable_count < MAX_CORE_VARIABLES) {
        strncpy(core_variables[core_variable_count].key, key, MAX_VAR_KEY_LEN - 1);
        core_variables[core_variable_count].key[MAX_VAR_KEY_LEN - 1] = '\0';
        strncpy(core_variables[core_variable_count].value, value, MAX_VAR_VALUE_LEN - 1);
        core_variables[core_variable_count].value[MAX_VAR_VALUE_LEN - 1] = '\0';
        core_variable_count++;
        core_variables_dirty = true;
    }
}

void core_variables_clear(void) {
    core_variable_count = 0;
    core_variables_dirty = false;
}

/* Helper to log from libretro core */
static void core_log(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    char buf[4096];
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    switch (level) {
        case RETRO_LOG_DEBUG:
        case RETRO_LOG_INFO:
            LOGI("[core] %s", buf);
            break;
        case RETRO_LOG_WARN:
            LOGW("[core] %s", buf);
            break;
        case RETRO_LOG_ERROR:
            LOGE("[core] %s", buf);
            break;
    }
}

/* HW render callbacks wired into retro_hw_render_callback */
static uintptr_t hw_get_current_framebuffer(void) {
    if (g_core.hw_gl_ctx) {
        return hw_gl_get_framebuffer(g_core.hw_gl_ctx);
    }
    return 0;
}

static void *hw_get_proc_address(const char *sym) {
    return hw_gl_get_proc_address(sym);
}

/*
 * Environment callback - the core calls this to query/set frontend features.
 * We handle the subset of environment commands needed for basic operation.
 */
static bool environment_callback(unsigned cmd, void *data) {
    /* GET_SENSOR_INTERFACE (25 | RETRO_ENVIRONMENT_EXPERIMENTAL = 0x10019) must
     * be intercepted by its FULL value, BEFORE the base_cmd masking below. The
     * mask (cmd & 0xFFFF) maps 0x10019 -> 25, which collides with our local
     * RETRO_ENVIRONMENT_SET_CONTROLLER_PORT_DEVICE_ENV (25); that case returns
     * true WITHOUT populating the retro_sensor_interface out-param. The core
     * (Azahar/3DS) then stores the uninitialized {set_sensor_state,
     * get_sensor_input} function pointers and later calls the garbage
     * set_sensor_state(port, ENABLE, rate) -> hard crash on desktop (the
     * uninitialized stack happened to be zero on Android, hiding it there).
     * We don't expose host motion sensors, so decline cleanly: the core keeps
     * its null sensor callbacks and motion is simply disabled. (#1237, #1243) */
    if (cmd == (25u | 0x10000u)) {
        return false;
    }
    /* Mask off RETRO_ENVIRONMENT_EXPERIMENTAL (0x10000) so experimental
     * commands like GET_HW_RENDER_INTERFACE match our case labels. */
    unsigned base_cmd = cmd & 0xFFFF;
    switch (base_cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            *(bool *)data = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            unsigned *fmt = (unsigned *)data;
            video_set_pixel_format(*fmt);
            LOGI("Core set pixel format: %u", *fmt);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            /* Core declares its input layout — acknowledge */
            return true;

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            *(const char **)data = g_core.system_dir;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            *(const char **)data = g_core.save_dir;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY: {
            /* Dolphin and other cores use this for supplementary data (Sys/).
             * Point to system_dir which is where BIOS/system files live. */
            *(const char **)data = g_core.system_dir;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback *cb = (struct retro_log_callback *)data;
            cb->log = core_log;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *var = (struct retro_variable *)data;
            if (var->key) {
                for (int i = 0; i < core_variable_count; i++) {
                    if (strcmp(core_variables[i].key, var->key) == 0) {
                        var->value = core_variables[i].value;
                        return true;
                    }
                }
            }
            var->value = NULL;
            return false;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            *(bool *)data = core_variables_dirty;
            core_variables_dirty = false;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            /* Parse variable declarations and store default values.
             * Format: key="var_name", value="Description; opt1|opt2|opt3"
             * The first option after the semicolon is the default. */
            const struct retro_variable *vars = (const struct retro_variable *)data;
            if (vars) {
                for (; vars->key; vars++) {
                    if (!vars->value) continue;

                    /* Only store default if we don't already have a user-set value */
                    bool already_set = false;
                    for (int i = 0; i < core_variable_count; i++) {
                        if (strcmp(core_variables[i].key, vars->key) == 0) {
                            already_set = true;
                            break;
                        }
                    }
                    if (already_set) {
                        continue;
                    }

                    /* Extract default: find "; " then take text up to first '|' */
                    const char *semi = strstr(vars->value, "; ");
                    if (!semi) continue;

                    const char *defval = semi + 2;
                    const char *pipe = strchr(defval, '|');
                    char buf[MAX_VAR_VALUE_LEN];
                    if (pipe) {
                        size_t len = (size_t)(pipe - defval);
                        if (len >= MAX_VAR_VALUE_LEN) len = MAX_VAR_VALUE_LEN - 1;
                        memcpy(buf, defval, len);
                        buf[len] = '\0';
                    } else {
                        strncpy(buf, defval, MAX_VAR_VALUE_LEN - 1);
                        buf[MAX_VAR_VALUE_LEN - 1] = '\0';
                    }

                    if (buf[0]) {
                        core_variables_set(vars->key, buf);
                    }
                }
                LOGI("Parsed SET_VARIABLES: %d variables stored", core_variable_count);
                for (int vi = 0; vi < core_variable_count; vi++) {
                    LOGI("  opt %s = %s", core_variables[vi].key, core_variables[vi].value);
                }

                /* N64 renderer selection per platform:
                 * - macOS: Angrylion (software) — avoids GL compositing issues with GLideN64
                 * - Android: GLideN64 (GLES) — proven HW render path on Android
                 * - Other: leave core defaults (GLideN64 / GLES3) */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "rdp-plugin")) {
#ifdef __APPLE__
                            if (strstr(v3->value, "angrylion")) {
                                LOGI("N64 core detected with Angrylion support, switching to software renderer");
                                core_variables_set("mupen64plus-rdp-plugin", "angrylion");
                                core_variables_set("mupen64plus-rsp-plugin", "parallel");
                                core_variables_set("mupen64plus-angrylion-multithread", "all threads");
                                core_variables_set("mupen64plus-angrylion-sync", "Low");
                            } else {
                                LOGI("N64 core detected but Angrylion NOT available in this build");
                            }
#elif defined(__ANDROID__)
                            LOGI("N64 core detected, selecting GLideN64 (GLES) renderer");
                            core_variables_set("mupen64plus-rdp-plugin", "gliden64");
                            core_variables_set("mupen64plus-rsp-plugin", "hle");
#endif
                            break;
                        }
                    }
                }

                /* parallel_n64 renderer selection. This core exposes its own
                 * option keys ("parallel-n64-gfxplugin"/"parallel-n64-rspplugin"),
                 * NOT mupen64plus_next's "rdp-plugin", so the block above never
                 * fires for it. Same per-platform intent as mupen:
                 * - macOS: Angrylion (software) — avoids GLideN64 GL compositing
                 *   issues; only switched when the build advertises angrylion.
                 * - Android: GLideN64 (GLES) — proven HW render path.
                 * - Windows/Linux: leave default (gliden64) — confirmed working. */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "parallel-n64-gfxplugin")) {
#ifdef __APPLE__
                            if (strstr(v3->value, "angrylion")) {
                                LOGI("parallel_n64 detected with Angrylion support, switching to software renderer on macOS");
                                core_variables_set("parallel-n64-gfxplugin", "angrylion");
                                core_variables_set("parallel-n64-rspplugin", "parallel");
                            } else {
                                LOGI("parallel_n64 detected but Angrylion NOT advertised; leaving GLideN64 default on macOS");
                            }
#elif defined(__ANDROID__)
                            LOGI("parallel_n64 detected, selecting GLideN64 (GLES) renderer");
                            core_variables_set("parallel-n64-gfxplugin", "gliden64");
                            core_variables_set("parallel-n64-rspplugin", "hle");
#endif
                            break;
                        }
                    }
                }

#ifdef __ANDROID__
                /* PSP (PPSSPP) backend selection on Android: force
                 * Vulkan to sidestep the Adreno EGL TLS bug
                 * (#907 / #916) — see GET_PREFERRED_HW_RENDER. */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "ppsspp_backend")) {
                            LOGI("PPSSPP detected, forcing Vulkan backend on Android (#916)");
                            core_variables_set("ppsspp_backend", "Vulkan");
                            break;
                        }
                    }
                }
#endif

#ifdef __ANDROID__
                /* PS1 (Beetle PSX HW) renderer selection on Android:
                 * Force OpenGL (GLES) renderer to avoid Granite Vulkan crashes
                 * on Adreno GPUs. The GLES HW renderer uses the same proven
                 * EGL pbuffer + Vulkan presentation pipeline as N64/GLideN64. */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "beetle_psx_hw_renderer")) {
                            LOGI("Beetle PSX HW detected, forcing OpenGL (GLES) renderer on Android");
                            core_variables_set("beetle_psx_hw_renderer", "hardware_gl");
                            break;
                        }
                    }
                }
#endif

                /* Dolphin: disable separate CPU thread to avoid deadlock.
                 * In libretro mode, retro_run() is called synchronously by
                 * the frontend. With dual-core enabled, Dolphin's CPU thread
                 * and video thread deadlock ~24 frames in because retro_run()
                 * IS the video thread — there's no separate one. */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    bool is_dolphin = false;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "dolphin")) {
                            is_dolphin = true;
                            break;
                        }
                    }
                    if (is_dolphin) {
                        LOGI("Dolphin core detected, disabling dual-core CPU thread");
                        core_variables_set("dolphin_main_cpu_thread", "disabled");
                    }
                }

#ifndef __ANDROID__
                /* Azahar (3DS): force Vulkan on desktop. The WGL/OpenGL path
                 * crashes; route through the offscreen Vulkan path (#1234).
                 * Detected via the core's "citra_" option namespace. Android
                 * keeps GLES (works there), so this is desktop-only. */
                {
                    const struct retro_variable *v4 = (const struct retro_variable *)data;
                    for (; v4->key; v4++) {
                        if (v4->key && strstr(v4->key, "citra_graphics_api")) {
                            LOGI("Azahar core detected, forcing Vulkan graphics API");
                            core_variables_set("citra_graphics_api", "Vulkan");
                            break;
                        }
                    }
                }
#endif

                /* ScummVM: sensible default gamepad mapping for the audience
                 * (#859). The core's built-in joypad-to-key defaults are
                 * tuned for the libretro-scummvm dev environment, not for a
                 * casual player picking up a handheld to play Monkey Island.
                 *
                 * Mapping (per the PO + UX agent synthesis in the issue,
                 * with shoulders for the primary mouse buttons per the
                 * project owner's revision):
                 *
                 *   L1     → Left mouse button   (most-used in point-and-click)
                 *   R1     → Right mouse button  (second-most-used)
                 *   X      → period (skip dialogue / advance text)
                 *   Y      → F5 (open ScummVM main menu — save/load/options)
                 *   Start  → space (pause)
                 *   D-pad  → arrow keys
                 *
                 * A and B keep the core defaults (typically Return and
                 * Escape) — those are sensible for in-game prompts. Select
                 * stays unbound (reserved for a future virtual-keyboard
                 * toggle). The right analog stick driving the cursor is
                 * unchanged — it's a separate code path inside the core,
                 * not a mapper variable, and feels right today.
                 *
                 * Value strings are libretro-scummvm option tokens. If a
                 * future core build renames any of them this block will
                 * silently no-op (core_variables_set is a tolerant
                 * upsert) and we'll see the old behaviour again. */
                {
                    const struct retro_variable *v3 = (const struct retro_variable *)data;
                    bool is_scummvm = false;
                    for (; v3->key; v3++) {
                        if (v3->key && strstr(v3->key, "scummvm_mapper_")) {
                            is_scummvm = true;
                            break;
                        }
                    }
                    if (is_scummvm) {
                        LOGI("ScummVM core detected, applying default gamepad mapping (#859)");
                        core_variables_set("scummvm_mapper_l", "MOUSE_LEFT");
                        core_variables_set("scummvm_mapper_r", "MOUSE_RIGHT");
                        core_variables_set("scummvm_mapper_x", "RETROK_PERIOD");
                        core_variables_set("scummvm_mapper_y", "RETROK_F5");
                        core_variables_set("scummvm_mapper_start", "RETROK_SPACE");
                        core_variables_set("scummvm_mapper_up", "RETROK_UP");
                        core_variables_set("scummvm_mapper_down", "RETROK_DOWN");
                        core_variables_set("scummvm_mapper_left", "RETROK_LEFT");
                        core_variables_set("scummvm_mapper_right", "RETROK_RIGHT");
                    }
                }

                /* Clear the dirty flag after initial population. The flag is
                 * meant to signal *user-initiated* variable changes (so the
                 * core can re-read its options), but core_variables_set bumps
                 * it on every insert — including the 36 initial defaults the
                 * core itself just declared via SET_VARIABLES. Leaving it
                 * true causes retro_run's first frame to call back into
                 * GET_VARIABLE_UPDATE, get true, and trigger an options-
                 * reload before the core's engine thread has finished
                 * initialising state like _graphicsManager. ScummVM crashes
                 * deterministically here (#852) because refreshRetroSettings
                 * dereferences a NULL graphics manager. Other cores tolerate
                 * the spurious reload; ScummVM does not.
                 */
                core_variables_dirty = false;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            struct retro_message *msg = (struct retro_message *)data;
            LOGI("[core message] %s", msg->msg);
            return true;
        }

        case RETRO_ENVIRONMENT_SHUTDOWN:
            LOGI("Core requested shutdown");
            return true;

        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
            return true;

        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;

        case RETRO_ENVIRONMENT_SET_CONTROLLER_PORT_DEVICE_ENV:
            /* Core is setting controller type per port — acknowledge */
            return true;

        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            /* No rumble support — return false so core falls back gracefully */
            return false;

        case RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES: {
            /* Return bitmask of supported input device types. */
            *(uint64_t *)data = (1 << RETRO_DEVICE_JOYPAD) |
                                (1 << RETRO_DEVICE_ANALOG) |
                                (1 << RETRO_DEVICE_MOUSE) |
                                (1 << RETRO_DEVICE_KEYBOARD) |
                                (1 << RETRO_DEVICE_POINTER);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            /* Core describes available controller types per port.
             * Acknowledge so the core knows we received the info. */
            return true;

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            /* Core is informing us of a geometry change */
            struct retro_game_geometry *geom = (struct retro_game_geometry *)data;
            g_core.av_info.geometry.base_width = geom->base_width;
            g_core.av_info.geometry.base_height = geom->base_height;
            g_core.av_info.geometry.aspect_ratio = geom->aspect_ratio;
            LOGI("Geometry changed: %ux%u", geom->base_width, geom->base_height);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: {
            /* Report we support v0 core options (basic key/value) */
            *(unsigned *)data = 0;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
            /* Acknowledge achievement support query — we have RetroAchievements */
            return true;

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE: {
            /* Tell core both audio and video are enabled.
             * Bit 0 = video enabled, bit 1 = audio enabled. */
            *(int *)data = 3;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
            /* Core declares serialization quirks — acknowledge */
            return true;

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            /* Tell cores which HW render context we prefer.
             * Prefer Vulkan for:
             *  - Dolphin (GameCube/Wii) — zero-copy compositing via VkImage
             *  - PPSSPP (PSP) on Android — sidesteps the Adreno EGL TLS
             *    bug (#907 / #916) where pthread_exit's automatic
             *    eglReleaseThread crashes after PPSSPP's GLES
             *    context_destroy. Vulkan doesn't use EGL.
             *
             * For PPSSPP we also disable the Vulkan extension filter that
             * normally hides VK_EXT_subgroup_size_control from cores
             * (originally added for Granite/paraLLEl-RDP on N64). PPSSPP
             * needs it for its presentation pipeline.
             *
             * Other cores: GLES3 on Android, OpenGL Core on desktop. */
            const char *libname = g_core.system_info.library_name;
            bool prefer_vulkan = libname && strstr(libname, "dolphin") != NULL;
#ifndef __ANDROID__
            /* Azahar (3DS): the desktop WGL/OpenGL path crashes; use the
             * offscreen Vulkan path (#1234) instead. Android keeps GLES
             * (which works there). library_name is "Azahar" (older: "Citra"). */
            if (libname && (strstr(libname, "Azahar") || strstr(libname, "Citra"))) {
                prefer_vulkan = true;
            }
#endif
#ifdef __ANDROID__
            if (libname && strstr(libname, "PPSSPP") != NULL) {
                prefer_vulkan = true;
                /* The setter writes to a file-scope global the wrappers
                 * read from, so it works even if g_gpu_renderer is
                 * still NULL at this point (GET_PREFERRED_HW_RENDER
                 * fires during retro_load_game / retro_init, before
                 * the GPU renderer has been instantiated on Android). */
                gpu_renderer_set_extension_filter_enabled(g_gpu_renderer, false);
            }
#endif
            if (prefer_vulkan) {
                *(unsigned *)data = RETRO_HW_CONTEXT_VULKAN;
            } else {
#ifdef __ANDROID__
                *(unsigned *)data = RETRO_HW_CONTEXT_OPENGLES3;
#else
                *(unsigned *)data = RETRO_HW_CONTEXT_OPENGL_CORE;
#endif
            }
            LOGI("Reporting preferred HW render: %u (core: %s)", *(unsigned *)data,
                 libname ? libname : "unknown");
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
            /* Core signals it accesses the GPU context from multiple threads
             * (e.g. Granite's background pipeline compilation). Acknowledge so
             * the core knows the frontend is aware. */
            return true;

        case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE: {
            /* Core provides context negotiation interface so it can participate
             * in VkDevice creation (request extensions, features, or create the
             * device itself). Required by paraLLEl-RDP in mupen64plus-next. */
            const struct retro_hw_render_context_negotiation_interface_vulkan *iface =
                (const struct retro_hw_render_context_negotiation_interface_vulkan *)data;
            if (iface && iface->interface_type == RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN) {
                g_core.hw_vk_negotiation = iface;
                LOGI("Accepted Vulkan context negotiation interface (version=%u)",
                     iface->interface_version);
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            struct retro_hw_render_callback *cb = (struct retro_hw_render_callback *)data;
            LOGI("Core requests HW render context type: %u (v%u.%u)",
                 cb->context_type, cb->version_major, cb->version_minor);
            /* Accept Vulkan context type on all platforms */
            if (cb->context_type == RETRO_HW_CONTEXT_VULKAN) {
                g_core.hw_render_callback = *cb;
                /* Vulkan does not use get_current_framebuffer or get_proc_address */
                g_core.hw_render_callback.get_current_framebuffer = NULL;
                g_core.hw_render_callback.get_proc_address = NULL;
                cb->get_current_framebuffer = NULL;
                cb->get_proc_address = NULL;
                g_core.hw_render_enabled = true;
                /* Tell GPU renderer about Y-axis convention so it can flip
                 * the viewport when compositing HW-rendered frames. */
                if (g_gpu_renderer) {
                    gpu_renderer_set_hw_bottom_left_origin(g_gpu_renderer,
                        cb->bottom_left_origin);
                }
                LOGI("Accepted Vulkan HW render (type=%u, depth=%d, stencil=%d, "
                     "bottom_left_origin=%d, cache_context=%d)",
                     cb->context_type, cb->depth, cb->stencil,
                     cb->bottom_left_origin, cb->cache_context);
                return true;
            }
#ifdef __ANDROID__
            /* Accept GLES context types on Android (e.g. GLideN64) */
            if (cb->context_type == RETRO_HW_CONTEXT_OPENGLES2 ||
                cb->context_type == RETRO_HW_CONTEXT_OPENGLES3 ||
                cb->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION) {
                g_core.hw_render_callback = *cb;
                g_core.hw_render_callback.get_current_framebuffer = hw_get_current_framebuffer;
                g_core.hw_render_callback.get_proc_address = hw_get_proc_address;
                cb->get_current_framebuffer = hw_get_current_framebuffer;
                cb->get_proc_address = hw_get_proc_address;
                g_core.hw_render_enabled = true;
                LOGI("Accepted GLES HW render (type=%u, v%u.%u, depth=%d, stencil=%d)",
                     cb->context_type, cb->version_major, cb->version_minor,
                     cb->depth, cb->stencil);
                return true;
            }
#else
            /* Accept OpenGL context types on desktop (macOS, Linux, Windows) */
            if (cb->context_type == RETRO_HW_CONTEXT_OPENGL ||
                cb->context_type == RETRO_HW_CONTEXT_OPENGL_CORE) {
                g_core.hw_render_callback = *cb;
                /* Wire up our callbacks so the core can query them */
                g_core.hw_render_callback.get_current_framebuffer = hw_get_current_framebuffer;
                g_core.hw_render_callback.get_proc_address = hw_get_proc_address;
                /* Copy our wired-up callbacks back to the core's struct */
                cb->get_current_framebuffer = hw_get_current_framebuffer;
                cb->get_proc_address = hw_get_proc_address;
                g_core.hw_render_enabled = true;
                /* Tell GPU renderer about Y-axis convention for GL readback flip */
                if (g_gpu_renderer) {
                    gpu_renderer_set_hw_bottom_left_origin(g_gpu_renderer,
                        cb->bottom_left_origin);
                }
                LOGI("Accepted OpenGL HW render (type=%u, bottom_left_origin=%d, depth=%d, stencil=%d)",
                     cb->context_type, cb->bottom_left_origin, cb->depth, cb->stencil);
                return true;
            }
#endif
            /* Unsupported context type */
            g_core.hw_render_callback = *cb;
            g_core.hw_render_enabled = false;
            return false;
        }

        case RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE: {
            if (g_core.hw_render_enabled &&
                g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
                g_gpu_renderer) {
                /* Lazy init: if the GPU renderer exists but HW render isn't
                 * initialized yet, do it now. On desktop, the GPU renderer is
                 * created by the composable before core load, so HW render
                 * must be initialized when the core first requests the interface. */
                if (!gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
                    /* Pass negotiation interface if the core provided one */
                    if (g_core.hw_vk_negotiation) {
                        gpu_renderer_set_vk_negotiation(g_gpu_renderer,
                            g_core.hw_vk_negotiation);
                    }
                    if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
                        if (g_core.hw_render_callback.context_reset) {
                            g_core.hw_render_callback.context_reset();
                        }
                        gpu_renderer_mark_hw_context_reset_done(g_gpu_renderer);
                        LOGI("Vulkan HW render context initialized lazily "
                             "(on GET_HW_RENDER_INTERFACE)");
                    } else {
                        LOGE("Failed to init Vulkan HW render context lazily");
                        return false;
                    }
                }
                void *iface = gpu_renderer_hw_vulkan_get_interface(g_gpu_renderer);
                if (iface) {
                    *(const struct retro_hw_render_interface_vulkan **)data = iface;
                    LOGI("Returning Vulkan HW render interface to core");
                    return true;
                }
            }
            return false;
        }

        default: {
            /* Log unknown commands once to detect missing features */
            static uint64_t seen_cmds = 0;
            unsigned cmd_bit = cmd & 63;
            if (cmd < 64 && !(seen_cmds & (1ULL << cmd_bit))) {
                seen_cmds |= (1ULL << cmd_bit);
                LOGI("Unhandled env cmd: %u", cmd);
            } else if (cmd >= 64) {
                static uint64_t seen_cmds_hi = 0;
                unsigned cmd_bit_hi = (cmd - 64) & 63;
                if (cmd < 128 && !(seen_cmds_hi & (1ULL << cmd_bit_hi))) {
                    seen_cmds_hi |= (1ULL << cmd_bit_hi);
                    LOGI("Unhandled env cmd: %u", cmd);
                }
            }
            return false;
        }
    }
}

/* Resolve a symbol from the loaded core shared library */
#define LOAD_SYM(sym) do { \
    g_core.sym = (sym##_t)sp_dlsym(g_core.handle, #sym); \
    if (!g_core.sym) { \
        LOGE("Failed to load symbol: %s", #sym); \
        return -1; \
    } \
} while(0)

/* Pre-load MoltenVK so cores (e.g. Dolphin) that dlopen("libvulkan.dylib")
 * or call vkCreateInstance can find Vulkan function pointers.
 *
 * IMPORTANT: We load libMoltenVK.dylib (the ICD), NOT libvulkan.dylib
 * (the Vulkan loader). The Vulkan loader on macOS requires the
 * VK_KHR_portability_enumeration extension to be enabled in
 * vkCreateInstance — cores like Dolphin don't set this flag, causing
 * VK_ERROR_INCOMPATIBLE_DRIVER. MoltenVK loaded directly doesn't
 * have this restriction. */
static void preload_vulkan_library(void) {
#ifdef __APPLE__
    static bool tried = false;
    if (tried) return;
    tried = true;

    /* Log environment variables for diagnostics */
    const char *dyld_lib = getenv("DYLD_LIBRARY_PATH");
    const char *dyld_fallback = getenv("DYLD_FALLBACK_LIBRARY_PATH");
    LOGI("DYLD_LIBRARY_PATH=%s", dyld_lib ? dyld_lib : "(null)");
    LOGI("DYLD_FALLBACK_LIBRARY_PATH=%s", dyld_fallback ? dyld_fallback : "(null)");

    /* On macOS, cores like Dolphin dlopen("libvulkan.dylib") and call
     * vkCreateInstance themselves. The Vulkan loader requires the
     * VK_KHR_portability_enumeration flag, which cores don't set, causing
     * VK_ERROR_INCOMPATIBLE_DRIVER. To fix this, we create a temp dir
     * with libvulkan.dylib symlinked to MoltenVK, then set
     * DYLD_FALLBACK_LIBRARY_PATH so dlopen finds MoltenVK instead. */
    const char *mvk_paths[] = {
        "/opt/homebrew/lib/libMoltenVK.dylib",  /* Homebrew ARM */
        "/usr/local/lib/libMoltenVK.dylib",     /* Homebrew Intel */
    };
    const char *mvk_found = NULL;
    for (int i = 0; i < 2; i++) {
        if (access(mvk_paths[i], F_OK) == 0) {
            mvk_found = mvk_paths[i];
            break;
        }
    }
    if (mvk_found) {
        /* Tell Dolphin to load MoltenVK directly via its LIBVULKAN_PATH env var.
         * Dolphin checks this before trying system library paths. */
        setenv("LIBVULKAN_PATH", mvk_found, 1);
        LOGI("Set LIBVULKAN_PATH=%s", mvk_found);

        /* Create shim dir with both versioned and unversioned symlinks.
         * Dolphin tries libvulkan.1.dylib first, then libvulkan.dylib. */
        const char *shim_dir = "/tmp/spela-vulkan";
        mkdir(shim_dir, 0755);
        const char *shim_names[] = { "libvulkan.dylib", "libvulkan.1.dylib" };
        for (int i = 0; i < 2; i++) {
            char shim_path[256];
            snprintf(shim_path, sizeof(shim_path), "%s/%s", shim_dir, shim_names[i]);
            unlink(shim_path);
            if (symlink(mvk_found, shim_path) == 0) {
                LOGI("Created shim: %s -> %s", shim_path, mvk_found);
            }
        }

        /* Set DYLD_FALLBACK_LIBRARY_PATH with shim dir FIRST */
        char fallback[512];
        snprintf(fallback, sizeof(fallback), "%s:/opt/homebrew/lib:/usr/local/lib", shim_dir);
        setenv("DYLD_FALLBACK_LIBRARY_PATH", fallback, 1);
        LOGI("Set DYLD_FALLBACK_LIBRARY_PATH=%s", fallback);

        /* Pre-load MoltenVK with RTLD_GLOBAL so symbols are available */
        sp_lib_t h = sp_dlopen(mvk_found, RTLD_NOW | RTLD_GLOBAL);
        if (h) {
            LOGI("Pre-loaded MoltenVK: %s", mvk_found);
        }
    } else {
        LOGW("MoltenVK not found at any known path");
    }
#endif
}

/* Load the libretro core from the given shared library path */
static int core_load(const char *path) {
    g_first_frame_run = false;
    preload_vulkan_library();
    if (g_core.handle) {
        LOGW("Core already loaded, unloading first");
        if (g_core.game_loaded) {
            g_core.retro_unload_game();
            g_core.game_loaded = false;
        }
        if (g_core.initialized) {
            g_core.retro_deinit();
            g_core.initialized = false;
        }
        sp_dlclose(g_core.handle);
        g_core.handle = NULL;
    }

    LOGI("Loading core: %s", path);
    g_core.handle = sp_dlopen(path, RTLD_LAZY);
    if (!g_core.handle) {
        LOGE("dlopen failed: %s", sp_dlerror());
        return -1;
    }

#ifdef __ANDROID__
    /* Pass the JavaVM pointer to cores that need it for thread attachment.
     * dlopen() doesn't trigger JNI_OnLoad like System.loadLibrary() would,
     * so we must do this manually.
     *
     * NOTE: Do NOT call JNI_OnLoad generically — cores like Dolphin export
     * JNI_OnLoad but expect app-specific Java classes (e.g.
     * org.dolphinemu.dolphinemu.NativeLibrary) which don't exist in our app.
     * Instead, use core-specific symbol lookups for cores that need the JVM. */
    if (g_jvm) {
        /* Play! PS2 core: call Framework::CJavaVM::SetJavaVM(JavaVM*) directly.
         * Play! stores a static JavaVM* pointer via this method, which its
         * PS2VM worker thread needs for JNI AttachCurrentThread(). */
        typedef void (*SetJavaVM_fn)(JavaVM *);
        SetJavaVM_fn set_jvm = (SetJavaVM_fn)sp_dlsym(g_core.handle,
            "_ZN9Framework7CJavaVM9SetJavaVMEP7_JavaVM");
        if (set_jvm) {
            LOGI("Play! core detected, passing JavaVM to CJavaVM::SetJavaVM");
            set_jvm(g_jvm);
        }
    }
#endif

    LOAD_SYM(retro_init);
    LOAD_SYM(retro_deinit);
    LOAD_SYM(retro_api_version);
    LOAD_SYM(retro_get_system_info);
    LOAD_SYM(retro_get_system_av_info);
    LOAD_SYM(retro_set_environment);
    LOAD_SYM(retro_set_video_refresh);
    LOAD_SYM(retro_set_audio_sample);
    LOAD_SYM(retro_set_audio_sample_batch);
    LOAD_SYM(retro_set_input_poll);
    LOAD_SYM(retro_set_input_state);
    LOAD_SYM(retro_set_controller_port_device);
    LOAD_SYM(retro_reset);
    LOAD_SYM(retro_run);
    LOAD_SYM(retro_load_game);
    LOAD_SYM(retro_unload_game);
    LOAD_SYM(retro_serialize_size);
    LOAD_SYM(retro_serialize);
    LOAD_SYM(retro_unserialize);
    LOAD_SYM(retro_get_memory_data);
    LOAD_SYM(retro_get_memory_size);

    /* Cheat functions are optional — not all cores implement them. */
    g_core.retro_cheat_reset = (retro_cheat_reset_t)sp_dlsym(g_core.handle, "retro_cheat_reset");
    g_core.retro_cheat_set   = (retro_cheat_set_t)sp_dlsym(g_core.handle, "retro_cheat_set");

    /* Get system info BEFORE registering callbacks — the environment callback
     * may query the core name (e.g. GET_PREFERRED_HW_RENDER uses it to choose
     * Vulkan vs GLES). retro_get_system_info is a pure info function that can
     * be called at any time without initialization. */
    g_core.retro_get_system_info(&g_core.system_info);
    LOGI("Core: %s v%s", g_core.system_info.library_name, g_core.system_info.library_version);
    LOGI("  valid_extensions: %s, need_fullpath: %d, block_extract: %d",
         g_core.system_info.valid_extensions ? g_core.system_info.valid_extensions : "(none)",
         g_core.system_info.need_fullpath, g_core.system_info.block_extract);

    /* Register callbacks before retro_init */
    g_core.retro_set_environment(environment_callback);
    g_core.retro_set_video_refresh(video_refresh_callback);
    g_core.retro_set_audio_sample(audio_sample_callback);
    g_core.retro_set_audio_sample_batch(audio_sample_batch_callback);
    g_core.retro_set_input_poll(input_poll_callback);
    g_core.retro_set_input_state(input_state_callback);

    LOGI("Core loaded successfully, API version: %u", g_core.retro_api_version());

    return 0;
}

#ifndef SPELA_CORE_HOST  /* JNI bindings are excluded from the native host build */
/* === JNI BINDINGS === */

#define JNI_FUNC(ret, name) JNIEXPORT ret JNICALL Java_com_spela_player_libretro_LibretroJni_##name

JNI_FUNC(jboolean, nativeLoadCore)(JNIEnv *env, jobject thiz, jstring corePath) {
    /* Capture JavaVM pointer from JNIEnv - needed for cores like Play! PS2
     * that spawn worker threads, and for achievements deinit on coroutine
     * dispatcher threads that need a valid JNIEnv. */
    if (!g_jvm) {
        (*env)->GetJavaVM(env, &g_jvm);
    }

    const char *path = (*env)->GetStringUTFChars(env, corePath, NULL);
    if (!path) {
        /* GetStringUTFChars returns NULL on OOM; passing NULL to dlopen
         * is platform-defined (Android may crash). Fail closed. */
        return JNI_FALSE;
    }
    int result = core_load(path);
    (*env)->ReleaseStringUTFChars(env, corePath, path);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(void, nativeInit)(JNIEnv *env, jobject thiz) {
    if (!g_core.handle) return;
    /* Init video/input BEFORE retro_init() because the core may call
     * SET_PIXEL_FORMAT during retro_init(). video_init() resets the
     * pixel format to the default (0RGB1555), so it must run first. */
    video_init();
    input_init();
    g_core.retro_init();
    g_core.initialized = true;

    /* Re-bind callbacks after retro_init(). Some cores (e.g. Beetle PSX HW
     * in Vulkan mode) reset their internal callback storage during retro_init(),
     * clearing the video_refresh pointer set during core_load(). Re-binding
     * ensures all callbacks are valid before retro_load_game()/retro_run(). */
    g_core.retro_set_video_refresh(video_refresh_callback);
    g_core.retro_set_audio_sample(audio_sample_callback);
    g_core.retro_set_audio_sample_batch(audio_sample_batch_callback);
    g_core.retro_set_input_poll(input_poll_callback);
    g_core.retro_set_input_state(input_state_callback);

    LOGI("Core initialized");
}

JNI_FUNC(jboolean, nativeLoadGame)(JNIEnv *env, jobject thiz, jstring gamePath) {
    if (!g_core.handle || !g_core.initialized) return JNI_FALSE;

    const char *path = (*env)->GetStringUTFChars(env, gamePath, NULL);
    if (!path) {
        /* GetStringUTFChars returns NULL on OOM. Bail rather than calling
         * fopen(NULL) / passing NULL into retro_load_game. */
        return JNI_FALSE;
    }

    struct retro_game_info game_info = {0};
    game_info.path = path;
    game_info.data = NULL;
    game_info.size = 0;
    game_info.meta = NULL;

    /* If core doesn't need fullpath, load the ROM into memory */
    if (!g_core.system_info.need_fullpath) {
        FILE *f = fopen(path, "rb");
        if (f) {
            fseek(f, 0, SEEK_END);
            game_info.size = ftell(f);
            fseek(f, 0, SEEK_SET);

            void *buf = malloc(game_info.size);
            if (buf) {
                /* Verify fread fully populated the buffer. A short read
                 * (truncated download, transient I/O error) would otherwise
                 * silently pass a partial buffer to retro_load_game and the
                 * core would interpret the tail as garbage memory. */
                size_t read_bytes = fread(buf, 1, game_info.size, f);
                if (read_bytes != game_info.size) {
                    LOGE("Short read from ROM: got %zu of %zu bytes",
                         read_bytes, game_info.size);
                    free(buf);
                    fclose(f);
                    (*env)->ReleaseStringUTFChars(env, gamePath, path);
                    return JNI_FALSE;
                }
                game_info.data = buf;
            }
            fclose(f);
        } else {
            LOGE("Failed to open ROM: %s", path);
            (*env)->ReleaseStringUTFChars(env, gamePath, path);
            return JNI_FALSE;
        }
    }

    bool loaded = g_core.retro_load_game(&game_info);

    /* Free ROM buffer if we allocated one */
    if (game_info.data) {
        free((void *)game_info.data);
    }

    (*env)->ReleaseStringUTFChars(env, gamePath, path);

    if (loaded) {
        g_core.game_loaded = true;
        g_core.retro_get_system_av_info(&g_core.av_info);
        audio_init(g_core.av_info.timing.sample_rate);
        LOGI("Game loaded: %ux%u (max %ux%u, aspect %.4f) @ %.2f fps, audio %.1f Hz",
             g_core.av_info.geometry.base_width,
             g_core.av_info.geometry.base_height,
             g_core.av_info.geometry.max_width,
             g_core.av_info.geometry.max_height,
             g_core.av_info.geometry.aspect_ratio,
             g_core.av_info.timing.fps,
             g_core.av_info.timing.sample_rate);

        /* Set controller port device type for all ports.
         * Cores like Dolphin require this handshake to enable input.
         * RETRO_DEVICE_JOYPAD (1) is the standard digital gamepad.
         * This must be called after retro_load_game(). */
        if (g_core.retro_set_controller_port_device) {
            for (unsigned port = 0; port < 4; port++) {
                g_core.retro_set_controller_port_device(port, RETRO_DEVICE_JOYPAD);
            }
            LOGI("Set controller port device (JOYPAD) for ports 0-3");
        }

        /* Initialize Vulkan HW render context if the core requested it.
         * On desktop, the GPU renderer is created before core load (offscreen mode),
         * so we must reinitialize the Vulkan context using the core's v2 negotiation
         * interface. This ensures core and frontend share the same VkInstance/VkDevice.
         * Without this reinit, nativeRun() skips all frames (HW render not active)
         * and retro_run() never executes — a deadlock. */
        if (g_core.hw_render_enabled &&
            g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
            g_gpu_renderer) {
            if (g_core.hw_vk_negotiation) {
                gpu_renderer_set_vk_negotiation(g_gpu_renderer,
                    g_core.hw_vk_negotiation);
                /* Reinit Vulkan context so create_instance/create_device use the
                 * negotiation callbacks — core and frontend share VkInstance. */
                if (!gpu_renderer_reinit_vulkan(g_gpu_renderer)) {
                    LOGE("Failed to reinit Vulkan context with negotiation");
                }
            }
            if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
                if (g_core.hw_render_callback.context_reset) {
                    g_core.hw_render_callback.context_reset();
                }
                gpu_renderer_mark_hw_context_reset_done(g_gpu_renderer);
                LOGI("Vulkan HW render context initialized after game load");
            } else {
                LOGE("Failed to init Vulkan HW render context after game load");
            }
        }

        /* Initialize OpenGL/GLES HW render context if the core requested it.
         * On Android, this only applies to GLES context types (GLideN64);
         * Vulkan HW render (paraLLEl-RDP) is initialized later in gpuInit. */
        if (g_core.hw_render_enabled &&
            g_core.hw_render_callback.context_type != RETRO_HW_CONTEXT_VULKAN) {
            g_core.hw_gl_ctx = hw_gl_create();
            g_core.hw_gl_was_used = (g_core.hw_gl_ctx != NULL);
            if (g_core.hw_gl_ctx) {
                unsigned vmaj = g_core.hw_render_callback.version_major;
                unsigned vmin = g_core.hw_render_callback.version_minor;
#ifndef __ANDROID__
                if (vmaj == 0) { vmaj = 3; vmin = 2; } /* desktop default: GL 3.2 */
#else
                if (vmaj == 0) { vmaj = 3; vmin = 0; } /* Android default: GLES 3.0 */
#endif
                if (hw_gl_init(g_core.hw_gl_ctx, vmaj, vmin,
                               g_core.hw_render_callback.depth,
                               g_core.hw_render_callback.stencil)) {
                    /* Resize FBO to match game geometry */
                    hw_gl_resize_fbo(g_core.hw_gl_ctx,
                                     g_core.av_info.geometry.max_width > 0
                                         ? g_core.av_info.geometry.max_width
                                         : g_core.av_info.geometry.base_width,
                                     g_core.av_info.geometry.max_height > 0
                                         ? g_core.av_info.geometry.max_height
                                         : g_core.av_info.geometry.base_height);
                    /* Call the core's context_reset so it can create GPU resources.
                     * This triggers rglgen_resolve_symbols() inside the core, which
                     * resolves GL 2.0+ functions via our get_proc_address callback. */
                    if (g_core.hw_render_callback.context_reset) {
                        hw_gl_make_current(g_core.hw_gl_ctx);
                        g_core.hw_render_callback.context_reset();
                    }

                    /* Rebind GL symbols in the core's GOT AFTER context_reset.
                     * context_reset triggers lazy symbol resolution — the core
                     * calls GL functions which resolve the lazy symbol pointers
                     * in __DATA.__la_symbol_ptr. By rebinding after, we overwrite
                     * the resolved OpenGL.framework addresses with our wrappers.
                     * This catches GL 1.x functions (glDrawBuffer, glClear, etc.)
                     * that are directly linked, not going through rglgen. */
                    hw_gl_rebind_gl_symbols();

                    /* Release context so the emulation thread can acquire it.
                     * On Android, nativeLoadGame runs on the UI/loading thread
                     * but retro_run() executes on SpelaEmulation thread.
                     * EGL requires releasing before another thread can bind. */
                    hw_gl_release_current(g_core.hw_gl_ctx);

                    LOGI("OpenGL HW render context initialized for core");
                } else {
                    LOGE("Failed to init OpenGL HW render context");
                    hw_gl_destroy(g_core.hw_gl_ctx);
                    g_core.hw_gl_ctx = NULL;
                    g_core.hw_render_enabled = false;
                }
            } else {
                LOGE("Failed to create HW GL context struct");
                g_core.hw_render_enabled = false;
            }
        }
    } else {
        LOGE("retro_load_game failed");
    }

    return loaded ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(void, nativeRun)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    /* Vulkan HW render: skip frames until the Vulkan HW context is active AND
     * the core's context_reset() has completed. hw_render_active flips true at
     * the end of gpu_renderer_hw_vulkan_init(), which runs BEFORE the caller
     * invokes context_reset() — gating on active alone let the emulation thread
     * run a frame in that gap, before the core built its GPU backend, crashing
     * PSP/PPSSPP on its first GE display list (#925/#1270). */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
        (!g_gpu_renderer || !gpu_renderer_is_hw_render_active(g_gpu_renderer) ||
         !gpu_renderer_is_hw_context_reset_done(g_gpu_renderer))) {
        static int vk_skip_count = 0;
        if (++vk_skip_count <= 5 || vk_skip_count % 300 == 0) {
            LOGI("VK HW: skipping frame %d (renderer=%p active=%d ctxReset=%d)",
                 vk_skip_count, (void*)g_gpu_renderer,
                 g_gpu_renderer ? gpu_renderer_is_hw_render_active(g_gpu_renderer) : -1,
                 g_gpu_renderer ? gpu_renderer_is_hw_context_reset_done(g_gpu_renderer) : -1);
        }
        return;
    }
#ifdef __ANDROID__
    /* GLES HW render: skip frames until GPU renderer is ready for presentation.
     * The GLES context is created during loadGame() but the Vulkan presentation
     * surface isn't available until surfaceCreated() fires and gpuInit() runs.
     * Running the core now would produce frames that can't be displayed. */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx &&
        (!g_gpu_renderer || !gpu_renderer_is_active(g_gpu_renderer))) {
        return;
    }
#endif
    /* GL/GLES HW render: make context current before retro_run */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
#ifndef __ANDROID__
        hw_gl_debug_reset_frame();
#endif
    }
    g_core.retro_run();
    g_first_frame_run = true;
    /* Release GL context after retro_run() so subsequent GPU operations
     * (nativeGpuRenderToBgra) aren't affected by an active GL context */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_release_current(g_core.hw_gl_ctx);
    }
    achievements_do_frame();
}

JNI_FUNC(void, nativeReset)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    g_core.retro_reset();
}

JNI_FUNC(void, nativeUnloadGame)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    /* Vulkan HW render teardown is split around retro_unload_game: fire
     * context_destroy first (cores expect it before unload), but only
     * destroy OUR VkDevice after retro_unload_game returns. The core's GPU
     * threads (e.g. Azahar's Vulkan::Scheduler worker, Granite's pipeline
     * compilers) are joined inside retro_unload_game / Core shutdown — for
     * Azahar, context_destroy doesn't stop the scheduler at all (it only
     * releases the renderer on the GL path). Destroying the device earlier
     * is a use-after-free on the dispatchable queue handle: the worker's
     * next vkQueueSubmit jumps through freed dispatch-table memory
     * (observed as a garbage-RIP SIGSEGV saving state in OoT 3D on Linux).
     * RetroArch likewise destroys its Vulkan context only after core
     * deinit. The old 200ms "grace period" here was a workaround for this
     * same race and is superseded by the correct ordering. */
    bool vk_deinit_pending = false;
    if (g_core.hw_render_enabled && g_gpu_renderer &&
        gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
        /* Wait for all GPU work to finish before core destroys its resources */
        gpu_renderer_wait_idle(g_gpu_renderer);
        if (g_core.hw_render_callback.context_destroy) {
            g_core.hw_render_callback.context_destroy();
        }
        vk_deinit_pending = true;
    }
    /* Tear down OpenGL/GLES HW render context before unloading game */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        if (g_core.hw_render_callback.context_destroy) {
            /* #907 — bind the context to SpelaEmulation so PPSSPP's
             * GLRenderManager has a current context to do its
             * teardown work against. Without this, PPSSPP's render
             * thread blocks indefinitely waiting for a binding it
             * can't acquire, and the whole teardown hangs.
             *
             * After context_destroy returns, Adreno's driver state
             * for our binding is corrupted (PPSSPP's render thread
             * released its own binding in a way that confuses the
             * driver). We DON'T attempt to release SpelaEmulation's
             * binding here — see hw_gl_deinit and the Kotlin-side
             * thread-parking trick: the thread is parked forever
             * after this, never exits, so pthread_exit's automatic
             * eglReleaseThread (which would crash trying to clean
             * up the corrupted binding) never runs. */
            hw_gl_make_current(g_core.hw_gl_ctx);
            g_core.hw_render_callback.context_destroy();
        }
        hw_gl_destroy(g_core.hw_gl_ctx);
        g_core.hw_gl_ctx = NULL;
        g_core.hw_render_enabled = false;
        LOGI("OpenGL HW render context destroyed");
    }
    g_core.retro_unload_game();
    g_core.game_loaded = false;
    if (vk_deinit_pending) {
        /* Core threads are joined now (see comment above) — safe to
         * destroy our Vulkan device. */
        gpu_renderer_wait_idle(g_gpu_renderer);
        gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
        g_core.hw_render_enabled = false;
        LOGI("Vulkan HW render context destroyed");
    }
    audio_deinit();
    /* Clear the displayed frame so the next game doesn't briefly show this
     * game's last frame (#1236). The next game's first video_refresh resets
     * the dimensions. */
    video_clear_frame();
    LOGI("Game unloaded");
}

JNI_FUNC(void, nativeDeinit)(JNIEnv *env, jobject thiz) {
    if (!g_core.initialized && !g_core.game_loaded) return;

    /* Mirror RetroArch's runloop_event_deinit_core (runloop.c:4059-4136) —
     * single ordered teardown on the same thread that ran retro_run. The
     * caller (AndroidLibretroController.stop) now hands off to the
     * emulation thread before reaching here, so the libretro contract
     * "all retro_* entry points on the same thread" is respected.
     *
     * Order:
     *   1. context_destroy callback (free HW render context)
     *   2. retro_unload_game
     *   3. retro_deinit
     *   4. video_deinit / input_deinit / audio_deinit
     *   5. destroy our hw_gl_ctx wrapper
     *   6. dlclose
     *
     * Previously this had per-flag skip-retro_deinit / skip-dlclose
     * branches added to dodge a Play! PS2 SIGSEGV. Strong evidence (see
     * RetroArch research and #724 investigation) suggests that crash was
     * a thread-mismatch artifact — Play!'s GL cleanup running on a thread
     * whose EGL context was different from the one that created the
     * resources. With teardown moved onto the emulation thread, the
     * standard order should be safe. */

    /* Step 1: HW render context_destroy. Vulkan only fires the callback
     * here — destroying our VkDevice is deferred to after retro_deinit,
     * when the core's GPU threads are joined (see nativeUnloadGame for the
     * full rationale; destroying earlier is a use-after-free race with the
     * core's submit threads). The GL path just fires the callback once.
     * Cores may have already destroyed their own context during emulation
     * (we observe hw_gl_ctx going NULL via the env callback in some
     * flows), so guard each branch. */
    bool vk_deinit_pending = false;
    if (g_core.hw_render_enabled && g_gpu_renderer &&
        gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
        gpu_renderer_wait_idle(g_gpu_renderer);
        if (g_core.hw_render_callback.context_destroy) {
            g_core.hw_render_callback.context_destroy();
        }
        vk_deinit_pending = true;
    } else if (g_core.hw_gl_ctx) {
        /* #907 — bind so PPSSPP's GLRenderManager has a current
         * context for cleanup. We rely on Kotlin-side thread-parking
         * to avoid the post-corruption pthread_exit crash; see the
         * comment in nativeUnloadGame. */
        hw_gl_make_current(g_core.hw_gl_ctx);
        if (g_core.hw_render_callback.context_destroy) {
            g_core.hw_render_callback.context_destroy();
        }
    }

    /* Step 2: retro_unload_game */
    if (g_core.game_loaded) {
        g_core.retro_unload_game();
        g_core.game_loaded = false;
    }

    /* Step 3: retro_deinit */
    if (g_core.initialized) {
        /* Cores like Play! flush a final frame from inside retro_deinit
         * (CGSH_OpenGL_Libretro::Release → MailBox flush → FlipImpl →
         * video_cb), and the data they pass references GS-handler
         * state that's already partially released. Drop frames from
         * here on so video_refresh_callback's memcpy doesn't deref
         * freed memory. (#916) */
        video_set_shutting_down();
        g_core.retro_deinit();
        g_core.initialized = false;
    }

    /* Step 3b: core threads are joined now — safe to destroy our Vulkan
     * device (see Step 1 comment). */
    if (vk_deinit_pending) {
        gpu_renderer_wait_idle(g_gpu_renderer);
        gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
        g_core.hw_render_enabled = false;
    }

    /* Step 4: subsystem teardown */
    video_deinit();
    input_deinit();
    audio_deinit();

    /* Step 5: destroy our GL context wrapper after the core has
     * detached from it via context_destroy. */
    if (g_core.hw_gl_ctx) {
        hw_gl_destroy(g_core.hw_gl_ctx);
        g_core.hw_gl_ctx = NULL;
    }
    g_core.hw_vk_negotiation = NULL;
    g_core.hw_render_enabled = false;
    memset(&g_core.hw_render_callback, 0, sizeof(g_core.hw_render_callback));

    /* Step 6: dlclose. RetroArch always dlcloses (runloop.c:3881) and we
     * now do too. The Android-only skip-for-HW-cores guard previously
     * here was a remnant of the Play! / Granite teardown crashes that
     * the same-thread retro_deinit move in PR #736 actually fixed.
     * Verified clean for Play! (PS2), mupen64plus_next (N64), and
     * Dolphin (GameCube) on AYN Thor in #786 — exit-and-relaunch in the
     * same process, no SIGSEGV, no Scudo "invalid chunk state". */
    if (g_core.handle) {
        sp_dlclose(g_core.handle);
        g_core.handle = NULL;
    }

    /* Step 7: run a GPU deinit that arrived while the core was still alive
     * (emulation screen onDispose racing this teardown) — see
     * nativeGpuDeinit. The core and its GPU threads are gone now. */
    if (g_gpu_deinit_deferred) {
        g_gpu_deinit_deferred = false;
        do_gpu_deinit();
    }

    /* Clear every retro_* function pointer in g_core. After dlclose the
     * code those pointers reference is unmapped, but the bytes in the
     * pointer slots still look non-NULL — every JNI entry that guards
     * with "if (g_core.retro_xxx)" then dereferences garbage. Pre-fix,
     * this surfaced as a crash at nativeSetControllerPortDevice when
     * the user exited a ScummVM game and started a new one in the same
     * process: the game-launch path calls setControllerPortDevice(0,
     * MOUSE) BEFORE the next core's loadCore rebuilds these slots. See
     * the #852 follow-up. */
    g_core.retro_init = NULL;
    g_core.retro_deinit = NULL;
    g_core.retro_api_version = NULL;
    g_core.retro_get_system_info = NULL;
    g_core.retro_get_system_av_info = NULL;
    g_core.retro_set_environment = NULL;
    g_core.retro_set_video_refresh = NULL;
    g_core.retro_set_audio_sample = NULL;
    g_core.retro_set_audio_sample_batch = NULL;
    g_core.retro_set_input_poll = NULL;
    g_core.retro_set_input_state = NULL;
    g_core.retro_set_controller_port_device = NULL;
    g_core.retro_reset = NULL;
    g_core.retro_run = NULL;
    g_core.retro_load_game = NULL;
    g_core.retro_unload_game = NULL;
    g_core.retro_serialize_size = NULL;
    g_core.retro_serialize = NULL;
    g_core.retro_unserialize = NULL;
    g_core.retro_get_memory_data = NULL;
    g_core.retro_get_memory_size = NULL;
    g_core.retro_cheat_reset = NULL;
    g_core.retro_cheat_set = NULL;

    LOGI("Core deinitialized");
}

JNI_FUNC(jlong, nativeSerializeSize)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return 0;
    /* Don't call retro_serialize_size before the core has run at least one frame.
     * Some cores (e.g. Dolphin) boot asynchronously and crash if queried too early. */
    if (!g_first_frame_run) return 0;
    return (jlong)g_core.retro_serialize_size();
}

/* True once retro_run() has returned at least once for the currently
 * loaded core. EmulationViewModel polls this after start() to gate
 * post-launch operations (save-state probe, deferred auto-load) that
 * are unsafe before the core has ticked one frame — replaces the
 * conservative fixed delay that was tuned for Dolphin's worst case.
 * See #737. */
JNI_FUNC(jboolean, nativeFirstFrameRun)(JNIEnv *env, jobject thiz) {
    return g_first_frame_run ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jbyteArray, nativeSerialize)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return NULL;

    if (g_gpu_renderer) gpu_renderer_check_hw_interface(g_gpu_renderer, "before retro_serialize");

    size_t size = g_core.retro_serialize_size();
    if (size == 0) return NULL;

    void *buf = malloc(size);
    if (!buf) return NULL;

    if (!g_core.retro_serialize(buf, size)) {
        free(buf);
        return NULL;
    }
    if (g_gpu_renderer) gpu_renderer_check_hw_interface(g_gpu_renderer, "after retro_serialize");

    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (jbyte *)buf);
    }
    free(buf);
    return result;
}

/* nativeSerializeToFile writes the libretro save state directly to the
 * given filesystem path without ever crossing the JNI boundary as a
 * jbyteArray. Returns the number of bytes written, or -1 on failure.
 * Used by the manual-save path to avoid a 30–50 MB Java-heap allocation
 * for cores like Dolphin (#798).
 */
JNI_FUNC(jlong, nativeSerializeToFile)(JNIEnv *env, jobject thiz, jstring path) {
    if (!g_core.game_loaded || !path) return -1;

    size_t size = g_core.retro_serialize_size();
    if (size == 0) return -1;

    void *buf = malloc(size);
    if (!buf) return -1;

    if (!g_core.retro_serialize(buf, size)) {
        free(buf);
        return -1;
    }

    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) {
        free(buf);
        return -1;
    }

    FILE *f = fopen(cpath, "wb");
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    if (!f) {
        free(buf);
        return -1;
    }

    size_t written = fwrite(buf, 1, size, f);
    int closed = fclose(f);
    free(buf);

    if (written != size || closed != 0) {
        return -1;
    }
    return (jlong)size;
}

/* nativeUnserializeFromFile reads a libretro save state from the given
 * filesystem path and applies it via retro_unserialize without ever
 * crossing the JNI boundary as a jbyteArray. Returns JNI_TRUE on
 * success. Used by the auto-load and slot-load paths to avoid a
 * 90+ MB Java-heap allocation on Android (#798).
 */
JNI_FUNC(jboolean, nativeUnserializeFromFile)(JNIEnv *env, jobject thiz, jstring path) {
    if (!g_core.game_loaded || !path) return JNI_FALSE;

    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) return JNI_FALSE;

    FILE *f = fopen(cpath, "rb");
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    if (!f) return JNI_FALSE;

    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return JNI_FALSE; }
    long sz = ftell(f);
    if (sz < 0) { fclose(f); return JNI_FALSE; }
    if (fseek(f, 0, SEEK_SET) != 0) { fclose(f); return JNI_FALSE; }

    void *buf = malloc((size_t)sz);
    if (!buf) { fclose(f); return JNI_FALSE; }

    size_t read = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    if (read != (size_t)sz) {
        free(buf);
        return JNI_FALSE;
    }

    /* Same context-current dance as nativeUnserialize — Citra/Azahar
     * reinitialises GLES inside retro_unserialize. Without a current
     * context, glGetString returns NULL and the core crashes. Safe
     * because unserialize runs on the emulation thread via the queue. */
    bool made_current = false;
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
        made_current = true;
    }

    bool ok = g_core.retro_unserialize(buf, (size_t)sz);
    free(buf);

    if (made_current) {
        hw_gl_release_current(g_core.hw_gl_ctx);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jboolean, nativeUnserialize)(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!g_core.game_loaded || !data) return JNI_FALSE;

    /* Ensure GLES context is current before unserialize. Some cores (e.g. Citra/Azahar)
     * reinitialize the OpenGL renderer during save state load (Core::System::Init →
     * RendererOpenGL → glGetString). Without a current context, glGetString returns NULL
     * and the core crashes. This is safe because unserialize runs on the emulation thread
     * via the queue, which is the only thread that binds this context. */
    bool made_current = false;
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
        made_current = true;
    }

    jsize size = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buf) {
        if (made_current) hw_gl_release_current(g_core.hw_gl_ctx);
        return JNI_FALSE;
    }

    if (g_gpu_renderer) gpu_renderer_check_hw_interface(g_gpu_renderer, "before retro_unserialize");
    bool result = g_core.retro_unserialize(buf, (size_t)size);
    if (g_gpu_renderer) gpu_renderer_check_hw_interface(g_gpu_renderer, "after retro_unserialize");
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);

    if (made_current) {
        hw_gl_release_current(g_core.hw_gl_ctx);
    }

    return result ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jbyteArray, nativeGetVideoFrame)(JNIEnv *env, jobject thiz) {
    /* #1044: hold video_lock for the read-pointer + size + memcpy
     * triple so the emulation thread's video_refresh_callback (which
     * may realloc frame_buffer on dimension change) cannot interleave
     * and leave the JNI side reading a freed pointer.
     *
     * NewByteArray is called BEFORE acquiring the lock — JNI primitive
     * allocation can block on the GC, and blocking the GC while the
     * emulation thread is parked on video_lock would deadlock if the
     * GC needs to enter native frames the emulation thread holds. */
    video_lock();
    size_t size = video_get_frame_buffer_size_unlocked();
    if (size == 0) {
        video_unlock();
        return NULL;
    }
    video_unlock();

    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (!result) return NULL;

    video_lock();
    /* Re-check under the lock: the writer may have shrunk the buffer
     * between our pre-alloc size sample and the lock acquisition. */
    size_t cur_size = video_get_frame_buffer_size_unlocked();
    const void *buffer = video_get_frame_buffer();
    if (buffer && cur_size > 0) {
        jsize copyLen = (jsize)cur_size < (jsize)size ? (jsize)cur_size : (jsize)size;
        (*env)->SetByteArrayRegion(env, result, 0, copyLen, (const jbyte *)buffer);
    }
    video_unlock();
    return result;
}

JNI_FUNC(jint, nativeFillVideoFrame)(JNIEnv *env, jobject thiz, jbyteArray out) {
    jsize arrayLen = (*env)->GetArrayLength(env, out);
    if (arrayLen <= 0) return 0;

    /* #1044: see nativeGetVideoFrame for the lock rationale. */
    video_lock();
    const void *buffer = video_get_frame_buffer();
    size_t size = video_get_frame_buffer_size_unlocked();
    if (!buffer || size == 0) {
        video_unlock();
        return 0;
    }
    jsize copyLen = (jsize)size < arrayLen ? (jsize)size : arrayLen;
    (*env)->SetByteArrayRegion(env, out, 0, copyLen, (const jbyte *)buffer);
    video_unlock();
    return (jint)copyLen;
}

JNI_FUNC(jint, nativeGetVideoWidth)(JNIEnv *env, jobject thiz) {
    return (jint)video_get_width();
}

JNI_FUNC(jint, nativeGetVideoHeight)(JNIEnv *env, jobject thiz) {
    return (jint)video_get_height();
}

JNI_FUNC(jint, nativeGetPixelFormat)(JNIEnv *env, jobject thiz) {
    return (jint)video_get_pixel_format();
}

JNI_FUNC(jshortArray, nativeGetAudioBuffer)(JNIEnv *env, jobject thiz) {
    /* #1044: hold audio_lock across the get_frames + memcpy + clear
     * triple so the emulation thread's audio_sample[_batch]_callback
     * cannot interleave and have its samples either overwritten by the
     * clear (missed-increment race) or read partially (torn write_pos).
     *
     * NewShortArray is allocated OUTSIDE the lock to avoid blocking
     * the GC while the emulation thread is parked on audio_lock. We
     * sample the frame count with the lock, drop the lock for the
     * allocation, then reacquire and re-sample under the lock. */
    audio_lock();
    size_t frames = audio_get_buffer_frames_unlocked();
    audio_unlock();
    if (frames == 0) return NULL;

    size_t samples = frames * 2; /* stereo */
    jshortArray result = (*env)->NewShortArray(env, (jsize)samples);
    if (!result) return NULL;

    audio_lock();
    /* Re-sample under the lock: the writer may have added more frames
     * between our first read and now. Copy at least the original count
     * (the array we allocated). */
    const int16_t *buffer = audio_get_buffer();
    size_t cur_frames = audio_get_buffer_frames_unlocked();
    size_t to_copy = cur_frames < frames ? cur_frames : frames;
    if (buffer && to_copy > 0) {
        (*env)->SetShortArrayRegion(env, result, 0, (jsize)(to_copy * 2), buffer);
    }
    audio_clear_buffer_unlocked();
    audio_unlock();
    return result;
}

JNI_FUNC(jint, nativeFillAudioBuffer)(JNIEnv *env, jobject thiz, jshortArray out) {
    jsize arrayLen = (*env)->GetArrayLength(env, out);
    if (arrayLen <= 0) return 0;

    /* #1044: see nativeGetAudioBuffer for the lock rationale. */
    audio_lock();
    const int16_t *buffer = audio_get_buffer();
    size_t frames = audio_get_buffer_frames_unlocked();
    if (!buffer || frames == 0) {
        audio_unlock();
        return 0;
    }
    size_t samples = frames * 2; /* stereo */
    jsize copyLen = (jsize)samples < arrayLen ? (jsize)samples : arrayLen;
    (*env)->SetShortArrayRegion(env, out, 0, copyLen, buffer);
    audio_clear_buffer_unlocked();
    audio_unlock();
    return (jint)copyLen;
}

JNI_FUNC(jint, nativeResampleAudio)(JNIEnv *env, jobject thiz,
                                     jshortArray out, jdouble ratio) {
    /* audio_resample takes audio_lock internally and consumes write_pos
     * + populates resampled_buffer atomically. We then need a second
     * acquisition to read out the resampled data. The window between
     * the two acquisitions is safe because the emulation thread can
     * only ADD to a fresh buffer (write_pos was reset to 0); it cannot
     * touch resampled_buffer. */
    size_t frames = audio_resample(ratio);
    if (frames == 0) return 0;

    jsize samples = (jsize)(frames * 2);
    jsize arrayLen = (*env)->GetArrayLength(env, out);
    jsize copyLen = samples < arrayLen ? samples : arrayLen;

    audio_lock();
    const int16_t *buf = audio_get_resampled_buffer();
    if (buf && copyLen > 0) {
        (*env)->SetShortArrayRegion(env, out, 0, copyLen, buf);
    }
    audio_unlock();
    return (jint)copyLen;
}

JNI_FUNC(void, nativeResetAudioResampler)(JNIEnv *env, jobject thiz) {
    audio_resampler_reset();
}

JNI_FUNC(void, nativeSetInputButton)(JNIEnv *env, jobject thiz,
                                      jint port, jint id, jboolean pressed) {
    input_set_button((unsigned)port, (unsigned)id, pressed == JNI_TRUE);
}

JNI_FUNC(void, nativeSetInputAnalog)(JNIEnv *env, jobject thiz,
                                      jint port, jint index, jint id, jshort value) {
    input_set_analog((unsigned)port, (unsigned)index, (unsigned)id, (int16_t)value);
}

JNI_FUNC(jboolean, nativeGetInputButton)(JNIEnv *env, jobject thiz,
                                          jint port, jint id) {
    return input_get_button((unsigned)port, (unsigned)id) ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jshort, nativeGetInputAnalog)(JNIEnv *env, jobject thiz,
                                        jint port, jint index, jint id) {
    return (jshort)input_get_analog((unsigned)port, (unsigned)index, (unsigned)id);
}

JNI_FUNC(jdouble, nativeGetTargetFps)(JNIEnv *env, jobject thiz) {
    return g_core.av_info.timing.fps;
}

JNI_FUNC(jfloat, nativeGetAspectRatio)(JNIEnv *env, jobject thiz) {
    /* Return the core-reported display aspect ratio.
     * If the core provides one, use it. Otherwise derive from geometry. */
    float ar = g_core.av_info.geometry.aspect_ratio;
    if (ar > 0.0f) return ar;
    unsigned bw = g_core.av_info.geometry.base_width;
    unsigned bh = g_core.av_info.geometry.base_height;
    if (bw > 0 && bh > 0) return (float)bw / (float)bh;
    return 0.0f;
}

JNI_FUNC(jdouble, nativeGetSampleRate)(JNIEnv *env, jobject thiz) {
    return g_core.av_info.timing.sample_rate;
}

JNI_FUNC(jstring, nativeGetCoreName)(JNIEnv *env, jobject thiz) {
    if (!g_core.handle) return NULL;
    return (*env)->NewStringUTF(env, g_core.system_info.library_name);
}

JNI_FUNC(void, nativeSetSystemDir)(JNIEnv *env, jobject thiz, jstring dir) {
    const char *path = (*env)->GetStringUTFChars(env, dir, NULL);
    if (!path) return; /* GetStringUTFChars NULL on OOM — leave system_dir as-is */
    strncpy(g_core.system_dir, path, sizeof(g_core.system_dir) - 1);
    (*env)->ReleaseStringUTFChars(env, dir, path);
}

JNI_FUNC(void, nativeSetSaveDir)(JNIEnv *env, jobject thiz, jstring dir) {
    const char *path = (*env)->GetStringUTFChars(env, dir, NULL);
    if (!path) return; /* GetStringUTFChars NULL on OOM — leave save_dir as-is */
    strncpy(g_core.save_dir, path, sizeof(g_core.save_dir) - 1);
    (*env)->ReleaseStringUTFChars(env, dir, path);
}

JNI_FUNC(jbyteArray, nativeGetSRAM)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return NULL;

    void *data = g_core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = g_core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (const jbyte *)data);
    }
    return result;
}

JNI_FUNC(jboolean, nativeSetSRAM)(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!g_core.game_loaded) return JNI_FALSE;

    void *sram = g_core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t sram_size = g_core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!sram || sram_size == 0) return JNI_FALSE;

    jsize data_len = (*env)->GetArrayLength(env, data);
    size_t copy_size = (size_t)data_len < sram_size ? (size_t)data_len : sram_size;

    jbyte *src = (*env)->GetByteArrayElements(env, data, NULL);
    if (!src) return JNI_FALSE;

    memcpy(sram, src, copy_size);
    (*env)->ReleaseByteArrayElements(env, data, src, JNI_ABORT);
    return JNI_TRUE;
}

JNI_FUNC(void, nativeSetCoreVariable)(JNIEnv *env, jobject thiz, jstring key, jstring value) {
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    if (!k) return;
    const char *v = (*env)->GetStringUTFChars(env, value, NULL);
    if (!v) {
        (*env)->ReleaseStringUTFChars(env, key, k);
        return;
    }
    core_variables_set(k, v);
    (*env)->ReleaseStringUTFChars(env, key, k);
    (*env)->ReleaseStringUTFChars(env, value, v);
}

JNI_FUNC(void, nativeSetInputPointer)(JNIEnv *env, jobject thiz,
                                       jint port, jint x, jint y, jboolean pressed) {
    input_set_pointer((unsigned)port, (int16_t)x, (int16_t)y, pressed == JNI_TRUE);
}

JNI_FUNC(void, nativeSetInputMouse)(JNIEnv *env, jobject thiz,
                                     jint port, jshort dx, jshort dy,
                                     jboolean left, jboolean right) {
    input_set_mouse((unsigned)port, (int16_t)dx, (int16_t)dy,
                    left == JNI_TRUE, right == JNI_TRUE);
}

JNI_FUNC(void, nativeSetInputKeyboard)(JNIEnv *env, jobject thiz,
                                        jint key, jboolean pressed) {
    input_set_keyboard((unsigned)key, pressed == JNI_TRUE);
}

JNI_FUNC(void, nativeSetControllerPortDevice)(JNIEnv *env, jobject thiz,
                                               jint port, jint device) {
    if (g_core.retro_set_controller_port_device) {
        g_core.retro_set_controller_port_device((unsigned)port, (unsigned)device);
        LOGI("Set controller port %d device to %d", port, device);
    }
}

/* === GPU Renderer JNI Methods === */

/* g_gpu_renderer is forward-declared near the top of this file */

JNI_FUNC(jboolean, nativeGpuInit)(JNIEnv *env, jobject thiz, jobject surface) {
    if (g_gpu_renderer) {
        LOGW("GPU renderer already initialized, destroying first");
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        video_set_gpu_renderer(NULL);
    }

#ifdef __ANDROID__
    /* Determine backend: Vulkan on Android */
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
    if (!g_gpu_renderer) {
        LOGE("Failed to create GPU renderer");
        return JNI_FALSE;
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow from Surface");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    /* Pass context negotiation interface to GPU renderer before device creation */
    if (g_core.hw_vk_negotiation) {
        gpu_renderer_set_vk_negotiation(g_gpu_renderer, g_core.hw_vk_negotiation);
    }

    if (!gpu_renderer_init_surface(g_gpu_renderer, window)) {
        LOGE("Failed to init GPU renderer surface");
        ANativeWindow_release(window);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    /* ANativeWindow is acquired inside gpu_renderer_init_surface,
     * release our local reference */
    ANativeWindow_release(window);
#else
    /* Desktop: Vulkan on all platforms (macOS via MoltenVK) */
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
    if (!g_gpu_renderer) {
        LOGE("Failed to create GPU renderer");
        return JNI_FALSE;
    }

    /* Desktop: use JAWT to extract native surface from AWT Canvas.
     * On macOS, platformInfo is an NSObject<JAWT_SurfaceLayers>*.
     * On Linux/Windows, platformInfo contains the native window handle. */
    JAWT awt;
    awt.version = JAWT_VERSION_9;
    if (!JAWT_GetAWT(env, &awt)) {
        LOGE("JAWT_GetAWT failed");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    JAWT_DrawingSurface *ds = awt.GetDrawingSurface(env, surface);
    if (!ds) {
        LOGE("GetDrawingSurface failed");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    jint lock_result = ds->Lock(ds);
    if ((lock_result & JAWT_LOCK_ERROR) != 0) {
        LOGE("DrawingSurface Lock failed");
        awt.FreeDrawingSurface(ds);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
    if (!dsi || !dsi->platformInfo) {
        LOGE("GetDrawingSurfaceInfo failed or no platformInfo");
        ds->Unlock(ds);
        awt.FreeDrawingSurface(ds);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    void *platform_info = dsi->platformInfo;
    int surface_width = dsi->bounds.width;
    int surface_height = dsi->bounds.height;
    LOGI("JAWT surface: %dx%d, platformInfo=%p", surface_width, surface_height, platform_info);

    if (!gpu_renderer_init_surface(g_gpu_renderer, platform_info)) {
        LOGE("Failed to init GPU renderer surface");
        ds->FreeDrawingSurfaceInfo(dsi);
        ds->Unlock(ds);
        awt.FreeDrawingSurface(ds);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    /* Resize to match the JAWT surface dimensions */
    if (surface_width > 0 && surface_height > 0) {
        gpu_renderer_resize(g_gpu_renderer, surface_width, surface_height);
    }

    ds->FreeDrawingSurfaceInfo(dsi);
    ds->Unlock(ds);
    awt.FreeDrawingSurface(ds);
#endif

    /* Wire up the GPU renderer to the video subsystem */
    video_set_gpu_renderer(g_gpu_renderer);

#ifdef __ANDROID__
    /* Initialize Vulkan HW render if core requested it */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN) {
        if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
            if (g_core.hw_render_callback.context_reset) {
                g_core.hw_render_callback.context_reset();
            }
            gpu_renderer_mark_hw_context_reset_done(g_gpu_renderer);
            LOGI("Vulkan HW render context initialized for core");
        } else {
            LOGE("Failed to init Vulkan HW render context");
            g_core.hw_render_enabled = false;
        }
    }
#endif

    LOGI("GPU renderer initialized successfully");
    return JNI_TRUE;
}

JNI_FUNC(void, nativeGpuRender)(JNIEnv *env, jobject thiz) {
    if (g_gpu_renderer) {
        gpu_renderer_render(g_gpu_renderer);
    }
}

JNI_FUNC(void, nativeGpuSetShader)(JNIEnv *env, jobject thiz, jint shaderId) {
    if (g_gpu_renderer) {
        gpu_renderer_set_shader(g_gpu_renderer, (int)shaderId);
    }
}

JNI_FUNC(void, nativeGpuResize)(JNIEnv *env, jobject thiz, jint width, jint height) {
    if (g_gpu_renderer) {
        gpu_renderer_resize(g_gpu_renderer, (int)width, (int)height);
    }
}

JNI_FUNC(void, nativeGpuSuspend)(JNIEnv *env, jobject thiz) {
    if (g_gpu_renderer) {
        gpu_renderer_suspend_surface(g_gpu_renderer);
        LOGI("GPU surface suspended");
    }
}

JNI_FUNC(jboolean, nativeGpuResume)(JNIEnv *env, jobject thiz, jobject surface) {
#ifdef __ANDROID__
    if (!g_gpu_renderer) return JNI_FALSE;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow for resume");
        return JNI_FALSE;
    }
    bool ok = gpu_renderer_resume_surface(g_gpu_renderer, window);
    ANativeWindow_release(window);
    if (ok) {
        LOGI("GPU surface resumed");
    } else {
        LOGE("Failed to resume GPU surface");
    }
    return ok ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

static void do_gpu_deinit(void) {
    if (g_gpu_renderer) {
        /* Destroy Vulkan HW render context before releasing surface */
        if (g_core.hw_render_enabled && gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
            gpu_renderer_wait_idle(g_gpu_renderer);
            if (g_core.hw_render_callback.context_destroy) {
                g_core.hw_render_callback.context_destroy();
            }
            /* Granite's DefaultDispatch threads may still be compiling pipelines.
             * Wait for device idle + grace period before destroying resources. */
            gpu_renderer_wait_idle(g_gpu_renderer);
            sp_sleep_ms(200); /* 200ms grace for Granite background threads */
            gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
            LOGI("Vulkan HW render context destroyed (surface deinit)");
        }
        video_set_gpu_renderer(NULL);
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        LOGI("GPU renderer destroyed");
    }
}

JNI_FUNC(void, nativeGpuDeinit)(JNIEnv *env, jobject thiz) {
    /* The emulation screen's onDispose fires this while the stop flow (auto
     * save → upload → unload on the emulation thread) may still be running —
     * navigation leaves the screen before the core is torn down. Destroying
     * the renderer here frees the gpu_renderer_t that the core's
     * retro_hw_render_interface_vulkan points into; the core's GPU threads
     * (Azahar's Vulkan::Scheduler) then read freed memory and jump through a
     * garbage lock_queue pointer (use-after-free crash on quit/resume of
     * OoT 3D). Defer the deinit to the end of nativeDeinit — the same
     * emulation thread that owns core teardown — whenever a core is alive. */
    if (g_core.game_loaded || g_core.initialized) {
        g_gpu_deinit_deferred = true;
        LOGI("GPU deinit deferred until core teardown completes");
        return;
    }
    do_gpu_deinit();
}

JNI_FUNC(jboolean, nativeGpuInitOffscreen)(JNIEnv *env, jobject thiz, jint width, jint height) {
    if (g_gpu_renderer) {
        LOGW("GPU renderer already initialized, destroying first");
        video_set_gpu_renderer(NULL);
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
    }

    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
    if (!g_gpu_renderer) {
        LOGE("Failed to create GPU renderer for offscreen");
        return JNI_FALSE;
    }

    /* Pass context negotiation interface to GPU renderer before device creation */
    if (g_core.hw_vk_negotiation) {
        gpu_renderer_set_vk_negotiation(g_gpu_renderer, g_core.hw_vk_negotiation);
    }

    if (!gpu_renderer_init_offscreen(g_gpu_renderer, (int)width, (int)height)) {
        LOGE("Failed to init offscreen GPU renderer");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    video_set_gpu_renderer(g_gpu_renderer);

    /* Initialize Vulkan HW render if core requested it */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN) {
        if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
            if (g_core.hw_render_callback.context_reset) {
                g_core.hw_render_callback.context_reset();
            }
            gpu_renderer_mark_hw_context_reset_done(g_gpu_renderer);
            LOGI("Vulkan HW render context initialized for core (offscreen)");
        } else {
            LOGE("Failed to init Vulkan HW render context (offscreen)");
            g_core.hw_render_enabled = false;
        }
    }

    LOGI("Offscreen GPU renderer initialized successfully");
    return JNI_TRUE;
}

JNI_FUNC(jlong, nativeGpuRenderToBgra)(JNIEnv *env, jobject thiz, jbyteArray outData) {
    if (!g_gpu_renderer || !outData) return 0;

    jsize capacity = (*env)->GetArrayLength(env, outData);

    /* Use GetByteArrayElements rather than GetPrimitiveArrayCritical: the
     * GPU readback inside gpu_renderer_render_to_bgra performs Vulkan
     * command submission and a fence wait — multi-millisecond GPU work.
     * Holding a critical section across that suspends the GC for the
     * full duration and is a known source of Android ANR / GC stalls.
     * GetByteArrayElements may copy on some JVM implementations, but the
     * frame buffer is bounded in size and GC progress matters more than
     * one extra memcpy here. See #1043. */
    jbyte *data = (*env)->GetByteArrayElements(env, outData, NULL);
    if (!data) return 0;

    unsigned w = 0, h = 0;
    size_t written = gpu_renderer_render_to_bgra(g_gpu_renderer, data, (size_t)capacity, &w, &h);

    (*env)->ReleaseByteArrayElements(env, outData, data, 0);
    if (written == 0) return 0;
    /* Pack width (high 32 bits) and height (low 32 bits) into a jlong */
    return ((jlong)w << 32) | (jlong)h;
}

JNI_FUNC(jboolean, nativeGpuIsActive)(JNIEnv *env, jobject thiz) {
    return (g_gpu_renderer && gpu_renderer_is_active(g_gpu_renderer)) ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jboolean, nativeIsHwRenderEnabled)(JNIEnv *env, jobject thiz) {
    return g_core.hw_render_enabled ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jboolean, nativeIsVulkanHwRender)(JNIEnv *env, jobject thiz) {
    return (g_core.hw_render_enabled &&
            g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN)
        ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jstring, nativeGetCoreLibraryName)(JNIEnv *env, jobject thiz) {
    const char *name = g_core.system_info.library_name;
    return (*env)->NewStringUTF(env, name ? name : "");
}

JNI_FUNC(void, nativeGpuSetSourceRect)(JNIEnv *env, jobject thiz,
                                        jint x, jint y, jint w, jint h) {
    if (g_gpu_renderer) {
        gpu_renderer_set_source_rect(g_gpu_renderer, (int)x, (int)y, (int)w, (int)h);
    }
}

/* === Cheats === */

JNI_FUNC(void, nativeCheatReset)(JNIEnv *env, jobject thiz) {
    if (g_core.retro_cheat_reset) {
        g_core.retro_cheat_reset();
    }
}

JNI_FUNC(void, nativeCheatSet)(JNIEnv *env, jobject thiz,
                                jint index, jboolean enabled, jstring code) {
    if (!g_core.retro_cheat_set) return;
    const char *codeStr = (*env)->GetStringUTFChars(env, code, NULL);
    if (!codeStr) return; /* GetStringUTFChars NULL on OOM */
    g_core.retro_cheat_set((unsigned)index, enabled == JNI_TRUE, codeStr);
    (*env)->ReleaseStringUTFChars(env, code, codeStr);
}

#endif /* !SPELA_CORE_HOST — end of JNI bindings */

/* ===================================================================== */
/* === NATIVE HOST API (non-JNI) — out-of-process core host (desktop) === */
/*                                                                       */
/* Mirrors the JNI entry points above but takes plain C types, so a      */
/* native main() (spela_core_host.c) can drive a core without a JVM.     */
/* Reuses the same static core_load / globals / subsystem functions.     */
/* See spela_host_api.h, player/native/CORE_HOST.md, and #1243.          */
/* ===================================================================== */

#ifndef __ANDROID__  /* host process is desktop-only (Android stays in-process) */

void sp_host_set_dirs(const char *system_dir, const char *save_dir) {
    if (system_dir) {
        strncpy(g_core.system_dir, system_dir, sizeof(g_core.system_dir) - 1);
        g_core.system_dir[sizeof(g_core.system_dir) - 1] = '\0';
    }
    if (save_dir) {
        strncpy(g_core.save_dir, save_dir, sizeof(g_core.save_dir) - 1);
        g_core.save_dir[sizeof(g_core.save_dir) - 1] = '\0';
    }
}

bool sp_host_load_core(const char *core_path) {
    return core_path && core_load(core_path) == 0;
}

void sp_host_init(void) {
    if (!g_core.handle) return;
    video_init();
    input_init();
    g_core.retro_init();
    g_core.initialized = true;
    /* Re-bind callbacks after retro_init (some cores reset them). */
    g_core.retro_set_video_refresh(video_refresh_callback);
    g_core.retro_set_audio_sample(audio_sample_callback);
    g_core.retro_set_audio_sample_batch(audio_sample_batch_callback);
    g_core.retro_set_input_poll(input_poll_callback);
    g_core.retro_set_input_state(input_state_callback);
    LOGI("[host] Core initialized");
}

bool sp_host_gpu_init_offscreen(int width, int height) {
    if (g_gpu_renderer) {
        video_set_gpu_renderer(NULL);
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
    }
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
    if (!g_gpu_renderer) {
        LOGE("[host] Failed to create GPU renderer for offscreen");
        return false;
    }
    if (g_core.hw_vk_negotiation) {
        gpu_renderer_set_vk_negotiation(g_gpu_renderer, g_core.hw_vk_negotiation);
    }
    if (!gpu_renderer_init_offscreen(g_gpu_renderer, width, height)) {
        LOGE("[host] Failed to init offscreen GPU renderer");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return false;
    }
    video_set_gpu_renderer(g_gpu_renderer);
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN) {
        if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
            if (g_core.hw_render_callback.context_reset) {
                g_core.hw_render_callback.context_reset();
            }
            LOGI("[host] Vulkan HW render context initialized (offscreen)");
        } else {
            LOGE("[host] Failed to init Vulkan HW render context (offscreen)");
            g_core.hw_render_enabled = false;
        }
    }
    LOGI("[host] Offscreen GPU renderer initialized");
    return true;
}

bool sp_host_load_game(const char *game_path) {
    if (!g_core.handle || !g_core.initialized || !game_path) return false;

    struct retro_game_info game_info = {0};
    game_info.path = game_path;

    if (!g_core.system_info.need_fullpath) {
        FILE *f = fopen(game_path, "rb");
        if (!f) { LOGE("[host] Failed to open ROM: %s", game_path); return false; }
        fseek(f, 0, SEEK_END);
        game_info.size = ftell(f);
        fseek(f, 0, SEEK_SET);
        void *buf = malloc(game_info.size);
        if (buf) {
            size_t read_bytes = fread(buf, 1, game_info.size, f);
            if (read_bytes != game_info.size) {
                LOGE("[host] Short read from ROM: %zu of %zu", read_bytes, game_info.size);
                free(buf); fclose(f); return false;
            }
            game_info.data = buf;
        }
        fclose(f);
    }

    bool loaded = g_core.retro_load_game(&game_info);
    if (game_info.data) free((void *)game_info.data);
    if (!loaded) { LOGE("[host] retro_load_game failed"); return false; }

    g_core.game_loaded = true;
    g_core.retro_get_system_av_info(&g_core.av_info);
    audio_init(g_core.av_info.timing.sample_rate);
    LOGI("[host] Game loaded: %ux%u @ %.2f fps, audio %.1f Hz",
         g_core.av_info.geometry.base_width, g_core.av_info.geometry.base_height,
         g_core.av_info.timing.fps, g_core.av_info.timing.sample_rate);

    if (g_core.retro_set_controller_port_device) {
        for (unsigned port = 0; port < 4; port++) {
            g_core.retro_set_controller_port_device(port, RETRO_DEVICE_JOYPAD);
        }
    }

    /* Vulkan HW render: GPU renderer was created offscreen before load_game;
     * reinit with the core's negotiation interface so they share VkInstance. */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
        g_gpu_renderer) {
        if (g_core.hw_vk_negotiation) {
            gpu_renderer_set_vk_negotiation(g_gpu_renderer, g_core.hw_vk_negotiation);
            if (!gpu_renderer_reinit_vulkan(g_gpu_renderer)) {
                LOGE("[host] Failed to reinit Vulkan with negotiation");
            }
        }
        if (gpu_renderer_hw_vulkan_init(g_gpu_renderer)) {
            if (g_core.hw_render_callback.context_reset) {
                g_core.hw_render_callback.context_reset();
            }
            LOGI("[host] Vulkan HW render context initialized after game load");
        } else {
            LOGE("[host] Failed to init Vulkan HW render after game load");
        }
    }
    return true;
}

void sp_host_run_frame(void) {
    if (!g_core.game_loaded) return;
    /* Vulkan HW render: skip frames until the HW context is ready. */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
        (!g_gpu_renderer || !gpu_renderer_is_hw_render_active(g_gpu_renderer))) {
        return;
    }
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
        hw_gl_debug_reset_frame();
    }
    g_core.retro_run();
    g_first_frame_run = true;
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_release_current(g_core.hw_gl_ctx);
    }
    achievements_do_frame();
}

unsigned    sp_host_video_width(void)       { return video_get_width(); }
unsigned    sp_host_video_height(void)      { return video_get_height(); }
unsigned    sp_host_pixel_format(void)      { return video_get_pixel_format(); }
const void *sp_host_video_buffer(void)      { return video_get_frame_buffer(); }
size_t      sp_host_video_buffer_size(void) { return video_get_frame_buffer_size(); }

size_t sp_host_render_to_bgra(void *dst, size_t dst_capacity, unsigned *w, unsigned *h) {
    if (!g_gpu_renderer || !dst) return 0;
    return gpu_renderer_render_to_bgra(g_gpu_renderer, dst, dst_capacity, w, h);
}

size_t sp_host_get_audio(int16_t *dst, size_t dst_frames) {
    if (!dst || dst_frames == 0) return 0;
    audio_lock();
    const int16_t *buf = audio_get_buffer();
    size_t frames = audio_get_buffer_frames_unlocked();
    if (!buf || frames == 0) { audio_unlock(); return 0; }
    size_t copy = frames < dst_frames ? frames : dst_frames;
    memcpy(dst, buf, copy * 2 * sizeof(int16_t));
    audio_clear_buffer_unlocked();
    audio_unlock();
    return copy;
}

void sp_host_set_button(unsigned port, unsigned id, bool pressed) {
    input_set_button(port, id, pressed);
}
void sp_host_set_analog(unsigned port, unsigned index, unsigned id, int16_t value) {
    input_set_analog(port, index, id, value);
}
void sp_host_set_pointer(unsigned port, int16_t x, int16_t y, bool pressed) {
    input_set_pointer(port, x, y, pressed);
}
void sp_host_set_core_variable(const char *key, const char *value) {
    if (key && value) core_variables_set(key, value);
}

double sp_host_target_fps(void)     { return g_core.av_info.timing.fps; }
double sp_host_sample_rate(void)    { return g_core.av_info.timing.sample_rate; }
float  sp_host_aspect_ratio(void)   { return g_core.av_info.geometry.aspect_ratio; }
bool   sp_host_is_hw_render(void)   { return g_core.hw_render_enabled; }
bool   sp_host_is_vulkan_hw_render(void) {
    return g_core.hw_render_enabled &&
           g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN;
}

void sp_host_unload(void) {
    if (g_core.game_loaded && g_core.retro_unload_game) g_core.retro_unload_game();
    g_core.game_loaded = false;
}
void sp_host_deinit(void) {
    /* The host is a short-lived process; the OS reclaims everything on exit.
     * Keep teardown minimal to avoid the subtle HW-render teardown hazards
     * the in-process JNI path must handle. */
    if (g_core.initialized && g_core.retro_deinit) {
        video_set_shutting_down();
        g_core.retro_deinit();
        g_core.initialized = false;
    }
}

#endif /* !__ANDROID__ */
