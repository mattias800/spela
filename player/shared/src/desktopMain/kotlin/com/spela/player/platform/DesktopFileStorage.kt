package com.spela.player.platform

import com.spela.player.util.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DesktopFileStorage : FileStorage {

    private val baseDir: File by lazy {
        resolveDesktopDataDir().apply { mkdirs() }
    }

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
        // Files.move with ATOMIC_MOVE + REPLACE_EXISTING gives a POSIX-style
        // atomic rename when supported by the filesystem; the existing
        // contents at [path] are never observable in a partially-written
        // state by a concurrent reader.
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
            // See AndroidFileStorage for rationale — the ZipFile +
            // streamed copy keeps the heap allocation bounded so the
            // largest libretro cores (#849, scummvm ~134 MB) don't OOM
            // when buffered as a ByteArray.
            //
            // Extract to a temp sibling and atomically rename into place.
            // The destination may be a libretro core .so that is (or was)
            // dlopen'ed — some cores stay mapped even after dlclose
            // (RTLD_NODELETE / static TLS). Overwriting the inode in place
            // while mapped makes the dynamic linker SIGBUS on the changed
            // pages (observed in _dl_lookup_map when the core updater
            // replaced azahar_libretro.so on game resume). A rename swaps
            // the directory entry and leaves the mapped old inode intact.
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
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Unit
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
                        val pad = ((512 - (size % 512)) % 512).toInt()
                        if (pad > 0) {
                            val skip = ByteArray(pad)
                            readFully(input, skip)
                        }
                    }
                }
            }
        }
}

// Tar helpers — see AndroidFileStorage for rationale on the hand-rolled
// USTAR writer / reader. Duplicated here so both platforms can ship the
// save_dir bundle (#864) without adding Apache Commons Compress.

private fun writeUstarHeader(out: java.io.OutputStream, name: String, size: Long) {
    val hdr = ByteArray(512)
    val nameBytes = name.encodeToByteArray()
    val nameLen = minOf(nameBytes.size, 100)
    System.arraycopy(nameBytes, 0, hdr, 0, nameLen)
    // 420 dec == 0644 oct (Kotlin has no octal literal).
    putOctal(hdr, 100, 8, 420L)
    putOctal(hdr, 108, 8, 0L)
    putOctal(hdr, 116, 8, 0L)
    putOctal(hdr, 124, 12, size)
    putOctal(hdr, 136, 12, 0L)
    for (i in 148 until 156) hdr[i] = ' '.code.toByte()
    hdr[156] = '0'.code.toByte()
    val ustar = "ustar "
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

internal fun resolveLinuxDataDir(home: String, xdgDataHome: String? = System.getenv("XDG_DATA_HOME")): File {
    val dataDir = xdgDataHome?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
    return File(dataDir, "spela")
}

/**
 * The per-OS app data directory. Everything the desktop app persists —
 * downloaded files (games/cores/saves) and the SQLDelight database — must
 * live under this directory, never at a path relative to the process cwd:
 * a packaged app's launch cwd is not stable across launches, so anything
 * written there silently "resets" (#1676).
 */
internal fun resolveDesktopDataDir(
    home: String = System.getProperty("user.home"),
    osName: String = System.getProperty("os.name").lowercase(),
    appData: String? = System.getenv("APPDATA"),
): File = when {
    osName.contains("win") -> File(appData ?: "$home/AppData/Roaming", "Spela")
    osName.contains("mac") -> File(home, "Library/Application Support/Spela")
    else -> resolveLinuxDataDir(home)
}
