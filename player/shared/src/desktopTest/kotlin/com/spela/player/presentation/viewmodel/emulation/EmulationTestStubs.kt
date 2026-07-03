package com.spela.player.presentation.viewmodel.emulation

import com.spela.player.presentation.viewmodel.ChallengeManager
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.NetplayManager
import com.spela.player.presentation.viewmodel.SaveManager
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.domain.model.DisplayAspectChoice
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.model.RACredentials
import com.spela.player.domain.model.RAStatus
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.LongestGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.PaginatedResult
import com.spela.player.domain.model.RenderScale
import com.spela.player.domain.model.RenderScaleChoice
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.model.WidescreenMode
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SessionCheatConfig
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.domain.usecase.GetConsolesUseCase
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.secondarydisplay.FakePlatformSecondaryDisplay
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher

// ── Mock Engine Factory ─────────────────────────────────────────────────────

object StubMockEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return MockEngine {
            respond(
                "{}",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }
}

// ── LibretroController stubs ────────────────────────────────────────────────

open class StubLibretroController : LibretroController {
    var loadCoreCallCount = 0; private set
    var loadGameCallCount = 0; private set
    var startCallCount = 0; private set
    var pauseCallCount = 0; private set
    var resumeCallCount = 0; private set
    var stopCallCount = 0; private set
    var serializeCallCount = 0; private set
    var unserializeCallCount = 0; private set
    var setFastForwardCallCount = 0; private set
    var refreshPausedVideoCallCount = 0; private set
    var getSRAMCallCount = 0; private set
    var setSRAMCallCount = 0; private set
    var clearNetplayModeCallCount = 0; private set
    val presentationEvents = mutableListOf<String>()
    val calls = mutableListOf<String>()
    val coreVariables = mutableMapOf<String, String>()
    val controllerPortDevices = mutableMapOf<Int, Int>()

    var lastLoadCorePath: String? = null; private set
    var lastLoadGamePath: String? = null; private set
    var lastFastForwardEnabled: Boolean? = null; private set
    var lastWidescreenMode: WidescreenMode? = null; private set
    var lastUnserializeData: ByteArray? = null; private set
    var lastSetSRAMData: ByteArray? = null; private set

    var supportsSaveStatesResult = true
    var serializeResult: ByteArray? = byteArrayOf()
    /** Drives [serialize] to throw, simulating a native serialize-to-file
     *  exception. Used by tests that verify SaveManager clears its
     *  in-progress flag when staging blows up (#803). */
    var serializeThrows: Boolean = false
    var unserializeResult = true
    var isHwRenderEnabledResult = false
    var getSRAMResult: ByteArray? = byteArrayOf(1, 2, 3)
    var setSRAMResult = true
    var loadCoreShouldThrow: Exception? = null

    override fun loadCore(corePath: String, saveDir: String?) {
        loadCoreShouldThrow?.let { throw it }
        loadCoreCallCount++
        lastLoadCorePath = corePath
        calls += "loadCore"
    }

    override fun loadGame(gamePath: String) {
        loadGameCallCount++
        lastLoadGamePath = gamePath
        calls += "loadGame"
    }

    override fun start() { startCallCount++ }
    override fun pause() { pauseCallCount++ }
    override fun resume() { resumeCallCount++ }
    override fun stop() { stopCallCount++ }
    override fun supportsSaveStates(): Boolean = supportsSaveStatesResult
    override fun serialize(): ByteArray? {
        serializeCallCount++
        if (serializeThrows) throw RuntimeException("test: native serialize threw")
        return serializeResult
    }
    override fun unserialize(data: ByteArray): Boolean { unserializeCallCount++; lastUnserializeData = data; return unserializeResult }
    override fun setFastForward(enabled: Boolean) { setFastForwardCallCount++; lastFastForwardEnabled = enabled }
    override fun setWidescreenMode(mode: WidescreenMode) {
        lastWidescreenMode = mode
        presentationEvents += "setWidescreenMode:${mode.storageId}"
    }
    override fun setCoreVariable(key: String, value: String) {
        coreVariables[key] = value
        calls += "setCoreVariable:$key=$value"
    }
    override fun setControllerPortDevice(port: Int, device: Int) {
        controllerPortDevices[port] = device
        calls += "setControllerPortDevice:$port=$device"
    }
    override fun clearCoreVariables() {
        coreVariables.clear()
        calls += "clearCoreVariables"
    }
    override fun refreshPausedVideo() {
        refreshPausedVideoCallCount++
        presentationEvents += "refreshPausedVideo"
    }
    override fun performanceStats(): Flow<Pair<Float, Float>> = emptyFlow()
    override fun isHwRenderEnabled(): Boolean = isHwRenderEnabledResult
    override fun getSRAM(): ByteArray? { getSRAMCallCount++; return getSRAMResult }
    override fun setSRAM(data: ByteArray): Boolean { setSRAMCallCount++; lastSetSRAMData = data; return setSRAMResult }
    override fun clearNetplayMode() { clearNetplayModeCallCount++ }

    /** Drives [consumeActivePlayMillis] — set the active-play millis the
     *  next drain should return. Reset to 0 on each drain, mirroring the
     *  real controller's getAndSet semantics (#1282). */
    var activePlayMillisToReturn = 0L
    var consumeActivePlayMillisCallCount = 0; private set
    override fun consumeActivePlayMillis(): Long {
        consumeActivePlayMillisCallCount++
        val v = activePlayMillisToReturn
        activePlayMillisToReturn = 0L
        return v
    }
}

class StubLibretroControllerWithVariableTracking : LibretroController {
    val coreVariables = mutableMapOf<String, String>()
    override fun loadCore(corePath: String, saveDir: String?) {}
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
    override fun setCoreVariable(key: String, value: String) { coreVariables[key] = value }
    override fun clearCoreVariables() { coreVariables.clear() }
}

// ── Repository stubs ────────────────────────────────────────────────────────

class StubGameRepository(
    private val consoleId: String = "nes",
    private val consoleName: String = "",
    private val consoleSaveStatePolicy: com.spela.player.domain.model.SaveStatePolicyTier =
        com.spela.player.domain.model.SaveStatePolicyTier.Small,
) : GameRepository {
    override suspend fun getConsoles() = Result.success(emptyList<com.spela.player.domain.model.Console>())
    override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
    override suspend fun getAllGames() = Result.success(emptyList<Game>())
    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
    override suspend fun getGameDetail(gameId: String) = Result.success(
        GameDetail(game = Game(
            id = gameId,
            title = "Test Game",
            consoleId = this.consoleId,
            consoleName = this.consoleName,
            consoleSaveStatePolicy = this.consoleSaveStatePolicy,
        ))
    )
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

class StubDownloadRepository : DownloadRepository {
    private val downloadsFlow = MutableStateFlow<List<DownloadProgress>>(emptyList())
    private val downloadedGamesFlow = MutableStateFlow<List<DownloadedGame>>(emptyList())
    private val perGameFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()

    override fun observeDownloads(): Flow<List<DownloadProgress>> = downloadsFlow
    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        perGameFlows.getOrPut(gameId) {
            MutableStateFlow(DownloadProgress(gameId = gameId, gameTitle = "", state = DownloadState.IDLE))
        }
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = downloadedGamesFlow
    override suspend fun downloadGame(gameId: String, gameTitle: String) = Result.success("/path/to/game.rom")
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String = "/path/to/game.rom"
    override suspend fun isGameCached(gameId: String) = true
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize() = 0L
    override suspend fun clearCache() {}
    override suspend fun scanForOrphanedDownloads() {}
}

class StubCoreRepository : CoreRepository {
    var recommendedCore: LibretroCore = LibretroCore(id = 1, name = "nestopia", displayName = "Nestopia")
    var localCorePath: String = "/path/to/core.so"
    override suspend fun getAvailableCores() = Result.success(emptyList<LibretroCore>())
    override suspend fun getRecommendedCore(gameId: String) = Result.success(recommendedCore)
    override suspend fun downloadCore(coreName: String, customDownloadUrl: String?, onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit) = Result.success(localCorePath)
    override suspend fun downloadCoreByHash(coreName: String, sha256: String, onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit) = Result.success(localCorePath)
    override suspend fun getLocalCorePath(coreName: String): String = localCorePath
    override suspend fun isCoreCached(coreName: String) = true
    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? = null
    override suspend fun getServerCoreSha(coreName: String): String? = null
}

class StubSaveDataRepository : SaveDataRepository {
    var saveLocalSRAMCallCount = 0; private set
    var loadLocalSRAMCallCount = 0; private set
    var lastSavedSRAMData: ByteArray? = null; private set

    var zipSaveDirectoryCallCount = 0; private set
    var unzipToSaveDirectoryCallCount = 0; private set
    var lastUnzippedData: ByteArray? = null; private set

    var loadLocalSRAMResult: ByteArray? = null
    var zipSaveDirectoryResult: ByteArray? = null

    override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {
        saveLocalSRAMCallCount++
        lastSavedSRAMData = data
    }
    override suspend fun loadLocalSRAM(gameId: String): ByteArray? {
        loadLocalSRAMCallCount++
        return loadLocalSRAMResult
    }
    override suspend fun getPendingSyncCount() = 0
    override suspend fun zipSaveDirectory(gameId: String): ByteArray? {
        zipSaveDirectoryCallCount++
        return zipSaveDirectoryResult
    }
    override suspend fun unzipToSaveDirectory(data: ByteArray) {
        unzipToSaveDirectoryCallCount++
        lastUnzippedData = data
    }
}

class StubPreferencesRepository : PreferencesRepository {
    var preferencesResult: Result<UserPreferences> = Result.success(UserPreferences())
    var resolveShaderResult: ShaderPreset = ShaderPreset.NONE
    var resolveDisplayAspectChoiceResult: DisplayAspectChoice = DisplayAspectChoice.AUTO
    var resolveRenderScaleChoiceResult: RenderScaleChoice = RenderScaleChoice.AUTO
    var lastResolvedDisplayAspectChoiceGameId: String? = null
        private set
    var lastResolvedDisplayAspectChoiceConsoleId: String? = null
        private set
    var lastSetDisplayAspectChoiceGameId: String? = null
        private set
    var lastSetDisplayAspectChoiceConsoleId: String? = null
        private set
    var lastSetDisplayAspectChoice: DisplayAspectChoice? = null
        private set

    override suspend fun getPreferences() = preferencesResult
    /** Records the consoleSaveStatePolicies map passed on the most
     *  recent updatePreferences call so tests can assert the
     *  EmulationViewModel writes the right console-level choice when
     *  the user picks a button on the first-launch prompt (#804). */
    var lastConsoleSaveStatePoliciesUpdate: Map<String, String>? = null
        private set

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        autoUpdateCoresEnabled: Boolean?,
        selectedShader: String?,
        selectedTheme: String?,
        consoleShaders: Map<String, String>?,
        consoleRenderScales: Map<String, String>?,
        consoleSaveStatePolicies: Map<String, String>?,
        gameSaveStatePolicies: Map<String, String>?,
        defaultSecondScreenPage: String?,
    ): Result<UserPreferences> {
        if (consoleSaveStatePolicies != null) {
            lastConsoleSaveStatePoliciesUpdate = consoleSaveStatePolicies
        }
        if (gameSaveStatePolicies != null) {
            lastGameSaveStatePoliciesUpdate = gameSaveStatePolicies
        }
        return Result.success(UserPreferences())
    }

    /** Records the gameSaveStatePolicies map passed on the most
     *  recent updatePreferences call so tests can assert the per-
     *  game toggle wiring (#804 phase 4b spec point c). */
    var lastGameSaveStatePoliciesUpdate: Map<String, String>? = null
        private set

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
    override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
    override suspend fun syncDeviceShaderOverrides() {}
    override suspend fun resolveShader(consoleId: String) = resolveShaderResult
    override fun resolveDisplayAspectChoice(gameId: String, consoleId: String): DisplayAspectChoice {
        lastResolvedDisplayAspectChoiceGameId = gameId
        lastResolvedDisplayAspectChoiceConsoleId = consoleId
        return resolveDisplayAspectChoiceResult
    }
    override fun setDisplayAspectChoice(gameId: String, consoleId: String, choice: DisplayAspectChoice) {
        lastSetDisplayAspectChoiceGameId = gameId
        lastSetDisplayAspectChoiceConsoleId = consoleId
        lastSetDisplayAspectChoice = choice
    }
    override fun resolveRenderScaleChoice(consoleId: String, preferences: UserPreferences?): RenderScaleChoice =
        resolveRenderScaleChoiceResult
    override fun resolveRenderScale(consoleId: String, preferences: UserPreferences?): RenderScale =
        resolveRenderScaleChoiceResult.scale ?: RenderScale.NATIVE
    override fun setRenderScale(consoleId: String, scale: RenderScale) {}
    override suspend fun pushDeviceShaderOverridesToServer() {}
    override suspend fun syncKeyMappingsFromServer() {}
    override suspend fun pushKeyMappingsToServer() {}
    override suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>) {}
    override suspend fun deleteGameKeyMappingOnServer(gameId: String) {}
    override suspend fun syncGameKeyMappingFromServer(gameId: String) {}
    override fun getOrientationLock(): String = "auto"
    override fun setOrientationLock(mode: String) {}
    override fun getControlTab(consoleId: String): String =
        if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
    override fun setControlTab(consoleId: String, tab: String) {}
    override fun getConsoleListGrouping(): String = "generation"
    override fun setConsoleListGrouping(grouping: String) {}
    override fun getConfirmButtonConvention(): String = "xbox"
    override fun setConfirmButtonConvention(convention: String) {}
}

class StubAchievementsRepository : AchievementsRepository {
    var getRATokenResult: Result<RACredentials> = Result.failure(IllegalStateException("RA not linked"))
    var getRAStatusResult: Result<RAStatus> = Result.success(RAStatus())

    override suspend fun getRAStatus() = getRAStatusResult
    override suspend fun linkRA(username: String, password: String) = Result.success(RAStatus())
    override suspend fun unlinkRA() = Result.success(Unit)
    override suspend fun getRAToken() = getRATokenResult
    override suspend fun updateRASettings(hardcoreEnabled: Boolean) = Result.success(RAStatus())
}

class StubAchievementsController : AchievementsController {
    var initCallCount = 0; private set
    var deinitCallCount = 0; private set
    var setHardcoreCallCount = 0; private set
    var lastHardcoreEnabled: Boolean? = null; private set

    override fun init() { initCallCount++ }
    override fun deinit() { deinitCallCount++ }
    override fun login(username: String, token: String) {}
    override fun loadGame(hash: String) {}
    override fun doFrame() {}
    override val isHardcore: Boolean = false
    override fun setHardcore(enabled: Boolean) { setHardcoreCallCount++; lastHardcoreEnabled = enabled }
    override val events: Flow<AchievementEvent> = emptyFlow()
    override fun httpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray) {}
}

class StubSharedSessionRepository : SharedSessionRepository {
    var uploadSharedSessionAutoSaveCallCount = 0; private set
    var releaseTurnCallCount = 0; private set
    var heartbeatCallCount = 0; private set
    var downloadSharedSessionAutoSaveCallCount = 0; private set

    var downloadSharedSessionAutoSaveResult: Result<ByteArray> = Result.success(byteArrayOf())
    var heartbeatResult: Result<Unit> = Result.success(Unit)

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
    override suspend fun releaseTurn(sharedSessionId: String): Result<Unit> { releaseTurnCallCount++; return Result.success(Unit) }
    override suspend fun heartbeat(sharedSessionId: String): Result<Unit> { heartbeatCallCount++; return heartbeatResult }
    override suspend fun uploadSharedSessionSave(sharedSessionId: String, name: String, turnToken: String, data: ByteArray) =
        Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = name))
    override suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long) = Result.success(byteArrayOf())
    override suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): Result<ByteArray> {
        downloadSharedSessionAutoSaveCallCount++
        return downloadSharedSessionAutoSaveResult
    }
    override suspend fun uploadSharedSessionAutoSave(sharedSessionId: String, turnToken: String, data: ByteArray): Result<SharedSessionSave> {
        uploadSharedSessionAutoSaveCallCount++
        return Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = "Auto Save", isAuto = true))
    }
}

class StubSessionRepository : SessionRepository {
    var uploadSessionAutoSaveCallCount = 0; private set
    var downloadSessionAutoSaveCallCount = 0; private set
    var uploadSessionSramCallCount = 0; private set
    var downloadSessionSramCallCount = 0; private set
    var uploadSessionSaveCallCount = 0; private set
    var createSessionCallCount = 0; private set
    var lastCreatedSessionName: String? = null; private set

    var downloadSessionAutoSaveResult: Result<ByteArray> = Result.failure(Exception("no auto-save"))
    var downloadSessionSramResult: Result<ByteArray> = Result.failure(Exception("no sram"))
    /** Drives the manual-save-upload outcome. Default = success so existing
     *  tests don't need to set it. Tests covering #803's failure feedback
     *  set this to [Result.failure] before calling [SaveManager.saveState]. */
    var uploadSessionSaveResult: Result<SaveState> = Result.success(SaveState(id = "1", name = "Manual Save"))
    var existingSessions: List<GameSession> = emptyList()

    override suspend fun getSessionsForGame(gameId: String) = Result.success(existingSessions)

    /**
     * Test hook for [getSession] — set to a function that returns the
     * desired session for a given id. Defaults to looking the id up in
     * [existingSessions] so seed-and-go tests work without explicit
     * wiring. VM tests that need a specific failure path can override.
     */
    var lookupSession: (String) -> Result<GameSession> = { id ->
        existingSessions.firstOrNull { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(Exception("stub: session $id not found"))
    }
    override suspend fun getSession(sessionId: String): Result<GameSession> = lookupSession(sessionId)
    override suspend fun createSession(gameId: String, name: String): Result<GameSession> {
        createSessionCallCount++
        lastCreatedSessionName = name
        return Result.success(GameSession(id = "auto-${createSessionCallCount}", gameId = gameId, name = name))
    }
    override suspend fun updateSession(sessionId: String, name: String?, coreName: String?) = Result.failure<GameSession>(Exception("stub"))

    /** Test hook — record every flag update so VM tests can assert what reached the server. */
    data class CoreFlagsInvocation(
        val sessionId: String,
        val userLockedCoreVersion: Boolean?,
        val autoLoadSuppressed: Boolean?,
        val rehearsalCrashPending: Boolean?,
    )
    val updateSessionCoreFlagsCalls: MutableList<CoreFlagsInvocation> = mutableListOf()
    /** Test hook — when set, [updateSessionCoreFlags] succeeds with this row. */
    var updateSessionCoreFlagsResult: Result<GameSession>? = null

    override suspend fun updateSessionCoreFlags(sessionId: String, userLockedCoreVersion: Boolean?, autoLoadSuppressed: Boolean?, rehearsalCrashPending: Boolean?): Result<GameSession> {
        updateSessionCoreFlagsCalls += CoreFlagsInvocation(
            sessionId = sessionId,
            userLockedCoreVersion = userLockedCoreVersion,
            autoLoadSuppressed = autoLoadSuppressed,
            rehearsalCrashPending = rehearsalCrashPending,
        )
        return updateSessionCoreFlagsResult ?: Result.success(
            GameSession(
                id = sessionId,
                gameId = "",
                name = "",
                userLockedCoreVersion = userLockedCoreVersion ?: false,
                autoLoadSuppressed = autoLoadSuppressed ?: false,
                rehearsalCrashPending = rehearsalCrashPending ?: false,
            ),
        )
    }
    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
    override suspend fun getSessionSaves(sessionId: String) = Result.success(emptyList<SaveState>())

    /** Records the most recent rename / delete calls so #831 tests can
     *  assert the SaveManager wiring without a real network. */
    var lastRenameCall: Triple<String, String, String>? = null
        private set
    var lastDeleteCall: Pair<String, String>? = null
        private set
    /** When set, the next updateSessionSave / deleteSessionSave call
     *  fails with this exception so the rollback path can be exercised. */
    var renameFailure: Throwable? = null
    var deleteFailure: Throwable? = null

    override suspend fun updateSessionSave(sessionId: String, saveId: String, name: String): Result<SaveState> {
        lastRenameCall = Triple(sessionId, saveId, name)
        renameFailure?.let { return Result.failure(it) }
        return Result.success(SaveState(id = saveId, name = name))
    }
    override suspend fun deleteSessionSave(sessionId: String, saveId: String): Result<Unit> {
        lastDeleteCall = sessionId to saveId
        deleteFailure?.let { return Result.failure(it) }
        return Result.success(Unit)
    }
    override suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String): Result<SaveState> {
        uploadSessionSaveCallCount++
        return uploadSessionSaveResult
    }
    /** Records the compression= form value received on the last
     *  uploadSessionSaveFromFile / *AutoSave* / *SlotSave* call. Tests
     *  assert this to verify SaveManager threads `staged.compression`
     *  through to the repository (#804 phase 2). */
    var lastUploadCompression: String? = null
        private set

    override suspend fun uploadSessionSaveFromFile(sessionId: String, name: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String): Result<SaveState> {
        uploadSessionSaveCallCount++
        lastUploadCompression = compression
        return uploadSessionSaveResult
    }
    /**
     * Returns 1024-byte payload by default — non-empty so tests that
     * upload these bytes (e.g. ShareSave in #979) can assert that the
     * actual data, not an empty placeholder, was forwarded.
     */
    var downloadedSaveBytes: ByteArray = ByteArray(1024)
    override suspend fun downloadSessionSave(sessionId: String, saveId: String) = Result.success(downloadedSaveBytes)
    override suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String): Result<Unit> {
        uploadSessionAutoSaveCallCount++
        return Result.success(Unit)
    }
    override suspend fun uploadSessionAutoSaveFromFile(sessionId: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String): Result<Unit> {
        uploadSessionAutoSaveCallCount++
        lastUploadCompression = compression
        return Result.success(Unit)
    }
    override suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray> {
        downloadSessionAutoSaveCallCount++
        return downloadSessionAutoSaveResult
    }
    override suspend fun downloadSessionAutoSaveToFile(sessionId: String, outputPath: String): Result<Unit> {
        downloadSessionAutoSaveCallCount++
        return downloadSessionAutoSaveResult.map { Unit }
    }
    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String): Result<Unit> {
        uploadSessionSramCallCount++
        return Result.success(Unit)
    }
    override suspend fun downloadSessionSram(sessionId: String): Result<ByteArray> {
        downloadSessionSramCallCount++
        return downloadSessionSramResult
    }
    override suspend fun uploadSessionSaveDirBundleFromFile(sessionId: String, tarPath: String, tarSize: Long): Result<Unit> = Result.success(Unit)
    override suspend fun downloadSessionSaveDirBundleToFile(sessionId: String, fileStorage: com.spela.player.util.FileStorage, outputPath: String): Result<Unit> =
        Result.failure(Exception("no save_dir bundle"))
    override suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String) =
        Result.success(SaveState(id = "1", name = "Slot $slot"))
    override suspend fun uploadSlotSaveFromFile(sessionId: String, slot: Int, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String, compression: String): Result<SaveState> {
        lastUploadCompression = compression
        return Result.success(SaveState(id = "1", name = "Slot $slot"))
    }
    override suspend fun downloadSlotSave(sessionId: String, slot: Int) = Result.failure<ByteArray>(Exception("stub"))
    override suspend fun downloadSlotSaveToFile(sessionId: String, slot: Int, outputPath: String) = Result.failure<Unit>(Exception("stub"))
    override suspend fun createSessionFromSharedSave(gameId: String, saveId: String) =
        Result.success(GameSession(id = "shared-session-1", gameId = gameId, name = "From shared save $saveId"))
    override suspend fun getSessionCheats(sessionId: String) = Result.success(SessionCheatConfig(false, emptyList()))
    override suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>) = Result.success(SessionCheatConfig(cheatsEnabled, enabledIndices))
    override suspend fun cloneSession(sessionId: String, name: String?, saveId: Long?) = Result.failure<GameSession>(Exception("stub"))
}

class StubChallengeRepository : ChallengeRepository {
    var downloadChallengeSaveCallCount = 0; private set
    var startAttemptCallCount = 0; private set
    var completeAttemptCallCount = 0; private set
    var abandonAttemptCallCount = 0; private set
    var createChallengeCallCount = 0; private set

    var downloadChallengeSaveResult: Result<ByteArray> = Result.success(byteArrayOf(99, 88))
    var startAttemptResult: Result<ChallengeAttempt> = Result.success(
        ChallengeAttempt(id = "attempt-1", challengeId = "c1", userId = "u1", username = "test", avatarUrl = null, status = "in_progress", startedAt = "", completedAt = null, durationMs = 0, isBest = false)
    )
    var completeAttemptResult: Result<ChallengeAttempt> = Result.success(
        ChallengeAttempt(id = "attempt-1", challengeId = "c1", userId = "u1", username = "test", avatarUrl = null, status = "completed", startedAt = "", completedAt = "", durationMs = 1000, isBest = false)
    )
    var createChallengeResult: Result<Challenge>? = null
    var abandonAttemptResult: Result<Unit> = Result.success(Unit)

    override suspend fun getChallenges(gameId: String?, consoleId: String?, difficulty: String?, sort: String?, page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getGameChallenges(gameId: String, page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getMyChallenges(page: Int) = Result.success(emptyList<Challenge>())
    override suspend fun getChallengeDetail(challengeId: String) = Result.failure<Challenge>(Exception("stub"))
    override suspend fun getLeaderboard(challengeId: String, page: Int) = Result.success(emptyList<ChallengeLeaderboardEntry>())
    override suspend fun createChallenge(gameId: String, name: String, description: String, type: String, difficulty: String, coreName: String, saveData: ByteArray, screenshotData: ByteArray?): Result<Challenge> {
        createChallengeCallCount++
        return createChallengeResult ?: Result.failure(Exception("stub"))
    }
    override suspend fun downloadChallengeSave(challengeId: String): Result<ByteArray> {
        downloadChallengeSaveCallCount++
        return downloadChallengeSaveResult
    }
    override suspend fun startAttempt(challengeId: String): Result<ChallengeAttempt> {
        startAttemptCallCount++
        return startAttemptResult
    }
    override suspend fun completeAttempt(challengeId: String, attemptId: String): Result<ChallengeAttempt> {
        completeAttemptCallCount++
        return completeAttemptResult
    }
    override suspend fun abandonAttempt(challengeId: String, attemptId: String): Result<Unit> {
        abandonAttemptCallCount++
        return abandonAttemptResult
    }
    override suspend fun getMyAttempts(challengeId: String) = Result.success(emptyList<ChallengeAttempt>())
    override suspend fun deleteChallenge(challengeId: String) = Result.success(Unit)
}

class StubKeyMappingRepository : KeyMappingRepository {
    override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? = null
    override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {}
    override suspend fun resetToDefault(consoleId: String, port: Int) {}
    override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {}
    override suspend fun getEffectiveMapping(consoleId: String, port: Int) = emptyMap<Int, Int>()
    override fun getDefaultMapping() = emptyMap<Int, Int>()
    override fun getAvailablePresets() = emptyList<KeyMappingPreset>()
    override suspend fun applyPreset(presetId: String) {}
    override suspend fun ensureDefaultsApplied() {}
    override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int) = emptyMap<Int, Int>()
    override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {}
    override suspend fun clearGameMapping(gameId: String) {}
    override suspend fun hasGameMapping(gameId: String) = false
}

class StubScreenshotCapture : ScreenshotCapture {
    var captureCallCount = 0; private set
    var captureResult: ByteArray? = byteArrayOf(1, 2, 3, 4)

    override fun captureScreenshot(): ByteArray? {
        captureCallCount++
        return captureResult
    }
}

class StubBiosRepository(
    private val missingFiles: List<BiosMissingFile> = emptyList(),
) : BiosRepository(
    apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager()),
    fileStorage = StubFileStorage(),
) {
    var preLaunchBiosCheckCallCount = 0; private set

    override suspend fun preLaunchBiosCheck(consoleId: String): List<BiosMissingFile> {
        preLaunchBiosCheckCallCount++
        return missingFiles
    }
}

/** Minimal FileStorage stub for BiosRepository superclass initialization. */
private class StubFileStorage : com.spela.player.util.FileStorage {
    override fun getGamesDir(): String = "/tmp/games"
    override fun getCoresDir(): String = "/tmp/cores"
    override fun getSavesDir(): String = "/tmp/saves"
    override fun getBiosDir(): String = "/tmp/bios"
    override suspend fun createDirectory(path: String) {}
    override suspend fun writeFile(path: String, data: ByteArray) {}
    override suspend fun readFile(path: String): ByteArray = byteArrayOf()
    override suspend fun fileExists(path: String): Boolean = false
    override suspend fun deleteFile(path: String) {}
    override suspend fun deleteDirectory(path: String) {}
    override suspend fun getDirectorySize(path: String): Long = 0
    override suspend fun writeFileStreaming(path: String, writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit) {}
    override suspend fun getFileSize(path: String): Long = 0
    override suspend fun listFiles(path: String): List<String> = emptyList()
    override suspend fun isDirectory(path: String): Boolean = false
    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0L
    override suspend fun extractTarFile(tarPath: String, destDir: String) {}
    override suspend fun sha256File(path: String): String? = null
}

/**
 * In-memory pending-upload queue for SaveManager tests (#804 phase 6).
 * Mirrors the SQLDelight repo's contract — auto-incremented ids, FIFO
 * ordering — without standing up a real database.
 */
class StubPendingSaveUploadRepository :
    com.spela.player.domain.repository.PendingSaveUploadRepository {

    private val rows = mutableListOf<com.spela.player.domain.model.PendingSaveUpload>()
    private var nextId = 1L

    /** Captures every row passed to [enqueue], even after the drain
     *  removes them from the live queue. Lets tests assert the row
     *  shape (e.g. server-visible name) without setting up an upload
     *  failure to keep the row alive. See #830. */
    val enqueueLog: MutableList<com.spela.player.domain.model.PendingSaveUpload> = mutableListOf()

    override suspend fun enqueue(
        sessionId: String,
        kind: com.spela.player.domain.model.PendingUploadKind,
        slot: Int?,
        name: String,
        coreName: String,
        compression: String,
        filePath: String,
        fileSize: Long,
        screenshotPath: String?,
        createdAt: Long,
    ): Long {
        val id = nextId++
        val row = com.spela.player.domain.model.PendingSaveUpload(
            id = id,
            sessionId = sessionId,
            kind = kind,
            slot = slot,
            name = name,
            coreName = coreName,
            compression = compression,
            filePath = filePath,
            fileSize = fileSize,
            screenshotPath = screenshotPath,
            createdAt = createdAt,
            retryCount = 0,
            lastError = null,
        )
        rows.add(row)
        enqueueLog.add(row)
        return id
    }

    override suspend fun getAll(): List<com.spela.player.domain.model.PendingSaveUpload> =
        rows.sortedWith(compareBy({ it.createdAt }, { it.id }))

    override suspend fun getForSession(
        sessionId: String,
    ): List<com.spela.player.domain.model.PendingSaveUpload> =
        rows.filter { it.sessionId == sessionId }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))

    override suspend fun getById(
        id: Long,
    ): com.spela.player.domain.model.PendingSaveUpload? =
        rows.find { it.id == id }

    override suspend fun count(): Long = rows.size.toLong()

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun markRetry(id: Long, lastError: String?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val r = rows[idx]
            rows[idx] = r.copy(retryCount = r.retryCount + 1, lastError = lastError)
        }
    }
}

// ── ViewModel Builder ───────────────────────────────────────────────────────

/**
 * Test builder for [EmulationViewModel].
 *
 * Uses its OWN [TestCoroutineScheduler] separate from [runTest]'s scheduler.
 * This prevents [runTest] cleanup from hanging on infinite-loop VM coroutines
 * (session timer, heartbeats) when a test assertion fails before tearDown.
 *
 * Tests must call [advanceTimeBy] instead of [TestScope.advanceTimeBy].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmulationViewModelTestBuilder {
    private val vmScheduler = TestCoroutineScheduler()
    val testDispatcher: TestDispatcher = StandardTestDispatcher(vmScheduler)

    val dispatchers: DispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    val libretroController = StubLibretroController()
    val coreRepository = StubCoreRepository()
    val preferencesRepository = StubPreferencesRepository()
    val saveDataRepository = StubSaveDataRepository()
    val sessionRepository = StubSessionRepository()
    val challengeRepository = StubChallengeRepository()
    val sharedSessionRepository = StubSharedSessionRepository()
    val achievementsRepository = StubAchievementsRepository()
    val achievementsController = StubAchievementsController()
    val keyMappingRepository = StubKeyMappingRepository()
    val fakeSecondaryDisplay = FakePlatformSecondaryDisplay()
    var biosRepository: BiosRepository? = null
    var screenshotCapture: ScreenshotCapture? = null
    var gameRepository: GameRepository = StubGameRepository()

    lateinit var vmScope: CoroutineScope
    lateinit var connectivityMonitor: ConnectivityMonitor
    lateinit var presenceService: PresenceService

    /**
     * Exposed so VM tests can emit RehearsalSaveBlocked events through
     * the real SaveManager surface (the VM's collector is what we want
     * to assert on). Set inside [build].
     */
    lateinit var saveManager: SaveManager

    /**
     * Exposed so VM tests can seed state fields (e.g. `sessionId`)
     * without walking the full StartGame pipeline. Most tests should
     * prefer intents over direct state pokes — use this sparingly and
     * only when driving a specific branch is impractical otherwise.
     */
    lateinit var mutableState: MutableStateFlow<EmulationState>

    /** Advance the VM's virtual clock by [ms] milliseconds and process pending tasks. */
    fun advanceTimeBy(ms: Long) {
        vmScheduler.advanceTimeBy(ms)
        vmScheduler.runCurrent()
    }

    fun build(): EmulationViewModel {
        vmScope = CoroutineScope(testDispatcher + Job())
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        connectivityMonitor = ConnectivityMonitor(apiClient, dispatchers, vmScope)
        presenceService = PresenceService(apiClient, StubMockEngineFactory, dispatchers, vmScope)
        val gamepadPortManager = GamepadPortManager(keyMappingRepository)
        mutableState = MutableStateFlow(EmulationState())
        val mutableStateLocal = mutableState

        val saveManagerLocal = SaveManager(
            saveDataRepository = saveDataRepository,
            connectivityMonitor = connectivityMonitor,
            libretroController = libretroController,
            screenshotCapture = screenshotCapture,
            _state = mutableStateLocal,
            dispatchers = dispatchers,
            scope = vmScope,
            sessionRepository = sessionRepository,
            fileStorage = StubFileStorage(),
            pendingUploadRepository = StubPendingSaveUploadRepository(),
        )
        saveManager = saveManagerLocal
        val challengeManager = ChallengeManager(
            challengeRepository = challengeRepository,
            libretroController = libretroController,
            screenshotCapture = screenshotCapture,
            _state = mutableStateLocal,
            dispatchers = dispatchers,
            scope = vmScope,
        )
        val netplayManager = NetplayManager(
            sharedSessionRepository = sharedSessionRepository,
            libretroController = libretroController,
            apiClient = apiClient,
            engineFactory = StubMockEngineFactory,
            _state = mutableStateLocal,
            dispatchers = dispatchers,
            scope = vmScope,
        )

        return EmulationViewModel(
            prepareGameUseCase = PrepareGameUseCase(
                downloadRepository = StubDownloadRepository(),
                coreRepository = coreRepository,
                coreUpdateService = com.spela.player.data.repository.CoreUpdateService(
                    coreRepository = coreRepository,
                    preferencesRepository = preferencesRepository,
                    dispatchers = dispatchers,
                    scope = vmScope,
                ),
            ),
            getGameDetailUseCase = GetGameDetailUseCase(gameRepository = gameRepository),
            preferencesRepository = preferencesRepository,
            achievementsRepository = achievementsRepository,
            achievementsController = achievementsController,
            libretroController = libretroController,
            secondaryDisplay = fakeSecondaryDisplay,
            presenceService = presenceService,
            gamepadPortManager = gamepadPortManager,
            saveManager = saveManagerLocal,
            challengeManager = challengeManager,
            netplayManager = netplayManager,
            _state = mutableStateLocal,
            dispatchers = dispatchers,
            scope = vmScope,
            biosRepository = biosRepository,
        )
    }

    fun tearDown() {
        if (::vmScope.isInitialized) vmScope.cancel(CancellationException("Test finished"))
    }
}
