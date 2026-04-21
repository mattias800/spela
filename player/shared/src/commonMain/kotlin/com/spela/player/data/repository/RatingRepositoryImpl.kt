package com.spela.player.data.repository

import com.spela.client.models.CreateOrUpdateRatingRequest
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.repository.RatingRepository

class RatingRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : RatingRepository {

    override suspend fun rateGame(gameId: String, rating: Int, review: String): Result<GameRating> = runCatching {
        val request = CreateOrUpdateRatingRequest(rating = rating.toLong(), review = review)
        val dto = apiClient.rateGame(gameId, request)
        dto.toDomain().resolveUrls()
    }

    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int): Result<List<GameRating>> = runCatching {
        apiClient.getGameRatings(gameId, page = page, pageSize = pageSize).data.map {
            it.toDomain().resolveUrls()
        }
    }

    override suspend fun getRatingSummary(gameId: String): Result<RatingSummary> = runCatching {
        apiClient.getGameRatingSummary(gameId).toDomain()
    }

    override suspend fun getMyRating(gameId: String): Result<GameRating?> = runCatching {
        try {
            apiClient.getMyRating(gameId).toDomain().resolveUrls()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteRating(gameId: String): Result<Unit> = runCatching {
        apiClient.deleteRating(gameId)
    }

    private fun GameRating.resolveUrls(): GameRating = copy(
        avatarUrl = apiClient.resolveUrl(avatarUrl),
    )
}
