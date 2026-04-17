package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.LibretroCore
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

    override suspend fun getLocalCorePath(coreName: String): String? {
        val fileName = coreFileName(coreName)
        val path = fileStorage.getCoresDir() + "/$fileName"
        return if (fileStorage.fileExists(path)) path else null
    }

    override suspend fun isCoreCached(coreName: String): Boolean {
        return getLocalCorePath(coreName) != null
    }
}
