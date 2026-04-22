package com.spela.player.platform

import com.spela.player.util.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopFileStorage : FileStorage {

    private val baseDir: File by lazy {
        val home = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        val appDir = when {
            osName.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Spela")
            osName.contains("mac") -> File(home, "Library/Application Support/Spela")
            else -> resolveLinuxDataDir(home)
        }
        appDir.apply { mkdirs() }
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

internal fun resolveLinuxDataDir(home: String, xdgDataHome: String? = System.getenv("XDG_DATA_HOME")): File {
    val dataDir = xdgDataHome?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
    return File(dataDir, "spela")
}
