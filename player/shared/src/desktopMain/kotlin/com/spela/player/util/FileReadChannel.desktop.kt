package com.spela.player.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.FileInputStream

actual fun openFileReadChannel(path: String): ByteReadChannel =
    FileInputStream(path).toByteReadChannel()
