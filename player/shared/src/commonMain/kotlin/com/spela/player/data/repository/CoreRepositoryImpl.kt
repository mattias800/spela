package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.repository.CorePrunedException
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.util.FileStorage
import com.spela.player.util.buildbotCoreUrl
import com.spela.player.util.coreFileName
import com.spela.player.util.extractFirstZipEntry
import com.spela.player.util.resolveDownloadUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

class CoreRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
    private val httpClient: HttpClient,
) : CoreRepository {

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> = runCatching {
        apiClient.getAvailableCores().map { it.toDomain() }
    }

    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> {
        println("[CoreRepo] getRecommendedCore start (gameId=$gameId)")
        return try {
            val core = apiClient.getRecommendedCore(gameId)
            println("[CoreRepo] getRecommendedCore returned: name=${core.name} displayName=${core.displayName}")
            Result.success(core)
        } catch (t: Throwable) {
            println("[CoreRepo] getRecommendedCore FAILED: ${t::class.simpleName}: ${t.message}")
            Result.failure(t)
        }
    }

    override suspend fun downloadCore(coreName: String, customDownloadUrl: String?, onProgress: (Float) -> Unit): Result<String> = runCatching {
        val fileName = coreFileName(coreName)
        val destPath = fileStorage.getCoresDir() + "/$fileName"
        val tempZipPath = "$destPath.zip.tmp"
        val url = if (!customDownloadUrl.isNullOrBlank()) resolveDownloadUrl(customDownloadUrl) else buildbotCoreUrl(coreName)
        println("[CoreRepo] downloadCore($coreName) → $url")

        // Stream the response to a temp .zip on disk and unzip from
        // there, never holding the full payload in memory. The largest
        // libretro cores (scummvm ~134 MB) overflow Android's default
        // heap (~87 MB free) when the response body is materialised as
        // a ByteArray. See #849.
        try {
            httpClient.prepareGet(url) {
                onDownload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0) onProgress(bytesSentTotal.toFloat() / contentLength)
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw RuntimeException("Core download failed: HTTP ${response.status.value} from $url")
                }
                val channel = response.bodyAsChannel()
                fileStorage.writeFileStreaming(tempZipPath) { append ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        append(buffer, 0, read)
                    }
                }
            }
            fileStorage.extractFirstZipEntryFromFile(tempZipPath, destPath)
        } finally {
            // Best-effort cleanup of the temp .zip. If the download
            // throws mid-flight the partial file would otherwise sit
            // in cores/ until the next download for the same core.
            runCatching { fileStorage.deleteFile(tempZipPath) }
        }
        destPath
    }

    override suspend fun downloadCoreByHash(
        coreName: String,
        sha256: String,
        onProgress: (Float) -> Unit,
    ): Result<String> = runCatching {
        // Resolve the server-side numeric core id. The versioned endpoint is
        // `/api/cores/{id}/download?sha256=...` and requires the numeric id;
        // we only know the core name up here in EmulationUseCases, so look
        // up the id from the cached cores list.
        val core = apiClient.getAvailableCores().firstOrNull { it.name == coreName }
            ?: throw RuntimeException("Unknown core name for versioned download: $coreName")

        val (status, body) = apiClient.downloadCoreByHash(
            coreId = core.id.toString(),
            sha256 = sha256,
            onProgress = { sent, total ->
                if (total != null && total > 0) onProgress(sent.toFloat() / total)
            },
        )
        if (status == 404) {
            throw CorePrunedException(sha256)
        }
        if (body == null) {
            throw RuntimeException("Versioned core download failed: HTTP $status")
        }
        // Historical binaries live alongside the latest — write them to a
        // shared filename so the emulator can load them. When cores/ already
        // contains a different version we overwrite (acceptable: the player
        // won't hit this path unless the pinned sha != the current sha, and
        // we want the pinned bytes to take precedence for this session).
        val fileName = coreFileName(coreName)
        val destPath = fileStorage.getCoresDir() + "/$fileName"
        val coreData = extractFirstZipEntry(body)
        fileStorage.writeFile(destPath, coreData)
        destPath
    }

    override suspend fun getLocalCorePath(coreName: String): String? {
        val fileName = coreFileName(coreName)
        val path = fileStorage.getCoresDir() + "/$fileName"
        return if (fileStorage.fileExists(path)) path else null
    }

    override suspend fun isCoreCached(coreName: String): Boolean {
        return getLocalCorePath(coreName) != null
    }

    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? {
        // No local binary → nothing to compare against; the caller should
        // fall through to the normal download path.
        val localPath = getLocalCorePath(coreName) ?: return null

        val serverSha = getServerCoreSha(coreName) ?: return null

        val localSha = fileStorage.sha256File(localPath) ?: return null

        return localSha.equals(serverSha, ignoreCase = true)
    }

    override suspend fun getServerCoreSha(coreName: String): String? {
        // Resolve the server-side numeric core id from the name. Any
        // failure here (network, name not found) becomes null so the
        // caller can treat it as "cannot decide".
        val coreId = runCatching {
            apiClient.getAvailableCores().firstOrNull { it.name == coreName }?.id
        }.getOrNull() ?: return null

        val manifest = runCatching { apiClient.getCoreManifest(coreId) }.getOrNull()
            ?: return null

        // Empty sha == the server has not fingerprinted this core
        // yet; callers must not treat empty as "no changes" because
        // it still carries no signal.
        return manifest.sha256.takeIf { it.isNotEmpty() }
    }
}
