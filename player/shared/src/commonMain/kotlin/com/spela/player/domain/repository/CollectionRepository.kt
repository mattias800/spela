package com.spela.player.domain.repository

import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail

interface CollectionRepository {
    suspend fun getMyCollections(page: Int = 1, pageSize: Int = 20): Result<List<GameCollection>>
    suspend fun getPublicCollections(page: Int = 1, pageSize: Int = 20): Result<List<GameCollection>>
    suspend fun getCollection(id: String): Result<GameCollectionDetail>
    suspend fun createCollection(name: String, description: String? = null, isPublic: Boolean = false): Result<GameCollection>
    suspend fun updateCollection(id: String, name: String? = null, description: String? = null, isPublic: Boolean? = null): Result<GameCollection>
    suspend fun deleteCollection(id: String): Result<Unit>
    suspend fun addGameToCollection(collectionId: String, gameId: String): Result<Unit>
    suspend fun removeGameFromCollection(collectionId: String, gameId: String): Result<Unit>
}
