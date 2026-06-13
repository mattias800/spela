#ifndef GAMEPAD_SDL3_H
#define GAMEPAD_SDL3_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadInit(JNIEnv *env, jobject obj);
JNIEXPORT void JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadShutdown(JNIEnv *env, jobject obj);
JNIEXPORT jobjectArray JNICALL Java_com_spela_player_libretro_LibretroJni_nativeGamepadPoll(JNIEnv *env, jobject obj);

#ifdef __cplusplus
}
#endif

#endif /* GAMEPAD_SDL3_H */
