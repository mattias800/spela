package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.domain.repository.ChallengeRepository

class ChallengeRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : ChallengeRepository {

    override suspend fun getChallenges(
        gameId: String?,
        consoleId: String?,
        difficulty: String?,
        sort: String?,
        page: Int,
    ): Result<List<Challenge>> = runCatching {
        apiClient.getChallenges(gameId, consoleId, difficulty, sort, page).data.orEmpty().map { dto ->
            val challenge = dto.toDomain()
            challenge.copy(
                screenshotUrl = apiClient.resolveUrl(challenge.screenshotUrl),
                gameCoverUrl = apiClient.resolveUrl(challenge.gameCoverUrl),
                creatorAvatarUrl = apiClient.resolveUrl(challenge.creatorAvatarUrl),
            )
        }
    }

    override suspend fun getGameChallenges(gameId: String, page: Int): Result<List<Challenge>> = runCatching {
        apiClient.getGameChallenges(gameId, page).data.orEmpty().map { dto ->
            val challenge = dto.toDomain()
            challenge.copy(
                screenshotUrl = apiClient.resolveUrl(challenge.screenshotUrl),
                gameCoverUrl = apiClient.resolveUrl(challenge.gameCoverUrl),
                creatorAvatarUrl = apiClient.resolveUrl(challenge.creatorAvatarUrl),
            )
        }
    }

    override suspend fun getMyChallenges(page: Int): Result<List<Challenge>> = runCatching {
        apiClient.getMyChallenges(page).data.orEmpty().map { dto ->
            val challenge = dto.toDomain()
            challenge.copy(
                screenshotUrl = apiClient.resolveUrl(challenge.screenshotUrl),
                gameCoverUrl = apiClient.resolveUrl(challenge.gameCoverUrl),
                creatorAvatarUrl = apiClient.resolveUrl(challenge.creatorAvatarUrl),
            )
        }
    }

    override suspend fun getChallengeDetail(challengeId: String): Result<Challenge> = runCatching {
        val challenge = apiClient.getChallenge(challengeId).toDomain()
        challenge.copy(
            screenshotUrl = apiClient.resolveUrl(challenge.screenshotUrl),
            gameCoverUrl = apiClient.resolveUrl(challenge.gameCoverUrl),
            creatorAvatarUrl = apiClient.resolveUrl(challenge.creatorAvatarUrl),
        )
    }

    override suspend fun getLeaderboard(
        challengeId: String,
        page: Int,
    ): Result<List<ChallengeLeaderboardEntry>> = runCatching {
        apiClient.getChallengeLeaderboard(challengeId, page).data.orEmpty().map { dto ->
            val entry = dto.toDomain()
            entry.copy(avatarUrl = apiClient.resolveUrl(entry.avatarUrl))
        }
    }

    override suspend fun createChallenge(
        gameId: String,
        name: String,
        description: String,
        type: String,
        difficulty: String,
        coreName: String,
        saveData: ByteArray,
        screenshotData: ByteArray?,
    ): Result<Challenge> = runCatching {
        val challenge = apiClient.createChallenge(
            gameId, name, description, type, difficulty, coreName, saveData, screenshotData,
        ).toDomain()
        challenge.copy(
            screenshotUrl = apiClient.resolveUrl(challenge.screenshotUrl),
            gameCoverUrl = apiClient.resolveUrl(challenge.gameCoverUrl),
            creatorAvatarUrl = apiClient.resolveUrl(challenge.creatorAvatarUrl),
        )
    }

    override suspend fun downloadChallengeSave(challengeId: String): Result<ByteArray> = runCatching {
        apiClient.downloadChallengeSave(challengeId)
    }

    override suspend fun startAttempt(challengeId: String): Result<ChallengeAttempt> = runCatching {
        apiClient.startChallengeAttempt(challengeId).toDomain()
    }

    override suspend fun completeAttempt(
        challengeId: String,
        attemptId: String,
    ): Result<ChallengeAttempt> = runCatching {
        apiClient.completeChallengeAttempt(challengeId, attemptId).toDomain()
    }

    override suspend fun abandonAttempt(challengeId: String, attemptId: String): Result<Unit> = runCatching {
        apiClient.abandonChallengeAttempt(challengeId, attemptId)
    }

    override suspend fun getMyAttempts(challengeId: String): Result<List<ChallengeAttempt>> = runCatching {
        apiClient.getMyChallengeAttempts(challengeId).map { it.toDomain() }
    }

    override suspend fun deleteChallenge(challengeId: String): Result<Unit> = runCatching {
        apiClient.deleteChallenge(challengeId)
    }
}
