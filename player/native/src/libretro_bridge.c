/*
 * Spela libretro bridge - Main core loading and lifecycle management.
 *
 * This implements the libretro frontend API: it loads cores as shared libraries,
 * registers callbacks, and drives the emulation loop.
 *
 * Reference: RetroArch's core_ctl.c and runloop.c
 */

#include "libretro_bridge.h"

#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "SpelaLibretro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) fprintf(stdout, __VA_ARGS__); fprintf(stdout, "\n")
#define LOGW(...) fprintf(stderr, "WARN: " __VA_ARGS__); fprintf(stderr, "\n")
#define LOGE(...) fprintf(stderr, "ERROR: " __VA_ARGS__); fprintf(stderr, "\n")
#endif

/* Global core instance */
libretro_core_t g_core = {0};

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

/*
 * Environment callback - the core calls this to query/set frontend features.
 * We handle the subset of environment commands needed for basic operation.
 */
static bool environment_callback(unsigned cmd, void *data) {
    switch (cmd) {
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
            /* No variables set by default */
            var->value = NULL;
            return false;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            *(bool *)data = false;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            /* Acknowledge but don't process variables yet */
            return true;

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

        default:
            LOGW("Unhandled environment cmd: %u", cmd);
            return false;
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
    g_core.retro_init();
    g_core.initialized = true;
    video_init();
    input_init();
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
    } else {
        LOGE("retro_load_game failed");
    }

    return loaded ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(void, nativeRun)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    g_core.retro_run();
}

JNI_FUNC(void, nativeReset)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    g_core.retro_reset();
}

JNI_FUNC(void, nativeUnloadGame)(JNIEnv *env, jobject thiz) {
    if (!g_core.game_loaded) return;
    g_core.retro_unload_game();
    g_core.game_loaded = false;
    audio_deinit();
    LOGI("Game unloaded");
}

JNI_FUNC(void, nativeDeinit)(JNIEnv *env, jobject thiz) {
    if (!g_core.initialized) return;
    if (g_core.game_loaded) {
        g_core.retro_unload_game();
        g_core.game_loaded = false;
    }
    g_core.retro_deinit();
    g_core.initialized = false;
    video_deinit();
    input_deinit();
    audio_deinit();

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
