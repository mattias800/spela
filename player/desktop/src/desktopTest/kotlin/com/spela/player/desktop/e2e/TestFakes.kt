package com.spela.player.desktop.e2e

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.presentation.viewmodel.LibretroController
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher

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
}

class FakeGameRepository : GameRepository {
    var shouldFail = false

    val consoles = listOf(
        Console("1", "Nintendo Entertainment System", "NES", 3, "#e53e3e"),
        Console("2", "Super Nintendo", "SNES", 2, "#3182ce"),
    )

    val games = listOf(
        Game(
            id = "1",
            title = "Castlevania",
            consoleId = "1",
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
            consoleId = "1",
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
            consoleId = "1",
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
            consoleId = "2",
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
            consoleId = "2",
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

    override suspend fun searchGames(query: String): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.title.contains(query, ignoreCase = true) })
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> {
        val game = games.find { it.id == gameId }
            ?: return Result.failure(Exception("Game not found"))
        return Result.success(GameDetail(game))
    }

    override suspend fun getRecentGames(): Result<List<Game>> {
        return Result.success(games.take(2))
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> {
        return Result.success(games.filter { it.isFavorite })
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
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
}

class FakeSaveRepository : SaveRepository {
    private val saves = mutableMapOf<String, MutableList<SaveState>>()
    private val autoSaves = mutableMapOf<String, ByteArray>()

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

    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> {
        return autoSaves[gameId]?.let { Result.success(it) }
            ?: Result.failure(Exception("No auto-save found"))
    }
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

    override fun performanceStats(): Flow<Pair<Float, Float>> = flow {
        while (true) {
            emit(59.9f to 16.5f)
            kotlinx.coroutines.delay(500)
        }
    }
}

class FakeFileStorage : FileStorage {
    override fun getGamesDir(): String = "/tmp/spela-test/games"
    override fun getCoresDir(): String = "/tmp/spela-test/cores"
    override fun getSavesDir(): String = "/tmp/spela-test/saves"
    override suspend fun writeFile(path: String, data: ByteArray) {}
    override suspend fun readFile(path: String): ByteArray = ByteArray(0)
    override suspend fun fileExists(path: String): Boolean = false
    override suspend fun deleteFile(path: String) {}
    override suspend fun deleteDirectory(path: String) {}
    override suspend fun getDirectorySize(path: String): Long = 0
}

class FakeKeyMappingRepository : KeyMappingRepository {
    private val mappings = mutableMapOf<String, MutableMap<Int, Int>>()

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

    override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> {
        return mappings[key(consoleId, port)] ?: emptyMap()
    }

    override fun getDefaultMapping(): Map<Int, Int> = emptyMap()
}

private object StubMockEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
    }
}

fun createFakeApiClient(): SpelaApiClient {
    return SpelaApiClient(StubMockEngineFactory, TokenManager())
}
