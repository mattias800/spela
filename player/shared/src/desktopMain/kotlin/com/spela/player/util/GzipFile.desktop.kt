package com.spela.player.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Synchronous I/O — caller is responsible for invoking on a worker
 * dispatcher (in production both Android and Desktop callers wrap this
 * in `withContext(dispatchers.io) { ... }`). Switching dispatchers
 * inside the helper would race with kotlinx-coroutines-test's
 * [advanceUntilIdle], so we keep the body on the caller's coroutine.
 */
actual suspend fun gzipFile(srcPath: String, destPath: String): Long {
    FileInputStream(srcPath).use { src ->
        BufferedInputStream(src).use { bin ->
            FileOutputStream(destPath).use { out ->
                GZIPOutputStream(BufferedOutputStream(out)).use { gz ->
                    bin.copyTo(gz)
                }
            }
        }
    }
    return File(destPath).length()
}

actual suspend fun gunzipFile(srcPath: String, destPath: String): Long {
    FileInputStream(srcPath).use { src ->
        GZIPInputStream(BufferedInputStream(src)).use { gz ->
            FileOutputStream(destPath).use { out ->
                BufferedOutputStream(out).use { bout ->
                    gz.copyTo(bout)
                }
            }
        }
    }
    return File(destPath).length()
}
