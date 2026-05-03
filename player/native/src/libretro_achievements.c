/*
 * Spela libretro bridge - RetroAchievements integration via rcheevos.
 *
 * This module wraps the rcheevos rc_client API and bridges it to Kotlin
 * through JNI callbacks. HTTP requests are delegated to Kotlin (Ktor),
 * and achievement events are forwarded as JNI method calls.
 */

#include "libretro_achievements.h"
#include "libretro_bridge.h"
#include <rc_client.h>
#include <stdlib.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/log.h>
#define ACH_TAG "SpelaAchievements"
#define ACH_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ACH_TAG, __VA_ARGS__)
#define ACH_LOGW(...) __android_log_print(ANDROID_LOG_WARN, ACH_TAG, __VA_ARGS__)
#define ACH_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ACH_TAG, __VA_ARGS__)
#else
#define ACH_LOGI(...) fprintf(stdout, "[achievements] " __VA_ARGS__); fprintf(stdout, "\n")
#define ACH_LOGW(...) fprintf(stderr, "[achievements] WARN: " __VA_ARGS__); fprintf(stderr, "\n")
#define ACH_LOGE(...) fprintf(stderr, "[achievements] ERROR: " __VA_ARGS__); fprintf(stderr, "\n")
#endif

/* Pending HTTP request tracking */
#define MAX_PENDING_REQUESTS 32

typedef struct {
    int request_id;
    rc_client_server_callback_t callback;
    void *callback_data;
    bool active;
} pending_request_t;

/* Module state */
static struct {
    rc_client_t *client;
    jobject jni_thiz;
    jclass jni_class;
    jmethodID on_achievement_event_method;
    jmethodID on_http_request_method;
    bool initialized;
    int next_request_id;
} ach_state = {0};

static pending_request_t pending_requests[MAX_PENDING_REQUESTS];

/*
 * Get a JNIEnv* for the current thread by attaching to the JVM.
 * Sets [*needs_detach] to true iff the thread was newly attached and
 * the caller must DetachCurrentThread before returning.
 *
 * rc_server_call and rc_event_handler are invoked from the emulation
 * thread (rc_client_do_frame), not the Kotlin thread that called
 * achievements_init. JNIEnv is strictly thread-local, so a cached
 * JNIEnv* from init time is invalid here — the previous code crashed
 * on strict JVMs and was undefined behaviour everywhere else (#1040).
 */
static JNIEnv *attach_thread_env(bool *needs_detach) {
    extern JavaVM *g_jvm;
    *needs_detach = false;
    if (!g_jvm) return NULL;
    JNIEnv *env = NULL;
    int status = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, (void **)&env, NULL) != JNI_OK) {
            return NULL;
        }
        *needs_detach = true;
    } else if (status != JNI_OK) {
        return NULL;
    }
    return env;
}

static void detach_thread_env_if_needed(bool needs_detach) {
    if (!needs_detach) return;
    extern JavaVM *g_jvm;
    if (g_jvm) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
}

/* --- rcheevos callbacks --- */

/*
 * Memory read callback for rcheevos.
 * Reads from the emulated system RAM exposed by the loaded libretro core.
 */
static uint32_t rc_read_memory(uint32_t address, uint8_t *buffer, uint32_t num_bytes, rc_client_t *client) {
    (void)client;

    if (!g_core.game_loaded) return 0;

    uint8_t *mem = (uint8_t *)g_core.retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM);
    size_t mem_size = g_core.retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);

    if (!mem || mem_size == 0) return 0;
    if (address >= mem_size) return 0;

    uint32_t avail = (uint32_t)(mem_size - address);
    uint32_t to_read = num_bytes < avail ? num_bytes : avail;
    memcpy(buffer, mem + address, to_read);
    return to_read;
}

/*
 * HTTP server call callback for rcheevos.
 * Delegates the HTTP request to Kotlin via JNI, storing the rcheevos
 * callback to be invoked later when Kotlin reports the response.
 */
static void rc_server_call(const rc_api_request_t *request, rc_client_server_callback_t callback,
                            void *callback_data, rc_client_t *client) {
    (void)client;

    if (!ach_state.initialized) {
        ACH_LOGE("Server call requested but achievements not initialized");
        rc_api_server_response_t response = {0};
        response.http_status_code = 0;
        callback(&response, callback_data);
        return;
    }

    /* Find a free pending request slot */
    int slot = -1;
    for (int i = 0; i < MAX_PENDING_REQUESTS; i++) {
        if (!pending_requests[i].active) {
            slot = i;
            break;
        }
    }

    if (slot < 0) {
        ACH_LOGE("No free pending request slots");
        rc_api_server_response_t response = {0};
        response.http_status_code = 0;
        callback(&response, callback_data);
        return;
    }

    bool needs_detach = false;
    JNIEnv *env = attach_thread_env(&needs_detach);
    if (!env) {
        ACH_LOGE("Failed to attach thread for JNI in rc_server_call");
        rc_api_server_response_t response = {0};
        response.http_status_code = 0;
        callback(&response, callback_data);
        return;
    }

    int request_id = ++ach_state.next_request_id;
    pending_requests[slot].request_id = request_id;
    pending_requests[slot].callback = callback;
    pending_requests[slot].callback_data = callback_data;
    pending_requests[slot].active = true;

    jstring url_jstr = (*env)->NewStringUTF(env, request->url ? request->url : "");
    jstring post_data_jstr = request->post_data
        ? (*env)->NewStringUTF(env, request->post_data)
        : NULL;

    (*env)->CallVoidMethod(env, ach_state.jni_thiz, ach_state.on_http_request_method,
                           (jint)request_id, url_jstr, post_data_jstr);

    (*env)->DeleteLocalRef(env, url_jstr);
    if (post_data_jstr) {
        (*env)->DeleteLocalRef(env, post_data_jstr);
    }
    detach_thread_env_if_needed(needs_detach);
}

/*
 * Event handler callback for rcheevos.
 * Maps rcheevos events to JNI calls back into Kotlin.
 */
static void rc_event_handler(const rc_client_event_t *event, rc_client_t *client) {
    (void)client;

    if (!ach_state.initialized) return;

    bool needs_detach = false;
    JNIEnv *env = attach_thread_env(&needs_detach);
    if (!env) return;

    jint event_type = (jint)event->type;
    jlong achievement_id = 0;
    jstring title_jstr = NULL;
    jstring desc_jstr = NULL;
    jint points = 0;

    if (event->achievement) {
        achievement_id = (jlong)event->achievement->id;
        if (event->achievement->title) {
            title_jstr = (*env)->NewStringUTF(env, event->achievement->title);
        }
        if (event->achievement->description) {
            desc_jstr = (*env)->NewStringUTF(env, event->achievement->description);
        }
        points = (jint)event->achievement->points;
    }

    (*env)->CallVoidMethod(env, ach_state.jni_thiz, ach_state.on_achievement_event_method,
                           event_type, achievement_id, title_jstr, desc_jstr, points);

    if (title_jstr) (*env)->DeleteLocalRef(env, title_jstr);
    if (desc_jstr) (*env)->DeleteLocalRef(env, desc_jstr);
    detach_thread_env_if_needed(needs_detach);
}

/* --- Login/load callbacks --- */

static void login_callback(int result, const char *error_message, rc_client_t *client, void *userdata) {
    (void)client;
    (void)userdata;

    if (result == RC_OK) {
        ACH_LOGI("Login successful");
    } else {
        ACH_LOGE("Login failed: %s (code %d)", error_message ? error_message : "unknown", result);
    }
}

static void load_game_callback(int result, const char *error_message, rc_client_t *client, void *userdata) {
    (void)client;
    (void)userdata;

    if (result == RC_OK) {
        ACH_LOGI("Game loaded for achievements");
    } else {
        ACH_LOGE("Game load failed: %s (code %d)", error_message ? error_message : "unknown", result);
    }
}

/* --- Public API --- */

void achievements_init(JNIEnv *env, jobject thiz) {
    if (ach_state.initialized) {
        ACH_LOGW("Achievements already initialized, deinitializing first");
        achievements_deinit();
    }

    memset(&ach_state, 0, sizeof(ach_state));
    memset(pending_requests, 0, sizeof(pending_requests));

    ach_state.jni_thiz = (*env)->NewGlobalRef(env, thiz);

    jclass cls = (*env)->GetObjectClass(env, thiz);
    ach_state.jni_class = (jclass)(*env)->NewGlobalRef(env, cls);
    (*env)->DeleteLocalRef(env, cls);

    ach_state.on_achievement_event_method = (*env)->GetMethodID(
        env, ach_state.jni_class, "onAchievementEvent",
        "(IJLjava/lang/String;Ljava/lang/String;I)V");

    ach_state.on_http_request_method = (*env)->GetMethodID(
        env, ach_state.jni_class, "onHttpRequest",
        "(ILjava/lang/String;Ljava/lang/String;)V");

    if (!ach_state.on_achievement_event_method || !ach_state.on_http_request_method) {
        ACH_LOGE("Failed to find JNI callback methods");
        (*env)->DeleteGlobalRef(env, ach_state.jni_thiz);
        (*env)->DeleteGlobalRef(env, ach_state.jni_class);
        memset(&ach_state, 0, sizeof(ach_state));
        return;
    }

    ach_state.client = rc_client_create(rc_read_memory, rc_server_call);
    if (!ach_state.client) {
        ACH_LOGE("Failed to create rc_client");
        (*env)->DeleteGlobalRef(env, ach_state.jni_thiz);
        (*env)->DeleteGlobalRef(env, ach_state.jni_class);
        memset(&ach_state, 0, sizeof(ach_state));
        return;
    }

    rc_client_set_event_handler(ach_state.client, rc_event_handler);
    ach_state.initialized = true;
    ACH_LOGI("Achievements subsystem initialized");
}

void achievements_deinit(void) {
    if (!ach_state.initialized) return;

    if (ach_state.client) {
        rc_client_destroy(ach_state.client);
    }

    /* Get a valid JNIEnv for the CURRENT thread. The cached jni_env was
     * captured on the init thread, but deinit may run on a different
     * coroutine dispatcher thread. Using a stale JNIEnv causes SIGSEGV. */
    JNIEnv *env = NULL;
    extern JavaVM *g_jvm;
    if (g_jvm) {
        int status = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            (*g_jvm)->AttachCurrentThread(g_jvm, (void **)&env, NULL);
        }
    }
    if (env) {
        if (ach_state.jni_thiz) {
            (*env)->DeleteGlobalRef(env, ach_state.jni_thiz);
        }
        if (ach_state.jni_class) {
            (*env)->DeleteGlobalRef(env, ach_state.jni_class);
        }
    }

    memset(pending_requests, 0, sizeof(pending_requests));
    memset(&ach_state, 0, sizeof(ach_state));
    ACH_LOGI("Achievements subsystem deinitialized");
}

void achievements_login(const char *username, const char *token) {
    if (!ach_state.initialized || !ach_state.client) {
        ACH_LOGE("Cannot login: achievements not initialized");
        return;
    }

    ACH_LOGI("Logging in as %s", username);
    rc_client_begin_login_with_token(ach_state.client, username, token, login_callback, NULL);
}

void achievements_load_game(const char *hash) {
    if (!ach_state.initialized || !ach_state.client) {
        ACH_LOGE("Cannot load game: achievements not initialized");
        return;
    }

    ACH_LOGI("Loading game with hash: %s", hash);
    rc_client_begin_load_game(ach_state.client, hash, load_game_callback, NULL);
}

void achievements_do_frame(void) {
    if (!ach_state.initialized || !ach_state.client) return;
    rc_client_do_frame(ach_state.client);
}

void achievements_set_hardcore(bool enabled) {
    if (!ach_state.initialized || !ach_state.client) return;
    rc_client_set_hardcore_enabled(ach_state.client, enabled ? 1 : 0);
    ACH_LOGI("Hardcore mode %s", enabled ? "enabled" : "disabled");
}

void achievements_http_complete(int request_id, int response_code, const uint8_t *body, size_t body_length) {
    if (!ach_state.initialized) return;

    for (int i = 0; i < MAX_PENDING_REQUESTS; i++) {
        if (pending_requests[i].active && pending_requests[i].request_id == request_id) {
            rc_api_server_response_t response = {0};
            response.body = (const char *)body;
            response.body_length = body_length;
            response.http_status_code = response_code;

            pending_requests[i].callback(&response, pending_requests[i].callback_data);
            pending_requests[i].active = false;
            return;
        }
    }

    ACH_LOGW("HTTP response for unknown request ID %d", request_id);
}

/* === JNI BINDINGS === */

#define ACH_JNI_FUNC(ret, name) JNIEXPORT ret JNICALL Java_com_spela_player_libretro_LibretroJni_##name

ACH_JNI_FUNC(void, nativeAchievementsInit)(JNIEnv *env, jobject thiz) {
    achievements_init(env, thiz);
}

ACH_JNI_FUNC(void, nativeAchievementsDeinit)(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    achievements_deinit();
}

ACH_JNI_FUNC(void, nativeAchievementsLogin)(JNIEnv *env, jobject thiz, jstring username, jstring token) {
    (void)thiz;
    const char *user = (*env)->GetStringUTFChars(env, username, NULL);
    const char *tok = (*env)->GetStringUTFChars(env, token, NULL);
    achievements_login(user, tok);
    (*env)->ReleaseStringUTFChars(env, username, user);
    (*env)->ReleaseStringUTFChars(env, token, tok);
}

ACH_JNI_FUNC(void, nativeAchievementsLoadGame)(JNIEnv *env, jobject thiz, jstring hash) {
    (void)thiz;
    const char *h = (*env)->GetStringUTFChars(env, hash, NULL);
    achievements_load_game(h);
    (*env)->ReleaseStringUTFChars(env, hash, h);
}

ACH_JNI_FUNC(void, nativeAchievementsDoFrame)(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    achievements_do_frame();
}

ACH_JNI_FUNC(void, nativeAchievementsSetHardcore)(JNIEnv *env, jobject thiz, jboolean enabled) {
    (void)env;
    (void)thiz;
    achievements_set_hardcore(enabled == JNI_TRUE);
}

ACH_JNI_FUNC(void, nativeAchievementsHttpComplete)(JNIEnv *env, jobject thiz,
                                                     jint requestId, jint responseCode,
                                                     jbyteArray responseBody) {
    (void)thiz;
    jsize len = (*env)->GetArrayLength(env, responseBody);
    jbyte *buf = (*env)->GetByteArrayElements(env, responseBody, NULL);
    if (!buf) return;

    achievements_http_complete(requestId, responseCode, (const uint8_t *)buf, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, responseBody, buf, JNI_ABORT);
}
