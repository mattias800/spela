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
class GameDetailSaveDeleteTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeSaveRepo: SaveDeleteTestSaveRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSaveRepo = SaveDeleteTestSaveRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GameDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val testGameRepo = SaveDeleteTestGameRepository()
        return GameDetailViewModel(
            getGameDetailUseCase = GetGameDetailUseCase(testGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(testGameRepo),
            togglePlayLaterUseCase = TogglePlayLaterUseCase(testGameRepo),
            downloadRepository = SaveDeleteTestDownloadRepository(),
            saveRepository = fakeSaveRepo,
            saveDataRepository = SaveDeleteTestSaveDataRepository(),
            ratingRepository = SaveDeleteTestRatingRepository(),
            sharedSaveRepository = SaveDeleteTestSharedSaveRepository(),
            getMyCollectionsUseCase = GetMyCollectionsUseCase(SaveDeleteTestCollectionRepository()),
            addGameToCollectionUseCase = AddGameToCollectionUseCase(SaveDeleteTestCollectionRepository()),
            createCollectionUseCase = CreateCollectionUseCase(SaveDeleteTestCollectionRepository()),
            getGameStatsUseCase = GetGameStatsUseCase(SaveDeleteTestGameStatsRepository()),
            gameStatsRepository = SaveDeleteTestGameStatsRepository(),
            challengeRepository = SaveDeleteTestChallengeRepository(),
            relayRepository = SaveDeleteTestRelayRepository(),
            gameRepository = testGameRepo,
            apiClient = apiClient,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun deleteSaveUpdatesUnsyncedSaveCount() = runTest(testDispatcher) {
        fakeSaveRepo.saves = listOf(
            SaveState(1, 1, "Save A", isSynced = false),
            SaveState(2, 1, "Save B", isSynced = false),
            SaveState(3, 1, "Save C", isSynced = true),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        assertEquals(3, vm.state.value.saveStates.size)
        assertEquals(2, vm.state.value.unsyncedSaveCount)

        vm.onIntent(GameDetailIntent.DeleteSave(1))
        advanceUntilIdle()

        assertEquals(2, vm.state.value.saveStates.size)
        assertEquals(1, vm.state.value.unsyncedSaveCount)
    }

    @Test
    fun deleteSyncedSaveDoesNotChangeUnsyncedCount() = runTest(testDispatcher) {
        fakeSaveRepo.saves = listOf(
            SaveState(1, 1, "Save A", isSynced = false),
            SaveState(2, 1, "Save B", isSynced = true),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.unsyncedSaveCount)

        vm.onIntent(GameDetailIntent.DeleteSave(2))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.saveStates.size)
        assertEquals(1, vm.state.value.unsyncedSaveCount)
    }

    @Test
    fun deleteSelectedSavesUpdatesUnsyncedSaveCount() = runTest(testDispatcher) {
        fakeSaveRepo.saves = listOf(
            SaveState(1, 1, "Save A", isSynced = false),
            SaveState(2, 1, "Save B", isSynced = false),
            SaveState(3, 1, "Save C", isSynced = true),
            SaveState(4, 1, "Save D", isSynced = false),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        assertEquals(3, vm.state.value.unsyncedSaveCount)

        // Enter selection mode and select two unsynced saves
        vm.onIntent(GameDetailIntent.ToggleSelectionMode)
        vm.onIntent(GameDetailIntent.ToggleSaveSelection(1))
        vm.onIntent(GameDetailIntent.ToggleSaveSelection(2))
        vm.onIntent(GameDetailIntent.DeleteSelectedSaves)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.saveStates.size)
        assertEquals(1, vm.state.value.unsyncedSaveCount)
        assertFalse(vm.state.value.isSelectionMode)
    }

    @Test
    fun deleteAllUnsyncedSavesSetsCountToZero() = runTest(testDispatcher) {
        fakeSaveRepo.saves = listOf(
            SaveState(1, 1, "Save A", isSynced = false),
            SaveState(2, 1, "Save B", isSynced = true),
        )
        val vm = createViewModel()
        vm.onIntent(GameDetailIntent.LoadGame("1"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.unsyncedSaveCount)

        vm.onIntent(GameDetailIntent.DeleteSave(1))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.saveStates.size)
        assertEquals(0, vm.state.value.unsyncedSaveCount)
    }
}

// Test fakes

private class SaveDeleteTestSaveRepository : SaveRepository {
    var saves = emptyList<SaveState>()

    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> = Result.success(saves)
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> =
        Result.success(SaveState(1, 1, name))
    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(0))
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
    override suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray) = Result.success(SaveState(1, 1, name))
}

private class SaveDeleteTestGameRepository : GameRepository {
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

private class SaveDeleteTestDownloadRepository : DownloadRepository {
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

private class SaveDeleteTestRatingRepository : RatingRepository {
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

private class SaveDeleteTestSharedSaveRepository : SharedSaveRepository {
    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> =
        Result.success(emptyList())
    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> =
        Result.failure(Exception("stub"))
    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(0))
    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> =
        Result.success(Unit)
}

private class SaveDeleteTestCollectionRepository : CollectionRepository {
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

private class SaveDeleteTestGameStatsRepository : GameStatsRepository {
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

private class SaveDeleteTestRelayRepository : RelayRepository {
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

private class SaveDeleteTestChallengeRepository : ChallengeRepository {
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

private class SaveDeleteTestSaveDataRepository : SaveDataRepository {
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
