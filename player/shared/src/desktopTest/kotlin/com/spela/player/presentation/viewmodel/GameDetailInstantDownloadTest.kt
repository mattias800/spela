package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameAchievement
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameRating
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.INSTANT_DOWNLOAD_FALLBACK_DELAY_MS
import com.spela.player.domain.model.INSTANT_DOWNLOAD_THRESHOLD_BYTES
import com.spela.player.domain.model.LongestGame
import com.spela.player.domain.model.PaginatedResult
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.RecentAchievement
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.domain.model.UserStats
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.CollectionRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.GameStatsRepository
import com.spela.player.domain.repository.RatingRepository
import com.spela.player.domain.repository.SharedSaveRepository
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.domain.usecase.AddGameToCollectionUseCase
import com.spela.player.domain.usecase.CreateCollectionUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.GetGameStatsUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
import com.spela.player.domain.usecase.TogglePlayLaterUseCase
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #932 instant-download UX tests. Verify the ViewModel side of the
 * silent download-then-launch flow:
 *   - sub-threshold games show isInstantDownload during the silent
 *     window and raise pendingAutoLaunch on success
 *   - if the download exceeds INSTANT_DOWNLOAD_FALLBACK_DELAY_MS
 *     the silent flag drops back to false so the regular progress UI
 *     can take over
 *   - above-threshold games never enter the silent flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailInstantDownloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeGameRepo: InstantDlStubGameRepository
    private lateinit var fakeDownloadRepo: BlockingDownloadRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeGameRepo = InstantDlStubGameRepository()
        fakeDownloadRepo = BlockingDownloadRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GameDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        return GameDetailViewModel(
            getGameDetailUseCase = GetGameDetailUseCase(fakeGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeGameRepo),
            togglePlayLaterUseCase = TogglePlayLaterUseCase(fakeGameRepo),
            downloadRepository = fakeDownloadRepo,
            ratingRepository = NoopRatingRepository(),
            sharedSaveRepository = NoopSharedSaveRepository(),
            getMyCollectionsUseCase = GetMyCollectionsUseCase(NoopCollectionRepository()),
            addGameToCollectionUseCase = AddGameToCollectionUseCase(NoopCollectionRepository()),
            createCollectionUseCase = CreateCollectionUseCase(NoopCollectionRepository()),
            getGameStatsUseCase = GetGameStatsUseCase(NoopGameStatsRepository()),
            gameStatsRepository = NoopGameStatsRepository(),
            challengeRepository = NoopChallengeRepository(),
            sharedSessionRepository = NoopSharedSessionRepository(),
            gameRepository = fakeGameRepo,
            preferencesRepository = com.spela.player.presentation.viewmodel.emulation.StubPreferencesRepository(),
            apiClient = apiClient,
            scrapeService = ScrapeService(apiClient, testDispatchers, scope),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun subThresholdDownload_setsInstantFlag_thenRaisesAutoLaunch() = runTest(testDispatcher) {
        fakeGameRepo.fileSize = 1024 * 1024 // 1 MB — well below 16 MB threshold
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.DownloadGameAndPlay)
        // Let the dispatch run but DON'T let the download complete yet.
        advanceTimeBy(50)

        assertTrue(vm.state.value.isDownloading)
        assertTrue(
            vm.state.value.isInstantDownload,
            "sub-threshold game should be in the silent window",
        )
        assertFalse(vm.state.value.pendingAutoLaunch)

        // Complete the download.
        fakeDownloadRepo.completeDownload(Result.success("/fake"))
        advanceUntilIdle()

        assertFalse(vm.state.value.isDownloading)
        assertFalse(vm.state.value.isInstantDownload)
        assertTrue(
            vm.state.value.pendingAutoLaunch,
            "successful sub-threshold download must signal auto-launch",
        )
        assertTrue(vm.state.value.isGameCached)
    }

    @Test
    fun subThresholdDownload_exceedsFallback_dropsSilentFlag() = runTest(testDispatcher) {
        fakeGameRepo.fileSize = 1024 * 1024
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.DownloadGameAndPlay)
        advanceTimeBy(50)
        assertTrue(vm.state.value.isInstantDownload)

        // Stay in flight past the silent window. The 750 ms fallback
        // should flip the silent flag off so the regular progress UI
        // takes over.
        advanceTimeBy(INSTANT_DOWNLOAD_FALLBACK_DELAY_MS + 100)

        assertTrue(vm.state.value.isDownloading, "still downloading")
        assertFalse(
            vm.state.value.isInstantDownload,
            "after the silent window the regular UI must be visible",
        )
        assertFalse(vm.state.value.pendingAutoLaunch)
    }

    @Test
    fun aboveThresholdDownload_neverEntersSilentFlow() = runTest(testDispatcher) {
        // 32 MB — above the 16 MB threshold.
        fakeGameRepo.fileSize = INSTANT_DOWNLOAD_THRESHOLD_BYTES + 1024
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.DownloadGameAndPlay)
        advanceTimeBy(50)

        assertTrue(vm.state.value.isDownloading)
        assertFalse(
            vm.state.value.isInstantDownload,
            "above-threshold game must show progress UI from frame 1",
        )

        fakeDownloadRepo.completeDownload(Result.success("/fake"))
        advanceUntilIdle()

        // Note: pendingAutoLaunch still fires for above-threshold —
        // the user dispatched DownloadGameAndPlay, so the launch is
        // expected once the download finishes. The visual difference
        // is only in whether the progress bar was visible.
        assertTrue(vm.state.value.pendingAutoLaunch)
    }

    @Test
    fun consumeAutoLaunch_clearsTheFlag() = runTest(testDispatcher) {
        fakeGameRepo.fileSize = 1024 * 1024
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.DownloadGameAndPlay)
        fakeDownloadRepo.completeDownload(Result.success("/fake"))
        advanceUntilIdle()
        assertTrue(vm.state.value.pendingAutoLaunch)

        vm.onIntent(GameDetailIntent.ConsumeAutoLaunch)
        assertFalse(vm.state.value.pendingAutoLaunch)
    }
}

private class InstantDlStubGameRepository : GameRepository {
    var fileSize: Long = 1024 * 1024 // default sub-threshold
    private val baseGame = Game(
        id = "1",
        title = "Test Game",
        consoleId = "nes",
        consoleName = "NES",
        scrapeAttempts = 1,
        userRating = null,
    )

    override suspend fun getConsoles(): Result<List<Console>> = Result.success(emptyList())
    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getAllGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
        Result.success(GameDetail(baseGame.copy(fileSize = fileSize)))
    override suspend fun getRecentGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getFavoriteGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getPlayLaterGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>> = Result.success(emptyList())
    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
    override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> = Result.success(emptyList())
    override suspend fun getLongestGames(): Result<List<LongestGame>> = Result.success(emptyList())
    override suspend fun getSimilarGames(gameId: String): Result<List<SimilarGame>> = Result.success(emptyList())
    override suspend fun getDeveloperGames(gameId: String): Result<List<DeveloperGame>> = Result.success(emptyList())
    override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
}

private class BlockingDownloadRepository : DownloadRepository {
    private val deferred = CompletableDeferred<Result<String>>()

    fun completeDownload(result: Result<String>) {
        deferred.complete(result)
    }

    override fun observeDownloads(): Flow<List<DownloadProgress>> = MutableStateFlow(emptyList())
    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        MutableStateFlow(DownloadProgress(gameId, state = DownloadState.IDLE))
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = MutableStateFlow(emptyList())
    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> = deferred.await()
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String? = null
    override suspend fun isGameCached(gameId: String): Boolean = false
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize(): Long = 0
    override suspend fun clearCache() {}
    override suspend fun scanForOrphanedDownloads() {}
}

private class NoopRatingRepository : RatingRepository {
    override suspend fun rateGame(gameId: String, rating: Int, review: String): Result<GameRating> =
        Result.success(GameRating("1", "1", "user", null, gameId, rating, review, ""))
    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int): Result<List<GameRating>> =
        Result.success(emptyList())
    override suspend fun getRatingSummary(gameId: String): Result<RatingSummary> =
        Result.success(RatingSummary(0.0, 0, emptyMap()))
    override suspend fun getMyRating(gameId: String): Result<GameRating?> = Result.success(null)
    override suspend fun deleteRating(gameId: String): Result<Unit> = Result.success(Unit)
}

private class NoopSharedSaveRepository : SharedSaveRepository {
    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> =
        Result.success(emptyList())
    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> =
        Result.success(SharedSaveState("1", "1", "user", null, gameId, name, description, saveData.size.toLong(), 0, ""))
    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(0))
    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> =
        Result.success(Unit)
}

private class NoopCollectionRepository : CollectionRepository {
    override suspend fun getMyCollections(page: Int, pageSize: Int): Result<List<GameCollection>> =
        Result.success(emptyList())
    override suspend fun getPublicCollections(page: Int, pageSize: Int): Result<List<GameCollection>> =
        Result.success(emptyList())
    override suspend fun getCollection(id: String): Result<GameCollectionDetail> =
        Result.failure(Exception("not found"))
    override suspend fun createCollection(name: String, description: String?, isPublic: Boolean): Result<GameCollection> =
        Result.success(GameCollection("1", "1", "user", name = name, description = description, isPublic = isPublic))
    override suspend fun updateCollection(id: String, name: String?, description: String?, isPublic: Boolean?): Result<GameCollection> =
        Result.success(GameCollection(id, "1", "user", name = name ?: "", description = description, isPublic = isPublic ?: false))
    override suspend fun deleteCollection(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun addGameToCollection(collectionId: String, gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeGameFromCollection(collectionId: String, gameId: String): Result<Unit> = Result.success(Unit)
}

private class NoopGameStatsRepository : GameStatsRepository {
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

private class NoopChallengeRepository : ChallengeRepository {
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

private class NoopSharedSessionRepository : SharedSessionRepository {
    override suspend fun getMySharedSessions() = Result.success(emptyList<SharedSession>())
    override suspend fun getSharedSession(sharedSessionId: String) = Result.failure<SharedSessionDetail>(Exception("stub"))
    override suspend fun getSharedSessionInvitations() = Result.success(emptyList<SharedSessionInvitation>())
    override suspend fun getPendingInvitationCount() = Result.success(0)
    override suspend fun createSharedSession(name: String, gameId: String, description: String, sourceSessionId: Long?) = Result.failure<SharedSessionDetail>(Exception("stub"))
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
