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
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelaySave
import com.spela.player.domain.model.SaveData
import com.spela.player.domain.model.QuickSaveSlot
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.StorageUsage
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.PrepareGameUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
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
    override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
    override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
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
    override suspend fun downloadCore(coreName: String, onProgress: (Float) -> Unit) = Result.success("/path/to/core.so")
    override suspend fun getLocalCorePath(coreName: String): String = "/path/to/core.so"
    override suspend fun isCoreCached(coreName: String) = true
}

class StubSaveRepository : SaveRepository {
    var uploadAutoSaveCallCount = 0; private set
    var downloadAutoSaveCallCount = 0; private set
    var saveLocallyCallCount = 0; private set
    var lastUploadedData: ByteArray? = null; private set

    var uploadAutoSaveResult: Result<SaveState> = Result.success(SaveState(id = 1, gameId = 1, name = "auto"))
    var downloadAutoSaveResult: Result<ByteArray> = Result.success(byteArrayOf(10, 20, 30))

    override suspend fun getSaveStates(gameId: String) = Result.success(emptyList<SaveState>())
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray, coreName: String?) = Result.success(SaveState(id = 1, gameId = 1, name = name, coreName = coreName))
    override suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String?) = Result.success(SaveState(id = 1, gameId = 1, name = name, coreName = coreName))
    override suspend fun downloadSaveState(gameId: String, saveId: String) = Result.success(byteArrayOf())
    override suspend fun deleteSaveState(gameId: String, saveId: String) = Result.success(Unit)
    override suspend fun uploadAutoSave(gameId: String, data: ByteArray, coreName: String?): Result<SaveState> {
        uploadAutoSaveCallCount++
        lastUploadedData = data
        return uploadAutoSaveResult
    }
    override suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> {
        uploadAutoSaveCallCount++
        lastUploadedData = data
        return uploadAutoSaveResult
    }
    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> {
        downloadAutoSaveCallCount++
        return downloadAutoSaveResult
    }
    override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState> {
        saveLocallyCallCount++
        return Result.success(SaveState(id = 1, gameId = 1, name = name))
    }
    override suspend fun loadLocalAutoSave(gameId: String) = Result.failure<ByteArray>(Exception("none"))
    override suspend fun getPendingSyncCount() = 0
    override suspend fun renameSaveState(gameId: String, saveId: String, name: String) = Result.success(Unit)
    override suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String) = Result.success(Unit)
    override suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String?) = Result.success(SaveState(id = 1, gameId = 1, name = "Slot $slot", coreName = coreName))
    override suspend fun loadFromSlot(gameId: String, slot: Int) = Result.success(byteArrayOf(10, 20, 30))
    override suspend fun getSlots(gameId: String) = Result.success(emptyList<QuickSaveSlot>())
    override suspend fun getAutoSaveHistory(gameId: String) = Result.success(emptyList<SaveState>())
    override suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>) = Result.success(saveIds.size)
    override suspend fun getStorageUsage() = Result.success(StorageUsage(0L, emptyList()))
    override suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray) = Result.success(SaveState(id = 1, gameId = 1, name = name))
}

class StubSaveDataRepository : SaveDataRepository {
    var saveLocalSRAMCallCount = 0; private set
    var loadLocalSRAMCallCount = 0; private set
    var uploadActiveSaveDataCallCount = 0; private set
    var downloadActiveSaveDataCallCount = 0; private set
    var lastSavedSRAMData: ByteArray? = null; private set

    var zipSaveDirectoryCallCount = 0; private set
    var unzipToSaveDirectoryCallCount = 0; private set
    var lastUnzippedData: ByteArray? = null; private set

    var loadLocalSRAMResult: ByteArray? = null
    var downloadActiveSaveDataResult: Result<ByteArray> = Result.success(ByteArray(0))
    var zipSaveDirectoryResult: ByteArray? = null
    /** If > 0, downloadActiveSaveData() delays by this many ms before returning. */
    var downloadActiveSaveDataDelayMs: Long = 0L

    override suspend fun getSaveDataList(gameId: String) = Result.success(emptyList<SaveData>())
    override suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): Result<SaveData> {
        uploadActiveSaveDataCallCount++
        return Result.success(SaveData(0, 0, "Active"))
    }
    override suspend fun downloadActiveSaveData(gameId: String): Result<ByteArray> {
        downloadActiveSaveDataCallCount++
        if (downloadActiveSaveDataDelayMs > 0L) kotlinx.coroutines.delay(downloadActiveSaveDataDelayMs)
        return downloadActiveSaveDataResult
    }
    override suspend fun downloadSaveData(gameId: String, saveDataId: String) = Result.success(ByteArray(0))
    override suspend fun activateSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
    override suspend fun renameSaveData(gameId: String, saveDataId: String, name: String) = Result.success(Unit)
    override suspend fun deleteSaveData(gameId: String, saveDataId: String) = Result.success(Unit)
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
    ) = Result.success(UserPreferences())

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
    override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
    override suspend fun syncDeviceShaderOverrides() {}
    override suspend fun resolveShader(consoleId: String) = resolveShaderResult
    override suspend fun pushDeviceShaderOverridesToServer() {}
    override suspend fun syncKeyMappingsFromServer() {}
    override suspend fun pushKeyMappingsToServer() {}
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

class StubRelayRepository : RelayRepository {
    var uploadRelayAutoSaveCallCount = 0; private set
    var releaseTurnCallCount = 0; private set
    var heartbeatCallCount = 0; private set
    var downloadRelayAutoSaveCallCount = 0; private set

    var downloadRelayAutoSaveResult: Result<ByteArray> = Result.success(byteArrayOf())
    var heartbeatResult: Result<Unit> = Result.success(Unit)

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
    override suspend fun releaseTurn(relayId: String): Result<Unit> { releaseTurnCallCount++; return Result.success(Unit) }
    override suspend fun heartbeat(relayId: String): Result<Unit> { heartbeatCallCount++; return heartbeatResult }
    override suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray) =
        Result.success(RelaySave(id = 1, relayId = relayId, name = name))
    override suspend fun downloadRelaySave(relayId: String, saveId: Long) = Result.success(byteArrayOf())
    override suspend fun downloadRelayAutoSave(relayId: String): Result<ByteArray> {
        downloadRelayAutoSaveCallCount++
        return downloadRelayAutoSaveResult
    }
    override suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray): Result<RelaySave> {
        uploadRelayAutoSaveCallCount++
        return Result.success(RelaySave(id = 1, relayId = relayId, name = "Auto Save", isAuto = true))
    }
    override suspend fun copyRelaySaveToGame(relayId: String, saveId: Long) = Result.success(Unit)
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
    val saveRepository = StubSaveRepository()
    val challengeRepository = StubChallengeRepository()
    val relayRepository = StubRelayRepository()
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
            saveGameStateUseCase = SaveGameStateUseCase(saveRepository = saveRepository),
            loadGameStateUseCase = LoadGameStateUseCase(saveRepository = saveRepository),
            saveDataRepository = saveDataRepository,
            saveRepository = saveRepository,
            connectivityMonitor = connectivityMonitor,
            libretroController = libretroController,
            screenshotCapture = screenshotCapture,
            _state = mutableState,
            dispatchers = dispatchers,
            scope = vmScope,
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
            relayRepository = relayRepository,
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
