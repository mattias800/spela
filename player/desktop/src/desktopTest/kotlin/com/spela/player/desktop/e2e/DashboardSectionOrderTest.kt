package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.domain.model.UserStats
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for dashboard relevance overhaul:
 * - Zone 1 sections present (Continue Playing, Play Later, Favorites)
 * - Continue Playing capped at 6 (was 10)
 * - Recent Activity capped at 2 (was 3)
 * - Global Top Rated section on dashboard (Zone 2)
 * - Your Stats at bottom (Zone 3)
 * - Empty state only when ALL personal/social sections are empty
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DashboardSectionOrderTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.deviceManager.setDeviceName("Test Device")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ── US-1: Zone 1 sections present ────────────────────────────────────────

    @Test
    fun continuePlayingAppearsInZone1() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // recentGames defaults to 2 items → Continue Playing shown
        onNodeWithText("Continue Playing").assertIsDisplayed()
    }

    @Test
    fun playLaterAppearsWhenDataPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.games = harness.gameRepo.games.map { it.copy(isInPlayLater = true) }

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Play Later").assertIsDisplayed()
    }

    @Test
    fun favoritesAppearsWhenDataPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.games = harness.gameRepo.games.map { it.copy(isFavorite = true) }

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun yourStatsShownWhenStatsAvailable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameStatsRepo.userStats = UserStats(
            totalPlayTime = 3600, gamesPlayed = 5, currentStreak = 1, longestStreak = 1,
            mostPlayedGame = null, mostPlayedGameTime = 0, lastPlayedAt = null,
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Your Stats").assertIsDisplayed()
    }

    // ── US-2: Top Rated on dashboard ─────────────────────────────────────────

    @Test
    fun topRatedSectionShownOnDashboardWhenDataAvailable() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.globalTopRatedGames = listOf(
            TopRatedGame(rank = 1, name = "Chrono Trigger", rating = 96.0, localGameId = null),
            TopRatedGame(rank = 2, name = "Super Metroid", rating = 94.0, localGameId = "1"),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Top Rated").assertIsDisplayed()
        onNodeWithContentDescription("Chrono Trigger, rated 96").assertExists()
        onNodeWithContentDescription("Super Metroid, rated 94").assertExists()
    }

    @Test
    fun topRatedSectionHiddenOnDashboardWhenEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // globalTopRatedGames defaults to empty

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Top Rated").assertDoesNotExist()
    }

    @Test
    fun topRatedAndTrendingChallengesBothShownInZone2() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.challengeRepo.preAddChallenge(id = "c1", gameId = "1", name = "Boss Rush")
        harness.gameRepo.globalTopRatedGames = listOf(
            TopRatedGame(rank = 1, name = "Chrono Trigger", rating = 96.0, localGameId = null),
        )

        setContent { harness.App() }
        advance(harness)

        // Both Zone 2 sections visible together
        onNodeWithText("Trending Challenges").assertIsDisplayed()
        onNodeWithText("Top Rated").assertIsDisplayed()
    }

    // ── US-3: Continue Playing capped at 6 ───────────────────────────────────

    @Test
    fun continuePlayingShowsAtMostSixGames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Override recent games with 10 items to verify the UI cap at 6
        harness.gameRepo.recentGamesOverride = (1..10).map { i ->
            Game(
                id = "recent$i",
                title = "RecentGame $i",
                consoleId = "nes",
                consoleName = "NES",
                fileName = "game$i.nes",
            )
        }

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Continue Playing").assertIsDisplayed()

        // Games 7-10 must NOT appear (UI cap is 6)
        onNodeWithText("RecentGame 7").assertDoesNotExist()
        onNodeWithText("RecentGame 8").assertDoesNotExist()
        onNodeWithText("RecentGame 9").assertDoesNotExist()
        onNodeWithText("RecentGame 10").assertDoesNotExist()
    }

    // ── US-4: Recent Activity capped at 2 ────────────────────────────────────

    @Test
    fun recentActivityShowsAtMostTwoItems() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.socialRepo.activityEvents = listOf(
            ActivityEvent(
                id = "1", userId = "u1", username = "AliceUser", userAvatarUrl = null,
                eventType = "started_playing", gameId = "1", gameTitle = "Game A",
                gameCoverUrl = null, gameConsoleName = "NES", createdAt = "2026-02-01T10:00:00Z",
            ),
            ActivityEvent(
                id = "2", userId = "u2", username = "BobUser", userAvatarUrl = null,
                eventType = "started_playing", gameId = "2", gameTitle = "Game B",
                gameCoverUrl = null, gameConsoleName = "NES", createdAt = "2026-02-01T09:00:00Z",
            ),
            ActivityEvent(
                id = "3", userId = "u3", username = "CarolUser", userAvatarUrl = null,
                eventType = "started_playing", gameId = "3", gameTitle = "Game C",
                gameCoverUrl = null, gameConsoleName = "NES", createdAt = "2026-02-01T08:00:00Z",
            ),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Recent Activity").assertIsDisplayed()

        // First two events visible by username (substring match in annotated string)
        onNodeWithText("AliceUser", substring = true).assertExists()
        onNodeWithText("BobUser", substring = true).assertExists()
        // Third event NOT shown
        onNodeWithText("CarolUser", substring = true).assertDoesNotExist()
    }

    // ── US-5: Empty state detection ──────────────────────────────────────────

    @Test
    fun emptyStateShownWhenAllSectionsEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.games = emptyList()
        // socialRepo defaults to empty

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Your library is empty").assertIsDisplayed()
        onNodeWithText("Continue Playing").assertDoesNotExist()
    }

    @Test
    fun dashboardShownWhenSocialActivityPresentEvenWithNoGames() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.games = emptyList()
        harness.socialRepo.activityEvents = listOf(
            ActivityEvent(
                id = "1", userId = "u1", username = "AliceUser", userAvatarUrl = null,
                eventType = "started_playing", gameId = "1", gameTitle = "Game A",
                gameCoverUrl = null, gameConsoleName = "NES", createdAt = "2026-02-01T10:00:00Z",
            ),
        )

        setContent { harness.App() }
        advance(harness)

        // Social data present → normal dashboard, NOT empty state
        onNodeWithText("Your library is empty").assertDoesNotExist()
        onNodeWithText("Recent Activity").assertIsDisplayed()
    }

    @Test
    fun dashboardShownWhenOnlineUsersPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.gameRepo.games = emptyList()
        harness.socialRepo.onlineUsers = listOf(
            OnlineUser(id = "u1", username = "Alice", avatarUrl = null, currentGame = null),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Your library is empty").assertDoesNotExist()
        onNodeWithText("Online Now").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenOnlyTopRatedDataPresent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // No personal games, no social data — only top-rated discovery data
        harness.gameRepo.games = emptyList()
        harness.gameRepo.globalTopRatedGames = listOf(
            TopRatedGame(rank = 1, name = "Chrono Trigger", rating = 96.0, localGameId = null),
        )

        setContent { harness.App() }
        advance(harness)

        // Top-rated only → still show empty state (discovery only, no personal data)
        onNodeWithText("Your library is empty").assertIsDisplayed()
    }
}
