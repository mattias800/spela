package com.spela.player.util

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual fun extractFirstZipEntry(zipData: ByteArray): ByteArray {
    ZipInputStream(ByteArrayInputStream(zipData)).use { zis ->
        val entry = zis.nextEntry ?: throw IllegalStateException("ZIP archive is empty")
        val data = zis.readBytes()
        zis.closeEntry()
        return data
    }
}
