package com.spela.player.desktop.e2e

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.AuthEventBus
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen

/**
 * Advance both the test dispatcher and Compose clock in bounded steps.
 *
 * Compose Multiplatform `waitForIdle()` hangs when the composition
 * contains infinite animations (`rememberInfiniteTransition`).  Disabling
 * `mainClock.autoAdvance` prevents this during the advance loop.
 *
 * We use bounded `advanceTimeBy` instead of `advanceUntilIdle()` on the
 * test dispatcher so that perpetual coroutine loops (e.g. the session
 * timer in EmulationViewModel) don't hang the scheduler.  Multiple
 * iterations handle cascading async chains (click -> navigation -> compose
 * effect -> ViewModel coroutine -> state update -> recomposition).
 *
 * After the loop completes, `autoAdvance` is restored to `true` so that
 * subsequent operations (`performScrollTo`, `assertIsDisplayed`, etc.)
 * which internally call `waitForIdle()` can advance scroll/layout
 * animations.  By the time the loop finishes, loading skeletons with
 * infinite shimmer animations should be replaced by real content.
 *
 * IMPORTANT: All infinite animations MUST be guarded by
 * `LocalAnimationsEnabled.current` checks. This includes Material 3
 * `CircularProgressIndicator` which has an internal infinite rotation.
 * Any un-guarded infinite animation will cause `waitForIdle()` to hang
 * after `autoAdvance` is restored to `true`.
 *
 * Tiered variants:
 * - [advanceQuick]: 2 iterations — for simple click-then-assert patterns
 * - [advance]: 4 iterations — standard default for navigation and data loads
 * - [advanceFully]: 6 iterations — for complex multi-source navigation chains
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
private fun ComposeUiTest.advanceN(harness: SpelaTestHarness, iterations: Int) {
    mainClock.autoAdvance = false
    repeat(iterations) {
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }
    mainClock.autoAdvance = true
}

/** 2 iterations — for simple click-then-assert (toggle, button tap, immediate state). */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun ComposeUiTest.advanceQuick(harness: SpelaTestHarness) = advanceN(harness, 2)

/** 4 iterations — standard default for navigation and single data loads. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun ComposeUiTest.advance(harness: SpelaTestHarness) = advanceN(harness, 4)

/** 6 iterations — for complex navigation chains that load multiple data sources. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun ComposeUiTest.advanceFully(harness: SpelaTestHarness) = advanceN(harness, 6)

/**
 * Navigate to the GameDetail screen for [gameId] and wait for all data to load.
 * Replaces the per-test-file private helpers that called advance() 4 times.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness, gameId: String = "1") {
    advance(harness) // settle initial composition
    harness.navigationViewModel.onIntent(
        NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
    )
    advanceFully(harness) // handle full navigation + data load chain
}

fun createTestDispatchers(testDispatcher: TestDispatcher): DispatcherProvider {
    return object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }
}

class FakeServerRepository : ServerRepository {
    private val servers = mutableListOf<ServerConnection>()
    private var activeServerId: String? = null
    private val serversFlow = MutableStateFlow<List<ServerConnection>>(emptyList())

    override fun observeServers(): Flow<List<ServerConnection>> = serversFlow

    override suspend fun getServers(): List<ServerConnection> = servers.map {
        it.copy(isActive = it.id == activeServerId)
    }

    override suspend fun getActiveServer(): ServerConnection? =
        servers.find { it.id == activeServerId }?.copy(isActive = true)

    override suspend fun addServer(name: String, url: String): ServerConnection {
        val server = ServerConnection(
            id = (servers.size + 1).toString(),
            name = name,
            url = url,
        )
        servers.add(server)
        serversFlow.value = servers.toList()
        return server
    }

    override suspend fun removeServer(id: String) {
        servers.removeAll { it.id == id }
        serversFlow.value = servers.toList()
    }

    override suspend fun setActiveServer(id: String) {
        activeServerId = id
    }

    fun preAddServer(name: String, url: String, active: Boolean = false): ServerConnection {
        val server = ServerConnection(
            id = (servers.size + 1).toString(),
            name = name,
            url = url,
        )
        servers.add(server)
        serversFlow.value = servers.toList()
        if (active) activeServerId = server.id
        return server
    }
}

class FakeAuthRepository : AuthRepository {
    var shouldFail = false
    var registeredUsers = mutableMapOf("player" to "player123")
    private var tokens: AuthTokens? = null

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<AuthTokens> {
        if (shouldFail) return Result.failure(Exception("Login failed"))
        if (registeredUsers[username] != password) {
            return Result.failure(Exception("Invalid credentials"))
        }
        val t = AuthTokens("test-access-token", "test-refresh-token")
        tokens = t
        return Result.success(t)
    }

    override suspend fun register(
        serverUrl: String,
        username: String,
        email: String,
        password: String,
    ): Result<AuthTokens> {
        if (shouldFail) return Result.failure(Exception("Registration failed"))
        registeredUsers[username] = password
        val t = AuthTokens("test-access-token", "test-refresh-token")
        tokens = t
        return Result.success(t)
    }

    override suspend fun refreshToken(
        serverUrl: String,
        refreshToken: String,
    ): Result<AuthTokens> {
        return Result.success(AuthTokens("new-access", "new-refresh"))
    }

    override suspend fun getCurrentUser(): Result<User> {
        return if (tokens != null) {
            Result.success(User("1", "player", "player@test.com", "player"))
        } else {
            Result.failure(Exception("Not logged in"))
        }
    }

    override suspend fun getStoredTokens(): AuthTokens? = tokens

    override suspend fun storeTokens(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clearTokens() {
        tokens = null
    }

    override fun isLoggedIn(): Boolean = tokens != null

    fun preSetTokens(accessToken: String = "test-access", refreshToken: String = "test-refresh") {
        tokens = AuthTokens(accessToken, refreshToken)
    }
}

class FakeGameRepository : GameRepository {
    var shouldFail = false
    var topRatedGames: List<TopRatedGame> = emptyList()
    var screenshotUrls: List<String> = emptyList()

    val consoles = listOf(
        Console("nes", "Nintendo Entertainment System", "NES", 3, "#e53e3e"),
        Console("snes", "Super Nintendo", "SNES", 2, "#3182ce"),
    )

    var games: List<Game> = listOf(
        Game(
            id = "1",
            title = "Castlevania",
            consoleId = "nes",
            consoleName = "NES",
            description = "A classic action platformer.",
            developer = "Konami",
            publisher = "Konami",
            releaseDate = "1986",
            genre = "Action",
            fileSize = 131072,
            fileName = "castlevania.nes",
            scrapeAttempts = 1,
        ),
        Game(
            id = "2",
            title = "Super Mario Bros.",
            consoleId = "nes",
            consoleName = "NES",
            description = "The original platformer.",
            developer = "Nintendo",
            publisher = "Nintendo",
            releaseDate = "1985",
            genre = "Platformer",
            fileSize = 40960,
            fileName = "smb.nes",
            scrapeAttempts = 1,
        ),
        Game(
            id = "3",
            title = "Mega Man 2",
            consoleId = "nes",
            consoleName = "NES",
            description = "Fight the Robot Masters.",
            developer = "Capcom",
            publisher = "Capcom",
            releaseDate = "1988",
            genre = "Action",
            fileSize = 262144,
            fileName = "megaman2.nes",
            scrapeAttempts = 1,
        ),
        Game(
            id = "4",
            title = "Chrono Trigger",
            consoleId = "snes",
            consoleName = "SNES",
            description = "Time-travel RPG masterpiece.",
            developer = "Square",
            publisher = "Square",
            releaseDate = "1995",
            genre = "RPG",
            fileSize = 4194304,
            fileName = "ct.sfc",
            scrapeAttempts = 1,
        ),
        Game(
            id = "5",
            title = "Super Metroid",
            consoleId = "snes",
            consoleName = "SNES",
            description = "Explore planet Zebes.",
            developer = "Nintendo R&D1",
            publisher = "Nintendo",
            releaseDate = "1994",
            genre = "Action-Adventure",
            fileSize = 3145728,
            fileName = "supermetroid.sfc",
            scrapeAttempts = 1,
        ),
    )

    override suspend fun getConsoles(): Result<List<Console>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(consoles)
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.consoleId == consoleId })
    }

    override suspend fun getAllGames(): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games)
    }

    override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.title.contains(query, ignoreCase = true) })
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> {
        val game = games.find { it.id == gameId }
            ?: return Result.failure(Exception("Game not found"))
        return Result.success(GameDetail(game, screenshots = screenshotUrls))
    }

    var recentGamesOverride: List<Game>? = null

    override suspend fun getRecentGames(): Result<List<Game>> {
        return Result.success(recentGamesOverride ?: games.take(2))
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> {
        return Result.success(games.filter { it.isFavorite })
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getPlayLaterGames(): Result<List<Game>> {
        return Result.success(games.filter { it.isInPlayLater })
    }

    override suspend fun addToPlayLater(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(topRatedGames)
    }

    var globalTopRatedGames: List<TopRatedGame> = emptyList()

    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(globalTopRatedGames)
    }

    var similarGames: List<SimilarGame> = emptyList()
    var developerGamesMap: Map<String, List<DeveloperGame>> = emptyMap()

    override suspend fun getSimilarGames(gameId: String): Result<List<SimilarGame>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(similarGames)
    }

    override suspend fun getDeveloperGames(gameId: String): Result<List<DeveloperGame>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(developerGamesMap[gameId] ?: emptyList())
    }
}

class FakeDownloadRepository : DownloadRepository {
    private val cachedGames = mutableSetOf<String>()
    private val downloadsFlow = MutableStateFlow<List<DownloadProgress>>(emptyList())
    private val perGameFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()

    override fun observeDownloads(): Flow<List<DownloadProgress>> = downloadsFlow

    override fun observeDownload(gameId: String): Flow<DownloadProgress> {
        return perGameFlows.getOrPut(gameId) {
            MutableStateFlow(DownloadProgress(gameId, state = DownloadState.IDLE))
        }
    }

    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> {
        cachedGames.add(gameId)
        val progress = DownloadProgress(
            gameId = gameId,
            gameTitle = gameTitle,
            state = DownloadState.COMPLETED,
            bytesDownloaded = 100,
            totalBytes = 100,
        )
        perGameFlows.getOrPut(gameId) { MutableStateFlow(progress) }.value = progress
        return Result.success("/fake/path/$gameId")
    }

    override suspend fun cancelDownload(gameId: String) {
        perGameFlows[gameId]?.value = DownloadProgress(gameId, state = DownloadState.IDLE)
    }

    override suspend fun getLocalGamePath(gameId: String): String? {
        return if (gameId in cachedGames) "/fake/games/$gameId" else null
    }

    override suspend fun isGameCached(gameId: String): Boolean = gameId in cachedGames

    override suspend fun deleteLocalGame(gameId: String) {
        cachedGames.remove(gameId)
    }

    override suspend fun getCacheSize(): Long = cachedGames.size * 100_000L

    override suspend fun clearCache() {
        cachedGames.clear()
    }

    fun preCacheGame(gameId: String) {
        cachedGames.add(gameId)
    }

    fun setActiveDownloads(downloads: List<DownloadProgress>) {
        downloadsFlow.value = downloads
    }
}

class FakeSaveRepository : SaveRepository {
    var saves = mutableMapOf<String, MutableList<SaveState>>()
    private val autoSaves = mutableMapOf<String, ByteArray>()
    private val localSaves = mutableMapOf<String, ByteArray>()

    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> {
        return Result.success(saves[gameId] ?: emptyList())
    }

    override suspend fun uploadSaveState(
        gameId: String,
        name: String,
        data: ByteArray,
    ): Result<SaveState> {
        val save = SaveState(
            id = (saves[gameId]?.size?.toLong() ?: 0L) + 1,
            gameId = gameId.toLongOrNull() ?: 0L,
            name = name,
            isAuto = false,
        )
        saves.getOrPut(gameId) { mutableListOf() }.add(save)
        return Result.success(save)
    }

    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> {
        return Result.success(ByteArray(256) { it.toByte() })
    }

    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> {
        saves[gameId]?.removeAll { it.id.toString() == saveId }
        return Result.success(Unit)
    }

    override suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState> {
        autoSaves[gameId] = data
        val save = SaveState(
            id = 0,
            gameId = gameId.toLongOrNull() ?: 0L,
            name = "Auto Save",
            isAuto = true,
        )
        return Result.success(save)
    }

    fun preUploadAutoSave(gameId: String, data: ByteArray) {
        autoSaves[gameId] = data
    }

    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> {
        return autoSaves[gameId]?.let { Result.success(it) }
            ?: Result.failure(Exception("No auto-save found"))
    }

    override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState> {
        localSaves[gameId] = data
        val save = SaveState(
            id = (saves[gameId]?.size?.toLong() ?: 0L) + 1,
            gameId = gameId.toLongOrNull() ?: 0L,
            name = name,
            isAuto = isAuto,
        )
        saves.getOrPut(gameId) { mutableListOf() }.add(save)
        return Result.success(save)
    }

    override suspend fun loadLocalAutoSave(gameId: String): Result<ByteArray> {
        return localSaves[gameId]?.let { Result.success(it) }
            ?: Result.failure(Exception("No local auto-save found"))
    }

    override suspend fun getPendingSyncCount(): Int = 0
}

class FakeCoreRepository : CoreRepository {
    private val cores = listOf(
        LibretroCore(1, "nestopia", "Nestopia UE", "1.52.0", "linux,macos,windows"),
        LibretroCore(2, "snes9x", "Snes9x", "1.62.3", "linux,macos,windows"),
    )

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> {
        return Result.success(cores)
    }

    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> {
        return Result.success(cores.first())
    }

    override suspend fun downloadCore(coreId: String, onProgress: (Float) -> Unit): Result<String> {
        onProgress(1f)
        return Result.success("/fake/cores/$coreId")
    }

    override suspend fun getLocalCorePath(coreId: String): String = "/fake/cores/$coreId"

    override suspend fun isCoreCached(coreId: String): Boolean = true
}

class FakeLibretroController : LibretroController {
    var loadedCore: String? = null
        private set
    var loadedGame: String? = null
        private set
    var isRunning = false
        private set
    var isPaused = false
        private set
    var isFastForward = false
        private set
    var serializedState: ByteArray? = null
    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set
    var saveCallCount = 0
        private set
    var loadCallCount = 0
        private set

    override fun loadCore(corePath: String) {
        loadedCore = corePath
    }

    override fun loadGame(gamePath: String) {
        loadedGame = gamePath
    }

    override fun start() {
        isRunning = true
        isPaused = false
        startCallCount++
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isRunning = false
        isPaused = false
        stopCallCount++
    }

    override fun serialize(): ByteArray? {
        saveCallCount++
        return serializedState ?: ByteArray(128) { it.toByte() }
    }

    override fun unserialize(data: ByteArray): Boolean {
        loadCallCount++
        serializedState = data
        return true
    }

    override fun setFastForward(enabled: Boolean) {
        isFastForward = enabled
    }

    override fun supportsSaveStates(): Boolean = true

    override fun performanceStats(): Flow<Pair<Float, Float>> = MutableStateFlow(59.9f to 16.5f)
}

class FakeFileStorage : FileStorage {
    override fun getGamesDir(): String = "/tmp/spela-test/games"
    override fun getCoresDir(): String = "/tmp/spela-test/cores"
    override fun getSavesDir(): String = "/tmp/spela-test/saves"
    override fun getBiosDir(): String = "/tmp/spela-test/bios"
    override suspend fun createDirectory(path: String) {}
    override suspend fun writeFile(path: String, data: ByteArray) {}
    override suspend fun readFile(path: String): ByteArray = ByteArray(0)
    override suspend fun fileExists(path: String): Boolean = false
    override suspend fun deleteFile(path: String) {}
    override suspend fun deleteDirectory(path: String) {}
    override suspend fun getDirectorySize(path: String): Long = 0
    override suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) {
        writer { _, _, _ -> }
    }
    override suspend fun getFileSize(path: String): Long = 0
    override suspend fun listFiles(path: String): List<String> = emptyList()
    override suspend fun isDirectory(path: String): Boolean = false
}

class FakePreferencesRepository : PreferencesRepository {
    var syncKeyMappingsCalled = false
    var pushKeyMappingsCalled = false

    override suspend fun getPreferences(): Result<UserPreferences> = Result.success(UserPreferences())
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
    override suspend fun syncKeyMappingsFromServer() { syncKeyMappingsCalled = true }
    override suspend fun pushKeyMappingsToServer() { pushKeyMappingsCalled = true }
}

class FakeAchievementsRepository : AchievementsRepository {
    override suspend fun getRAStatus(): Result<RAStatus> = Result.success(RAStatus())
    override suspend fun linkRA(username: String, password: String): Result<RAStatus> = Result.success(RAStatus())
    override suspend fun unlinkRA(): Result<Unit> = Result.success(Unit)
    override suspend fun getRAToken(): Result<RACredentials> = Result.failure(Exception("Not linked"))
    override suspend fun updateRASettings(hardcoreEnabled: Boolean): Result<RAStatus> = Result.success(RAStatus())
}

class FakeAchievementsController : com.spela.player.domain.controller.AchievementsController {
    override fun init() {}
    override fun deinit() {}
    override fun login(username: String, token: String) {}
    override fun loadGame(hash: String) {}
    override fun doFrame() {}
    override val isHardcore: Boolean = false
    override fun setHardcore(enabled: Boolean) {}
    override val events: Flow<AchievementEvent> = flow {}
    override fun httpComplete(requestId: Int, responseCode: Int, responseBody: ByteArray) {}
}

class FakeRatingRepository : RatingRepository {
    var ratingSummary: RatingSummary = RatingSummary(0.0, 0, emptyMap())
    var myRating: GameRating? = null
    var gameRatings: List<GameRating> = emptyList()

    override suspend fun rateGame(gameId: String, rating: Int, review: String): Result<GameRating> =
        Result.success(GameRating("1", "1", "player", null, gameId, rating, review, ""))
    override suspend fun getGameRatings(gameId: String, page: Int, pageSize: Int): Result<List<GameRating>> =
        Result.success(gameRatings)
    override suspend fun getRatingSummary(gameId: String): Result<RatingSummary> =
        Result.success(ratingSummary)
    override suspend fun getMyRating(gameId: String): Result<GameRating?> =
        Result.success(myRating)
    override suspend fun deleteRating(gameId: String): Result<Unit> =
        Result.success(Unit)
}

class FakeSharedSaveRepository : SharedSaveRepository {
    var sharedSaves: List<SharedSaveState> = emptyList()

    override suspend fun getSharedSaves(gameId: String, page: Int, pageSize: Int): Result<List<SharedSaveState>> =
        Result.success(sharedSaves)
    override suspend fun shareSave(gameId: String, name: String, description: String, saveData: ByteArray): Result<SharedSaveState> =
        Result.success(SharedSaveState("1", "1", "player", null, gameId, name, description, saveData.size.toLong(), 0, ""))
    override suspend fun downloadSharedSave(gameId: String, saveId: String): Result<ByteArray> =
        Result.success(ByteArray(256) { it.toByte() })
    override suspend fun deleteSharedSave(gameId: String, saveId: String): Result<Unit> =
        Result.success(Unit)
}

class FakeRelayRepository : RelayRepository {
    var relays: List<Relay> = emptyList()
    var relayDetail: RelayDetail? = null
    var invitations: List<RelayInvitation> = emptyList()
    var relaySaves: List<RelaySave> = emptyList()
    var gameRelays: List<Relay> = emptyList()

    override suspend fun getMyRelays(page: Int, pageSize: Int): Result<List<Relay>> =
        Result.success(relays)
    override suspend fun getRelay(relayId: String): Result<RelayDetail> =
        relayDetail?.let { Result.success(it) }
            ?: Result.failure(Exception("Relay not found"))
    override suspend fun getRelayInvitations(): Result<List<RelayInvitation>> =
        Result.success(invitations)
    override suspend fun getPendingInvitationCount(): Result<Int> =
        Result.success(invitations.size)
    override suspend fun createRelay(name: String, gameId: String, description: String): Result<RelayDetail> =
        Result.success(RelayDetail(id = "new-relay", name = name, gameId = gameId, ownerId = "1", ownerUsername = "player"))
    override suspend fun deleteRelay(relayId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun inviteUser(relayId: String, username: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun acceptInvitation(invitationId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun leaveRelay(relayId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun removeMember(relayId: String, userId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun getGameRelays(gameId: String): Result<List<Relay>> =
        Result.success(gameRelays)
    override suspend fun getRelaySaves(relayId: String): Result<List<RelaySave>> =
        Result.success(relaySaves)
    override suspend fun deleteRelaySave(relayId: String, saveId: Long): Result<Unit> =
        Result.success(Unit)
    override suspend fun takeTurn(relayId: String): Result<String> =
        Result.success("fake-turn-token")
    override suspend fun releaseTurn(relayId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun heartbeat(relayId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray): Result<RelaySave> =
        Result.success(RelaySave(id = 1, relayId = relayId, name = name, fileSize = data.size.toLong()))
    override suspend fun downloadRelaySave(relayId: String, saveId: Long): Result<ByteArray> =
        Result.success(ByteArray(256) { it.toByte() })
    override suspend fun downloadRelayAutoSave(relayId: String): Result<ByteArray> =
        Result.success(ByteArray(256) { it.toByte() })
    override suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray): Result<RelaySave> =
        Result.success(RelaySave(id = 1, relayId = relayId, name = "Auto Save", isAuto = true, fileSize = data.size.toLong()))
}

class FakeCollectionRepository : CollectionRepository {
    var myCollections: List<GameCollection> = emptyList()
    var publicCollections: List<GameCollection> = emptyList()
    var collectionDetail: GameCollectionDetail? = null

    override suspend fun getMyCollections(page: Int, pageSize: Int): Result<List<GameCollection>> =
        Result.success(myCollections)
    override suspend fun getPublicCollections(page: Int, pageSize: Int): Result<List<GameCollection>> =
        Result.success(publicCollections)
    override suspend fun getCollection(id: String): Result<GameCollectionDetail> =
        collectionDetail?.let { Result.success(it) }
            ?: Result.failure(Exception("Collection not found"))
    override suspend fun createCollection(name: String, description: String?, isPublic: Boolean): Result<GameCollection> =
        Result.success(GameCollection(id = "new", userId = "1", username = "player", name = name, description = description, isPublic = isPublic))
    override suspend fun updateCollection(id: String, name: String?, description: String?, isPublic: Boolean?): Result<GameCollection> =
        Result.success(GameCollection(id = id, userId = "1", username = "player", name = name ?: ""))
    override suspend fun deleteCollection(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun addGameToCollection(collectionId: String, gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeGameFromCollection(collectionId: String, gameId: String): Result<Unit> = Result.success(Unit)
}

class FakeStatsRepository : StatsRepository {
    var mostPlayedGames: List<MostPlayedGame> = emptyList()
    var activePlayers: List<ActivePlayer> = emptyList()

    override suspend fun getMostPlayedGames(): Result<List<MostPlayedGame>> =
        Result.success(mostPlayedGames)
    override suspend fun getMostActivePlayers(): Result<List<ActivePlayer>> =
        Result.success(activePlayers)
}

class FakeGameStatsRepository : GameStatsRepository {
    var gameStats: GameStats = GameStats(totalPlayers = 0, totalPlayTime = 0, averagePlayTime = 0, topPlayers = emptyList())
    var userStats: UserStats = UserStats(totalPlayTime = 0, gamesPlayed = 0, currentStreak = 0, longestStreak = 0, mostPlayedGame = null, mostPlayedGameTime = 0, lastPlayedAt = null)
    var recentAchievements: List<RecentAchievement> = emptyList()
    var achievements: List<GameAchievement> = emptyList()
    var achievementProgress: List<AchievementProgress> = emptyList()
    var achievementTimeline: AchievementTimelineData? = null
    var achievementLeaderboard: List<AchievementPlayerRanking> = emptyList()

    override suspend fun getGameStats(gameId: String): Result<GameStats> = Result.success(gameStats)
    override suspend fun getGameAchievements(gameId: String): Result<List<GameAchievement>> = Result.success(achievements)
    override suspend fun getAchievementProgress(gameId: String): Result<List<AchievementProgress>> = Result.success(achievementProgress)
    override suspend fun getAchievementTimeline(gameId: String): Result<AchievementTimelineData> =
        achievementTimeline?.let { Result.success(it) }
            ?: Result.success(AchievementTimelineData(raGameId = null, gameTitle = "", totalPlayTime = 0, timeline = emptyList(), totalAchievements = 0, unlockedCount = 0, totalPoints = 0, earnedPoints = 0))
    override suspend fun getAchievementLeaderboard(gameId: String): Result<List<AchievementPlayerRanking>> = Result.success(achievementLeaderboard)
    override suspend fun getUserStats(): Result<UserStats> = Result.success(userStats)
    override suspend fun getRecentAchievements(): Result<List<RecentAchievement>> = Result.success(recentAchievements)
}

class FakeSocialRepository : SocialRepository {
    var onlineUsers: List<OnlineUser> = emptyList()
    var activityEvents: List<ActivityEvent> = emptyList()

    override suspend fun getOnlineUsers(): Result<List<OnlineUser>> = Result.success(onlineUsers)
    override suspend fun getActivityFeed(page: Int, pageSize: Int): Result<List<ActivityEvent>> =
        Result.success(activityEvents)
    override suspend fun getPublicProfile(userId: String): Result<com.spela.player.domain.model.PublicProfile> =
        Result.failure(Exception("Not implemented in fake"))
}

class FakeKeyMappingRepository : KeyMappingRepository {
    private val mappings = mutableMapOf<String, MutableMap<Int, Int>>()
    private val gameMappings = mutableMapOf<String, MutableMap<Int, Int>>()
    var applyPresetCalled: String? = null
    var ensureDefaultsCalled = false

    private fun key(consoleId: String, port: Int) = "$consoleId:$port"

    override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? {
        val bindings = mappings[key(consoleId, port)] ?: return null
        return KeyMappingProfile(
            consoleId = consoleId,
            port = port,
            bindings = bindings.toMap(),
        )
    }

    override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {
        mappings.getOrPut(key(consoleId, port)) { mutableMapOf() }[retroButtonId] = platformKeyCode
    }

    override suspend fun resetToDefault(consoleId: String, port: Int) {
        mappings.remove(key(consoleId, port))
    }

    override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {
        mappings[key(consoleId, port)]?.remove(retroButtonId)
    }

    override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> {
        return mappings[key(consoleId, port)] ?: emptyMap()
    }

    override fun getDefaultMapping(): Map<Int, Int> = emptyMap()

    override fun getAvailablePresets(): List<KeyMappingPreset> = listOf(
        KeyMappingPreset("test-preset", "Test Preset", "A test preset", emptyMap()),
    )

    override suspend fun applyPreset(presetId: String) {
        applyPresetCalled = presetId
    }

    override suspend fun ensureDefaultsApplied() {
        ensureDefaultsCalled = true
    }

    override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> {
        return gameMappings[gameId] ?: getEffectiveMapping(consoleId, port)
    }

    override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {
        gameMappings[gameId] = bindings.toMutableMap()
    }

    override suspend fun clearGameMapping(gameId: String) {
        gameMappings.remove(gameId)
    }

    override suspend fun hasGameMapping(gameId: String): Boolean {
        return gameMappings.containsKey(gameId)
    }
}

class FakeNetplayRepository : NetplayRepository {
    var sessions: List<NetplaySession> = emptyList()
    var currentSession: NetplaySession? = null

    override suspend fun createSession(
        gameId: String,
        inputDelay: Int,
    ): Result<NetplaySession> {
        val session = NetplaySession(
            id = "fake-session-1",
            gameId = gameId,
            hostUserId = "user-1",
            hostUsername = "TestHost",
            inputDelay = inputDelay,
            inviteCode = "ABCD1234",
        )
        return Result.success(session)
    }

    override suspend fun getSessions(): Result<List<NetplaySession>> = Result.success(sessions)
    override suspend fun getSession(sessionId: String): Result<NetplaySession> =
        currentSession?.let { Result.success(it) }
            ?: Result.failure(Exception("Session not found"))
    override suspend fun joinByInviteCode(code: String): Result<NetplaySession> =
        currentSession?.let { Result.success(it) }
            ?: Result.failure(Exception("Invalid code"))
    override suspend fun leaveSession(sessionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateInputDelay(sessionId: String, inputDelay: Int): Result<NetplaySession> =
        currentSession?.let { Result.success(it.copy(inputDelay = inputDelay)) }
            ?: Result.failure(Exception("Session not found"))
}

class FakeChallengeRepository : ChallengeRepository {
    var challenges: MutableList<Challenge> = mutableListOf()
    var leaderboard: List<ChallengeLeaderboardEntry> = emptyList()
    var myAttempts: List<ChallengeAttempt> = emptyList()
    var shouldFail = false
    private var nextAttemptId = 1

    override suspend fun createChallenge(
        gameId: String,
        name: String,
        description: String,
        type: String,
        difficulty: String,
        coreName: String,
        saveData: ByteArray,
        screenshotData: ByteArray?,
    ): Result<Challenge> {
        if (shouldFail) return Result.failure(Exception("Create failed"))
        val challenge = Challenge(
            id = (challenges.size + 1).toString(),
            creatorId = "1",
            creatorUsername = "player",
            creatorAvatarUrl = null,
            gameId = gameId,
            gameTitle = "Castlevania",
            gameCoverUrl = null,
            gameConsoleName = "NES",
            name = name,
            description = description,
            type = ChallengeType.fromApiId(type),
            difficulty = ChallengeDifficulty.fromApiId(difficulty),
            status = "active",
            screenshotUrl = null,
            coreName = coreName,
            saveFileSize = saveData.size.toLong(),
            attemptCount = 0,
            completionCount = 0,
            bestTimeMs = null,
            expiresAt = null,
            createdAt = "2026-02-17T12:00:00Z",
        )
        challenges.add(challenge)
        return Result.success(challenge)
    }

    override suspend fun getChallenges(
        gameId: String?,
        consoleId: String?,
        difficulty: String?,
        sort: String?,
        page: Int,
    ): Result<List<Challenge>> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        var result = challenges.toList()
        if (gameId != null) result = result.filter { it.gameId == gameId }
        return Result.success(result)
    }

    override suspend fun getChallengeDetail(challengeId: String): Result<Challenge> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        val challenge = challenges.find { it.id == challengeId }
            ?: return Result.failure(Exception("Challenge not found"))
        return Result.success(challenge)
    }

    override suspend fun deleteChallenge(challengeId: String): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Delete failed"))
        challenges.removeAll { it.id == challengeId }
        return Result.success(Unit)
    }

    override suspend fun getGameChallenges(gameId: String, page: Int): Result<List<Challenge>> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        return Result.success(challenges.filter { it.gameId == gameId })
    }

    override suspend fun getMyChallenges(page: Int): Result<List<Challenge>> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        return Result.success(challenges.filter { it.creatorId == "1" })
    }

    override suspend fun startAttempt(challengeId: String): Result<ChallengeAttempt> {
        if (shouldFail) return Result.failure(Exception("Start failed"))
        val attempt = ChallengeAttempt(
            id = (nextAttemptId++).toString(),
            challengeId = challengeId,
            userId = "1",
            username = "player",
            avatarUrl = null,
            status = "in_progress",
            startedAt = "2026-02-17T12:00:00Z",
            completedAt = null,
            durationMs = 0,
            isBest = false,
        )
        return Result.success(attempt)
    }

    override suspend fun completeAttempt(
        challengeId: String,
        attemptId: String,
    ): Result<ChallengeAttempt> {
        if (shouldFail) return Result.failure(Exception("Complete failed"))
        return Result.success(
            ChallengeAttempt(
                id = attemptId,
                challengeId = challengeId,
                userId = "1",
                username = "player",
                avatarUrl = null,
                status = "completed",
                startedAt = "2026-02-17T12:00:00Z",
                completedAt = "2026-02-17T12:05:30Z",
                durationMs = 330_000,
                isBest = true,
            )
        )
    }

    override suspend fun abandonAttempt(challengeId: String, attemptId: String): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Abandon failed"))
        return Result.success(Unit)
    }

    override suspend fun getMyAttempts(challengeId: String): Result<List<ChallengeAttempt>> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        return Result.success(myAttempts.filter { it.challengeId == challengeId })
    }

    override suspend fun getLeaderboard(
        challengeId: String,
        page: Int,
    ): Result<List<ChallengeLeaderboardEntry>> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        return Result.success(leaderboard)
    }

    override suspend fun downloadChallengeSave(challengeId: String): Result<ByteArray> {
        if (shouldFail) return Result.failure(Exception("Download failed"))
        return Result.success(ByteArray(256) { it.toByte() })
    }

    // ── Test helpers ──

    fun preAddChallenge(
        id: String = "1",
        gameId: String = "1",
        name: String = "Beat Dracula",
        type: ChallengeType = ChallengeType.COMPLETION,
        difficulty: ChallengeDifficulty = ChallengeDifficulty.MEDIUM,
        attemptCount: Int = 5,
        completionCount: Int = 3,
        bestTimeMs: Long? = 120_000,
    ): Challenge {
        val challenge = Challenge(
            id = id,
            creatorId = "1",
            creatorUsername = "player",
            creatorAvatarUrl = null,
            gameId = gameId,
            gameTitle = if (gameId == "1") "Castlevania" else "Super Mario Bros.",
            gameCoverUrl = null,
            gameConsoleName = "NES",
            name = name,
            description = "Complete this challenge!",
            type = type,
            difficulty = difficulty,
            status = "active",
            screenshotUrl = null,
            coreName = "nestopia",
            saveFileSize = 8192,
            attemptCount = attemptCount,
            completionCount = completionCount,
            bestTimeMs = bestTimeMs,
            expiresAt = null,
            createdAt = "2026-02-17T12:00:00Z",
        )
        challenges.add(challenge)
        return challenge
    }

    fun preSetLeaderboard(entries: List<ChallengeLeaderboardEntry>) {
        leaderboard = entries
    }

    fun preSetMyAttempts(attempts: List<ChallengeAttempt>) {
        myAttempts = attempts
    }
}

class FakeBiosRepository(
    apiClient: com.spela.player.data.remote.api.SpelaApiClient = createFakeApiClient(),
    fileStorage: FileStorage = FakeFileStorage(),
) : com.spela.player.data.repository.BiosRepository(apiClient, fileStorage) {
    var consolesWithMissingBios: Map<String, com.spela.player.domain.model.BiosConsoleStatus> = emptyMap()
    var preLaunchMissingFiles: List<com.spela.player.domain.model.BiosMissingFile> = emptyList()
    var syncCalled = false
    var fetchStatusCalled = false

    override suspend fun syncBiosFiles() {
        syncCalled = true
    }

    override suspend fun fetchBiosStatus(): com.spela.player.data.remote.dto.BiosStatusResponse? {
        fetchStatusCalled = true
        return null
    }

    override suspend fun getConsoleStatus(consoleId: String): com.spela.player.domain.model.BiosConsoleStatus? {
        return consolesWithMissingBios[consoleId]
    }

    override suspend fun preLaunchBiosCheck(consoleId: String): List<com.spela.player.domain.model.BiosMissingFile> {
        return preLaunchMissingFiles
    }

    override suspend fun getConsolesWithMissingBios(): Map<String, com.spela.player.domain.model.BiosConsoleStatus> {
        return consolesWithMissingBios
    }

    fun preSetConsolesWithMissingBios(consoles: Map<String, com.spela.player.domain.model.BiosConsoleStatus>) {
        consolesWithMissingBios = consoles
    }

    fun preSetPreLaunchMissingFiles(files: List<com.spela.player.domain.model.BiosMissingFile>) {
        preLaunchMissingFiles = files
    }
}

class FakeSaveDataRepository : SaveDataRepository {
    var saveDataList: MutableList<SaveData> = mutableListOf()
    private val localSram = mutableMapOf<String, ByteArray>()

    override suspend fun getSaveDataList(gameId: String): Result<List<SaveData>> =
        Result.success(saveDataList.filter { it.gameId == gameId.toLongOrNull() ?: 0L })

    override suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): Result<SaveData> {
        val sd = SaveData(
            id = (saveDataList.size + 1).toLong(),
            gameId = gameId.toLongOrNull() ?: 0L,
            name = "Active",
            fileSize = data.size.toLong(),
            isActive = true,
        )
        saveDataList.add(sd)
        return Result.success(sd)
    }

    override suspend fun downloadActiveSaveData(gameId: String): Result<ByteArray> =
        localSram[gameId]?.let { Result.success(it) }
            ?: Result.failure(Exception("No active save data"))

    override suspend fun downloadSaveData(gameId: String, saveDataId: String): Result<ByteArray> =
        Result.success(ByteArray(128) { it.toByte() })

    override suspend fun activateSaveData(gameId: String, saveDataId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun renameSaveData(gameId: String, saveDataId: String, name: String): Result<Unit> {
        val idx = saveDataList.indexOfFirst { it.id.toString() == saveDataId }
        if (idx >= 0) saveDataList[idx] = saveDataList[idx].copy(name = name)
        return Result.success(Unit)
    }

    override suspend fun deleteSaveData(gameId: String, saveDataId: String): Result<Unit> {
        saveDataList.removeAll { it.id.toString() == saveDataId }
        return Result.success(Unit)
    }

    override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {
        localSram[gameId] = data
    }

    override suspend fun loadLocalSRAM(gameId: String): ByteArray? = localSram[gameId]

    override suspend fun getPendingSyncCount(): Int = 0

    fun preAddSaveData(
        id: Long = 1,
        gameId: Long = 1,
        name: String = "Save 1",
        isActive: Boolean = false,
        fileSize: Long = 8192,
    ): SaveData {
        val sd = SaveData(id = id, gameId = gameId, name = name, fileSize = fileSize, isActive = isActive)
        saveDataList.add(sd)
        return sd
    }
}

class FakeConnectivityMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    private val _onReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onReconnect: kotlinx.coroutines.flow.SharedFlow<Unit> = _onReconnect.asSharedFlow()

    fun start() {}
    fun reportOffline() { _isOnline.value = false }
    fun reportOnline() {
        val wasOffline = !_isOnline.value
        _isOnline.value = true
        if (wasOffline) _onReconnect.tryEmit(Unit)
    }

    fun setOnline(online: Boolean) {
        if (online) reportOnline() else reportOffline()
    }
}

class FakeSyncEngine {
    private val _syncState = MutableStateFlow(com.spela.player.data.remote.SyncState())
    val syncState: StateFlow<com.spela.player.data.remote.SyncState> = _syncState.asStateFlow()
    var syncAllCallCount = 0
        private set

    fun start() {}
    suspend fun syncAll() {
        syncAllCallCount++
    }
}

private object StubMockEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
    }
}

fun createFakeApiClient(): SpelaApiClient {
    return SpelaApiClient(StubMockEngineFactory, TokenManager(), AuthEventBus())
}
