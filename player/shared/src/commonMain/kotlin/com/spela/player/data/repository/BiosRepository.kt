package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.BiosStatusResponse
import com.spela.player.domain.model.BiosConsoleStatus
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.util.FileStorage

open class BiosRepository(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) {
    private var cachedBiosStatus: BiosStatusResponse? = null

    open suspend fun syncBiosFiles() {
        val serverFiles = try {
            apiClient.getBiosStatus().files
        } catch (e: Exception) {
            return // Server may not support BIOS yet
        }
        val biosDir = fileStorage.getBiosDir()
        for (file in serverFiles) {
            val localPath = "$biosDir/${file.name}"
            if (!fileStorage.fileExists(localPath)) {
                try {
                    val data = apiClient.downloadBiosFile(file.name)
                    fileStorage.writeFile(localPath, data)
                } catch (e: Exception) {
                    println("[Bios] Failed to download ${file.name}: ${e.message}")
                }
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
    open suspend fun fetchBiosStatus(): BiosStatusResponse? {
        return try {
            val status = apiClient.getBiosStatus()
            cachedBiosStatus = status
            status
        } catch (e: Exception) {
            println("[Bios] Failed to fetch BIOS status: ${e.message}")
            cachedBiosStatus
        }
    }

    fun getCachedBiosStatus(): BiosStatusResponse? = cachedBiosStatus

    /**
     * Returns the BIOS status for a given console. Checks which required files
     * are missing both locally and on the server.
     */
    open suspend fun getConsoleStatus(consoleId: String): BiosConsoleStatus? {
        val status = cachedBiosStatus ?: fetchBiosStatus() ?: return null
        val console = status.consoles.find { it.consoleId == consoleId } ?: return null

        val missingFiles = console.files
            .filter { it.required && it.status == "missing" }
            .map { BiosMissingFile(it.fileName, it.description, it.required) }

        // Also check local files - some may have been synced already
        val localFiles = getLocalBiosFiles()
        val actuallyMissing = missingFiles.filter { missing ->
            missing.fileName !in localFiles
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
            val localPath = "$biosDir/${missing.fileName}"
            if (!fileStorage.fileExists(localPath)) {
                try {
                    val data = apiClient.downloadBiosFile(missing.fileName)
                    fileStorage.writeFile(localPath, data)
                } catch (e: Exception) {
                    stillMissing.add(missing)
                }
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

        return status.consoles
            .filter { it.biosRequired && it.status == "missing" }
            .mapNotNull { console ->
                val missingFiles = console.files
                    .filter { it.required && it.status == "missing" }
                    .filter { it.fileName !in localFiles }
                    .map { BiosMissingFile(it.fileName, it.description, it.required) }

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
