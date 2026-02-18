/*
 * Spela libretro bridge - Main core loading and lifecycle management.
 *
 * This implements the libretro frontend API: it loads cores as shared libraries,
 * registers callbacks, and drives the emulation loop.
 *
 * Reference: RetroArch's core_ctl.c and runloop.c
 */

#ifdef __ANDROID__
#include <vulkan/vulkan.h>
#endif

#include "libretro_bridge.h"
#include "libretro_achievements.h"
#include "gpu_renderer.h"

#include "hw_render_gl.h"

#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
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
        g_bridge_log_file = fopen("/tmp/spela_bridge.log", "w");
        if (g_bridge_log_file) setbuf(g_bridge_log_file, NULL);
    }
    return g_bridge_log_file ? g_bridge_log_file : stderr;
}
#define LOGI(...) do { FILE *f = bridge_log_get(); fprintf(f, __VA_ARGS__); fprintf(f, "\n"); } while(0)
#define LOGW(...) do { FILE *f = bridge_log_get(); fprintf(f, "WARN: " __VA_ARGS__); fprintf(f, "\n"); } while(0)
#define LOGE(...) do { FILE *f = bridge_log_get(); fprintf(f, "ERROR: " __VA_ARGS__); fprintf(f, "\n"); } while(0)
#endif

/* Global core instance */
libretro_core_t g_core = {0};

/* GPU renderer instance - used by both env callbacks and JNI methods */
static gpu_renderer_t *g_gpu_renderer = NULL;

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
                    if (already_set) continue;

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

                /* N64 renderer selection per platform:
                 * - macOS: Angrylion (software) — avoids GL compositing issues with GLideN64
                 * - Android: paraLLEl-RDP (Vulkan) — triggers our Vulkan HW render pipeline
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

        case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE: {
#ifdef __ANDROID__
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
#endif
            return false;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            struct retro_hw_render_callback *cb = (struct retro_hw_render_callback *)data;
            LOGI("Core requests HW render context type: %u (v%u.%u)",
                 cb->context_type, cb->version_major, cb->version_minor);
#ifdef __ANDROID__
            /* Accept Vulkan context type on Android */
            if (cb->context_type == RETRO_HW_CONTEXT_VULKAN) {
                g_core.hw_render_callback = *cb;
                /* Vulkan does not use get_current_framebuffer or get_proc_address */
                g_core.hw_render_callback.get_current_framebuffer = NULL;
                g_core.hw_render_callback.get_proc_address = NULL;
                cb->get_current_framebuffer = NULL;
                cb->get_proc_address = NULL;
                g_core.hw_render_enabled = true;
                LOGI("Accepted Vulkan HW render (type=%u, depth=%d, stencil=%d)",
                     cb->context_type, cb->depth, cb->stencil);
                return true;
            }
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
                LOGI("Accepted GLES HW render (type=%u, v%u.%u, depth=%d, stencil=%d, bottom_left_origin=%d)",
                     cb->context_type, cb->version_major, cb->version_minor,
                     cb->depth, cb->stencil, cb->bottom_left_origin);
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
#ifdef __ANDROID__
            if (g_core.hw_render_enabled &&
                g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
                g_gpu_renderer) {
                void *iface = gpu_renderer_hw_vulkan_get_interface(g_gpu_renderer);
                if (iface) {
                    *(const struct retro_hw_render_interface_vulkan **)data = iface;
                    LOGI("Returning Vulkan HW render interface to core");
                    return true;
                }
            }
#endif
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
    g_core.sym = (sym##_t)dlsym(g_core.handle, #sym); \
    if (!g_core.sym) { \
        LOGE("Failed to load symbol: %s", #sym); \
        return -1; \
    } \
} while(0)

/* Load the libretro core from the given shared library path */
static int core_load(const char *path) {
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
        dlclose(g_core.handle);
        g_core.handle = NULL;
    }

    LOGI("Loading core: %s", path);
    g_core.handle = dlopen(path, RTLD_LAZY);
    if (!g_core.handle) {
        LOGE("dlopen failed: %s", dlerror());
        return -1;
    }

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

    /* Register callbacks before retro_init */
    g_core.retro_set_environment(environment_callback);
    g_core.retro_set_video_refresh(video_refresh_callback);
    g_core.retro_set_audio_sample(audio_sample_callback);
    g_core.retro_set_audio_sample_batch(audio_sample_batch_callback);
    g_core.retro_set_input_poll(input_poll_callback);
    g_core.retro_set_input_state(input_state_callback);

    LOGI("Core loaded successfully, API version: %u", g_core.retro_api_version());

    g_core.retro_get_system_info(&g_core.system_info);
    LOGI("Core: %s v%s", g_core.system_info.library_name, g_core.system_info.library_version);

    return 0;
}

/* === JNI BINDINGS === */

#define JNI_FUNC(ret, name) JNIEXPORT ret JNICALL Java_com_spela_player_libretro_LibretroJni_##name

JNI_FUNC(jboolean, nativeLoadCore)(JNIEnv *env, jobject thiz, jstring corePath) {
    const char *path = (*env)->GetStringUTFChars(env, corePath, NULL);
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
    LOGI("Core initialized");
}

JNI_FUNC(jboolean, nativeLoadGame)(JNIEnv *env, jobject thiz, jstring gamePath) {
    if (!g_core.handle || !g_core.initialized) return JNI_FALSE;

    const char *path = (*env)->GetStringUTFChars(env, gamePath, NULL);

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
                fread(buf, 1, game_info.size, f);
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
        LOGI("Game loaded: %ux%u @ %.2f fps, audio %.1f Hz",
             g_core.av_info.geometry.base_width,
             g_core.av_info.geometry.base_height,
             g_core.av_info.timing.fps,
             g_core.av_info.timing.sample_rate);

        /* Initialize OpenGL/GLES HW render context if the core requested it.
         * On Android, this only applies to GLES context types (GLideN64);
         * Vulkan HW render (paraLLEl-RDP) is initialized later in gpuInit. */
#ifdef __ANDROID__
        if (g_core.hw_render_enabled &&
            g_core.hw_render_callback.context_type != RETRO_HW_CONTEXT_VULKAN) {
#else
        if (g_core.hw_render_enabled) {
#endif
            g_core.hw_gl_ctx = hw_gl_create();
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
#ifdef __ANDROID__
    /* Vulkan HW render: skip frames until Vulkan HW context is ready */
    if (g_core.hw_render_enabled &&
        g_core.hw_render_callback.context_type == RETRO_HW_CONTEXT_VULKAN &&
        (!g_gpu_renderer || !gpu_renderer_is_hw_render_active(g_gpu_renderer))) {
        return;
    }
    /* GLES HW render: make context current before retro_run */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
    }
#else
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        hw_gl_make_current(g_core.hw_gl_ctx);
        hw_gl_debug_reset_frame();
    }
#endif
    g_core.retro_run();
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
#ifdef __ANDROID__
    /* Tear down Vulkan HW render context before unloading game */
    if (g_core.hw_render_enabled && g_gpu_renderer &&
        gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
        /* Wait for all GPU work to finish before core destroys its resources */
        gpu_renderer_wait_idle(g_gpu_renderer);
        if (g_core.hw_render_callback.context_destroy) {
            g_core.hw_render_callback.context_destroy();
        }
        /* Granite's background threads (DefaultDispatch) may still be compiling
         * pipelines. context_destroy signals them to stop but doesn't join them.
         * Wait for the device to go idle, then give threads time to exit. */
        gpu_renderer_wait_idle(g_gpu_renderer);
        usleep(200000); /* 200ms grace period for background thread shutdown */
        gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
        g_core.hw_render_enabled = false;
        LOGI("Vulkan HW render context destroyed");
    }
#endif
    /* Tear down OpenGL/GLES HW render context before unloading game */
    if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
        if (g_core.hw_render_callback.context_destroy) {
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
    audio_deinit();
    LOGI("Game unloaded");
}

JNI_FUNC(void, nativeDeinit)(JNIEnv *env, jobject thiz) {
    if (!g_core.initialized) return;
    if (g_core.game_loaded) {
#ifdef __ANDROID__
        /* Clean up Vulkan HW render if still active */
        if (g_core.hw_render_enabled && g_gpu_renderer &&
            gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
            gpu_renderer_wait_idle(g_gpu_renderer);
            if (g_core.hw_render_callback.context_destroy) {
                g_core.hw_render_callback.context_destroy();
            }
            gpu_renderer_wait_idle(g_gpu_renderer);
            usleep(200000); /* 200ms grace for Granite background threads */
            gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
            g_core.hw_render_enabled = false;
        }
#endif
        /* Clean up GL/GLES HW render if still active */
        if (g_core.hw_render_enabled && g_core.hw_gl_ctx) {
            if (g_core.hw_render_callback.context_destroy) {
                hw_gl_make_current(g_core.hw_gl_ctx);
                g_core.hw_render_callback.context_destroy();
            }
            hw_gl_destroy(g_core.hw_gl_ctx);
            g_core.hw_gl_ctx = NULL;
            g_core.hw_render_enabled = false;
        }
        g_core.retro_unload_game();
        g_core.game_loaded = false;
    }
    g_core.retro_deinit();
    g_core.initialized = false;
    video_deinit();
    input_deinit();
    audio_deinit();

    /* Clear pointers into core's memory before dlclose */
    g_core.hw_vk_negotiation = NULL;
    g_core.hw_render_enabled = false;
    memset(&g_core.hw_render_callback, 0, sizeof(g_core.hw_render_callback));

    if (g_core.handle) {
        dlclose(g_core.handle);
        g_core.handle = NULL;
    }
    LOGI("Core deinitialized");
}

JNI_FUNC(jlong, nativeSerializeSize)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return 0;
    return (jlong)g_core.retro_serialize_size();
}

JNI_FUNC(jbyteArray, nativeSerialize)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return NULL;

    size_t size = g_core.retro_serialize_size();
    if (size == 0) return NULL;

    void *buf = malloc(size);
    if (!buf) return NULL;

    if (!g_core.retro_serialize(buf, size)) {
        free(buf);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (jbyte *)buf);
    }
    free(buf);
    return result;
}

JNI_FUNC(jboolean, nativeUnserialize)(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!g_core.game_loaded || !data) return JNI_FALSE;

    jsize size = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buf) return JNI_FALSE;

    bool result = g_core.retro_unserialize(buf, (size_t)size);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jbyteArray, nativeGetVideoFrame)(JNIEnv *env, jobject thiz) {
    const void *buffer = video_get_frame_buffer();
    size_t size = video_get_frame_buffer_size();
    if (!buffer || size == 0) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (const jbyte *)buffer);
    }
    return result;
}

JNI_FUNC(jint, nativeFillVideoFrame)(JNIEnv *env, jobject thiz, jbyteArray out) {
    const void *buffer = video_get_frame_buffer();
    size_t size = video_get_frame_buffer_size();
    if (!buffer || size == 0) return 0;

    jsize arrayLen = (*env)->GetArrayLength(env, out);
    jsize copyLen = (jsize)size < arrayLen ? (jsize)size : arrayLen;
    (*env)->SetByteArrayRegion(env, out, 0, copyLen, (const jbyte *)buffer);
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
    const int16_t *buffer = audio_get_buffer();
    size_t frames = audio_get_buffer_frames();
    if (!buffer || frames == 0) return NULL;

    size_t samples = frames * 2; /* stereo */
    jshortArray result = (*env)->NewShortArray(env, (jsize)samples);
    if (result) {
        (*env)->SetShortArrayRegion(env, result, 0, (jsize)samples, buffer);
    }
    audio_clear_buffer();
    return result;
}

JNI_FUNC(jint, nativeFillAudioBuffer)(JNIEnv *env, jobject thiz, jshortArray out) {
    const int16_t *buffer = audio_get_buffer();
    size_t frames = audio_get_buffer_frames();
    if (!buffer || frames == 0) return 0;

    size_t samples = frames * 2; /* stereo */
    jsize arrayLen = (*env)->GetArrayLength(env, out);
    jsize copyLen = (jsize)samples < arrayLen ? (jsize)samples : arrayLen;
    (*env)->SetShortArrayRegion(env, out, 0, copyLen, buffer);
    audio_clear_buffer();
    return (jint)copyLen;
}

JNI_FUNC(void, nativeSetInputButton)(JNIEnv *env, jobject thiz,
                                      jint port, jint id, jboolean pressed) {
    input_set_button((unsigned)port, (unsigned)id, pressed == JNI_TRUE);
}

JNI_FUNC(void, nativeSetInputAnalog)(JNIEnv *env, jobject thiz,
                                      jint port, jint index, jint id, jshort value) {
    input_set_analog((unsigned)port, (unsigned)index, (unsigned)id, (int16_t)value);
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
    strncpy(g_core.system_dir, path, sizeof(g_core.system_dir) - 1);
    (*env)->ReleaseStringUTFChars(env, dir, path);
}

JNI_FUNC(void, nativeSetSaveDir)(JNIEnv *env, jobject thiz, jstring dir) {
    const char *path = (*env)->GetStringUTFChars(env, dir, NULL);
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

JNI_FUNC(void, nativeSetCoreVariable)(JNIEnv *env, jobject thiz, jstring key, jstring value) {
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = (*env)->GetStringUTFChars(env, value, NULL);
    core_variables_set(k, v);
    (*env)->ReleaseStringUTFChars(env, key, k);
    (*env)->ReleaseStringUTFChars(env, value, v);
}

JNI_FUNC(void, nativeSetInputPointer)(JNIEnv *env, jobject thiz,
                                       jint port, jint x, jint y, jboolean pressed) {
    input_set_pointer((unsigned)port, (int16_t)x, (int16_t)y, pressed == JNI_TRUE);
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
    /* Desktop: determine backend based on platform */
#ifdef __APPLE__
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_METAL);
#else
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
#endif
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

JNI_FUNC(void, nativeGpuDeinit)(JNIEnv *env, jobject thiz) {
    if (g_gpu_renderer) {
#ifdef __ANDROID__
        /* Destroy Vulkan HW render context before releasing surface */
        if (g_core.hw_render_enabled && gpu_renderer_is_hw_render_active(g_gpu_renderer)) {
            gpu_renderer_wait_idle(g_gpu_renderer);
            if (g_core.hw_render_callback.context_destroy) {
                g_core.hw_render_callback.context_destroy();
            }
            /* Granite's DefaultDispatch threads may still be compiling pipelines.
             * Wait for device idle + grace period before destroying resources. */
            gpu_renderer_wait_idle(g_gpu_renderer);
            usleep(200000); /* 200ms grace for Granite background threads */
            gpu_renderer_hw_vulkan_deinit(g_gpu_renderer);
            LOGI("Vulkan HW render context destroyed (surface deinit)");
        }
#endif
        video_set_gpu_renderer(NULL);
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        LOGI("GPU renderer destroyed");
    }
}

JNI_FUNC(jboolean, nativeGpuInitOffscreen)(JNIEnv *env, jobject thiz, jint width, jint height) {
    if (g_gpu_renderer) {
        LOGW("GPU renderer already initialized, destroying first");
        video_set_gpu_renderer(NULL);
        gpu_renderer_deinit_surface(g_gpu_renderer);
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
    }

#ifdef __APPLE__
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_METAL);
#else
    g_gpu_renderer = gpu_renderer_create(GPU_BACKEND_VULKAN);
#endif
    if (!g_gpu_renderer) {
        LOGE("Failed to create GPU renderer for offscreen");
        return JNI_FALSE;
    }

    if (!gpu_renderer_init_offscreen(g_gpu_renderer, (int)width, (int)height)) {
        LOGE("Failed to init offscreen GPU renderer");
        gpu_renderer_destroy(g_gpu_renderer);
        g_gpu_renderer = NULL;
        return JNI_FALSE;
    }

    video_set_gpu_renderer(g_gpu_renderer);
    LOGI("Offscreen GPU renderer initialized successfully");
    return JNI_TRUE;
}

JNI_FUNC(jint, nativeGpuRenderToBgra)(JNIEnv *env, jobject thiz, jbyteArray outData) {
    if (!g_gpu_renderer || !outData) return 0;

    jsize capacity = (*env)->GetArrayLength(env, outData);
    jbyte *data = (*env)->GetPrimitiveArrayCritical(env, outData, NULL);
    if (!data) return 0;

    unsigned w = 0, h = 0;
    size_t written = gpu_renderer_render_to_bgra(g_gpu_renderer, data, (size_t)capacity, &w, &h);

    (*env)->ReleasePrimitiveArrayCritical(env, outData, data, 0);
    return (jint)written;
}

JNI_FUNC(jboolean, nativeGpuIsActive)(JNIEnv *env, jobject thiz) {
    return (g_gpu_renderer && gpu_renderer_is_active(g_gpu_renderer)) ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jboolean, nativeIsHwRenderEnabled)(JNIEnv *env, jobject thiz) {
    return g_core.hw_render_enabled ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(void, nativeGpuSetSourceRect)(JNIEnv *env, jobject thiz,
                                        jint x, jint y, jint w, jint h) {
    if (g_gpu_renderer) {
        gpu_renderer_set_source_rect(g_gpu_renderer, (int)x, (int)y, (int)w, (int)h);
    }
}
