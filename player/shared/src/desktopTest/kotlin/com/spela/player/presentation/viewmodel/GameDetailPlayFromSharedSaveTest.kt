package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.AddGameToCollectionUseCase
import com.spela.player.domain.usecase.CreateCollectionUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
import com.spela.player.domain.usecase.GetGameStatsUseCase
import com.spela.player.domain.usecase.TogglePlayLaterUseCase
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailPlayFromSharedSaveTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeSessionRepo: FakePlayFromSharedSaveSessionRepository
    private lateinit var fakeSharedSaveRepo: FakePlayFromSharedSaveSharedSaveRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSessionRepo = FakePlayFromSharedSaveSessionRepository()
        fakeSharedSaveRepo = FakePlayFromSharedSaveSharedSaveRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GameDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val testGameRepo = PlayFromSharedSaveTestGameRepository()
        return GameDetailViewModel(
            getGameDetailUseCase = GetGameDetailUseCase(testGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(testGameRepo),
            togglePlayLaterUseCase = TogglePlayLaterUseCase(testGameRepo),
            downloadRepository = PlayFromSharedSaveTestDownloadRepository(),
            ratingRepository = PlayFromSharedSaveTestRatingRepository(),
            sharedSaveRepository = fakeSharedSaveRepo,
            getMyCollectionsUseCase = GetMyCollectionsUseCase(PlayFromSharedSaveTestCollectionRepository()),
            addGameToCollectionUseCase = AddGameToCollectionUseCase(PlayFromSharedSaveTestCollectionRepository()),
            createCollectionUseCase = CreateCollectionUseCase(PlayFromSharedSaveTestCollectionRepository()),
            getGameStatsUseCase = GetGameStatsUseCase(PlayFromSharedSaveTestGameStatsRepository()),
            gameStatsRepository = PlayFromSharedSaveTestGameStatsRepository(),
            challengeRepository = PlayFromSharedSaveTestChallengeRepository(),
            sharedSessionRepository = PlayFromSharedSaveTestSharedSessionRepository(),
            gameRepository = testGameRepo,
            preferencesRepository = com.spela.player.presentation.viewmodel.emulation.StubPreferencesRepository(),
            apiClient = apiClient,
            scrapeService = ScrapeService(apiClient, testDispatchers, scope),
            dispatchers = testDispatchers,
            scope = scope,
            sessionRepository = fakeSessionRepo,
        )
    }

    @Test
    fun playFromSharedSaveCreatesSessionAndSetsNavigationId() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Boss Fight", "Before final boss", 1024, 5, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.PlayFromSharedSave("s1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isPlayingFromSharedSave)
        assertNotNull(state.playFromSharedSaveSessionId)
        assertEquals("session-1", state.playFromSharedSaveSessionId)
        assertTrue(state.sessions.any { it.id == "session-1" })
    }

    @Test
    fun playFromSharedSaveShowsLoadingState() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Boss Fight", "desc", 1024, 0, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        assertFalse(vm.state.value.isPlayingFromSharedSave)
        assertNull(vm.state.value.playFromSharedSaveSessionId)
    }

    @Test
    fun playFromSharedSaveShowsErrorOnFailure() = runTest(testDispatcher) {
        fakeSessionRepo.shouldFailCreateFromSharedSave = true
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Boss Fight", "desc", 1024, 0, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.PlayFromSharedSave("s1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isPlayingFromSharedSave)
        assertNull(state.playFromSharedSaveSessionId)
        assertNotNull(state.error)
        assertTrue(state.error.orEmpty().contains("Failed to create session"))
    }

    @Test
    fun playFromSharedSaveAddsSessionToList() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Save A", "desc", 512, 0, ""),
            SharedSaveState("s2", "u2", "Bob", null, "1", "Save B", "desc", 1024, 3, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        val sessionsBefore = vm.state.value.sessions.size

        vm.onIntent(GameDetailIntent.PlayFromSharedSave("s2"))
        advanceUntilIdle()

        assertEquals(sessionsBefore + 1, vm.state.value.sessions.size)
        val newSession = vm.state.value.sessions.find { it.id == "session-1" }
        assertNotNull(newSession)
        assertEquals("1", newSession.gameId)
    }
}

private class FakePlayFromSharedSaveSessionRepository : SessionRepository {
    var shouldFailCreateFromSharedSave = false
    private var nextId = 1

    override suspend fun getSessionsForGame(gameId: String) = Result.success(emptyList<GameSession>())
    override suspend fun getSession(sessionId: String) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun createSession(gameId: String, name: String) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun createSessionFromSharedSave(gameId: String, saveId: String): Result<GameSession> {
        if (shouldFailCreateFromSharedSave) return Result.failure(Exception("Failed to create session from shared save"))
        val session = GameSession(
            id = "session-${nextId++}",
            gameId = gameId,
            name = "From shared save $saveId",
        )
        return Result.success(session)
    }
    override suspend fun updateSession(sessionId: String, name: String?, coreName: String?) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun updateSessionCoreFlags(sessionId: String, userLockedCoreVersion: Boolean?, autoLoadSuppressed: Boolean?, rehearsalCrashPending: Boolean?) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
    override suspend fun getSessionSaves(sessionId: String) = Result.success(emptyList<SaveState>())
    override suspend fun updateSessionSave(sessionId: String, saveId: String, name: String) =
        Result.failure<SaveState>(UnsupportedOperationException())
    override suspend fun deleteSessionSave(sessionId: String, saveId: String) = Result.success(Unit)
    override suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String) =
        Result.success(SaveState(id = "1", name = name))
    override suspend fun uploadSessionSaveFromFile(sessionId: String, name: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String) =
        Result.success(SaveState(id = "1", name = name))
    override suspend fun downloadSessionSave(sessionId: String, saveId: String) = Result.success(byteArrayOf())
    override suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String) = Result.success(Unit)
    override suspend fun uploadSessionAutoSaveFromFile(sessionId: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String) = Result.success(Unit)
    override suspend fun downloadSessionAutoSave(sessionId: String) = Result.failure<ByteArray>(Exception("stub"))
    override suspend fun downloadSessionAutoSaveToFile(sessionId: String, outputPath: String) = Result.failure<Unit>(Exception("stub"))
    override suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String) =
        Result.success(SaveState(id = "1", name = "Slot $slot"))
    override suspend fun uploadSlotSaveFromFile(sessionId: String, slot: Int, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String) =
        Result.success(SaveState(id = "1", name = "Slot $slot"))
    override suspend fun downloadSlotSave(sessionId: String, slot: Int) = Result.failure<ByteArray>(Exception("stub"))
    override suspend fun downloadSlotSaveToFile(sessionId: String, slot: Int, outputPath: String) = Result.failure<Unit>(Exception("stub"))
    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String) = Result.success(Unit)
    override suspend fun downloadSessionSram(sessionId: String) = Result.failure<ByteArray>(Exception("stub"))
    override suspend fun uploadSessionSaveDirBundleFromFile(sessionId: String, tarPath: String, tarSize: Long): Result<Unit> = Result.success(Unit)
    override suspend fun downloadSessionSaveDirBundleToFile(sessionId: String, fileStorage: com.spela.player.util.FileStorage, outputPath: String): Result<Unit> = Result.failure(Exception("stub"))
    override suspend fun getSessionCheats(sessionId: String) = Result.success(SessionCheatConfig(false, emptyList()))
    override suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>) =
        Result.success(SessionCheatConfig(cheatsEnabled, enabledIndices))
    override suspend fun cloneSession(sessionId: String, name: String?, saveId: Long?) = Result.failure<GameSession>(Exception("stub"))
}

private class FakePlayFromSharedSaveSharedSaveRepository : SharedSaveRepository {
    var saves = emptyList<SharedSaveState>()

    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int) = Result.success(saves)
    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray) =
        Result.success(SharedSaveState("new-1", "1", "user", null, gameId, name, description, saveData.size.toLong(), 0, ""))
    override suspend fun downloadSharedSave(gameId: String, saveId: String) = Result.success(ByteArray(256))
    override suspend fun deleteSharedSave(gameId: String, saveId: String) = Result.success(Unit)
}

private class PlayFromSharedSaveTestGameRepository : GameRepository {
    private val game = Game(
        id = "1",
        title = "Test Game",
        consoleId = "nes",
        consoleName = "NES",
        scrapeAttempts = 1,
    )

    override suspend fun getConsoles() = Result.success(emptyList<Console>())
    override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
    override suspend fun getAllGames() = Result.success(emptyList<Game>())
    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
    override suspend fun getGameDetail(gameId: String) = Result.success(GameDetail(game))
    override suspend fun getRecentGames() = Result.success(emptyList<Game>())
    override suspend fun getFavoriteGames() = Result.success(emptyList<Game>())
    override suspend fun addFavorite(gameId: String) = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String) = Result.success(Unit)
    override suspend fun getPlayLaterGames() = Result.success(emptyList<Game>())
    override suspend fun addToPlayLater(gameId: String) = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String) = Result.success(Unit)
    override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
    override suspend fun getTopRatedGamesGlobal() = Result.success(emptyList<TopRatedGame>())
    override suspend fun getTopRatedAvailable() = Result.success(emptyList<TopListGame>())
    override suspend fun getLongestGames() = Result.success(emptyList<LongestGame>())
    override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
    override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
    override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
}

private class PlayFromSharedSaveTestRatingRepository : RatingRepository {
    override suspend fun rateGame(gameId: String, rating: Int, review: String) =
        Result.success(GameRating("1", "1", "user", null, gameId, rating, review, ""))
    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int) = Result.success(emptyList<GameRating>())
    override suspend fun getRatingSummary(gameId: String) = Result.success(RatingSummary(0.0, 0, emptyMap()))
    override suspend fun getMyRating(gameId: String) = Result.success<GameRating?>(null)
    override suspend fun deleteRating(gameId: String) = Result.success(Unit)
}

private class PlayFromSharedSaveTestDownloadRepository : DownloadRepository {
    override fun observeDownloads() = MutableStateFlow(emptyList<DownloadProgress>())
    override fun observeDownload(gameId: String) = MutableStateFlow(DownloadProgress(gameId, state = DownloadState.IDLE))
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = MutableStateFlow(emptyList())
    override suspend fun downloadGame(gameId: String, gameTitle: String) = Result.success("/fake")
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String? = null
    override suspend fun isGameCached(gameId: String) = false
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize() = 0L
    override suspend fun clearCache() {}
    override suspend fun scanForOrphanedDownloads() {}
}

private class PlayFromSharedSaveTestCollectionRepository : CollectionRepository {
    override suspend fun getMyCollections(page: Int, pageSize: Int) = Result.success(emptyList<GameCollection>())
    override suspend fun getPublicCollections(page: Int, pageSize: Int) = Result.success(emptyList<GameCollection>())
    override suspend fun getCollection(id: String) = Result.failure<GameCollectionDetail>(Exception("not found"))
    override suspend fun createCollection(name: String, description: String?, isPublic: Boolean) =
        Result.success(GameCollection("1", "1", "user", name = name, description = description, isPublic = isPublic))
    override suspend fun updateCollection(id: String, name: String?, description: String?, isPublic: Boolean?) =
        Result.success(GameCollection(id, "1", "user", name = name ?: "", description = description, isPublic = isPublic ?: false))
    override suspend fun deleteCollection(id: String) = Result.success(Unit)
    override suspend fun addGameToCollection(collectionId: String, gameId: String) = Result.success(Unit)
    override suspend fun removeGameFromCollection(collectionId: String, gameId: String) = Result.success(Unit)
}

private class PlayFromSharedSaveTestGameStatsRepository : GameStatsRepository {
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

private class PlayFromSharedSaveTestSharedSessionRepository : SharedSessionRepository {
    override suspend fun getMySharedSessions() = Result.success(emptyList<SharedSession>())
    override suspend fun getSharedSession(sharedSessionId: String) = Result.failure<SharedSessionDetail>(Exception("stub"))
    override suspend fun getSharedSessionInvitations() = Result.success(emptyList<SharedSessionInvitation>())
    override suspend fun getPendingInvitationCount() = Result.success(0)
    override suspend fun createSharedSession(name: String, gameId: String, description: String) = Result.failure<SharedSessionDetail>(Exception("stub"))
    override suspend fun deleteSharedSession(sharedSessionId: String) = Result.success(Unit)
    override suspend fun inviteUser(sharedSessionId: String, username: String) = Result.success(Unit)
    override suspend fun acceptInvitation(invitationId: String) = Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String) = Result.success(Unit)
    override suspend fun leaveSharedSession(sharedSessionId: String) = Result.success(Unit)
    override suspend fun removeMember(sharedSessionId: String, userId: String) = Result.success(Unit)
    override suspend fun getGameSharedSessions(gameId: String) = Result.success(emptyList<SharedSession>())
    override suspend fun getSharedSessionSaves(sharedSessionId: String) = Result.success(emptyList<SharedSessionSave>())
    override suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long) = Result.success(Unit)
    override suspend fun takeTurn(sharedSessionId: String) = Result.success("stub-token")
    override suspend fun releaseTurn(sharedSessionId: String) = Result.success(Unit)
    override suspend fun heartbeat(sharedSessionId: String) = Result.success(Unit)
    override suspend fun uploadSharedSessionSave(sharedSessionId: String, name: String, turnToken: String, data: ByteArray) =
        Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = name))
    override suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long) = Result.success(byteArrayOf())
    override suspend fun downloadSharedSessionAutoSave(sharedSessionId: String) = Result.success(byteArrayOf())
    override suspend fun uploadSharedSessionAutoSave(sharedSessionId: String, turnToken: String, data: ByteArray) =
        Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = "Auto Save", isAuto = true))
}

private class PlayFromSharedSaveTestChallengeRepository : ChallengeRepository {
    override suspend fun getChallenges(gameId: String?, consoleId: String?, difficulty: String?, sort: String?, page: Int) =
        Result.success(emptyList<Challenge>())
    override suspend fun getGameChallenges(gameId: String, page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getMyChallenges(page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getChallengeDetail(challengeId: String) = Result.failure<Challenge>(Exception("stub"))
    override suspend fun getLeaderboard(challengeId: String, page: Int) = Result.success(emptyList<ChallengeLeaderboardEntry>())
    override suspend fun createChallenge(gameId: String, name: String, description: String, type: String, difficulty: String, coreName: String, saveData: ByteArray, screenshotData: ByteArray?) =
        Result.failure<Challenge>(Exception("stub"))
    override suspend fun downloadChallengeSave(challengeId: String) = Result.success(byteArrayOf())
    override suspend fun startAttempt(challengeId: String) =
        Result.success(ChallengeAttempt("1", challengeId, "1", "test", null, "in_progress", "", null, 0, false))
    override suspend fun completeAttempt(challengeId: String, attemptId: String) =
        Result.success(ChallengeAttempt(attemptId, challengeId, "1", "test", null, "completed", "", "", 1000, false))
    override suspend fun abandonAttempt(challengeId: String, attemptId: String) = Result.success(Unit)
    override suspend fun getMyAttempts(challengeId: String) = Result.success(emptyList<ChallengeAttempt>())
    override suspend fun deleteChallenge(challengeId: String) = Result.success(Unit)
}
