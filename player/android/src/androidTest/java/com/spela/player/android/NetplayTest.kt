package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android netplay smoke test — single-device integration test.
 *
 * Verifies the full netplay session lifecycle through the real app UI
 * on one device, with the second player simulated via direct API calls.
 *
 * Test flow:
 *   1. Admin logs in, navigates to an NES game, taps "Netplay" to create session
 *   2. Lobby screen appears with invite code and "Waiting for player" slot
 *   3. Direct API call joins as "player" (simulates second device)
 *   4. Lobby updates to show both players connected
 *   5. Auto-launch countdown begins ("Starting game in...")
 *
 * This catches real Android integration issues: auth token flow, WebSocket
 * connection, session API round-trips, and real-time lobby updates.
 */
@RunWith(AndroidJUnit4::class)
class NetplayTest {
    

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNetplaySessionCreationAndJoin() {
        // 1. Login as admin and navigate to an NES game
        rule.loginAsAdmin()
        rule.navigateToCastlevania()

        // 2. Open the play menu and tap "Netplay" to create a session
        //    The play button has a dropdown/split menu with "Netplay" option.
        //    First check if the game needs downloading.
        rule.downloadGameIfNeeded()
        rule.tapOn("More options")
        rule.waitForText("Netplay")
        rule.tapOn("Netplay")

        // 3. Wait for the lobby screen to appear
        rule.waitForText("Netplay Lobby", timeout = 10_000)
        rule.waitForContentDescription("Waiting for player to join", timeout = 5_000)

        // 4. Extract the invite code from the screen.
        //    The SpSessionCode component displays a 6-char code.
        //    We'll read it by finding the node with testTag or text pattern.
        //    Since we can't easily extract the code from Compose semantics,
        //    we'll use the API to list admin's sessions and get the invite code.
        val inviteCode = getInviteCodeViaApi("admin", "admin123")
            ?: throw AssertionError("Could not get invite code from API")

        // 5. Join the session as "player" via direct API call
        val joinResult = joinSessionViaApi("player", "player123", inviteCode)
        assert(joinResult) { "Failed to join netplay session via API" }

        // 6. Wait for the lobby to show the second player connected.
        //    The player slot updates via WebSocket push or poll.
        rule.waitForContentDescription("Player: player", timeout = 15_000)

        // 7. Verify the auto-launch countdown starts
        rule.waitForText("Starting game in", timeout = 10_000)

        // 8. Press back to cancel before the game actually launches
        //    (we don't need to run emulation for this smoke test)
        rule.pressBack()
    }

    // ── API helpers ──

    private fun getInviteCodeViaApi(username: String, password: String): String? {
        return runBlocking {
            try {
                val token = loginViaApi(username, password) ?: return@runBlocking null
                val url = URL("$SERVER_URL/api/netplay/sessions")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.requestMethod = "GET"

                if (conn.responseCode != 200) return@runBlocking null
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                // Parse invite code from JSON response.
                // Sessions response is { "data": [...], ... } with inviteCode field.
                // Use simple regex to avoid adding a JSON dependency to tests.
                val codeMatch = Regex("\"inviteCode\"\\s*:\\s*\"([A-Z0-9]+)\"").find(body)
                codeMatch?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun joinSessionViaApi(username: String, password: String, code: String): Boolean {
        return runBlocking {
            try {
                val token = loginViaApi(username, password) ?: return@runBlocking false
                val url = URL("$SERVER_URL/api/netplay/sessions/join")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.write("""{"code":"$code"}""".toByteArray())
                val success = conn.responseCode in 200..299
                conn.disconnect()
                success
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun loginViaApi(username: String, password: String): String? {
        return try {
            val url = URL("$SERVER_URL/api/auth/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Content-Type", "application/json")
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.write(
                """{"username":"$username","password":"$password"}""".toByteArray()
            )
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val tokenMatch = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(body)
            tokenMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val SERVER_URL = "http://127.0.0.1:8080"
    }
}
