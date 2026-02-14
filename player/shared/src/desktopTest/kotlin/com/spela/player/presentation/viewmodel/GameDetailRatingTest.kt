package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
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
class GameDetailRatingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeGameRepo: StubGameRepository
    private lateinit var fakeRatingRepo: StubRatingRepository
    private lateinit var fakeDownloadRepo: StubDownloadRepository
    private lateinit var fakeSaveRepo: StubSaveRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeGameRepo = StubGameRepository()
        fakeRatingRepo = StubRatingRepository()
        fakeDownloadRepo = StubDownloadRepository()
        fakeSaveRepo = StubSaveRepository()
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
            saveRepository = fakeSaveRepo,
            ratingRepository = fakeRatingRepo,
            sharedSaveRepository = StubSharedSaveRepository(),
            apiClient = apiClient,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun loadGamePopulatesMyRating() = runTest(testDispatcher) {
        fakeGameRepo.userRating = 4
        val vm = createViewModel()

        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(4, state.myRating)
        assertNotNull(state.ratingSummary)
    }

    @Test
    fun rateGameUpdatesMyRating() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.RateGame(5))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(5, state.myRating)
        assertFalse(state.isRating)
    }

    @Test
    fun deleteRatingClearsMyRating() = runTest(testDispatcher) {
        fakeGameRepo.userRating = 3
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()
        assertEquals(3, vm.state.value.myRating)

        vm.onIntent(GameDetailIntent.DeleteRating)
        advanceUntilIdle()

        assertNull(vm.state.value.myRating)
    }

    @Test
    fun rateGameFailureSetsError() = runTest(testDispatcher) {
        fakeRatingRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.RateGame(3))
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isRating)
    }
}

private class StubGameRepository : GameRepository {
    var userRating: Int? = null
    private val game = Game(
        id = "1",
        title = "Test Game",
        consoleId = "1",
        consoleName = "NES",
        scrapeAttempts = 1,
        userRating = null,
    )

    override suspend fun getConsoles(): Result<List<Console>> = Result.success(emptyList())
    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getAllGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun searchGames(query: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
        Result.success(GameDetail(game.copy(userRating = userRating)))
    override suspend fun getRecentGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getFavoriteGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getPlayLaterGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
}

private class StubRatingRepository : RatingRepository {
    var shouldFail = false

    override suspend fun rateGame(gameId: String, rating: Int, review: String): Result<GameRating> {
        if (shouldFail) return Result.failure(Exception("Rating failed"))
        return Result.success(GameRating("1", "1", "user", null, gameId, rating, review, ""))
    }
    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int): Result<List<GameRating>> =
        Result.success(emptyList())
    override suspend fun getRatingSummary(gameId: String): Result<RatingSummary> =
        Result.success(RatingSummary(4.0, 10, mapOf(5 to 5, 4 to 3, 3 to 2)))
    override suspend fun getMyRating(gameId: String): Result<GameRating?> =
        Result.success(null)
    override suspend fun deleteRating(gameId: String): Result<Unit> =
        Result.success(Unit)
}

private class StubDownloadRepository : DownloadRepository {
    override fun observeDownloads(): Flow<List<DownloadProgress>> = MutableStateFlow(emptyList())
    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        MutableStateFlow(DownloadProgress(gameId, state = DownloadState.IDLE))
    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> = Result.success("/fake")
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String? = null
    override suspend fun isGameCached(gameId: String): Boolean = false
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize(): Long = 0
    override suspend fun clearCache() {}
}

private class StubSaveRepository : SaveRepository {
    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> = Result.success(emptyList())
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState> =
        Result.success(SaveState(1, 1, "save"))
    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(0))
    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> = Result.success(Unit)
    override suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState> =
        Result.success(SaveState(1, 1, "auto"))
    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> = Result.success(ByteArray(0))
}

private class StubSharedSaveRepository : SharedSaveRepository {
    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> =
        Result.success(emptyList())
    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> =
        Result.success(SharedSaveState("1", "1", "user", null, gameId, name, description, saveData.size.toLong(), 0, ""))
    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(0))
    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> =
        Result.success(Unit)
}
