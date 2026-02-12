#ifndef LIBRETRO_ACHIEVEMENTS_H
#define LIBRETRO_ACHIEVEMENTS_H

#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

/* Initialize the achievements subsystem. Must be called from JNI thread. */
void achievements_init(JNIEnv *env, jobject thiz);

/* Shut down the achievements subsystem. */
void achievements_deinit(void);

/* Login to RA with existing token. */
void achievements_login(const char *username, const char *token);

/* Load game achievements by ROM hash. */
void achievements_load_game(const char *hash);

/* Process one frame (call after retro_run). */
void achievements_do_frame(void);

/* Toggle hardcore mode. */
void achievements_set_hardcore(bool enabled);

/* Called from Kotlin when HTTP request completes. */
void achievements_http_complete(int request_id, int response_code, const uint8_t *body, size_t body_length);

#endif /* LIBRETRO_ACHIEVEMENTS_H */
