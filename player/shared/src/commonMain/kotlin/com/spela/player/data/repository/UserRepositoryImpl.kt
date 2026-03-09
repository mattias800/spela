package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.UserSearchResult
import com.spela.player.domain.repository.UserRepository
import com.spela.player.domain.repository.UserSearchPage

class UserRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : UserRepository {

    override suspend fun searchUsers(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<UserSearchPage> = runCatching {
        val response = apiClient.searchUsers(query, page, pageSize)
        UserSearchPage(
            data = response.data.map { it.toDomain() },
            total = response.total,
            page = response.page,
            pageSize = response.pageSize,
        )
    }

    override suspend fun getRecentPartners(): Result<List<UserSearchResult>> = runCatching {
        apiClient.getRecentPartners().map { it.toDomain() }
    }
}
