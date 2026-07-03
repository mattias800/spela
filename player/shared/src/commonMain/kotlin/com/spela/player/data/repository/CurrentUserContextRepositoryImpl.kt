package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.CurrentUserContext
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.CurrentUserContextRepository
import kotlin.time.Clock

class CurrentUserContextRepositoryImpl(
    database: SpelaDatabase,
) : CurrentUserContextRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun cache(user: User) {
        cache(CurrentUserContext(userId = user.id, username = user.username))
    }

    override suspend fun cache(context: CurrentUserContext) {
        queries.insertCachedUser(
            user_id = context.userId,
            username = context.username,
            cached_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun getCached(): CurrentUserContext? =
        queries.getCachedUser().executeAsOneOrNull()?.let {
            CurrentUserContext(
                userId = it.user_id,
                username = it.username,
            )
        }

    override suspend fun clear() {
        queries.deleteCachedUser()
    }
}
