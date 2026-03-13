package com.spela.player.domain.repository

import com.spela.player.domain.model.GlobalSearchResult

interface SearchRepository {
    suspend fun globalSearch(query: String, limit: Int = 5): Result<GlobalSearchResult>
}
