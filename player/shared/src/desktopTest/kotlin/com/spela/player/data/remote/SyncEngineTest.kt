package com.spela.player.data.remote

import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSyncEngine(
        scope: CoroutineScope,
        preferencesRepository: PreferencesRepository = NoOpPreferencesRepository(),
        gameRepository: GameRepository = NoOpGameRepository(),
    ): SyncEngine {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        return SyncEngine(
            connectivityMonitor = connectivityMonitor,
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    private fun createSyncEngineWithMonitor(
        scope: CoroutineScope,
        preferencesRepository: PreferencesRepository = NoOpPreferencesRepository(),
        gameRepository: GameRepository = NoOpGameRepository(),
    ): Pair<SyncEngine, ConnectivityMonitor> {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        val syncEngine = SyncEngine(
            connectivityMonitor = connectivityMonitor,
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            dispatchers = testDispatchers,
            scope = scope,
        )
        return syncEngine to connectivityMonitor
    }

    @Test
    fun initialStateIsNotSyncingWithNoLastSyncedAt() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        assertFalse(syncEngine.syncState.value.isSyncing)
        assertNull(syncEngine.syncState.value.lastSyncedAt)
    }

    @Test
    fun syncAllSetsIsSyncingFalseAfterCompletion() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertFalse(syncEngine.syncState.value.isSyncing)
    }

    @Test
    fun syncAllSetsLastSyncedAtOnSuccess() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
    }

    @Test
    fun syncAllCompletesWithoutError() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertFalse(syncEngine.syncState.value.isSyncing)
        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
    }

    @Test
    fun reconnectTriggersSyncAll() = runTest(testDispatcher) {
        val (syncEngine, connectivityMonitor) = createSyncEngineWithMonitor(this)

        val collectJob = launch(testDispatcher) {
            connectivityMonitor.onReconnect.collect { syncEngine.syncAll() }
        }
        advanceUntilIdle()

        assertNull(syncEngine.syncState.value.lastSyncedAt)

        connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)
        advanceUntilIdle()

        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
        collectJob.cancel()
    }

    @Test
    fun syncAllWithFailingReposStillCompletes() = runTest(testDispatcher) {
        val failingPrefs = FailingPreferencesRepository()
        val failingGames = FailingGameRepository()
        val syncEngine = createSyncEngine(
            scope = this,
            preferencesRepository = failingPrefs,
            gameRepository = failingGames,
        )

        syncEngine.syncAll()
        advanceUntilIdle()

        assertFalse(syncEngine.syncState.value.isSyncing)
        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
    }

    @Test
    fun refreshCachesCallsPreferencesAndGameRepos() = runTest(testDispatcher) {
        val trackingPrefs = TrackingPreferencesRepository()
        val trackingGames = TrackingGameRepository()
        val syncEngine = createSyncEngine(
            scope = this,
            preferencesRepository = trackingPrefs,
            gameRepository = trackingGames,
        )

        syncEngine.syncAll()
        advanceUntilIdle()

        assertTrue(trackingPrefs.getPreferencesCalled)
        assertTrue(trackingGames.getConsolesCalled)
    }

    // --- Stub implementations ---

    private class NoOpPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences() = Result.success(UserPreferences())
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, autoUpdateCoresEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?, consoleSaveStatePolicies: Map<String, String>?, gameSaveStatePolicies: Map<String, String>?, defaultSecondScreenPage: String?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
        override fun getOrientationLock(): String = "auto"
        override fun setOrientationLock(mode: String) {}
        override fun getControlTab(consoleId: String): String =
            if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
        override fun setControlTab(consoleId: String, tab: String) {}
    }

    private class NoOpGameRepository : GameRepository {
        override suspend fun getConsoles() = Result.success(emptyList<Console>())
        override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
        override suspend fun getAllGames() = Result.success(emptyList<Game>())
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
        override suspend fun getGameDetail(gameId: String) = Result.success(GameDetail(game = Game(id = gameId, title = "Test", consoleId = "nes")))
        override suspend fun getRecentGames() = Result.success(emptyList<Game>())
        override suspend fun getFavoriteGames() = Result.success(emptyList<Game>())
        override suspend fun addFavorite(gameId: String) = Result.success(Unit)
        override suspend fun removeFavorite(gameId: String) = Result.success(Unit)
        override suspend fun getPlayLaterGames() = Result.success(emptyList<Game>())
        override suspend fun addToPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
        override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
        override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> = Result.success(emptyList())
        override suspend fun getLongestGames(): Result<List<LongestGame>> = Result.success(emptyList())
        override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
        override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
        override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.success(emptyList())
        override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    }

    private class FailingPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences(): Result<UserPreferences> = throw RuntimeException("Preferences fetch failed")
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, autoUpdateCoresEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?, consoleSaveStatePolicies: Map<String, String>?, gameSaveStatePolicies: Map<String, String>?, defaultSecondScreenPage: String?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
        override fun getOrientationLock(): String = "auto"
        override fun setOrientationLock(mode: String) {}
        override fun getControlTab(consoleId: String): String =
            if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
        override fun setControlTab(consoleId: String, tab: String) {}
    }

    private class FailingGameRepository : GameRepository {
        override suspend fun getConsoles(): Result<List<Console>> = throw RuntimeException("Consoles fetch failed")
        override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
        override suspend fun getAllGames() = Result.success(emptyList<Game>())
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
        override suspend fun getGameDetail(gameId: String) = Result.success(GameDetail(game = Game(id = gameId, title = "Test", consoleId = "nes")))
        override suspend fun getRecentGames() = Result.success(emptyList<Game>())
        override suspend fun getFavoriteGames() = Result.success(emptyList<Game>())
        override suspend fun addFavorite(gameId: String) = Result.success(Unit)
        override suspend fun removeFavorite(gameId: String) = Result.success(Unit)
        override suspend fun getPlayLaterGames() = Result.success(emptyList<Game>())
        override suspend fun addToPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
        override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
        override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> = Result.success(emptyList())
        override suspend fun getLongestGames(): Result<List<LongestGame>> = Result.success(emptyList())
        override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
        override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
        override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.failure(Exception("fail"))
        override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    }

    private class TrackingPreferencesRepository : PreferencesRepository {
        var getPreferencesCalled = false
        override suspend fun getPreferences(): Result<UserPreferences> {
            getPreferencesCalled = true
            return Result.success(UserPreferences())
        }
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, autoUpdateCoresEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?, consoleSaveStatePolicies: Map<String, String>?, gameSaveStatePolicies: Map<String, String>?, defaultSecondScreenPage: String?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
        override fun getOrientationLock(): String = "auto"
        override fun setOrientationLock(mode: String) {}
        override fun getControlTab(consoleId: String): String =
            if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
        override fun setControlTab(consoleId: String, tab: String) {}
    }

    private class TrackingGameRepository : GameRepository {
        var getConsolesCalled = false
        override suspend fun getConsoles(): Result<List<Console>> {
            getConsolesCalled = true
            return Result.success(emptyList())
        }
        override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
        override suspend fun getAllGames() = Result.success(emptyList<Game>())
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
        override suspend fun getGameDetail(gameId: String) = Result.success(GameDetail(game = Game(id = gameId, title = "Test", consoleId = "nes")))
        override suspend fun getRecentGames() = Result.success(emptyList<Game>())
        override suspend fun getFavoriteGames() = Result.success(emptyList<Game>())
        override suspend fun addFavorite(gameId: String) = Result.success(Unit)
        override suspend fun removeFavorite(gameId: String) = Result.success(Unit)
        override suspend fun getPlayLaterGames() = Result.success(emptyList<Game>())
        override suspend fun addToPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
        override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
        override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> = Result.success(emptyList())
        override suspend fun getLongestGames(): Result<List<LongestGame>> = Result.success(emptyList())
        override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
        override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
        override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.success(emptyList())
        override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    }
}
