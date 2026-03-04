package com.spela.player.libretro

/**
 * JNI declarations for the native libretro bridge (desktop).
 * Identical to the Android version -- the native library "spela-libretro"
 * exports JNI symbols matching this class name.
 */
class LibretroJni {

    companion object {
        init {
            System.loadLibrary("spela-libretro")
        }
    }

    /* Core lifecycle */
    external fun nativeLoadCore(corePath: String): Boolean
    external fun nativeInit()
    external fun nativeLoadGame(gamePath: String): Boolean
    external fun nativeRun()
    external fun nativeReset()
    external fun nativeUnloadGame()
    external fun nativeDeinit()

    /* Save state */
    external fun nativeSerializeSize(): Long
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean

    /* Video */
    external fun nativeGetVideoFrame(): ByteArray?
    external fun nativeGetVideoWidth(): Int
    external fun nativeGetVideoHeight(): Int
    external fun nativeGetPixelFormat(): Int

    /* Audio */
    external fun nativeGetAudioBuffer(): ShortArray?

    /* Input */
    external fun nativeSetInputButton(port: Int, id: Int, pressed: Boolean)
    external fun nativeGetInputButton(port: Int, id: Int): Boolean
    external fun nativeSetInputAnalog(port: Int, index: Int, id: Int, value: Short)
    external fun nativeGetInputAnalog(port: Int, index: Int, id: Int): Short
    external fun nativeSetInputPointer(port: Int, x: Int, y: Int, pressed: Boolean)

    /* Core variables */
    external fun nativeSetCoreVariable(key: String, value: String)

    /* Info */
    external fun nativeGetTargetFps(): Double
    external fun nativeGetSampleRate(): Double
    external fun nativeGetCoreName(): String?
    external fun nativeGetAspectRatio(): Float

    /* Paths */
    external fun nativeSetSystemDir(dir: String)
    external fun nativeSetSaveDir(dir: String)

    /* Memory */
    external fun nativeGetSRAM(): ByteArray?
    external fun nativeSetSRAM(data: ByteArray): Boolean

    /* GPU Renderer */
    external fun nativeGpuInit(surface: Any): Boolean
    external fun nativeGpuInitOffscreen(width: Int, height: Int): Boolean
    external fun nativeGpuRender()
    external fun nativeGpuRenderToBgra(outData: ByteArray): Int
    external fun nativeGpuSetShader(shaderId: Int)
    external fun nativeGpuResize(width: Int, height: Int)
    external fun nativeGpuDeinit()
    external fun nativeGpuIsActive(): Boolean
    external fun nativeGpuSetSourceRect(x: Int, y: Int, w: Int, h: Int)

    /* Gamepad (SDL2) */
    external fun nativeGamepadInit(): Boolean
    external fun nativeGamepadShutdown()
    external fun nativeGamepadPoll(): Array<GamepadState>?

    /* Cheats */
    external fun nativeCheatReset()
    external fun nativeCheatSet(index: Int, enabled: Boolean, code: String)

    /* Achievements */
    external fun nativeAchievementsInit()
    external fun nativeAchievementsDeinit()
    external fun nativeAchievementsLogin(username: String, token: String)
    external fun nativeAchievementsLoadGame(hash: String)
    external fun nativeAchievementsDoFrame()
    external fun nativeAchievementsSetHardcore(enabled: Boolean)
    external fun nativeAchievementsHttpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray)

    var achievementEventListener: ((Int, String?, String?, Int) -> Unit)? = null
    var httpRequestListener: ((Int, String, String?) -> Unit)? = null

    // Called from native code when an achievement event occurs
    fun onAchievementEvent(eventType: Int, title: String?, description: String?, points: Int) {
        achievementEventListener?.invoke(eventType, title, description, points)
    }

    // Called from native code when rcheevos needs an HTTP request
    fun onHttpRequest(requestId: Int, url: String, postData: String?) {
        httpRequestListener?.invoke(requestId, url, postData)
    }
}
