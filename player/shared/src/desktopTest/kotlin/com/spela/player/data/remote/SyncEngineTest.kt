package com.spela.player.data.remote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
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

    private lateinit var database: SpelaDatabase
    private lateinit var fileStorage: InMemoryFileStorage

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            SpelaDatabase.Schema.create(it)
        }
        database = SpelaDatabase(driver)
        fileStorage = InMemoryFileStorage()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSyncEngine(
        scope: CoroutineScope,
        saveRepository: SaveRepository = NoOpSaveRepository(),
        saveDataRepository: SaveDataRepository = NoOpSaveDataRepository(),
        preferencesRepository: PreferencesRepository = NoOpPreferencesRepository(),
        gameRepository: GameRepository = NoOpGameRepository(),
    ): SyncEngine {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        return SyncEngine(
            connectivityMonitor = connectivityMonitor,
            saveRepository = saveRepository,
            saveDataRepository = saveDataRepository,
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            apiClient = apiClient,
            database = database,
            fileStorage = fileStorage,
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
            saveRepository = NoOpSaveRepository(),
            saveDataRepository = NoOpSaveDataRepository(),
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            apiClient = apiClient,
            database = database,
            fileStorage = fileStorage,
            dispatchers = testDispatchers,
            scope = scope,
        )
        return syncEngine to connectivityMonitor
    }

    // Test 1: Initial state is not syncing with no lastSyncedAt
    @Test
    fun initialStateIsNotSyncingWithNoLastSyncedAt() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        assertFalse(syncEngine.syncState.value.isSyncing)
        assertNull(syncEngine.syncState.value.lastSyncedAt)
        assertEquals(0, syncEngine.syncState.value.pendingSaveStates)
        assertEquals(0, syncEngine.syncState.value.pendingSaveData)
    }

    // Test 2: syncAll sets isSyncing false after completion
    @Test
    fun syncAllSetsIsSyncingFalseAfterCompletion() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertFalse(syncEngine.syncState.value.isSyncing)
    }

    // Test 3: syncAll sets lastSyncedAt on success
    @Test
    fun syncAllSetsLastSyncedAtOnSuccess() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
    }

    // Test 4: syncAll with no pending items completes without error
    @Test
    fun syncAllWithNoPendingItemsCompletesWithoutError() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        syncEngine.syncAll()
        advanceUntilIdle()

        assertFalse(syncEngine.syncState.value.isSyncing)
        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
        assertEquals(0, syncEngine.syncState.value.pendingSaveStates)
        assertEquals(0, syncEngine.syncState.value.pendingSaveData)
    }

    // Test 5: Pending save states reflected in count
    // syncAll() internally calls updatePendingCounts() — no need for start()
    @Test
    fun pendingSaveStatesReflectedInCount() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "save-1",
            game_id = "game-1",
            name = "Quick Save",
            local_path = "/tmp/saves/save1.dat",
            file_size = 1024,
            is_auto = 0,
            created_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis(),
            server_id = null,
            sync_status = "pending",
            last_synced_at = null,
        )

        syncEngine.syncAll()
        advanceUntilIdle()

        assertEquals(1, syncEngine.syncState.value.pendingSaveStates)
    }

    // Test 6: Pending save data reflected in count
    @Test
    fun pendingSaveDataReflectedInCount() = runTest(testDispatcher) {
        val syncEngine = createSyncEngine(this)

        database.spelaDatabaseQueries.insertLocalSaveData(
            id = "sram-1",
            game_id = "game-1",
            name = "Active SRAM",
            local_path = "/tmp/saves/sram1.dat",
            file_size = 2048,
            is_active = 1,
            created_at = System.currentTimeMillis(),
            updated_at = System.currentTimeMillis(),
            server_id = null,
            sync_status = "pending",
        )

        syncEngine.syncAll()
        advanceUntilIdle()

        assertEquals(1, syncEngine.syncState.value.pendingSaveData)
    }

    // Test 7: reconnect event triggers syncAll
    @Test
    fun reconnectTriggersSyncAll() = runTest(testDispatcher) {
        val (syncEngine, connectivityMonitor) = createSyncEngineWithMonitor(this)

        // Simulate what start() does: collect onReconnect and call syncAll
        val collectJob = launch(testDispatcher) {
            connectivityMonitor.onReconnect.collect { syncEngine.syncAll() }
        }
        advanceUntilIdle()

        assertNull(syncEngine.syncState.value.lastSyncedAt)

        connectivityMonitor.reportOffline()
        connectivityMonitor.reportOnline()
        advanceUntilIdle()

        assertNotNull(syncEngine.syncState.value.lastSyncedAt)
        collectJob.cancel()
    }

    // Test 8: syncAll with failing repos still completes successfully
    // (refreshCaches wraps calls in runCatching, syncPendingSave* catch per-item)
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

    // Test 9: refreshCaches calls preferences and game repos
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

    private class InMemoryFileStorage : FileStorage {
        val files = mutableMapOf<String, ByteArray>()
        override fun getGamesDir() = "/tmp/games"
        override fun getCoresDir() = "/tmp/cores"
        override fun getSavesDir() = "/tmp/saves"
        override fun getBiosDir() = "/tmp/bios"
        override suspend fun createDirectory(path: String) {}
        override suspend fun writeFile(path: String, data: ByteArray) { files[path] = data }
        override suspend fun readFile(path: String) = files[path] ?: throw Exception("Not found: $path")
        override suspend fun fileExists(path: String) = files.containsKey(path)
        override suspend fun deleteFile(path: String) { files.remove(path) }
        override suspend fun deleteDirectory(path: String) { files.keys.removeAll { it.startsWith(path) } }
        override suspend fun getDirectorySize(path: String) = 0L
        override suspend fun writeFileStreaming(path: String, writer: suspend (suspend (ByteArray, Int, Int) -> Unit) -> Unit) {
            val chunks = mutableListOf<ByteArray>()
            writer { bytes, offset, length -> chunks.add(bytes.copyOfRange(offset, offset + length)) }
            files[path] = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        }
        override suspend fun getFileSize(path: String) = files[path]?.size?.toLong() ?: 0L
        override suspend fun listFiles(path: String) = emptyList<String>()
        override suspend fun isDirectory(path: String) = false
    }

    private class NoOpSaveRepository : SaveRepository {
        override suspend fun getSaveStates(gameId: String) = Result.success(emptyList<SaveState>())
        override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray) = Result.success(SaveState(1, 1, name))
        override suspend fun downloadSaveState(gameId: String, saveId: String) = Result.success(ByteArray(0))
        override suspend fun deleteSaveState(gameId: String, saveId: String) = Result.success(Unit)
        override suspend fun uploadAutoSave(gameId: String, data: ByteArray) = Result.success(SaveState(1, 1, "auto"))
        override suspend fun downloadAutoSave(gameId: String) = Result.success(ByteArray(0))
        override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean) = Result.success(SaveState(1, 1, name))
        override suspend fun loadLocalAutoSave(gameId: String) = Result.failure<ByteArray>(Exception("none"))
        override suspend fun getPendingSyncCount() = 0
    }

    private class NoOpSaveDataRepository : SaveDataRepository {
        override suspend fun getSaveDataList(gameId: String) = Result.success(emptyList<SaveData>())
        override suspend fun uploadActiveSaveData(gameId: String, data: ByteArray) = Result.success(SaveData(0, 0, "Active"))
        override suspend fun downloadActiveSaveData(gameId: String) = Result.success(ByteArray(0))
        override suspend fun downloadSaveData(gameId: String, saveDataId: String) = Result.success(ByteArray(0))
        override suspend fun activateSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
        override suspend fun renameSaveData(gameId: String, saveDataId: String, name: String) = Result.success(Unit)
        override suspend fun deleteSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
        override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {}
        override suspend fun loadLocalSRAM(gameId: String): ByteArray? = null
        override suspend fun getPendingSyncCount() = 0
    }

    private class NoOpPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences() = Result.success(UserPreferences())
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
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
    }

    private class FailingPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences(): Result<UserPreferences> = throw RuntimeException("Preferences fetch failed")
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
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
    }

    private class TrackingPreferencesRepository : PreferencesRepository {
        var getPreferencesCalled = false
        override suspend fun getPreferences(): Result<UserPreferences> {
            getPreferencesCalled = true
            return Result.success(UserPreferences())
        }
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
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
    }
}
