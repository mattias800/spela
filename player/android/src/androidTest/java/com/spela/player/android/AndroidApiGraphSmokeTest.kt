package com.spela.player.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatformTools
import java.net.HttpURLConnection
import java.net.URI

/**
 * Minimal Android-specific API graph smoke for #1483.
 *
 * Endpoint-specific clone-session and core-flag contract coverage now runs as
 * an opt-in JVM/live-server suite. Keep this tiny instrumentation test so CI
 * still proves the target app process starts, Android production Koin resolves
 * the real OkHttp-backed [SpelaApiClient], TokenManager accepts live tokens,
 * and an authenticated generated-client request works on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class AndroidApiGraphSmokeTest : AndroidApiSmokeBase() {

    @Test
    fun productionGraphAuthenticatesAgainstLiveServer() {
        val tokens = loginAndExtractTokens()
            ?: throw AssertionError("Player login via API failed — is the backend up on $SERVER_URL?")

        val koin = KoinPlatformTools.defaultContext().get()
        val apiClient = koin.get<SpelaApiClient>()
        val tokenManager = koin.get<TokenManager>()
        koin.get<SessionRepository>()

        apiClient.setBaseUrl(SERVER_URL)
        runBlocking {
            tokenManager.setTokens(tokens.first, tokens.second)
            val user = apiClient.getCurrentUser()
            check(user.username == PLAYER_USERNAME) {
                "Expected authenticated Android API call as $PLAYER_USERNAME, got ${user.username}"
            }
        }

        // Resolving SessionRepository above is intentional: Koin throws if the
        // Android production graph no longer exposes the repository binding.
    }

    private fun loginAndExtractTokens(): Pair<String, String>? {
        return try {
            val conn = (URI("$SERVER_URL/api/auth/login").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.use {
                it.write("""{"username":"$PLAYER_USERNAME","password":"$PLAYER_PASSWORD"}""".toByteArray())
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val access = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return null
            val refresh = Regex("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return null
            access to refresh
        } catch (e: Exception) {
            android.util.Log.w(LOG_TAG, "loginAndExtractTokens failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val SERVER_URL = "http://127.0.0.1:8080"
        const val PLAYER_USERNAME = "player"
        const val PLAYER_PASSWORD = "player123"
        const val LOG_TAG = "AndroidApiGraphSmoke"
    }
}
