package com.spela.player.util

import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class GzipFileTest {

    @Test
    fun roundTripsRandomBytes() = runTest {
        val src = File.createTempFile("gz-src-", ".bin").apply { deleteOnExit() }
        val dst = File.createTempFile("gz-dst-", ".gz").apply { deleteOnExit() }
        // Pseudo-random bytes resist the dictionary so the gzip output
        // isn't trivially small; this also catches buffering bugs that
        // would silently truncate at a typical 8 KiB chunk boundary.
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        src.writeBytes(payload)

        val gzippedSize = gzipFile(src.absolutePath, dst.absolutePath)

        assertTrue(gzippedSize > 0, "gzipped output should be non-empty")
        assertTrue(gzippedSize == dst.length(), "returned size should match on-disk size")

        val roundTripped = GZIPInputStream(FileInputStream(dst)).use { it.readBytes() }
        assertContentEquals(payload, roundTripped)
    }
}
