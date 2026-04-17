package com.spela.player.data.repository

import com.spela.client.models.LinkRAAccountRequest
import com.spela.client.models.UpdateRASettingsRequest
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.model.RACredentials
import com.spela.player.domain.model.RAStatus
import com.spela.player.domain.repository.AchievementsRepository

class AchievementsRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : AchievementsRepository {
    override suspend fun getRAStatus(): Result<RAStatus> = runCatching {
        val dto = apiClient.getRAStatus()
        RAStatus(linked = dto.linked, username = dto.username, hardcoreEnabled = dto.hardcoreEnabled)
    }

    override suspend fun linkRA(username: String, password: String): Result<RAStatus> = runCatching {
        val dto = apiClient.linkRA(LinkRAAccountRequest(username = username, password = password))
        RAStatus(linked = dto.linked, username = dto.username, hardcoreEnabled = dto.hardcoreEnabled)
    }

    override suspend fun unlinkRA(): Result<Unit> = runCatching {
        apiClient.unlinkRA()
    }

    override suspend fun getRAToken(): Result<RACredentials> = runCatching {
        val dto = apiClient.getRAToken()
        RACredentials(username = dto.username, token = dto.token)
    }

    override suspend fun updateRASettings(hardcoreEnabled: Boolean): Result<RAStatus> = runCatching {
        val dto = apiClient.updateRASettings(UpdateRASettingsRequest(hardcoreEnabled = hardcoreEnabled))
        RAStatus(linked = dto.linked, username = dto.username, hardcoreEnabled = dto.hardcoreEnabled)
    }
}
