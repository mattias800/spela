package com.spela.player.domain.usecase

import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.domain.repository.CollectionRepository

class GetMyCollectionsUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 20): Result<List<GameCollection>> =
        collectionRepository.getMyCollections(page = page, pageSize = pageSize)
}

class GetPublicCollectionsUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(page: Int = 1, pageSize: Int = 20): Result<List<GameCollection>> =
        collectionRepository.getPublicCollections(page = page, pageSize = pageSize)
}

class GetCollectionDetailUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(id: String): Result<GameCollectionDetail> =
        collectionRepository.getCollection(id)
}

class CreateCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ): Result<GameCollection> =
        collectionRepository.createCollection(name = name, description = description, isPublic = isPublic)
}

class UpdateCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(
        id: String,
        name: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
    ): Result<GameCollection> =
        collectionRepository.updateCollection(id = id, name = name, description = description, isPublic = isPublic)
}

class DeleteCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(id: String): Result<Unit> =
        collectionRepository.deleteCollection(id)
}

class AddGameToCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String, gameId: String): Result<Unit> =
        collectionRepository.addGameToCollection(collectionId, gameId)
}

class RemoveGameFromCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String, gameId: String): Result<Unit> =
        collectionRepository.removeGameFromCollection(collectionId, gameId)
}
