package com.spela.player.domain.repository

import com.spela.player.domain.model.UserSearchResult

interface UserRepository {
    suspend fun searchUsers(query: String = "", page: Int = 1, pageSize: Int = 20): Result<UserSearchPage>
    suspend fun getRecentPartners(): Result<List<UserSearchResult>>
}

data class UserSearchPage(
    val data: List<UserSearchResult>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
