package com.spela.player.domain.repository

import com.spela.player.domain.model.CurrentUserContext
import com.spela.player.domain.model.User

interface CurrentUserContextRepository {
    suspend fun cache(user: User)
    suspend fun cache(context: CurrentUserContext)
    suspend fun getCached(): CurrentUserContext?
    suspend fun clear()
}
