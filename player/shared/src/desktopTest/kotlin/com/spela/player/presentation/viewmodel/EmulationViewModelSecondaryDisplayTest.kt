package com.spela.player.presentation.viewmodel

import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.model.RACredentials
import com.spela.player.domain.model.RAStatus
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.secondarydisplay.FakePlatformSecondaryDisplay
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelSecondaryDisplayTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeSecondaryDisplay: FakePlatformSecondaryDisplay
    private lateinit var fakeLibretroController: StubLibretroController

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSecondaryDisplay = FakePlatformSecondaryDisplay()
        fakeLibretroController = StubLibretroController()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EmulationViewModel {
        val scope = CoroutineScope(testDispatcher)
        return EmulationViewModel(
            prepareGameUseCase = PrepareGameUseCase(
                downloadRepository = StubDownloadRepository(),
                coreRepository = StubCoreRepository(),
            ),
            saveGameStateUseCase = SaveGameStateUseCase(
                saveRepository = StubSaveRepository(),
            ),
            loadGameStateUseCase = LoadGameStateUseCase(
                saveRepository = StubSaveRepository(),
            ),
            getGameDetailUseCase = GetGameDetailUseCase(
                gameRepository = StubGameRepository(),
            ),
            preferencesRepository = StubPreferencesRepository(),
            achievementsRepository = StubAchievementsRepository(),
            achievementsController = StubAchievementsController(),
            libretroController = fakeLibretroController,
            secondaryDisplay = fakeSecondaryDisplay,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    // -- SecondaryDisplayAvailabilityChanged intent tests --

    @Test
    fun secondaryDisplayAvailableWhileRunningShowsDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        // Start a game so isRunning = true
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceUntilIdle()

        assertTrue(vm.state.value.isRunning)

        // Secondary display becomes available
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertTrue(vm.state.value.secondaryDisplayActive)
        assertTrue(fakeSecondaryDisplay.isShowing)
        assertEquals(1, fakeSecondaryDisplay.showCallCount)
    }

    @Test
    fun secondaryDisplayAvailableWhileNotRunningDoesNotShow() = runTest(testDispatcher) {
        val vm = createViewModel()

        // Not running, secondary display reports available
        assertFalse(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
        // Already inactive, so no dismiss call needed
        assertEquals(0, fakeSecondaryDisplay.showCallCount)
        assertEquals(0, fakeSecondaryDisplay.dismissCallCount)
    }

    @Test
    fun secondaryDisplayBecomesUnavailableDismissesDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        // Start game and activate secondary display
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceUntilIdle()
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertTrue(vm.state.value.secondaryDisplayActive)
        assertTrue(fakeSecondaryDisplay.isShowing)

        // Secondary display disconnected
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(false))

        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
    }

    @Test
    fun secondaryDisplayActiveDefaultsToFalse() {
        val vm = createViewModel()
        assertFalse(vm.state.value.secondaryDisplayActive)
    }

    @Test
    fun multipleAvailabilityChangesTrackCorrectly() = runTest(testDispatcher) {
        val vm = createViewModel()

        // Start game
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceUntilIdle()

        // Connect
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)
        assertEquals(1, fakeSecondaryDisplay.showCallCount)

        // Disconnect
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(false))
        assertFalse(vm.state.value.secondaryDisplayActive)

        // Reconnect
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)
        assertEquals(2, fakeSecondaryDisplay.showCallCount)
    }

    @Test
    fun stopGameDismissesSecondaryDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        // Start game, activate secondary display
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceUntilIdle()
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)

        // Stop game
        vm.onIntent(EmulationIntent.StopGame)
        advanceUntilIdle()

        // stopGame() should dismiss the secondary display and reset state
        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
    }

    @Test
    fun duplicateAvailableIntentDoesNotDoubleShow() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceUntilIdle()

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        // Dedup guard prevents double show — only one call goes through
        assertEquals(1, fakeSecondaryDisplay.showCallCount)
        assertTrue(vm.state.value.secondaryDisplayActive)
    }

    // -- Stub implementations for EmulationViewModel dependencies --

    private class StubDownloadRepository : DownloadRepository {
        override fun observeDownloads(): Flow<List<DownloadProgress>> = emptyFlow()
        override fun observeDownload(gameId: String): Flow<DownloadProgress> = emptyFlow()
        override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> =
            Result.success("/path/to/game.rom")
        override suspend fun cancelDownload(gameId: String) {}
        override suspend fun getLocalGamePath(gameId: String): String = "/path/to/game.rom"
        override suspend fun isGameCached(gameId: String): Boolean = true
        override suspend fun deleteLocalGame(gameId: String) {}
        override suspend fun getCacheSize(): Long = 0
        override suspend fun clearCache() {}
    }

    private class StubCoreRepository : CoreRepository {
        override suspend fun getAvailableCores(): Result<List<LibretroCore>> =
            Result.success(emptyList())
        override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> =
            Result.success(LibretroCore(id = 1, name = "nestopia", displayName = "Nestopia"))
        override suspend fun downloadCore(coreName: String, onProgress: (Float) -> Unit): Result<String> =
            Result.success("/path/to/core.so")
        override suspend fun getLocalCorePath(coreName: String): String = "/path/to/core.so"
        override suspend fun isCoreCached(coreName: String): Boolean = true
    }

    private class StubSaveRepository : SaveRepository {
        override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> =
            Result.success(emptyList())
        override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState> =
            Result.success(SaveState(id = 1, gameId = 1, name = name))
        override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> =
            Result.success(byteArrayOf())
        override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState> =
            Result.success(SaveState(id = 1, gameId = 1, name = "auto"))
        override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> =
            Result.success(byteArrayOf())
    }

    private class StubGameRepository : GameRepository {
        override suspend fun getConsoles(): Result<List<com.spela.player.domain.model.Console>> =
            Result.success(emptyList())
        override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun getAllGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun searchGames(query: String): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
            Result.success(
                GameDetail(
                    game = Game(id = gameId, title = "Test Game", consoleId = "nes"),
                )
            )
        override suspend fun getRecentGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun getFavoriteGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun addFavorite(gameId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun removeFavorite(gameId: String): Result<Unit> =
            Result.success(Unit)
    }

    private class StubPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences(): Result<UserPreferences> =
            Result.success(UserPreferences())

        override suspend fun updatePreferences(
            showPerformanceOverlay: Boolean?,
            autoSaveEnabled: Boolean?,
            autoLoadSaveEnabled: Boolean?,
            selectedShader: String?,
            consoleShaders: Map<String, String>?,
        ): Result<UserPreferences> = Result.success(UserPreferences())

        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset> = emptyMap()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun resolveShader(consoleId: String): ShaderPreset = ShaderPreset.NONE
        override suspend fun pushDeviceShaderOverridesToServer() {}
    }

    private class StubAchievementsRepository : AchievementsRepository {
        override suspend fun getRAStatus(): Result<RAStatus> =
            Result.success(RAStatus())
        override suspend fun linkRA(username: String, password: String): Result<RAStatus> =
            Result.success(RAStatus())
        override suspend fun unlinkRA(): Result<Unit> = Result.success(Unit)
        override suspend fun getRAToken(): Result<RACredentials> =
            Result.failure(IllegalStateException("RA not linked"))
        override suspend fun updateRASettings(hardcoreEnabled: Boolean): Result<RAStatus> =
            Result.success(RAStatus())
    }

    private class StubAchievementsController : AchievementsController {
        override fun init() {}
        override fun deinit() {}
        override fun login(username: String, token: String) {}
        override fun loadGame(hash: String) {}
        override fun doFrame() {}
        override val isHardcore: Boolean = false
        override fun setHardcore(enabled: Boolean) {}
        override val events: Flow<AchievementEvent> = emptyFlow()
        override fun httpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray) {}
    }

    private class StubLibretroController : LibretroController {
        override fun loadCore(corePath: String) {}
        override fun loadGame(gamePath: String) {}
        override fun start() {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun supportsSaveStates(): Boolean = true
        override fun serialize(): ByteArray = byteArrayOf()
        override fun unserialize(data: ByteArray): Boolean = true
        override fun setFastForward(enabled: Boolean) {}
        override fun performanceStats(): Flow<Pair<Float, Float>> = emptyFlow()
    }
}
