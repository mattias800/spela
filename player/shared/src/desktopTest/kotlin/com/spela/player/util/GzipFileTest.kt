package com.spela.player.util

import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
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

    @Test
    fun gunzipReversesGzip() = runTest {
        val raw = File.createTempFile("gz-raw-", ".bin").apply { deleteOnExit() }
        val gzipped = File.createTempFile("gz-mid-", ".gz").apply { deleteOnExit() }
        val restored = File.createTempFile("gz-out-", ".bin").apply { deleteOnExit() }
        val payload = ByteArray(96 * 1024) { (it * 7 % 251).toByte() }
        raw.writeBytes(payload)

        gzipFile(raw.absolutePath, gzipped.absolutePath)
        val restoredSize = gunzipFile(gzipped.absolutePath, restored.absolutePath)

        assertTrue(restoredSize == payload.size.toLong())
        assertContentEquals(payload, restored.readBytes())
    }

    @Test
    fun gunzipReadsThirdPartyGzip() = runTest {
        // Bytes produced outside our pipeline must also inflate cleanly,
        // since the server may eventually re-encode saves itself.
        val gzipped = File.createTempFile("gz-third-", ".gz").apply { deleteOnExit() }
        val restored = File.createTempFile("gz-third-out-", ".bin").apply { deleteOnExit() }
        val payload = "hello world from elsewhere".repeat(2000).toByteArray()
        GZIPOutputStream(FileOutputStream(gzipped)).use { it.write(payload) }

        val restoredSize = gunzipFile(gzipped.absolutePath, restored.absolutePath)

        assertTrue(restoredSize == payload.size.toLong())
        assertContentEquals(payload, restored.readBytes())
    }
}
