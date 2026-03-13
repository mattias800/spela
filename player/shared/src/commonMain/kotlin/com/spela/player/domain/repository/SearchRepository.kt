package com.spela.player.domain.repository

import com.spela.player.domain.model.GlobalSearchResult

interface SearchRepository {
    suspend fun globalSearch(query: String, limit: Int = 5): Result<GlobalSearchResult>

    fun getRecentSearches(): List<String>
    fun addRecentSearch(query: String)
    fun removeRecentSearch(query: String)
    fun clearRecentSearches()
}
