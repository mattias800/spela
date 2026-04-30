package com.spela.player.util

/**
 * Gzip-compress the file at [srcPath], writing the deflated stream to
 * [destPath]. Returns the size in bytes of the destination file.
 *
 * Implemented per platform on top of `java.util.zip.GZIPOutputStream`
 * for both Android and Desktop. The streaming form is used so the JVM
 * never has to hold the full save in a ByteArray — important for the
 * Dolphin GameCube ~90 MB case that drove #798.
 *
 * Throws on any I/O failure. Callers are expected to catch and fall
 * back to uploading the uncompressed save (a transient gzip failure
 * must not block the save itself). See #804 phase 2.
 */
expect suspend fun gzipFile(srcPath: String, destPath: String): Long
