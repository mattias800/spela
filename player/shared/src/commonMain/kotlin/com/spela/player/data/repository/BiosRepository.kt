package com.spela.player.data.repository

import com.spela.client.models.BiosListResponse
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.model.BiosConsoleStatus
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.util.FileStorage

open class BiosRepository(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) {
    private var cachedBiosStatus: BiosListResponse? = null

    open suspend fun syncBiosFiles() {
        val serverFiles = try {
            apiClient.getBiosStatus().files
        } catch (e: Exception) {
            return // Server may not support BIOS yet
        }
        val biosDir = fileStorage.getBiosDir()
        for (file in serverFiles) {
            // Skip files the server itself reports as absent. The registry
            // lists optional BIOS the server may not hold (PS2, X68000, …);
            // attempting to download a "missing" entry just 404s and spams
            // the log on every startup (#1207).
            // Skip files the server itself reports as absent. The registry
            // lists optional BIOS the server may not hold (PS2, X68000, …);
            // attempting to download a "missing" entry just 404s and spams
            // the log on every startup (#1207).
            if (file.status == "missing") continue
            val localDir = if (!file.subDir.isNullOrEmpty()) "$biosDir/${file.subDir}" else biosDir
            val localPath = "$localDir/${file.name}"
            if (fileStorage.fileExists(localPath)) continue
            try {
                if (!file.subDir.isNullOrEmpty()) fileStorage.createDirectory(localDir)
                if (file.bundle) {
                    // Bundle entries are a directory tree on the
                    // server (PPSSPP system assets, etc.). Fetch
                    // the whole tree as a zip and extract into
                    // `<biosDir>/<SubDir>/`. The sentinel at
                    // `localPath` lives somewhere inside the zip,
                    // so this also satisfies the next pass's
                    // fileExists check. See #911.
                    val archive = apiClient.downloadBiosArchive(file.name)
                    fileStorage.unzipBytesToDirectory(archive, localDir)
                } else {
                    val data = apiClient.downloadBiosFile(file.name)
                    fileStorage.writeFile(localPath, data)
                }
            } catch (e: Exception) {
                println("[Bios] Failed to download ${file.name}: ${e.message}")
            }
        }
    }

    suspend fun getLocalBiosFiles(): List<String> {
        val biosDir = fileStorage.getBiosDir()
        return fileStorage.listFiles(biosDir)
    }

    /**
     * Fetches enriched BIOS status from the server. Caches the result.
     */
    open suspend fun fetchBiosStatus(): BiosListResponse? {
        return try {
            val status = apiClient.getBiosStatus()
            cachedBiosStatus = status
            status
        } catch (e: Exception) {
            println("[Bios] Failed to fetch BIOS status: ${e.message}")
            cachedBiosStatus
        }
    }

    fun getCachedBiosStatus(): BiosListResponse? = cachedBiosStatus

    /**
     * Returns the BIOS status for a given console. Checks which required files
     * are missing both locally and on the server.
     */
    open suspend fun getConsoleStatus(consoleId: String): BiosConsoleStatus? {
        val status = cachedBiosStatus ?: fetchBiosStatus() ?: return null
        val console = status.consoles.find { it.consoleId == consoleId } ?: return null
        val biosDir = fileStorage.getBiosDir()

        val missingFiles = console.files
            .filter { it.required && it.status == "missing" }
            .map { BiosMissingFile(it.fileName, it.description, it.required, it.subDir, it.bundle) }

        // Also check local files - some may have been synced already
        val localFiles = getLocalBiosFiles()
        val actuallyMissing = missingFiles.filter { missing ->
            val localPath = if (!missing.subDir.isNullOrEmpty()) {
                "$biosDir/${missing.subDir}/${missing.fileName}"
            } else {
                "$biosDir/${missing.fileName}"
            }
            missing.fileName !in localFiles && !fileStorage.fileExists(localPath)
        }

        return BiosConsoleStatus(
            consoleId = console.consoleId,
            consoleName = console.consoleName,
            biosRequired = console.biosRequired,
            status = if (actuallyMissing.isEmpty() && console.biosRequired) "ready" else console.status,
            missingFiles = actuallyMissing,
        )
    }

    /**
     * Pre-launch BIOS check. Tries to download missing required files on-the-fly.
     * Returns the list of still-missing files after the attempt.
     */
    open suspend fun preLaunchBiosCheck(consoleId: String): List<BiosMissingFile> {
        val consoleStatus = getConsoleStatus(consoleId) ?: return emptyList()

        if (!consoleStatus.biosRequired || consoleStatus.missingFiles.isEmpty()) {
            return emptyList()
        }

        // Try to download missing files from server
        val biosDir = fileStorage.getBiosDir()
        val stillMissing = mutableListOf<BiosMissingFile>()

        for (missing in consoleStatus.missingFiles) {
            val localDir = if (!missing.subDir.isNullOrEmpty()) "$biosDir/${missing.subDir}" else biosDir
            val localPath = "$localDir/${missing.fileName}"
            if (fileStorage.fileExists(localPath)) continue
            try {
                if (!missing.subDir.isNullOrEmpty()) fileStorage.createDirectory(localDir)
                if (missing.bundle) {
                    val archive = apiClient.downloadBiosArchive(missing.fileName)
                    fileStorage.unzipBytesToDirectory(archive, localDir)
                } else {
                    val data = apiClient.downloadBiosFile(missing.fileName)
                    fileStorage.writeFile(localPath, data)
                }
            } catch (e: Exception) {
                stillMissing.add(missing)
            }
        }

        return stillMissing
    }

    /**
     * Returns a map of consoleId -> BiosConsoleStatus for all consoles
     * that have missing required BIOS files.
     */
    open suspend fun getConsolesWithMissingBios(): Map<String, BiosConsoleStatus> {
        val status = cachedBiosStatus ?: fetchBiosStatus() ?: return emptyMap()
        val localFiles = getLocalBiosFiles()
        val biosDir = fileStorage.getBiosDir()

        return status.consoles
            .filter { it.biosRequired && it.status == "missing" }
            .mapNotNull { console ->
                val missingFiles = console.files
                    .filter { it.required && it.status == "missing" }
                    .filter { file ->
                        val localPath = if (!file.subDir.isNullOrEmpty()) {
                            "$biosDir/${file.subDir}/${file.fileName}"
                        } else {
                            "$biosDir/${file.fileName}"
                        }
                        file.fileName !in localFiles && !fileStorage.fileExists(localPath)
                    }
                    .map { BiosMissingFile(it.fileName, it.description, it.required, it.subDir, it.bundle) }

                if (missingFiles.isNotEmpty()) {
                    console.consoleId to BiosConsoleStatus(
                        consoleId = console.consoleId,
                        consoleName = console.consoleName,
                        biosRequired = true,
                        status = "missing",
                        missingFiles = missingFiles,
                    )
                } else null
            }
            .toMap()
    }
}
