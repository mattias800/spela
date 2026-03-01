package com.spela.player.presentation.navigation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.RestoreSessionUseCase
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

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

    private fun createViewModel(): NavigationViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val restoreSessionUseCase = RestoreSessionUseCase(
            authRepository = NoSessionAuthRepository(),
            serverRepository = NoSessionServerRepository(),
            apiClient = apiClient,
        )
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        val testDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            SpelaDatabase.Schema.create(it)
        }
        val testDatabase = SpelaDatabase(testDriver)
        val syncEngine = SyncEngine(
            connectivityMonitor = connectivityMonitor,
            saveRepository = NoOpSaveRepository(),
            saveDataRepository = NoOpSaveDataRepository(),
            preferencesRepository = NoOpPreferencesRepository(),
            gameRepository = NoOpGameRepository(),
            apiClient = apiClient,
            database = testDatabase,
            fileStorage = NoOpFileStorage(),
            dispatchers = testDispatchers,
            scope = scope,
        )
        return NavigationViewModel(
            restoreSessionUseCase = restoreSessionUseCase,
            connectivityMonitor = connectivityMonitor,
            syncEngine = syncEngine,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun goBackPopsFromBackStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))

        assertEquals(SpScreen.Console("nes"), vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.GoBack)

        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun navigateToSetsIsGoingBackFalse() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        assertFalse(vm.state.value.isGoingBack)

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertFalse(vm.state.value.isGoingBack)
    }

    @Test
    fun goBackSetsIsGoingBackTrue() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))

        vm.onIntent(NavigationIntent.GoBack)
        assertTrue(vm.state.value.isGoingBack)
    }

    @Test
    fun showOverlayPreservesNavigationState() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        assertTrue(vm.state.value.showInGameOverlay)
        assertEquals(screenBefore, vm.state.value.screenBehindOverlay)
        assertEquals(backStackBefore, vm.state.value.backStackBehindOverlay)
    }

    @Test
    fun hideOverlayRestoresNavigationState() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))
        vm.onIntent(NavigationIntent.HideOverlay)

        assertFalse(vm.state.value.showInGameOverlay)
        assertNull(vm.state.value.overlayGameId)
        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(backStackBefore, vm.state.value.backStack)
        assertNull(vm.state.value.screenBehindOverlay)
        assertTrue(vm.state.value.backStackBehindOverlay.isEmpty())
    }

    @Test
    fun hideOverlayRestoresEvenIfBackStackModifiedDuringGameplay() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        // Simulate back stack corruption during gameplay
        vm.onIntent(NavigationIntent.GoBack)
        vm.onIntent(NavigationIntent.GoBack)

        // HideOverlay should restore the original state
        vm.onIntent(NavigationIntent.HideOverlay)

        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(backStackBefore, vm.state.value.backStack)
    }

    @Test
    fun nextSectionCyclesThroughAllSections() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Collections, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Activity, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Settings, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun previousSectionCyclesBackward() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Settings, vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.PreviousSection)
        assertEquals(SpScreen.Activity, vm.state.value.currentScreen)
    }

    @Test
    fun sectionCyclingClearsBackStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertTrue(vm.state.value.backStack.isNotEmpty())

        vm.onIntent(NavigationIntent.NextSection)
        assertTrue(vm.state.value.backStack.isEmpty())
    }

    @Test
    fun goBackFromConsolesReturnsToHome() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)
        // Manually clear backstack to simulate section cycling
        vm.onIntent(NavigationIntent.NextSection) // goes to Collections
        vm.onIntent(NavigationIntent.PreviousSection) // goes back to Consoles with empty backstack
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)
        assertTrue(vm.state.value.backStack.isEmpty())

        vm.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun goBackFromCollectionsReturnsToHome() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Navigate to Collections via section cycling (empty backstack)
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NextSection) // Consoles
        vm.onIntent(NavigationIntent.NextSection) // Collections
        assertEquals(SpScreen.Collections, vm.state.value.currentScreen)
        assertTrue(vm.state.value.backStack.isEmpty())

        vm.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun nextSectionFromSubScreenMapsToCorrectTab() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Navigate deep into a console screen (belongs to CONSOLES tab)
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))

        // NextSection from Console (CONSOLES tab) should go to Collections
        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(SpScreen.Collections, vm.state.value.currentScreen)
        assertTrue(vm.state.value.backStack.isEmpty())
    }

    // Minimal fakes that cause RestoreSessionUseCase to return NoSession

    private class NoSessionServerRepository : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> = flowOf(emptyList())
        override suspend fun getServers(): List<ServerConnection> = emptyList()
        override suspend fun getActiveServer(): ServerConnection? = null
        override suspend fun addServer(name: String, url: String): ServerConnection =
            throw UnsupportedOperationException()
        override suspend fun removeServer(id: String) = throw UnsupportedOperationException()
        override suspend fun setActiveServer(id: String) = throw UnsupportedOperationException()
    }

    private class NoSessionAuthRepository : AuthRepository {
        override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun getCurrentUser(): Result<User> = throw UnsupportedOperationException()
        override suspend fun getStoredTokens(): AuthTokens? = null
        override suspend fun storeTokens(tokens: AuthTokens) = throw UnsupportedOperationException()
        override suspend fun clearTokens() = throw UnsupportedOperationException()
        override fun isLoggedIn(): Boolean = false
    }

    // Minimal stubs for SyncEngine dependencies (never actually called in these tests)

    private class NoOpSaveRepository : SaveRepository {
        override suspend fun getSaveStates(gameId: String) = Result.success(emptyList<SaveState>())
        override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray) = Result.success(SaveState(1, 1, name))
        override suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?) = Result.success(SaveState(1, 1, name))
        override suspend fun downloadSaveState(gameId: String, saveId: String) = Result.success(ByteArray(0))
        override suspend fun deleteSaveState(gameId: String, saveId: String) = Result.success(Unit)
        override suspend fun uploadAutoSave(gameId: String, data: ByteArray) = Result.success(SaveState(1, 1, "auto"))
        override suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?) = Result.success(SaveState(1, 1, "auto"))
        override suspend fun downloadAutoSave(gameId: String) = Result.success(ByteArray(0))
        override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean) = Result.success(SaveState(1, 1, name))
        override suspend fun loadLocalAutoSave(gameId: String) = Result.failure<ByteArray>(Exception("none"))
        override suspend fun getPendingSyncCount() = 0
        override suspend fun renameSaveState(gameId: String, saveId: String, name: String) = Result.success(Unit)
        override suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String) = Result.success(Unit)
        override suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray?) = Result.success(SaveState(1, 1, "Slot $slot"))
        override suspend fun loadFromSlot(gameId: String, slot: Int) = Result.success(ByteArray(0))
        override suspend fun getSlots(gameId: String) = Result.success(emptyList<QuickSaveSlot>())
        override suspend fun getAutoSaveHistory(gameId: String) = Result.success(emptyList<SaveState>())
        override suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>) = Result.success(saveIds.size)
        override suspend fun getStorageUsage() = Result.success(StorageUsage(0L, emptyList()))
        override suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray) = Result.success(SaveState(1, 1, name))
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
        override suspend fun zipSaveDirectory(gameId: String): ByteArray? = null
        override suspend fun unzipToSaveDirectory(data: ByteArray) {}
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
        override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
        override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
        override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
    }

    private class NoOpFileStorage : FileStorage {
        override fun getGamesDir() = "/tmp/games"
        override fun getCoresDir() = "/tmp/cores"
        override fun getSavesDir() = "/tmp/saves"
        override fun getBiosDir() = "/tmp/bios"
        override suspend fun createDirectory(path: String) {}
        override suspend fun writeFile(path: String, data: ByteArray) {}
        override suspend fun readFile(path: String) = ByteArray(0)
        override suspend fun fileExists(path: String) = false
        override suspend fun deleteFile(path: String) {}
        override suspend fun deleteDirectory(path: String) {}
        override suspend fun getDirectorySize(path: String) = 0L
        override suspend fun writeFileStreaming(path: String, writer: suspend (suspend (ByteArray, Int, Int) -> Unit) -> Unit) {
            writer { _, _, _ -> }
        }
        override suspend fun getFileSize(path: String) = 0L
        override suspend fun listFiles(path: String) = emptyList<String>()
        override suspend fun isDirectory(path: String) = false
        override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
        override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
    }
}
