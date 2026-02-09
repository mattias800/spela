package com.spela.player.util

/**
 * Extracts the first file from a ZIP archive byte array.
 * Used for extracting libretro core .so files from buildbot .zip downloads.
 */
expect fun extractFirstZipEntry(zipData: ByteArray): ByteArray
