package com.spela.player.presentation.viewmodel

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
            saveDataRepository = SharedSaveTestSaveDataRepository(),
            ratingRepository = TestRatingRepository(),
            sharedSaveRepository = fakeSharedSaveRepo,
            getMyCollectionsUseCase = GetMyCollectionsUseCase(TestCollectionRepository()),
            addGameToCollectionUseCase = AddGameToCollectionUseCase(TestCollectionRepository()),
            createCollectionUseCase = CreateCollectionUseCase(TestCollectionRepository()),
            getGameStatsUseCase = GetGameStatsUseCase(SharedSaveTestGameStatsRepository()),
            gameStatsRepository = SharedSaveTestGameStatsRepository(),
            challengeRepository = SharedSaveTestChallengeRepository(),
            relayRepository = SharedSaveTestRelayRepository(),
            gameRepository = testGameRepo,
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
        consoleId = "nes",
        consoleName = "NES",
        scrapeAttempts = 1,
    )

    override suspend fun getConsoles(): Result<List<Console>> = Result.success(emptyList())
    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getAllGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
        Result.success(GameDetail(game))
    override suspend fun getRecentGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun getFavoriteGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getPlayLaterGames(): Result<List<Game>> = Result.success(emptyList())
    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>> = Result.success(emptyList())
    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
    override suspend fun getSimilarGames(gameId: String): Result<List<SimilarGame>> = Result.success(emptyList())
    override suspend fun getDeveloperGames(gameId: String): Result<List<DeveloperGame>> = Result.success(emptyList())
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
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> =
        if (saveData != null) Result.success(saveData!!) else Result.success(ByteArray(0))
    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> = Result.success(Unit)
    override suspend fun uploadAutoSave(gameId: String, data: ByteArray, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, "auto"))
    override suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, "auto"))
    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> = Result.success(ByteArray(0))
    override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun loadLocalAutoSave(gameId: String): Result<ByteArray> = Result.failure(Exception("none"))
    override suspend fun getPendingSyncCount(): Int = 0
    override suspend fun renameSaveState(gameId: String, saveId: String, name: String) = Result.success(Unit)
    override suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String) = Result.success(Unit)
    override suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String?) = Result.success(SaveState(1, 1, "Slot $slot"))
    override suspend fun loadFromSlot(gameId: String, slot: Int) = Result.success(ByteArray(0))
    override suspend fun getSlots(gameId: String) = Result.success(emptyList<QuickSaveSlot>())
    override suspend fun getAutoSaveHistory(gameId: String) = Result.success(emptyList<SaveState>())
    override suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>) = Result.success(saveIds.size)
    override suspend fun getStorageUsage() = Result.success(StorageUsage(0L, emptyList()))
    override suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray, coreName: String?) = Result.success(SaveState(1, 1, name))
}

private class SharedSaveTestSaveDataRepository : SaveDataRepository {
    override suspend fun getSaveDataList(gameId: String) = Result.success(emptyList<SaveData>())
    override suspend fun uploadActiveSaveData(gameId: String, data: ByteArray) = Result.success(SaveData(0, 0, "Active"))
    override suspend fun downloadActiveSaveData(gameId: String) = Result.success(ByteArray(0))
    override suspend fun downloadSaveData(gameId: String, saveDataId: String) = Result.success(ByteArray(0))
    override suspend fun activateSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
    override suspend fun renameSaveData(gameId: String, saveDataId: String, name: String) = Result.success(Unit)
    override suspend fun deleteSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
    override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {}
    override suspend fun loadLocalSRAM(gameId: String): ByteArray? = null
    override suspend fun getPendingSyncCount(): Int = 0
    override suspend fun zipSaveDirectory(gameId: String): ByteArray? = null
    override suspend fun unzipToSaveDirectory(data: ByteArray) {}
}

private class SharedSaveTestGameStatsRepository : GameStatsRepository {
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

private class SharedSaveTestRelayRepository : RelayRepository {
    override suspend fun getMyRelays(page: Int, pageSize: Int) = Result.success(emptyList<Relay>())
    override suspend fun getRelay(relayId: String) = Result.failure<RelayDetail>(Exception("stub"))
    override suspend fun getRelayInvitations() = Result.success(emptyList<RelayInvitation>())
    override suspend fun getPendingInvitationCount() = Result.success(0)
    override suspend fun createRelay(name: String, gameId: String, description: String) = Result.failure<RelayDetail>(Exception("stub"))
    override suspend fun deleteRelay(relayId: String) = Result.success(Unit)
    override suspend fun inviteUser(relayId: String, username: String) = Result.success(Unit)
    override suspend fun acceptInvitation(invitationId: String) = Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String) = Result.success(Unit)
    override suspend fun leaveRelay(relayId: String) = Result.success(Unit)
    override suspend fun removeMember(relayId: String, userId: String) = Result.success(Unit)
    override suspend fun getGameRelays(gameId: String) = Result.success(emptyList<Relay>())
    override suspend fun getRelaySaves(relayId: String) = Result.success(emptyList<RelaySave>())
    override suspend fun deleteRelaySave(relayId: String, saveId: Long) = Result.success(Unit)
    override suspend fun takeTurn(relayId: String) = Result.success("stub-token")
    override suspend fun releaseTurn(relayId: String) = Result.success(Unit)
    override suspend fun heartbeat(relayId: String) = Result.success(Unit)
    override suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray) =
        Result.success(RelaySave(id = 1, relayId = relayId, name = name))
    override suspend fun downloadRelaySave(relayId: String, saveId: Long) = Result.success(byteArrayOf())
    override suspend fun downloadRelayAutoSave(relayId: String) = Result.success(byteArrayOf())
    override suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray) =
        Result.success(RelaySave(id = 1, relayId = relayId, name = "Auto Save", isAuto = true))
    override suspend fun copyRelaySaveToGame(relayId: String, saveId: Long) = Result.success(Unit)
}

private class SharedSaveTestChallengeRepository : ChallengeRepository {
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
