package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
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
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.secondarydisplay.FakePlatformSecondaryDisplay
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    private lateinit var stubPresenceService: PresenceService
    private lateinit var vmScope: CoroutineScope

    private object StubMockEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSecondaryDisplay = FakePlatformSecondaryDisplay()
        fakeLibretroController = StubLibretroController()
        vmScope = CoroutineScope(testDispatcher + Job())
        stubPresenceService = PresenceService(SpelaApiClient(StubMockEngineFactory, TokenManager()), StubMockEngineFactory, testDispatchers, vmScope)
    }

    @AfterTest
    fun tearDown() {
        vmScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EmulationViewModel {
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
            presenceService = stubPresenceService,
            relayRepository = StubRelayRepository(),
            challengeRepository = StubChallengeRepository(),
            screenshotCapture = null,
            apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager()),
            engineFactory = StubMockEngineFactory,
            dispatchers = testDispatchers,
            scope = vmScope,
        )
    }

    // -- SecondaryDisplayAvailabilityChanged intent tests --

    @Test
    fun secondaryDisplayAvailableWhileRunningShowsDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertTrue(vm.state.value.secondaryDisplayActive)
        assertTrue(fakeSecondaryDisplay.isShowing)
        assertEquals(1, fakeSecondaryDisplay.showCallCount)
        vmScope.cancel()
    }

    @Test
    fun secondaryDisplayAvailableWhileNotRunningDoesNotShow() = runTest(testDispatcher) {
        val vm = createViewModel()

        assertFalse(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
        assertEquals(0, fakeSecondaryDisplay.showCallCount)
        assertEquals(0, fakeSecondaryDisplay.dismissCallCount)
        vmScope.cancel()
    }

    @Test
    fun secondaryDisplayBecomesUnavailableDismissesDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertTrue(vm.state.value.secondaryDisplayActive)
        assertTrue(fakeSecondaryDisplay.isShowing)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(false))

        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
        vmScope.cancel()
    }

    @Test
    fun secondaryDisplayActiveDefaultsToFalse() {
        val vm = createViewModel()
        assertFalse(vm.state.value.secondaryDisplayActive)
    }

    @Test
    fun multipleAvailabilityChangesTrackCorrectly() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)
        assertEquals(1, fakeSecondaryDisplay.showCallCount)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(false))
        assertFalse(vm.state.value.secondaryDisplayActive)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)
        assertEquals(2, fakeSecondaryDisplay.showCallCount)
        vmScope.cancel()
    }

    @Test
    fun stopGameDismissesSecondaryDisplay() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        assertTrue(vm.state.value.secondaryDisplayActive)

        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)

        assertFalse(vm.state.value.secondaryDisplayActive)
        assertFalse(fakeSecondaryDisplay.isShowing)
        vmScope.cancel()
    }

    @Test
    fun duplicateAvailableIntentDoesNotDoubleShow() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))
        vm.onIntent(EmulationIntent.SecondaryDisplayAvailabilityChanged(true))

        assertEquals(1, fakeSecondaryDisplay.showCallCount)
        assertTrue(vm.state.value.secondaryDisplayActive)
        vmScope.cancel()
    }

    // -- DS dual-screen detection tests --

    @Test
    fun ndsConsoleIdTriggersIsDualScreenConsole() = runTest(testDispatcher) {
        val vm = createViewModelWithConsoleId("nds")

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(192, vm.state.value.dualScreenSplitY)
        vmScope.cancel()
    }

    @Test
    fun nonDsConsoleIdDoesNotTriggerDualScreen() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertFalse(vm.state.value.isDualScreenConsole)
        assertEquals(0, vm.state.value.dualScreenSplitY)
        vmScope.cancel()
    }

    @Test
    fun ndsConsoleIdSetsCoreVariables() = runTest(testDispatcher) {
        val controller = StubLibretroControllerWithVariableTracking()
        val vm = createViewModelWithConsoleIdAndController("nds", controller)

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertEquals("vertical", controller.coreVariables["desmume_screens_layout"])
        assertEquals("0", controller.coreVariables["desmume_screens_gap"])
        vmScope.cancel()
    }

    private fun createViewModelWithConsoleId(consoleId: String): EmulationViewModel {
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
                gameRepository = StubGameRepositoryWithConsoleId(consoleId),
            ),
            preferencesRepository = StubPreferencesRepository(),
            achievementsRepository = StubAchievementsRepository(),
            achievementsController = StubAchievementsController(),
            libretroController = fakeLibretroController,
            secondaryDisplay = fakeSecondaryDisplay,
            presenceService = stubPresenceService,
            relayRepository = StubRelayRepository(),
            challengeRepository = StubChallengeRepository(),
            screenshotCapture = null,
            apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager()),
            engineFactory = StubMockEngineFactory,
            dispatchers = testDispatchers,
            scope = vmScope,
        )
    }

    private fun createViewModelWithConsoleIdAndController(consoleId: String, controller: LibretroController): EmulationViewModel {
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
                gameRepository = StubGameRepositoryWithConsoleId(consoleId),
            ),
            preferencesRepository = StubPreferencesRepository(),
            achievementsRepository = StubAchievementsRepository(),
            achievementsController = StubAchievementsController(),
            libretroController = controller,
            secondaryDisplay = fakeSecondaryDisplay,
            presenceService = stubPresenceService,
            relayRepository = StubRelayRepository(),
            challengeRepository = StubChallengeRepository(),
            screenshotCapture = null,
            apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager()),
            engineFactory = StubMockEngineFactory,
            dispatchers = testDispatchers,
            scope = vmScope,
        )
    }

    // -- Stub implementations for EmulationViewModel dependencies --

    private class StubLibretroControllerWithVariableTracking : LibretroController {
        val coreVariables = mutableMapOf<String, String>()
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
        override fun setCoreVariable(key: String, value: String) {
            coreVariables[key] = value
        }
    }

    private class StubGameRepositoryWithConsoleId(private val consoleId: String) : GameRepository {
        override suspend fun getConsoles(): Result<List<com.spela.player.domain.model.Console>> =
            Result.success(emptyList())
        override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun getAllGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun getGameDetail(gameId: String): Result<GameDetail> =
            Result.success(
                GameDetail(
                    game = Game(id = gameId, title = "Test DS Game", consoleId = this.consoleId),
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
        override suspend fun getPlayLaterGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun addToPlayLater(gameId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String): Result<Unit> =
            Result.success(Unit)
    }

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
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> =
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
        override suspend fun getPlayLaterGames(): Result<List<Game>> =
            Result.success(emptyList())
        override suspend fun addToPlayLater(gameId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String): Result<Unit> =
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
            selectedTheme: String?,
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

    private class StubRelayRepository : RelayRepository {
        override suspend fun getMyRelays(page: Int, pageSize: Int) = Result.success(emptyList<com.spela.player.domain.model.Relay>())
        override suspend fun getRelay(relayId: String) = Result.failure<com.spela.player.domain.model.RelayDetail>(Exception("stub"))
        override suspend fun getRelayInvitations() = Result.success(emptyList<com.spela.player.domain.model.RelayInvitation>())
        override suspend fun getPendingInvitationCount() = Result.success(0)
        override suspend fun createRelay(name: String, gameId: String, description: String) = Result.failure<com.spela.player.domain.model.RelayDetail>(Exception("stub"))
        override suspend fun deleteRelay(relayId: String) = Result.success(Unit)
        override suspend fun inviteUser(relayId: String, username: String) = Result.success(Unit)
        override suspend fun acceptInvitation(invitationId: String) = Result.success(Unit)
        override suspend fun rejectInvitation(invitationId: String) = Result.success(Unit)
        override suspend fun leaveRelay(relayId: String) = Result.success(Unit)
        override suspend fun removeMember(relayId: String, userId: String) = Result.success(Unit)
        override suspend fun getGameRelays(gameId: String) = Result.success(emptyList<com.spela.player.domain.model.Relay>())
        override suspend fun getRelaySaves(relayId: String) = Result.success(emptyList<com.spela.player.domain.model.RelaySave>())
        override suspend fun deleteRelaySave(relayId: String, saveId: Long) = Result.success(Unit)
        override suspend fun takeTurn(relayId: String) = Result.success("stub-token")
        override suspend fun releaseTurn(relayId: String) = Result.success(Unit)
        override suspend fun heartbeat(relayId: String) = Result.success(Unit)
        override suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray) =
            Result.success(com.spela.player.domain.model.RelaySave(id = 1, relayId = relayId, name = name))
        override suspend fun downloadRelaySave(relayId: String, saveId: Long) = Result.success(byteArrayOf())
        override suspend fun downloadRelayAutoSave(relayId: String) = Result.success(byteArrayOf())
        override suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray) =
            Result.success(com.spela.player.domain.model.RelaySave(id = 1, relayId = relayId, name = "Auto Save", isAuto = true))
    }

    private class StubChallengeRepository : ChallengeRepository {
        override suspend fun getChallenges(gameId: String?, consoleId: String?, difficulty: String?, sort: String?, page: Int) =
            Result.success(emptyList<com.spela.player.domain.model.Challenge>())
        override suspend fun getGameChallenges(gameId: String, page: Int) =
            Result.success(emptyList<com.spela.player.domain.model.Challenge>())
        override suspend fun getMyChallenges(page: Int) =
            Result.success(emptyList<com.spela.player.domain.model.Challenge>())
        override suspend fun getChallengeDetail(challengeId: String) =
            Result.failure<com.spela.player.domain.model.Challenge>(Exception("stub"))
        override suspend fun getLeaderboard(challengeId: String, page: Int) =
            Result.success(emptyList<com.spela.player.domain.model.ChallengeLeaderboardEntry>())
        override suspend fun createChallenge(gameId: String, name: String, description: String, type: String, difficulty: String, coreName: String, saveData: ByteArray, screenshotData: ByteArray?) =
            Result.failure<com.spela.player.domain.model.Challenge>(Exception("stub"))
        override suspend fun downloadChallengeSave(challengeId: String) = Result.success(byteArrayOf())
        override suspend fun startAttempt(challengeId: String) =
            Result.success(com.spela.player.domain.model.ChallengeAttempt(id = "1", challengeId = challengeId, userId = "1", username = "test", avatarUrl = null, status = "in_progress", startedAt = "", completedAt = null, durationMs = 0, isBest = false))
        override suspend fun completeAttempt(challengeId: String, attemptId: String) =
            Result.success(com.spela.player.domain.model.ChallengeAttempt(id = attemptId, challengeId = challengeId, userId = "1", username = "test", avatarUrl = null, status = "completed", startedAt = "", completedAt = "", durationMs = 1000, isBest = false))
        override suspend fun abandonAttempt(challengeId: String, attemptId: String) = Result.success(Unit)
        override suspend fun getMyAttempts(challengeId: String) =
            Result.success(emptyList<com.spela.player.domain.model.ChallengeAttempt>())
        override suspend fun deleteChallenge(challengeId: String) = Result.success(Unit)
    }
}
