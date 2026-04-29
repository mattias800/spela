package com.spela.player.util

import io.ktor.utils.io.ByteReadChannel

/**
 * Open a [ByteReadChannel] backed by the file at [path]. Used to stream
 * a multipart upload directly from disk without ever loading the full
 * file into a JVM ByteArray — the path that fixes #798 (GameCube saves
 * are ~90 MB and don't fit alongside the rest of the heap).
 *
 * Implemented per platform; both Android and Desktop wrap a JVM
 * FileInputStream via ktor's `toByteReadChannel`.
 */
expect fun openFileReadChannel(path: String): ByteReadChannel
