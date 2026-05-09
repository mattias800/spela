package com.spela.player.android

import com.spela.player.domain.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.mp.KoinPlatformTools
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android smoke test for `POST /api/sessions/{id}/clone` (issue #553).
 *
 * Purpose — integration, not UI verification. The shared KMP composables
 * (SessionsSection, SessionDetailScreen, clone dialog, success snackbar)
 * are already covered by desktop E2E tests with fake repositories. This
 * test exercises the parts desktop cannot:
 *
 *   * Real HTTP round-trip to the server's `/api/sessions/{id}/clone` endpoint,
 *   * Real JWT auth header (the production [SessionRepository] picks up the
 *     token saved by the login flow through its injected [SpelaApiClient]),
 *   * The generated Kotlin OpenAPI client's JSON ser/deser against the live
 *     server schema (catches any Kotlin ⇄ Go type drift),
 *   * Server-side transaction + ownership assignment persisted in the DB.
 *
 * We explicitly go through the Koin-bound production [SessionRepository]
 * rather than hand-rolled cURL so that the test catches the same classes
 * of integration bug the real app would hit. The cURL calls in the helpers
 * are only used for test bootstrap/teardown (seed + cleanup) so that an
 * assertion failure during the repository call is unambiguous.
 *
 * Why this test doesn't drive the session-list UI on the AYN Thor:
 *   * The AYN Thor is a clamshell handheld with an always-connected gamepad.
 *   * When a gamepad is connected the app hides the bottom-nav bar (and the
 *     side-rail) — see `SpelaApp.kt`. Navigation is gamepad-only (R1/L1).
 *   * `adb shell input tap` is unreliable because the digitizer is powered
 *     down when the clamshell is closed.
 *   * Driving tab switches via Instrumentation key injection doesn't reach
 *     [MainActivity.onKeyDown] as a gamepad source, so the nav intent never
 *     fires. The UI-level menu interaction is thoroughly covered by the
 *     desktop E2E tests, where it belongs per CLAUDE.md:
 *     "If the code is in commonMain/, test it on desktop."
 */
class CloneSessionSmokeTest : BaseE2ETest() {

    @Test
    fun cloneSessionRoundTripsThroughRealApi() {
        // ── Bootstrap: admin token via cURL for teardown privileges ──
        val bootstrapToken = loginViaApi(PLAYER_USERNAME, PLAYER_PASSWORD)
            ?: throw AssertionError("Player login via API failed — is the backend up on $SERVER_URL?")

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

        var cloneId: String? = null
        try {
            // ── Bring the app up & log in as player so Koin's SessionRepository
            //    has a live auth token to send on its way out. ──
            android.util.Log.i(LOG_TAG, "[diag] bootstrapToken=${bootstrapToken.take(12)}...")
            rule.ensureLoggedIn()
            android.util.Log.i(LOG_TAG, "App is logged in as $PLAYER_USERNAME")

            // ── The integration assert: call the real SessionRepository
            //    that's bound in the app's production Koin graph. This
            //    exercises: SessionRepositoryImpl → SpelaApiClient →
            //    generated SessionsApi → real HTTP to 127.0.0.1:8080
            //    → real JSON deserialization of GameSessionResponse. ──
            val koin = KoinPlatformTools.defaultContext().get()
            val sessionRepo = koin.get<SessionRepository>()

            // Diagnostic: confirm whether the production Koin graph has
            // an access token at the moment we're about to call the
            // session repo. If hasTokens is false here, the UI-driven
            // ensureLoggedIn() above didn't actually persist tokens —
            // pointing at a NavigationViewModel/RestoreSession routing
            // bug rather than a Ktor auth-plugin bug.
            val tokenManager = koin.get<com.spela.player.data.remote.interceptor.TokenManager>()
            val accessHead = tokenManager.accessToken?.take(12)
            android.util.Log.i(
                LOG_TAG,
                "[diag] tokenManager.hasTokens=${tokenManager.hasTokens()} accessHead=$accessHead",
            )

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

    private fun loginViaApi(username: String, password: String): String? {
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
            Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            android.util.Log.w(LOG_TAG, "loginViaApi failed: ${e.message}")
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
        // Castlevania (NES) — stable id in the e2e seed data.
        private const val LOG_TAG = "CloneSessionSmoke"
    }
}
