package com.spela.player.domain.repository

import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.RatingSummary

interface RatingRepository {
    suspend fun rateGame(gameId: String, rating: Int, review: String = ""): Result<GameRating>
    suspend fun getGameRatings(gameId: String, page: Int = 1, pageSize: Int = 20): Result<List<GameRating>>
    suspend fun getRatingSummary(gameId: String): Result<RatingSummary>
    suspend fun getMyRating(gameId: String): Result<GameRating?>
    suspend fun deleteRating(gameId: String): Result<Unit>
}
