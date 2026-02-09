package com.spela.player.libretro

/**
 * JNI declarations for the native libretro bridge.
 * The native library "spela-libretro" implements these methods.
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
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean

    /* Video */
    external fun nativeGetVideoFrame(): ByteArray?
    external fun nativeFillVideoFrame(out: ByteArray): Int
    external fun nativeGetVideoWidth(): Int
    external fun nativeGetVideoHeight(): Int
    external fun nativeGetPixelFormat(): Int

    /* Audio */
    external fun nativeGetAudioBuffer(): ShortArray?
    external fun nativeFillAudioBuffer(out: ShortArray): Int

    /* Input */
    external fun nativeSetInputButton(port: Int, id: Int, pressed: Boolean)
    external fun nativeSetInputAnalog(port: Int, index: Int, id: Int, value: Short)

    /* Info */
    external fun nativeGetTargetFps(): Double
    external fun nativeGetSampleRate(): Double
    external fun nativeGetCoreName(): String?

    /* Paths */
    external fun nativeSetSystemDir(dir: String)
    external fun nativeSetSaveDir(dir: String)

    /* Memory */
    external fun nativeGetSRAM(): ByteArray?
}
