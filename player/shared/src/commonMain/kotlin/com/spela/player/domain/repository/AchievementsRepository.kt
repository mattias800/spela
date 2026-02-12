package com.spela.player.domain.repository

import com.spela.player.domain.model.RACredentials
import com.spela.player.domain.model.RAStatus

interface AchievementsRepository {
    suspend fun getRAStatus(): Result<RAStatus>
    suspend fun linkRA(username: String, password: String): Result<RAStatus>
    suspend fun unlinkRA(): Result<Unit>
    suspend fun getRAToken(): Result<RACredentials>
    suspend fun updateRASettings(hardcoreEnabled: Boolean): Result<RAStatus>
}
