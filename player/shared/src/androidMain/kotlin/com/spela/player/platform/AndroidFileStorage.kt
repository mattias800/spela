package com.spela.player.platform

import android.content.Context
import com.spela.player.util.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidFileStorage(private val context: Context) : FileStorage {

    private val baseDir: File
        get() = context.filesDir

    override fun getGamesDir(): String = File(baseDir, "games").apply { mkdirs() }.absolutePath
    override fun getCoresDir(): String = File(baseDir, "cores").apply { mkdirs() }.absolutePath
    override fun getSavesDir(): String = File(baseDir, "saves").apply { mkdirs() }.absolutePath
    override fun getBiosDir(): String = File(baseDir, "bios").apply { mkdirs() }.absolutePath

    override suspend fun createDirectory(path: String) = withContext(Dispatchers.IO) {
        File(path).mkdirs()
        Unit
    }

    override suspend fun writeFile(path: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    override suspend fun atomicWriteFile(path: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val target = File(path)
        target.parentFile?.mkdirs()
        val tmp = File(path + ".tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(target)) {
            // POSIX-correct rename should overwrite atomically. If renameTo
            // refuses (e.g. cross-device or concurrent issue), fall back to
            // a copy + delete so the write still completes — non-atomic but
            // matches the prior in-place write semantics. Then propagate.
            target.delete()
            if (!tmp.renameTo(target)) {
                target.writeBytes(data)
                tmp.delete()
            }
        }
        Unit
    }

    override suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    override suspend fun fileExists(path: String): Boolean = File(path).exists()

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        File(path).delete()
        Unit
    }

    override suspend fun deleteDirectory(path: String) = withContext(Dispatchers.IO) {
        File(path).deleteRecursively()
        Unit
    }

    override suspend fun getDirectorySize(path: String): Long = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) return@withContext 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { fos ->
            writer { bytes, offset, length ->
                fos.write(bytes, offset, length)
            }
        }
    }

    override suspend fun appendFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()
        // append = true: continue after the existing bytes on disk (resume), #1296.
        java.io.FileOutputStream(file, true).use { fos ->
            writer { bytes, offset, length ->
                fos.write(bytes, offset, length)
            }
        }
    }

    override suspend fun getFileSize(path: String): Long = File(path).length()

    override suspend fun listFiles(path: String): List<String> = withContext(Dispatchers.IO) {
        File(path).listFiles()?.map { it.name } ?: emptyList()
    }

    override suspend fun isDirectory(path: String): Boolean = File(path).isDirectory

    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext null
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return@withContext null
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            for (file in files) {
                val entryName = file.relativeTo(dir).path
                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        baos.toByteArray()
    }

    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) = withContext(Dispatchers.IO) {
        val target = File(targetDir)
        target.mkdirs()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(target, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {
        withContext(Dispatchers.IO) {
            val src = File(zipPath)
            val dest = File(destPath)
            dest.parentFile?.mkdirs()
            // Use ZipFile rather than ZipInputStream so we don't have to
            // walk the archive sequentially — buildbot cores are stored
            // uncompressed, so ZipFile lets us copy the entry's
            // InputStream straight to the destination FileOutputStream
            // with a small buffer (java.io.InputStream.copyTo defaults
            // to 8 KB), keeping the heap allocation bounded.
            //
            // Extract to a temp sibling and rename into place. The
            // destination may be a libretro core .so that is (or was)
            // dlopen'ed — some cores stay mapped even after dlclose.
            // Overwriting the inode in place while mapped makes the dynamic
            // linker SIGBUS on the changed pages (observed on desktop in
            // _dl_lookup_map when the core updater replaced
            // azahar_libretro.so on game resume); a rename swaps the
            // directory entry and leaves the mapped old inode intact.
            val tmp = File("$destPath.extract.tmp")
            java.util.zip.ZipFile(src).use { zf ->
                val entry = zf.entries().asSequence().firstOrNull()
                    ?: throw IllegalStateException("ZIP archive is empty: $zipPath")
                zf.getInputStream(entry).use { input ->
                    tmp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                // Same fallback semantics as atomicWriteFile: rename should
                // overwrite atomically on POSIX; if it refuses, fall back to
                // delete + rename so the extract still completes.
                dest.delete()
                if (!tmp.renameTo(dest)) {
                    throw IllegalStateException("Failed to move extracted core into place: $destPath")
                }
            }
        }
    }

    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long =
        withContext(Dispatchers.IO) {
            val src = File(dirPath)
            if (!src.exists() || !src.isDirectory) return@withContext 0L
            val dest = File(destPath)
            dest.parentFile?.mkdirs()
            val files = src.walkTopDown().filter { it.isFile }.toList()
            if (files.isEmpty()) return@withContext 0L
            dest.outputStream().buffered().use { out ->
                for (f in files) {
                    val rel = f.relativeTo(src).invariantSeparatorsPath
                    writeUstarHeader(out, rel, f.length())
                    f.inputStream().use { input -> input.copyTo(out) }
                    val pad = ((512 - (f.length() % 512)) % 512).toInt()
                    if (pad > 0) out.write(ByteArray(pad))
                }
                // Two zero blocks mark the end of the archive.
                out.write(ByteArray(1024))
            }
            dest.length()
        }

    override suspend fun extractTarFile(tarPath: String, destDir: String) =
        withContext(Dispatchers.IO) {
            val src = File(tarPath)
            if (!src.exists() || src.length() == 0L) return@withContext
            val dest = File(destDir)
            dest.mkdirs()
            src.inputStream().buffered().use { input ->
                while (true) {
                    val header = ByteArray(512)
                    if (!readFully(input, header)) break
                    if (header.all { it == 0.toByte() }) break
                    val name = parseTarString(header, 0, 100)
                    if (name.isEmpty()) break
                    val sizeStr = parseTarString(header, 124, 12)
                    val size = sizeStr.toLongOrNull(8) ?: 0L
                    if (size > 0) {
                        val outFile = File(dest, name)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { out ->
                            var remaining = size
                            val buf = ByteArray(64 * 1024)
                            while (remaining > 0) {
                                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                val read = input.read(buf, 0, toRead)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                remaining -= read
                            }
                        }
                        // Skip padding to next 512-byte boundary.
                        val pad = ((512 - (size % 512)) % 512).toInt()
                        if (pad > 0) {
                            val skip = ByteArray(pad)
                            readFully(input, skip)
                        }
                    }
                }
            }
        }

    override suspend fun sha256File(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.isFile) return@withContext null
        runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}

/**
 * Writes a USTAR-format header for a single file entry. Hand-rolled so we
 * don't pull in Apache Commons Compress just for this; the format is well-
 * defined (POSIX 1003.1-1988) and the subset we use (regular files only,
 * no symlinks, no hardlinks, paths ≤ 100 chars) fits in a few lines.
 *
 * If [name] exceeds 100 bytes it's truncated — keep paths short. The
 * fixed-format checksum is computed by summing every byte of the header
 * with the checksum field treated as eight ASCII spaces, then writing the
 * sum as a six-digit octal followed by NUL + space.
 */
private fun writeUstarHeader(out: java.io.OutputStream, name: String, size: Long) {
    val hdr = ByteArray(512)
    val nameBytes = name.encodeToByteArray()
    val nameLen = minOf(nameBytes.size, 100)
    System.arraycopy(nameBytes, 0, hdr, 0, nameLen)
    // 420 dec == 0644 oct (Kotlin has no octal literal). Standard rw-r--r--
    // permission bits — match what RetroArch writes to its save dir tarballs.
    putOctal(hdr, 100, 8, 420L)               // mode
    putOctal(hdr, 108, 8, 0L)                 // uid
    putOctal(hdr, 116, 8, 0L)                 // gid
    putOctal(hdr, 124, 12, size)              // size
    putOctal(hdr, 136, 12, 0L)                // mtime — irrelevant for save bundles, keep stable
    // checksum field is initially 8 spaces while computing
    for (i in 148 until 156) hdr[i] = ' '.code.toByte()
    hdr[156] = '0'.code.toByte()              // typeflag = regular file
    val ustar = "ustar "
    val ver = "00"
    for ((i, c) in ustar.withIndex()) hdr[257 + i] = c.code.toByte()
    for ((i, c) in ver.withIndex()) hdr[263 + i] = c.code.toByte()
    var sum = 0
    for (b in hdr) sum += (b.toInt() and 0xFF)
    val sumStr = "%06o".format(sum)
    for ((i, c) in sumStr.withIndex()) hdr[148 + i] = c.code.toByte()
    hdr[154] = 0
    hdr[155] = ' '.code.toByte()
    out.write(hdr)
}

private fun putOctal(buf: ByteArray, off: Int, len: Int, value: Long) {
    val s = "%0${len - 1}o".format(value)
    val bytes = s.encodeToByteArray()
    val n = minOf(bytes.size, len - 1)
    System.arraycopy(bytes, 0, buf, off, n)
    buf[off + len - 1] = 0
}

private fun parseTarString(buf: ByteArray, off: Int, len: Int): String {
    var end = off
    while (end < off + len && buf[end] != 0.toByte()) end++
    return String(buf, off, end - off).trim()
}

private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
    var read = 0
    while (read < buf.size) {
        val n = input.read(buf, read, buf.size - read)
        if (n < 0) return read > 0 && read == buf.size
        read += n
    }
    return true
}
