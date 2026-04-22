package com.spela.player.platform

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopFileStorageTest {

    @Test
    fun xdgDataHomeIsRespected() {
        val result = resolveLinuxDataDir("/home/user", "/custom/data")
        assertEquals(File("/custom/data", "spela"), result)
    }

    @Test
    fun xdgDataHomeFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", null)
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }

    @Test
    fun xdgDataHomeBlankFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", "")
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }

    @Test
    fun xdgDataHomeWhitespaceFallsBackToDefault() {
        val result = resolveLinuxDataDir("/home/user", "   ")
        assertEquals(File("/home/user/.local/share", "spela"), result)
    }

    // sha256File is the building block for #555 Phase 2 cache invalidation —
    // lock the hash format and the error-tolerance contract (null on
    // missing file) so the repository layer can rely on a stable
    // "cannot decide" signal instead of having to catch exceptions.

    @Test
    fun sha256OfKnownPayloadMatchesReference() = runTest {
        val fs = DesktopFileStorage()
        val tmp = Files.createTempFile("spela-sha-test", ".bin").toFile()
        try {
            // "spela" — sha256 verified via `printf 'spela' | shasum -a 256`.
            tmp.writeBytes("spela".toByteArray(Charsets.UTF_8))
            val hash = fs.sha256File(tmp.absolutePath)
            assertEquals(
                "faba5d28404544d90cb933b3027f668cea4b3ec74dc032582605f9f6a68e87f7",
                hash,
            )
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun sha256OfEmptyFileMatchesReference() = runTest {
        val fs = DesktopFileStorage()
        val tmp = Files.createTempFile("spela-sha-test-empty", ".bin").toFile()
        try {
            val hash = fs.sha256File(tmp.absolutePath)
            // Empty-string sha256 reference value.
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                hash,
            )
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun sha256ReturnsNullForMissingFile() = runTest {
        val fs = DesktopFileStorage()
        val path = "/tmp/spela-does-not-exist-${System.nanoTime()}"
        assertNull(fs.sha256File(path))
    }

    @Test
    fun sha256ReturnsNullForDirectory() = runTest {
        val fs = DesktopFileStorage()
        val dir = Files.createTempDirectory("spela-sha-dir").toFile()
        try {
            assertNull(fs.sha256File(dir.absolutePath))
        } finally {
            dir.delete()
        }
    }
}
