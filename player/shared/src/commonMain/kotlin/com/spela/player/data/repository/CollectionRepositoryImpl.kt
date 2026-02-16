package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.CreateCollectionRequest
import com.spela.player.data.remote.dto.UpdateCollectionRequest
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.domain.repository.CollectionRepository

class CollectionRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : CollectionRepository {

    override suspend fun getMyCollections(page: Int, pageSize: Int): Result<List<GameCollection>> = runCatching {
        apiClient.getMyCollections(page = page, pageSize = pageSize).data.map { dto ->
            dto.toDomain().resolveImageUrls()
        }
    }

    override suspend fun getPublicCollections(page: Int, pageSize: Int): Result<List<GameCollection>> = runCatching {
        apiClient.getPublicCollections(page = page, pageSize = pageSize).data.map { dto ->
            dto.toDomain().resolveImageUrls()
        }
    }

    override suspend fun getCollection(id: String): Result<GameCollectionDetail> = runCatching {
        val detail = apiClient.getCollection(id).toDomain()
        detail.copy(
            avatarUrl = apiClient.resolveUrl(detail.avatarUrl),
            coverUrl = apiClient.resolveUrl(detail.coverUrl),
            games = detail.games.map { game ->
                game.copy(coverUrl = apiClient.resolveUrl(game.coverUrl))
            },
        )
    }

    override suspend fun createCollection(
        name: String,
        description: String?,
        isPublic: Boolean,
    ): Result<GameCollection> = runCatching {
        apiClient.createCollection(
            CreateCollectionRequest(name = name, description = description, isPublic = isPublic)
        ).toDomain().resolveImageUrls()
    }

    override suspend fun updateCollection(
        id: String,
        name: String?,
        description: String?,
        isPublic: Boolean?,
    ): Result<GameCollection> = runCatching {
        apiClient.updateCollection(
            id, UpdateCollectionRequest(name = name, description = description, isPublic = isPublic)
        ).toDomain().resolveImageUrls()
    }

    override suspend fun deleteCollection(id: String): Result<Unit> = runCatching {
        apiClient.deleteCollection(id)
    }

    override suspend fun addGameToCollection(collectionId: String, gameId: String): Result<Unit> = runCatching {
        apiClient.addGameToCollection(collectionId, gameId)
    }

    override suspend fun removeGameFromCollection(collectionId: String, gameId: String): Result<Unit> = runCatching {
        apiClient.removeGameFromCollection(collectionId, gameId)
    }

    private fun GameCollection.resolveImageUrls(): GameCollection = copy(
        avatarUrl = apiClient.resolveUrl(avatarUrl),
        coverUrl = apiClient.resolveUrl(coverUrl),
    )
}
