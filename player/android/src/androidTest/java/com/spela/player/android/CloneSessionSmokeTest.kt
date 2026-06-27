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
import java.net.URL

/**
 * Android smoke test for `POST /api/sessions/{id}/clone` (issue #553).
 *
 * Purpose — integration of the cloneSession endpoint round-trip. The
 * shared KMP composables (clone dialog, snackbar, list) are covered by
 * desktop E2E with fakes; this test exercises the parts desktop cannot:
 *
 *   * Real HTTP round-trip to `/api/sessions/{id}/clone` and the
 *     deserializer of `GameSessionResponse` against the live server.
 *   * Real auth header sent by the production [SpelaApiClient].
 *   * Server-side transaction + ownership transfer persisted in the DB.
 *
 * We go through the Koin-bound production [SessionRepository] so the
 * test catches the same classes of bug the real app would hit. The
 * cURL helpers below are only used for test bootstrap/teardown.
 *
 * Auth-state setup is **programmatic, not UI-driven** (#1146): the
 * shared `addServerAndLogin` UI flow doesn't reliably persist tokens
 * on the AVD because UiAutomator's `setText` of the SpTextField
 * AndroidView/EditText doesn't always make the click on "Sign In"
 * dispatch to LoginViewModel — so the app reaches Home with an empty
 * TokenManager. Since this test exists to verify the cloneSession
 * round-trip rather than the login UX, we skip BaseE2ETest's UI login
 * and seed the production Koin graph (TokenManager + SpelaApiClient
 * baseUrl) directly with the bootstrap login result.
 */
@RunWith(AndroidJUnit4::class)
class CloneSessionSmokeTest : AndroidApiSmokeBase() {

    @Test
    fun cloneSessionRoundTripsThroughRealApi() {
        // ── Bootstrap: get access + refresh tokens for the test process. ──
        val tokens = loginAndExtractTokens(PLAYER_USERNAME, PLAYER_PASSWORD)
            ?: throw AssertionError("Player login via API failed — is the backend up on $SERVER_URL?")
        val bootstrapToken = tokens.first

        // The hardcoded id 126 was Castlevania in the local seed, but
        // CI only has the public-domain `nestest.nes` (different id).
        // Discover an available game at runtime so the test runs in
        // either environment.
        val gameId = firstAvailableGameId(bootstrapToken)
            ?: throw AssertionError("No games available on backend — check seed data")
        android.util.Log.i(LOG_TAG, "Using gameId=$gameId for clone test")

        // Use a run-unique suffix so the test is idempotent across retries.
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val sourceName = "QA-Clone-Source-$suffix"

        val sourceId = createSessionViaApi(bootstrapToken, gameId, sourceName)
            ?: throw AssertionError("Failed to seed source session via API (gameId=$gameId)")
        android.util.Log.i(LOG_TAG, "Seeded source session id=$sourceId name='$sourceName'")

        // ── Seed the production Koin graph with the live token + base URL.
        //    The session repo (and therefore the cloneSession call below)
        //    reads these on every request via SpelaApiClient. This is the
        //    same end-state ensureLoggedIn() *should* have left us in, but
        //    the UI path doesn't reliably get there on the AVD (see class
        //    docstring). ──
        val koin = KoinPlatformTools.defaultContext().get()
        val apiClient = koin.get<SpelaApiClient>()
        val tokenManager = koin.get<TokenManager>()
        apiClient.setBaseUrl(SERVER_URL)
        runBlocking { tokenManager.setTokens(tokens.first, tokens.second) }
        android.util.Log.i(LOG_TAG, "Seeded TokenManager and apiClient.baseUrl")

        var cloneId: String? = null
        try {
            val sessionRepo = koin.get<SessionRepository>()

            // Mirror the production UI path: the clone dialog always sends a
            // trimmed name (pre-filled with "{source} (Copy)"). We pass the
            // same string the dialog would. Passing name=null here would
            // exercise the unused default-name path, which is currently
            // broken — tracked separately as #663 (SpelaApiClient serializes
            // a null request body as the JSON literal "null", which the
            // server's validator rejects with 422). The user-reachable flow
            // does NOT hit that path.
            val expectedCopyName = "$sourceName (Copy)"
            val result = runBlocking {
                sessionRepo.cloneSession(sessionId = sourceId, name = expectedCopyName, saveId = null)
            }
            val cloned = result.getOrElse { err ->
                throw AssertionError(
                    "cloneSession() failed against real backend: ${err::class.simpleName}: ${err.message}"
                )
            }
            cloneId = cloned.id
            android.util.Log.i(LOG_TAG, "Clone OK id=$cloneId name='${cloned.name}'")

            check(cloned.id != sourceId) {
                "Cloned session got the same id as source — server did not create a new row"
            }
            check(cloned.name == expectedCopyName) {
                "Expected cloned name='$expectedCopyName' but got '${cloned.name}'"
            }
            check(cloned.gameId == gameId) {
                "Clone attached to wrong game id: '${cloned.gameId}' (expected $gameId)"
            }

            // ── Server-side persistence cross-check via an independent
            //    HTTP call. A bug where the Kotlin client returns a
            //    synthetic response without the server actually writing
            //    would be caught here. ──
            val listAfter = listSessionsViaApi(bootstrapToken, gameId)
                ?: throw AssertionError("Could not list sessions after clone")
            val persisted = listAfter.firstOrNull { it.first == cloneId }
                ?: throw AssertionError(
                    "Cloned session id=$cloneId not present in server's session list for game $gameId"
                )
            check(persisted.second == expectedCopyName) {
                "Server returned stale/mismatched name for clone: '${persisted.second}'"
            }

            // ── Ownership — the clone endpoint must transfer ownership to
            //    the caller (important for US-1 shared-session cloning, and
            //    a simple invariant for this happy path). ──
            val ownerId = getSessionOwnerIdViaApi(bootstrapToken, cloneId)
                ?: throw AssertionError("Could not read ownerId of cloned session $cloneId")
            val callerUserId = getCurrentUserIdViaApi(bootstrapToken)
                ?: throw AssertionError("Could not read caller's own userId")
            check(ownerId == callerUserId) {
                "Cloned session ownerId=$ownerId but caller's userId=$callerUserId — " +
                    "clone endpoint did not transfer ownership to the caller"
            }

            // ── Lightweight UI touch: pull the just-cloned session back
            //    through the same repository to confirm the full read path
            //    still works (the deserializer didn't blow up on any field). ──
            val fetched = runBlocking { sessionRepo.getSession(cloneId) }.getOrNull()
                ?: throw AssertionError("Could not re-fetch cloned session $cloneId via SessionRepository")
            check(fetched.name == expectedCopyName)
            android.util.Log.i(LOG_TAG, "Smoke test PASS — cloneId=$cloneId")
        } finally {
            // Teardown — best-effort; failures here shouldn't mask test verdict.
            runCatching { cloneId?.let { deleteSessionViaApi(bootstrapToken, it) } }
            runCatching { deleteSessionViaApi(bootstrapToken, sourceId) }
        }
    }

    // ── HTTP helpers (direct against the real backend on 127.0.0.1:8080).
    //    Used for bootstrap/teardown and for the independent server-side
    //    persistence cross-check — NOT for the primary integration assert,
    //    which goes through the production Koin-bound repository above. ──

    /**
     * Pull the first game id off `GET /api/games`. The clone test
     * doesn't care which game it points at — only that the
     * referenced id exists. The local seed has Castlevania, the CI
     * runner has only `nestest.nes`; resolving dynamically lets the
     * test work in both environments.
     */
    private fun firstAvailableGameId(token: String): String? {
        return try {
            val conn = (URL("$SERVER_URL/api/games?pageSize=1").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode != 200) {
                android.util.Log.w(LOG_TAG, "firstAvailableGameId HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            // Each GameResponse has an `id` field — take the first.
            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            android.util.Log.w(LOG_TAG, "firstAvailableGameId failed: ${e.message}")
            null
        }
    }

    /**
     * Login and return (accessToken, refreshToken). Both are needed:
     * `accessToken` is sent on every request, `refreshToken` is what
     * the bearer-auth plugin's refreshTokens callback consults when a
     * 401 comes back (without it the plugin clears tokens and the
     * test would fail with the same 401 storm we're trying to avoid).
     */
    private fun loginAndExtractTokens(username: String, password: String): Pair<String, String>? {
        return try {
            val conn = (URL("$SERVER_URL/api/auth/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.use {
                it.write("""{"username":"$username","password":"$password"}""".toByteArray())
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

    private fun createSessionViaApi(token: String, gameId: String, name: String): String? {
        return try {
            val conn = (URL("$SERVER_URL/api/games/$gameId/sessions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.use {
                it.write("""{"name":"$name"}""".toByteArray())
            }
            if (conn.responseCode !in 200..299) {
                android.util.Log.w(LOG_TAG, "createSession HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            android.util.Log.w(LOG_TAG, "createSessionViaApi failed: ${e.message}")
            null
        }
    }

    private fun listSessionsViaApi(token: String, gameId: String): List<Pair<String, String>>? {
        return try {
            val conn = (URL("$SERVER_URL/api/games/$gameId/sessions").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            // Extract each {"id":"...","...","name":"..."} pair. We pair id+name
            // per-session-object. Plain regex — no JSON dep needed here.
            Regex("\"id\"\\s*:\\s*\"(\\d+)\"[^}]*\"name\"\\s*:\\s*\"([^\"]*)\"")
                .findAll(body)
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
        } catch (e: Exception) {
            android.util.Log.w(LOG_TAG, "listSessionsViaApi failed: ${e.message}")
            null
        }
    }

    private fun getSessionOwnerIdViaApi(token: String, sessionId: String): String? {
        return try {
            val conn = (URL("$SERVER_URL/api/sessions/$sessionId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("\"ownerId\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentUserIdViaApi(token: String): String? {
        return try {
            val conn = (URL("$SERVER_URL/api/user/profile").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteSessionViaApi(token: String, sessionId: String) {
        try {
            val conn = (URL("$SERVER_URL/api/sessions/$sessionId").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
            }
            conn.responseCode // drain
            conn.disconnect()
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        private const val SERVER_URL = "http://127.0.0.1:8080"
        private const val PLAYER_USERNAME = "player"
        private const val PLAYER_PASSWORD = "player123"
        private const val LOG_TAG = "CloneSessionSmoke"
    }
}
