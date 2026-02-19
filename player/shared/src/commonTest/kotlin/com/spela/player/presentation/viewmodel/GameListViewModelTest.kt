package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.domain.usecase.*
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val fakeGameRepo = FakeGameRepository()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val noOpEngineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine(MockEngineConfig().apply {
                addHandler { respond("", HttpStatusCode.OK) }
                block()
            })
        }
    }

    private fun createViewModel(): GameListViewModel {
        val scope = CoroutineScope(testDispatcher)
        val stubStatsRepo = GameListTestGameStatsRepository()
        val apiClient = SpelaApiClient(noOpEngineFactory, TokenManager())
        return GameListViewModel(
            getConsolesUseCase = GetConsolesUseCase(fakeGameRepo),
            getGamesForConsoleUseCase = GetGamesForConsoleUseCase(fakeGameRepo),
            searchGamesUseCase = SearchGamesUseCase(fakeGameRepo),
            getRecentGamesUseCase = GetRecentGamesUseCase(fakeGameRepo),
            getFavoriteGamesUseCase = GetFavoriteGamesUseCase(fakeGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeGameRepo),
            getPlayLaterGamesUseCase = GetPlayLaterGamesUseCase(fakeGameRepo),
            togglePlayLaterUseCase = TogglePlayLaterUseCase(fakeGameRepo),
            getUserStatsUseCase = GetUserStatsUseCase(stubStatsRepo),
            getRecentAchievementsUseCase = GetRecentAchievementsUseCase(stubStatsRepo),
            challengeRepository = GameListTestChallengeRepository(),
            scrapeService = ScrapeService(apiClient, testDispatchers, scope),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue(state.consoles.isEmpty())
        assertTrue(state.games.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun loadDashboardPopulatesState() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadDashboard)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.consoles.size)
        assertEquals(1, state.recentGames.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadConsolesPopulatesConsoles() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.consoles.size)
        assertEquals("NES", state.consoles[0].name)
        assertEquals("SNES", state.consoles[1].name)
    }

    @Test
    fun selectConsoleLoadsGames() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.SelectConsole("1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("1", state.selectedConsoleId)
        assertEquals(2, state.games.size)
    }

    @Test
    fun searchFindsGames() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.Search("Mario"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Mario", state.searchQuery)
        assertEquals(1, state.games.size)
        assertEquals("Super Mario Bros.", state.games[0].title)
    }

    @Test
    fun errorStateOnFailure() = runTest(testDispatcher) {
        fakeGameRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeGameRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(GameListIntent.DismissError)
        assertNull(vm.state.value.error)
    }
}

class FakeGameRepository : GameRepository {
    var shouldFail = false

    private val consoles = listOf(
        Console("1", "NES", "NES", 10),
        Console("2", "SNES", "SNES", 5),
    )

    private val games = listOf(
        Game("1", "Super Mario Bros.", "1", "NES", fileSize = 40960, fileName = "smb.nes"),
        Game("2", "The Legend of Zelda", "1", "NES", fileSize = 131072, fileName = "zelda.nes"),
        Game("3", "Chrono Trigger", "2", "SNES", fileSize = 4194304, fileName = "ct.sfc"),
    )

    override suspend fun getConsoles(): Result<List<Console>> {
        return if (shouldFail) Result.failure(Exception("Network error")) else Result.success(consoles)
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.consoleId == consoleId })
    }

    override suspend fun getAllGames(): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error")) else Result.success(games)
    }

    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.title.contains(query, ignoreCase = true) })
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> {
        val game = games.find { it.id == gameId }
            ?: return Result.failure(Exception("Not found"))
        return Result.success(GameDetail(game))
    }

    override suspend fun getRecentGames(): Result<List<Game>> {
        return Result.success(games.take(1))
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> {
        return Result.success(emptyList())
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getPlayLaterGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
}

private class GameListTestGameStatsRepository : GameStatsRepository {
    override suspend fun getGameStats(gameId: String) = Result.success(GameStats(0, 0L, 0L, emptyList()))
    override suspend fun getGameAchievements(gameId: String) = Result.success(emptyList<GameAchievement>())
    override suspend fun getAchievementProgress(gameId: String) = Result.success(emptyList<AchievementProgress>())
    override suspend fun getAchievementTimeline(gameId: String) = Result.success(
        AchievementTimelineData(null, "", 0L, emptyList(), 0, 0, 0, 0)
    )
    override suspend fun getAchievementLeaderboard(gameId: String) = Result.success(emptyList<AchievementPlayerRanking>())
    override suspend fun getUserStats() = Result.success(UserStats(0L, 0L, 0, 0, null, 0L, null))
    override suspend fun getRecentAchievements() = Result.success(emptyList<RecentAchievement>())
}

private class GameListTestChallengeRepository : ChallengeRepository {
    override suspend fun getChallenges(gameId: String?, consoleId: String?, difficulty: String?, sort: String?, page: Int) =
        Result.success(emptyList<Challenge>())
    override suspend fun getGameChallenges(gameId: String, page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getMyChallenges(page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getChallengeDetail(challengeId: String) = Result.failure<Challenge>(Exception("stub"))
    override suspend fun getLeaderboard(challengeId: String, page: Int) = Result.success(emptyList<ChallengeLeaderboardEntry>())
    override suspend fun createChallenge(gameId: String, name: String, description: String, type: String, difficulty: String, coreName: String, saveData: ByteArray, screenshotData: ByteArray?) =
        Result.failure<Challenge>(Exception("stub"))
    override suspend fun downloadChallengeSave(challengeId: String) = Result.success(byteArrayOf())
    override suspend fun startAttempt(challengeId: String) = Result.failure<ChallengeAttempt>(Exception("stub"))
    override suspend fun completeAttempt(challengeId: String, attemptId: String) = Result.failure<ChallengeAttempt>(Exception("stub"))
    override suspend fun abandonAttempt(challengeId: String, attemptId: String) = Result.success(Unit)
    override suspend fun getMyAttempts(challengeId: String) = Result.success(emptyList<ChallengeAttempt>())
    override suspend fun deleteChallenge(challengeId: String) = Result.success(Unit)
}
