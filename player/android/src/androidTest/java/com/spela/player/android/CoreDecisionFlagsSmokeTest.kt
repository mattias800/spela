package com.spela.player.android

import com.spela.player.domain.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.mp.KoinPlatformTools
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android smoke test for the #672 core-upgrade decision flags on
 * `PUT /api/sessions/{id}`. Verifies the new `userLockedCoreVersion`,
 * `autoLoadSuppressed`, and `rehearsalCrashPending` fields round-trip
 * through the production SessionRepository → SpelaApiClient → generated
 * OpenAPI client → real server → real DB.
 *
 * Purpose — integration, not UI. Sheets A–D and the lock chip are
 * covered by desktop composable + VM tests with fake repos. This test
 * exercises what desktop cannot:
 *
 *   * Real HTTP round-trip to `PUT /api/sessions/{id}`,
 *   * Real JWT auth the production SessionRepository attaches,
 *   * Generated Kotlin client's `*bool` tri-state JSON serialization
 *     against the live server schema (catches any Kotlin ⇄ Go drift
 *     in the #673 flag contract),
 *   * Server-side GORM persistence of each flag read back via an
 *     independent cURL GET.
 *
 * See `CloneSessionSmokeTest.kt` in this package for the pattern this
 * test is based on — same Koin-bound production repo + cURL-based
 * bootstrap/teardown/cross-check structure.
 */
class CoreDecisionFlagsSmokeTest : BaseE2ETest() {

    @Test
    fun coreDecisionFlagsRoundTripThroughRealApi() {
        val bootstrapToken = loginViaApi(PLAYER_USERNAME, PLAYER_PASSWORD)
            ?: throw AssertionError("Player login via API failed — is the backend up on $SERVER_URL?")

        // Discover an available game id at runtime — local seed has
        // Castlevania (id 126), CI's testdata-public ships only
        // nestest.nes (a different id). The flag round-trip cares
        // about session lifecycle, not which game it points at.
        val gameId = firstAvailableGameId(bootstrapToken)
            ?: throw AssertionError("No games available on backend — check seed data")
        android.util.Log.i(LOG_TAG, "Using gameId=$gameId for core-flags test")

        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val sourceName = "QA-CoreFlags-$suffix"
        val sessionId = createSessionViaApi(bootstrapToken, gameId, sourceName)
            ?: throw AssertionError("Failed to seed session via API (gameId=$gameId)")
        android.util.Log.i(LOG_TAG, "Seeded session id=$sessionId name='$sourceName'")

        try {
            rule.ensureLoggedIn()
            android.util.Log.i(LOG_TAG, "App logged in as $PLAYER_USERNAME")

            val koin = KoinPlatformTools.defaultContext().get()
            val sessionRepo = koin.get<SessionRepository>()

            // ── Lock the session (Sheet A → Lock or session-detail chip click) ──
            val lockResult = runBlocking {
                sessionRepo.updateSessionCoreFlags(
                    sessionId = sessionId,
                    userLockedCoreVersion = true,
                )
            }
            val lockedResponse = lockResult.getOrElse { err ->
                throw AssertionError(
                    "updateSessionCoreFlags(userLocked=true) failed: " +
                        "${err::class.simpleName}: ${err.message}",
                )
            }
            check(lockedResponse.userLockedCoreVersion) {
                "Response still reports userLockedCoreVersion=false after lock write"
            }
            check(!lockedResponse.autoLoadSuppressed) {
                "Lock write spilled over into autoLoadSuppressed (must be independent flag)"
            }
            check(!lockedResponse.rehearsalCrashPending) {
                "Lock write spilled over into rehearsalCrashPending (must be independent flag)"
            }
            // Independent cross-check: server actually persisted the flag.
            val afterLock = getSessionJsonViaApi(bootstrapToken, sessionId)
                ?: throw AssertionError("Could not fetch session after lock write")
            check(afterLock.contains("\"userLockedCoreVersion\":true")) {
                "Server did not persist userLockedCoreVersion=true. Got: $afterLock"
            }
            android.util.Log.i(LOG_TAG, "Lock write persisted ✓")

            // ── Set the auto-load suppression sentinel (Sheet B / D → Start fresh) ──
            val suppressResult = runBlocking {
                sessionRepo.updateSessionCoreFlags(
                    sessionId = sessionId,
                    autoLoadSuppressed = true,
                )
            }
            val afterSuppress = suppressResult.getOrElse { err ->
                throw AssertionError(
                    "updateSessionCoreFlags(autoLoadSuppressed=true) failed: " +
                        "${err::class.simpleName}: ${err.message}",
                )
            }
            check(afterSuppress.autoLoadSuppressed) {
                "Response still reports autoLoadSuppressed=false after suppress write"
            }
            check(afterSuppress.userLockedCoreVersion) {
                "Suppress write cleared the lock — tri-state update semantics broken " +
                    "(a null field must leave the flag untouched, not reset to false)"
            }
            val afterSuppressServer = getSessionJsonViaApi(bootstrapToken, sessionId)
                ?: throw AssertionError("Could not fetch session after suppress write")
            check(afterSuppressServer.contains("\"autoLoadSuppressed\":true")) {
                "Server did not persist autoLoadSuppressed=true. Got: $afterSuppressServer"
            }
            check(afterSuppressServer.contains("\"userLockedCoreVersion\":true")) {
                "Suppress write must leave the prior lock flag intact — server shows: $afterSuppressServer"
            }
            android.util.Log.i(LOG_TAG, "Suppress write persisted independently ✓")

            // ── Arm the crash sentinel (rehearsal entry) ──
            val armResult = runBlocking {
                sessionRepo.updateSessionCoreFlags(
                    sessionId = sessionId,
                    rehearsalCrashPending = true,
                )
            }
            val afterArm = armResult.getOrElse { err ->
                throw AssertionError(
                    "updateSessionCoreFlags(rehearsalCrashPending=true) failed: " +
                        "${err::class.simpleName}: ${err.message}",
                )
            }
            check(afterArm.rehearsalCrashPending) {
                "Response still reports rehearsalCrashPending=false after arm write"
            }
            val afterArmServer = getSessionJsonViaApi(bootstrapToken, sessionId)
                ?: throw AssertionError("Could not fetch session after arm write")
            check(afterArmServer.contains("\"rehearsalCrashPending\":true")) {
                "Server did not persist rehearsalCrashPending=true. Got: $afterArmServer"
            }
            // Independent cross-check that the arm write didn't silently
            // reset its siblings on the server side (response-echo false
            // positives won't cover this case).
            check(afterArmServer.contains("\"userLockedCoreVersion\":true")) {
                "Arm write must leave the lock flag intact — server shows: $afterArmServer"
            }
            check(afterArmServer.contains("\"autoLoadSuppressed\":true")) {
                "Arm write must leave the auto-load suppression flag intact — server shows: $afterArmServer"
            }
            android.util.Log.i(LOG_TAG, "Crash sentinel arm persisted ✓")

            // ── Unlock (session-detail chip "Use the latest version instead") ──
            val unlockResult = runBlocking {
                sessionRepo.updateSessionCoreFlags(
                    sessionId = sessionId,
                    userLockedCoreVersion = false,
                )
            }
            val afterUnlock = unlockResult.getOrElse { err ->
                throw AssertionError("updateSessionCoreFlags(unlock) failed: ${err.message}")
            }
            check(!afterUnlock.userLockedCoreVersion) {
                "Unlock write did not clear userLockedCoreVersion"
            }
            check(afterUnlock.autoLoadSuppressed) {
                "Unlock write must leave autoLoadSuppressed intact"
            }
            check(afterUnlock.rehearsalCrashPending) {
                "Unlock write must leave rehearsalCrashPending intact"
            }
            // Independent cross-check — the in-memory response above
            // could theoretically be a request echo, so re-read from
            // the server to prove persistence and tri-state semantics.
            val afterUnlockServer = getSessionJsonViaApi(bootstrapToken, sessionId)
                ?: throw AssertionError("Could not fetch session after unlock write")
            check(afterUnlockServer.contains("\"userLockedCoreVersion\":false")) {
                "Server did not persist the unlock. Got: $afterUnlockServer"
            }
            check(afterUnlockServer.contains("\"autoLoadSuppressed\":true")) {
                "Unlock write must leave autoLoadSuppressed intact on the server — got: $afterUnlockServer"
            }
            check(afterUnlockServer.contains("\"rehearsalCrashPending\":true")) {
                "Unlock write must leave rehearsalCrashPending intact on the server — got: $afterUnlockServer"
            }
            android.util.Log.i(LOG_TAG, "Unlock persisted, siblings untouched ✓")

            android.util.Log.i(LOG_TAG, "Smoke test PASS — sessionId=$sessionId")
        } finally {
            runCatching { deleteSessionViaApi(bootstrapToken, sessionId) }
        }
    }

    // ── HTTP helpers ──

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

    private fun getSessionJsonViaApi(token: String, sessionId: String): String? {
        return try {
            val conn = (URL("$SERVER_URL/api/sessions/$sessionId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body
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
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        private const val SERVER_URL = "http://127.0.0.1:8080"
        private const val PLAYER_USERNAME = "player"
        private const val PLAYER_PASSWORD = "player123"
        private const val LOG_TAG = "CoreFlagsSmoke"
    }
}
