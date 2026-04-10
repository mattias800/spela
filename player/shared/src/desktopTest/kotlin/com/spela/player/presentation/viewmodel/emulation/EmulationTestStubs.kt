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
import com.spela.player.domain.model.DownloadProgress
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
import com.spela.player.domain.model.UserPreferences
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
    var getSRAMCallCount = 0; private set
    var setSRAMCallCount = 0; private set
    var clearNetplayModeCallCount = 0; private set

    var lastLoadCorePath: String? = null; private set
    var lastLoadGamePath: String? = null; private set
    var lastFastForwardEnabled: Boolean? = null; private set
    var lastUnserializeData: ByteArray? = null; private set
    var lastSetSRAMData: ByteArray? = null; private set

    var supportsSaveStatesResult = true
    var serializeResult: ByteArray? = byteArrayOf()
    var unserializeResult = true
    var isHwRenderEnabledResult = false
    var getSRAMResult: ByteArray? = byteArrayOf(1, 2, 3)
    var setSRAMResult = true
    var loadCoreShouldThrow: Exception? = null

    override fun loadCore(corePath: String) {
        loadCoreShouldThrow?.let { throw it }
        loadCoreCallCount++
        lastLoadCorePath = corePath
    }

    override fun loadGame(gamePath: String) {
        loadGameCallCount++
        lastLoadGamePath = gamePath
    }

    override fun start() { startCallCount++ }
    override fun pause() { pauseCallCount++ }
    override fun resume() { resumeCallCount++ }
    override fun stop() { stopCallCount++ }
    override fun supportsSaveStates(): Boolean = supportsSaveStatesResult
    override fun serialize(): ByteArray? { serializeCallCount++; return serializeResult }
    override fun unserialize(data: ByteArray): Boolean { unserializeCallCount++; lastUnserializeData = data; return unserializeResult }
    override fun setFastForward(enabled: Boolean) { setFastForwardCallCount++; lastFastForwardEnabled = enabled }
    override fun performanceStats(): Flow<Pair<Float, Float>> = emptyFlow()
    override fun isHwRenderEnabled(): Boolean = isHwRenderEnabledResult
    override fun getSRAM(): ByteArray? { getSRAMCallCount++; return getSRAMResult }
    override fun setSRAM(data: ByteArray): Boolean { setSRAMCallCount++; lastSetSRAMData = data; return setSRAMResult }
    override fun clearNetplayMode() { clearNetplayModeCallCount++ }
}

class StubLibretroControllerWithVariableTracking : LibretroController {
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
    override fun setCoreVariable(key: String, value: String) { coreVariables[key] = value }
}

// ── Repository stubs ────────────────────────────────────────────────────────

class StubGameRepository(private val consoleId: String = "nes") : GameRepository {
    override suspend fun getConsoles() = Result.success(emptyList<com.spela.player.domain.model.Console>())
    override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
    override suspend fun getAllGames() = Result.success(emptyList<Game>())
    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
    override suspend fun getGameDetail(gameId: String) = Result.success(
        GameDetail(game = Game(id = gameId, title = "Test Game", consoleId = this.consoleId))
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
    override fun observeDownloads(): Flow<List<DownloadProgress>> = emptyFlow()
    override fun observeDownload(gameId: String): Flow<DownloadProgress> = emptyFlow()
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = emptyFlow()
    override suspend fun downloadGame(gameId: String, gameTitle: String) = Result.success("/path/to/game.rom")
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String = "/path/to/game.rom"
    override suspend fun isGameCached(gameId: String) = true
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize() = 0L
    override suspend fun clearCache() {}
}

class StubCoreRepository : CoreRepository {
    override suspend fun getAvailableCores() = Result.success(emptyList<LibretroCore>())
    override suspend fun getRecommendedCore(gameId: String) = Result.success(LibretroCore(id = 1, name = "nestopia", displayName = "Nestopia"))
    override suspend fun downloadCore(coreName: String, downloadUrl: String?, onProgress: (Float) -> Unit) = Result.success("/path/to/core.so")
    override suspend fun getLocalCorePath(coreName: String): String = "/path/to/core.so"
    override suspend fun isCoreCached(coreName: String) = true
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

    override suspend fun getPreferences() = preferencesResult
    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        selectedShader: String?,
        selectedTheme: String?,
        consoleShaders: Map<String, String>?,
        defaultSecondScreenPage: String?,
    ) = Result.success(UserPreferences())

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
    override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
    override suspend fun syncDeviceShaderOverrides() {}
    override suspend fun resolveShader(consoleId: String) = resolveShaderResult
    override suspend fun pushDeviceShaderOverridesToServer() {}
    override suspend fun syncKeyMappingsFromServer() {}
    override suspend fun pushKeyMappingsToServer() {}
    override fun getOrientationLock(): String = "auto"
    override fun setOrientationLock(mode: String) {}
    override fun getControlTab(consoleId: String): String =
        if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
    override fun setControlTab(consoleId: String, tab: String) {}
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

    override suspend fun getMySharedSessions(page: Int, pageSize: Int) = Result.success(emptyList<SharedSession>())
    override suspend fun getSharedSession(sharedSessionId: String) = Result.failure<SharedSessionDetail>(Exception("stub"))
    override suspend fun getSharedSessionInvitations() = Result.success(emptyList<SharedSessionInvitation>())
    override suspend fun getPendingInvitationCount() = Result.success(0)
    override suspend fun createSharedSession(name: String, gameId: String, description: String) = Result.failure<SharedSessionDetail>(Exception("stub"))
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
    override suspend fun copySharedSessionSaveToGame(sharedSessionId: String, saveId: Long) = Result.success(Unit)
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
    var existingSessions: List<GameSession> = emptyList()

    override suspend fun getSessionsForGame(gameId: String) = Result.success(existingSessions)
    override suspend fun getSession(sessionId: String) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun createSession(gameId: String, name: String): Result<GameSession> {
        createSessionCallCount++
        lastCreatedSessionName = name
        return Result.success(GameSession(id = "auto-${createSessionCallCount}", gameId = gameId, name = name))
    }
    override suspend fun updateSession(sessionId: String, name: String?, coreName: String?) = Result.failure<GameSession>(Exception("stub"))
    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
    override suspend fun getSessionSaves(sessionId: String) = Result.success(emptyList<SaveState>())
    override suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String): Result<SaveState> {
        uploadSessionSaveCallCount++
        return Result.success(SaveState(id = 1, gameId = 1, name = name))
    }
    override suspend fun downloadSessionSave(sessionId: String, saveId: String) = Result.success(byteArrayOf())
    override suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String): Result<Unit> {
        uploadSessionAutoSaveCallCount++
        return Result.success(Unit)
    }
    override suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray> {
        downloadSessionAutoSaveCallCount++
        return downloadSessionAutoSaveResult
    }
    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String): Result<Unit> {
        uploadSessionSramCallCount++
        return Result.success(Unit)
    }
    override suspend fun downloadSessionSram(sessionId: String): Result<ByteArray> {
        downloadSessionSramCallCount++
        return downloadSessionSramResult
    }
    override suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String) =
        Result.success(SaveState(id = 1, gameId = 1, name = "Slot $slot"))
    override suspend fun downloadSlotSave(sessionId: String, slot: Int) = Result.failure<ByteArray>(Exception("stub"))
    override suspend fun createSessionFromSharedSave(gameId: String, saveId: String) =
        Result.success(GameSession(id = "shared-session-1", gameId = gameId, name = "From shared save $saveId"))
    override suspend fun getSessionCheats(sessionId: String) = Result.success(SessionCheatConfig(false, emptyList()))
    override suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>) = Result.success(SessionCheatConfig(cheatsEnabled, enabledIndices))
    override suspend fun duplicateSession(sessionId: String, name: String?) = Result.failure<GameSession>(Exception("stub"))
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
        val mutableState = MutableStateFlow(EmulationState())

        val saveManager = SaveManager(
            saveDataRepository = saveDataRepository,
            connectivityMonitor = connectivityMonitor,
            libretroController = libretroController,
            screenshotCapture = screenshotCapture,
            _state = mutableState,
            dispatchers = dispatchers,
            scope = vmScope,
            sessionRepository = sessionRepository,
        )
        val challengeManager = ChallengeManager(
            challengeRepository = challengeRepository,
            libretroController = libretroController,
            screenshotCapture = screenshotCapture,
            _state = mutableState,
            dispatchers = dispatchers,
            scope = vmScope,
        )
        val netplayManager = NetplayManager(
            sharedSessionRepository = sharedSessionRepository,
            libretroController = libretroController,
            apiClient = apiClient,
            engineFactory = StubMockEngineFactory,
            _state = mutableState,
            dispatchers = dispatchers,
            scope = vmScope,
        )

        return EmulationViewModel(
            prepareGameUseCase = PrepareGameUseCase(
                downloadRepository = StubDownloadRepository(),
                coreRepository = StubCoreRepository(),
            ),
            getGameDetailUseCase = GetGameDetailUseCase(gameRepository = gameRepository),
            preferencesRepository = preferencesRepository,
            achievementsRepository = achievementsRepository,
            achievementsController = achievementsController,
            libretroController = libretroController,
            secondaryDisplay = fakeSecondaryDisplay,
            presenceService = presenceService,
            gamepadPortManager = gamepadPortManager,
            saveManager = saveManager,
            challengeManager = challengeManager,
            netplayManager = netplayManager,
            _state = mutableState,
            dispatchers = dispatchers,
            scope = vmScope,
            biosRepository = biosRepository,
        )
    }

    fun tearDown() {
        if (::vmScope.isInitialized) vmScope.cancel(CancellationException("Test finished"))
    }
}
