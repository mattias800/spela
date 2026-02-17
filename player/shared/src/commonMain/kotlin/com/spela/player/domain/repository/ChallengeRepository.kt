package com.spela.player.domain.repository

import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeLeaderboardEntry

interface ChallengeRepository {
    suspend fun getChallenges(
        gameId: String? = null,
        consoleId: String? = null,
        difficulty: String? = null,
        sort: String? = null,
        page: Int = 1,
    ): Result<List<Challenge>>

    suspend fun getGameChallenges(gameId: String, page: Int = 1): Result<List<Challenge>>

    suspend fun getMyChallenges(page: Int = 1): Result<List<Challenge>>

    suspend fun getChallengeDetail(challengeId: String): Result<Challenge>

    suspend fun getLeaderboard(challengeId: String, page: Int = 1): Result<List<ChallengeLeaderboardEntry>>

    suspend fun createChallenge(
        gameId: String,
        name: String,
        description: String,
        type: String,
        difficulty: String,
        coreName: String,
        saveData: ByteArray,
        screenshotData: ByteArray?,
    ): Result<Challenge>

    suspend fun downloadChallengeSave(challengeId: String): Result<ByteArray>

    suspend fun startAttempt(challengeId: String): Result<ChallengeAttempt>

    suspend fun completeAttempt(challengeId: String, attemptId: String): Result<ChallengeAttempt>

    suspend fun abandonAttempt(challengeId: String, attemptId: String): Result<Unit>

    suspend fun getMyAttempts(challengeId: String): Result<List<ChallengeAttempt>>

    suspend fun deleteChallenge(challengeId: String): Result<Unit>
}
