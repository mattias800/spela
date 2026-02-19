package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.AddGameToCollectionUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
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
class GameDetailSharedSaveTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeSharedSaveRepo: TestSharedSaveRepository
    private lateinit var fakeSaveRepo: TestSaveRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSharedSaveRepo = TestSharedSaveRepository()
        fakeSaveRepo = TestSaveRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GameDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val testGameRepo = TestGameRepository()
        return GameDetailViewModel(
            getGameDetailUseCase = GetGameDetailUseCase(testGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(testGameRepo),
            togglePlayLaterUseCase = TogglePlayLaterUseCase(testGameRepo),
            downloadRepository = TestDownloadRepository(),
            saveRepository = fakeSaveRepo,
            ratingRepository = TestRatingRepository(),
            sharedSaveRepository = fakeSharedSaveRepo,
            getMyCollectionsUseCase = GetMyCollectionsUseCase(TestCollectionRepository()),
            addGameToCollectionUseCase = AddGameToCollectionUseCase(TestCollectionRepository()),
            apiClient = apiClient,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun loadGamePopulatesSharedSaves() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Boss Fight", "Before final boss", 1024, 5, ""),
        )
        val vm = createViewModel()

        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.sharedSaves.size)
        assertEquals("Boss Fight", state.sharedSaves[0].name)
    }

    @Test
    fun loadSharedSavesRefreshesList() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.sharedSaves.size)

        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "New Save", "desc", 512, 0, ""),
        )
        vm.onIntent(GameDetailIntent.LoadSharedSaves)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sharedSaves.size)
        assertEquals("New Save", vm.state.value.sharedSaves[0].name)
    }

    @Test
    fun deleteSharedSaveRemovesFromList() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Save A", "desc", 512, 0, ""),
            SharedSaveState("s2", "u2", "Bob", null, "1", "Save B", "desc", 1024, 3, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.sharedSaves.size)

        vm.onIntent(GameDetailIntent.DeleteSharedSave("s1"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sharedSaves.size)
        assertEquals("s2", vm.state.value.sharedSaves[0].id)
    }

    @Test
    fun downloadSharedSaveAddsSaveState() = runTest(testDispatcher) {
        fakeSharedSaveRepo.saves = listOf(
            SharedSaveState("s1", "u1", "Alice", null, "1", "Boss Save", "desc", 256, 1, ""),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.saveStates.size)

        vm.onIntent(GameDetailIntent.DownloadSharedSave("s1"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.saveStates.size)
        assertEquals("Shared Save", vm.state.value.saveStates[0].name)
    }

    @Test
    fun shareSaveAddsToSharedList() = runTest(testDispatcher) {
        fakeSaveRepo.saveData = ByteArray(128)
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.sharedSaves.size)

        vm.onIntent(GameDetailIntent.ShareSave("save1", "My Save", "Beat level 3"))
        advanceUntilIdle()

        assertFalse(vm.state.value.isSharing)
        assertEquals(1, vm.state.value.sharedSaves.size)
        assertEquals("My Save", vm.state.value.sharedSaves[0].name)
    }

    @Test
    fun shareSaveFailureSetsError() = runTest(testDispatcher) {
        fakeSaveRepo.saveData = ByteArray(128)
        fakeSharedSaveRepo.shouldFailShare = true
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        vm.onIntent(GameDetailIntent.ShareSave("save1", "My Save", "desc"))
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isSharing)
    }
}

private class TestSharedSaveRepository : SharedSaveRepository {
    var saves = emptyList<SharedSaveState>()
    var shouldFailShare = false

    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> =
        Result.success(saves)

    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> {
        if (shouldFailShare) return Result.failure(Exception("Share failed"))
        return Result.success(SharedSaveState("new-1", "1", "user", null, gameId, name, description, saveData.size.toLong(), 0, ""))
    }

    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(256))

    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> =
        Result.success(Unit)
}

private class TestGameRepository : GameRepository {
    private val game = Game(
        id = "1",
        title = "Test Game",
        consoleId = "1",
        consoleName = "NES",
        scrapeAttempts = 1,
    )

    override suspend fun getConsoles(): Result<List<Console>> = Result.success(emptyList())
    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getAllGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun searchGames(query: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
        Result.success(GameDetail(game))
    override suspend fun getRecentGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getFavoriteGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getPlayLaterGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
}

private class TestRatingRepository : RatingRepository {
    override suspend fun rateGame(gameId: String, rating: Int, review: String): Result<GameRating> =
        Result.success(GameRating("1", "1", "user", null, gameId, rating, review, ""))
    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int): Result<List<GameRating>> =
        Result.success(emptyList())
    override suspend fun getRatingSummary(gameId: String): Result<RatingSummary> =
        Result.success(RatingSummary(0.0, 0, emptyMap()))
    override suspend fun getMyRating(gameId: String): Result<GameRating?> =
        Result.success(null)
    override suspend fun deleteRating(gameId: String): Result<Unit> =
        Result.success(Unit)
}

private class TestDownloadRepository : DownloadRepository {
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

private class TestCollectionRepository : CollectionRepository {
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

private class TestSaveRepository : SaveRepository {
    var saveData: ByteArray? = null

    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> = Result.success(emptyList())
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> =
        if (saveData != null) Result.success(saveData!!) else Result.success(ByteArray(0))
    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> = Result.success(Unit)
    override suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState> =
        Result.success(SaveState(1, 1, "auto"))
    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> = Result.success(ByteArray(0))
}
