package com.spela.player.libretro

import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementEventType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DesktopAchievementsController(
    private val jni: LibretroJni,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
) : AchievementsController {

    private val _events = MutableSharedFlow<AchievementEvent>(extraBufferCapacity = 16)
    override val events: Flow<AchievementEvent> = _events.asSharedFlow()

    private var _isHardcore = false
    override val isHardcore: Boolean get() = _isHardcore

    override fun init() {
        jni.achievementEventListener = { eventType, achievementId, title, description, points ->
            val type = AchievementEventType.fromNativeValue(eventType)
            if (type != null) {
                _events.tryEmit(
                    AchievementEvent(
                        type = type,
                        achievementId = achievementId,
                        title = title ?: "",
                        description = description ?: "",
                        points = points,
                    )
                )
            }
        }

        jni.httpRequestListener = { requestId, url, postData ->
            scope.launch {
                try {
                    val response = if (postData != null) {
                        httpClient.post(url) {
                            contentType(ContentType.Application.FormUrlEncoded)
                            setBody(postData)
                        }
                    } else {
                        httpClient.get(url)
                    }
                    val body: ByteArray = response.body()
                    jni.nativeAchievementsHttpComplete(requestId, response.status.value, body)
                } catch (_: Exception) {
                    jni.nativeAchievementsHttpComplete(requestId, 0, ByteArray(0))
                }
            }
        }

        jni.nativeAchievementsInit()
    }

    override fun deinit() {
        jni.nativeAchievementsDeinit()
        jni.achievementEventListener = null
        jni.httpRequestListener = null
    }

    override fun login(username: String, token: String) {
        jni.nativeAchievementsLogin(username, token)
    }

    override fun loadGame(hash: String) {
        jni.nativeAchievementsLoadGame(hash)
    }

    override fun doFrame() {
        jni.nativeAchievementsDoFrame()
    }

    override fun setHardcore(enabled: Boolean) {
        _isHardcore = enabled
        jni.nativeAchievementsSetHardcore(enabled)
    }

    override fun httpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray) {
        jni.nativeAchievementsHttpComplete(requestId, responseCode, responseBody)
    }
}
