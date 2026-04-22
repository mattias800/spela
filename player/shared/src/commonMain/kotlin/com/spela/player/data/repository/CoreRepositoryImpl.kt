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

class CoreRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
    private val httpClient: HttpClient,
) : CoreRepository {

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> = runCatching {
        apiClient.getAvailableCores().map { it.toDomain() }
    }

    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> = runCatching {
        apiClient.getRecommendedCore(gameId)
    }

    override suspend fun downloadCore(coreName: String, downloadUrl: String?, onProgress: (Float) -> Unit): Result<String> = runCatching {
        val fileName = coreFileName(coreName)
        val destPath = fileStorage.getCoresDir() + "/$fileName"
        val url = if (downloadUrl != null) resolveDownloadUrl(downloadUrl) else buildbotCoreUrl(coreName)

        val response: HttpResponse = httpClient.get(url) {
            onDownload { bytesSentTotal, contentLength ->
                if (contentLength != null && contentLength > 0) onProgress(bytesSentTotal.toFloat() / contentLength)
            }
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Core download failed: HTTP ${response.status.value} from $url")
        }
        val zipData: ByteArray = response.body()
        val coreData = extractFirstZipEntry(zipData)
        fileStorage.writeFile(destPath, coreData)
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
