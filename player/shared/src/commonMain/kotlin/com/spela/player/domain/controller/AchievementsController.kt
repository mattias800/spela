package com.spela.player.domain.controller

import com.spela.player.domain.model.AchievementEvent
import kotlinx.coroutines.flow.Flow

interface AchievementsController {
    fun init()
    fun deinit()
    fun login(username: String, token: String)
    fun loadGame(hash: String)
    fun doFrame()
    val isHardcore: Boolean
    fun setHardcore(enabled: Boolean)
    val events: Flow<AchievementEvent>
    fun httpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray)
}
