package com.spela.player.desktop.e2e

import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.repository.ControllerStyleOverrideRepositoryImpl
import com.spela.player.data.repository.GamepadMappingRepositoryImpl
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.SyncEngine
import com.spela.player.domain.usecase.*
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
import com.spela.player.presentation.ui.SpelaAppDependencies
import com.spela.player.platform.secondarydisplay.DesktopSecondaryDisplay
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.viewmodel.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher

/**
 * Test harness that creates the full Spela app UI backed by fake repositories.
 * This allows Compose UI tests to exercise the complete app without any real
 * network, file system, or native library dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpelaTestHarness(
    val testDispatcher: TestDispatcher,
) {
    val dispatchers = createTestDispatchers(testDispatcher)
    val scope = CoroutineScope(testDispatcher)

    val serverRepo = FakeServerRepository()
    val authRepo = FakeAuthRepository()
    val gameRepo = FakeGameRepository()
    val federationRepo = FakeFederationRepository()
    val downloadRepo = FakeDownloadRepository()
    val coreRepo = FakeCoreRepository()
    val libretroController = FakeLibretroController()
    val secondaryDisplay: PlatformSecondaryDisplay = DesktopSecondaryDisplay()

    private val fakeApiClient = createFakeApiClient()
    private val testDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        SpelaDatabase.Schema.create(it)
    }
    private val testDatabase = SpelaDatabase(testDriver)
    val deviceManager = DeviceManager(testDatabase, fakeApiClient)
    val saveDataRepo = FakeSaveDataRepository()
    val connectivityMonitor = ConnectivityMonitor(fakeApiClient, dispatchers, scope)
    val syncEngine = SyncEngine(
        connectivityMonitor = connectivityMonitor,
        preferencesRepository = FakePreferencesRepository(),
        gameRepository = FakeGameRepository(),
        dispatchers = dispatchers,
        scope = scope,
    )

    val navigationViewModel = NavigationViewModel(
        restoreSessionUseCase = RestoreSessionUseCase(authRepo, serverRepo, fakeApiClient),
        connectivityMonitor = connectivityMonitor,
        syncEngine = syncEngine,
        dispatchers = dispatchers,
        scope = scope,
    )

    init {
        // Flush restoreSession() so isRestoringSession becomes false before tests run.
        // With no active server, this resolves to NoSession → ServerConnection screen,
        // matching the original default NavigationState behavior.
        // Use bounded advance instead of advanceUntilIdle() — if ViewModels with
        // perpetual loops (e.g. GamepadConfigViewModel) are ever reordered above this
        // init block, advanceUntilIdle() would hang forever.
        testDispatcher.scheduler.advanceTimeBy(2_000)
        testDispatcher.scheduler.runCurrent()
    }

    val serverConnectionViewModel = ServerConnectionViewModel(
        serverRepository = serverRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val loginViewModel = LoginViewModel(
        loginUseCase = LoginUseCase(authRepo, deviceManager),
        registerUseCase = RegisterUseCase(authRepo, deviceManager),
        dispatchers = dispatchers,
        scope = scope,
    )

    val preferencesRepo = FakePreferencesRepository()
    val ratingRepo = FakeRatingRepository()
    val sharedSaveRepo = FakeSharedSaveRepository()
    val collectionRepo = FakeCollectionRepository()

    val gameStatsRepo = FakeGameStatsRepository()
    val challengeRepo = FakeChallengeRepository()
    val biosRepo = FakeBiosRepository(fakeApiClient, FakeFileStorage())
    val cheatRepo = FakeCheatRepository()
    val sessionRepo = FakeSessionRepository()
    val exploreRepo = FakeExploreRepository()
    val searchRepo = FakeSearchRepository()

    private val scrapeService = com.spela.player.data.remote.ScrapeService(fakeApiClient, dispatchers, scope)

    val gameListViewModel = GameListViewModel(
        getConsolesUseCase = GetConsolesUseCase(gameRepo),
        getGamesForConsoleUseCase = GetGamesForConsoleUseCase(gameRepo),
        searchGamesUseCase = SearchGamesUseCase(gameRepo),
        getRecentGamesUseCase = GetRecentGamesUseCase(gameRepo),
        getFavoriteGamesUseCase = GetFavoriteGamesUseCase(gameRepo),
        toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepo),
        getPlayLaterGamesUseCase = GetPlayLaterGamesUseCase(gameRepo),
        togglePlayLaterUseCase = TogglePlayLaterUseCase(gameRepo),
        getUserStatsUseCase = GetUserStatsUseCase(gameStatsRepo),
        getRecentAchievementsUseCase = GetRecentAchievementsUseCase(gameStatsRepo),
        challengeRepository = challengeRepo,
        scrapeService = scrapeService,
        gameRepository = gameRepo,
        dispatchers = dispatchers,
        scope = scope,
        biosRepository = biosRepo,
        authRepository = authRepo,
    )
    val sharedSessionRepo = FakeSharedSessionRepository()

    val gameDetailViewModel = GameDetailViewModel(
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepo),
        togglePlayLaterUseCase = TogglePlayLaterUseCase(gameRepo),
        downloadRepository = downloadRepo,
        ratingRepository = ratingRepo,
        sharedSaveRepository = sharedSaveRepo,
        getMyCollectionsUseCase = GetMyCollectionsUseCase(collectionRepo),
        addGameToCollectionUseCase = AddGameToCollectionUseCase(collectionRepo),
        createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
        getGameStatsUseCase = GetGameStatsUseCase(gameStatsRepo),
        gameStatsRepository = gameStatsRepo,
        challengeRepository = challengeRepo,
        sharedSessionRepository = sharedSessionRepo,
        gameRepository = gameRepo,
        preferencesRepository = preferencesRepo,
        apiClient = fakeApiClient,
        scrapeService = scrapeService,
        dispatchers = dispatchers,
        scope = scope,
        biosRepository = biosRepo,
        cheatRepository = cheatRepo,
        sessionRepository = sessionRepo,
        exploreRepository = exploreRepo,
    )

    val connectedServersViewModel = ConnectedServersViewModel(
        federationRepository = federationRepo,
        dispatchers = dispatchers,
        scope = scope,
    )
    val remoteGameDetailViewModel = RemoteGameDetailViewModel(
        federationRepository = federationRepo,
        authRepository = authRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    private val stubEngineFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
    }
    val presenceService = PresenceService(fakeApiClient, stubEngineFactory, dispatchers, scope)

    val keyMappingRepo = FakeKeyMappingRepository()
    val gamepadPortManager = GamepadPortManager(
        keyMappingRepo,
        scope,
        nowMs = { testDispatcher.scheduler.currentTime },
        gamepadMappingRepository = GamepadMappingRepositoryImpl(testDatabase),
    )

    // Achievement fakes are exposed so tests can drive the popup wiring:
    // set `achievementsRepo.raTokenResult = Result.success(...)` to enable
    // the VM's event collector at game launch, then call
    // `achievementsCtrl.emitEvent(...)` to fire an unlock.
    val achievementsRepo = FakeAchievementsRepository()
    val achievementsCtrl = FakeAchievementsController()

    private val emulationState = MutableStateFlow(EmulationState())

    private val saveManager = SaveManager(
        saveDataRepository = saveDataRepo,
        connectivityMonitor = connectivityMonitor,
        libretroController = libretroController,
        screenshotCapture = null,
        _state = emulationState,
        dispatchers = dispatchers,
        scope = scope,
        sessionRepository = sessionRepo,
        fileStorage = HarnessFileStorage(),
        pendingUploadRepository = HarnessPendingSaveUploadRepository(),
    )
    private val challengeManager = ChallengeManager(
        challengeRepository = challengeRepo,
        libretroController = libretroController,
        screenshotCapture = null,
        _state = emulationState,
        dispatchers = dispatchers,
        scope = scope,
    )
    private val netplayManager = NetplayManager(
        sharedSessionRepository = sharedSessionRepo,
        libretroController = libretroController,
        apiClient = fakeApiClient,
        engineFactory = stubEngineFactory,
        _state = emulationState,
        dispatchers = dispatchers,
        scope = scope,
    )

    val coreUpdateService = com.spela.player.data.repository.CoreUpdateService(
        coreRepository = coreRepo,
        preferencesRepository = preferencesRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val emulationViewModel = EmulationViewModel(
        prepareGameUseCase = PrepareGameUseCase(downloadRepo, coreRepo, coreUpdateService),
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        preferencesRepository = preferencesRepo,
        achievementsRepository = achievementsRepo,
        achievementsController = achievementsCtrl,
        libretroController = libretroController,
        secondaryDisplay = secondaryDisplay,
        presenceService = presenceService,
        gamepadPortManager = gamepadPortManager,
        saveManager = saveManager,
        challengeManager = challengeManager,
        netplayManager = netplayManager,
        _state = emulationState,
        dispatchers = dispatchers,
        scope = scope,
        biosRepository = biosRepo,
        cheatRepository = cheatRepo,
    )

    val downloadsViewModel = DownloadsViewModel(
        downloadRepository = downloadRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val settingsViewModel = SettingsViewModel(
        authRepository = authRepo,
        downloadRepository = downloadRepo,
        preferencesRepository = preferencesRepo,
        gameRepository = gameRepo,
        serverRepository = serverRepo,
        achievementsRepository = FakeAchievementsRepository(),
        keyMappingRepository = keyMappingRepo,
        deviceManager = deviceManager,
        syncEngine = syncEngine,
        connectivityMonitor = connectivityMonitor,
        apiClient = fakeApiClient,
        dispatchers = dispatchers,
        scope = scope,
    )

    val keyMappingViewModel = KeyMappingViewModel(
        keyMappingRepository = keyMappingRepo,
        preferencesRepository = preferencesRepo,
        gamepadPortManager = gamepadPortManager,
        dispatchers = dispatchers,
        scope = scope,
    )

    val gamepadConfigViewModel = GamepadConfigViewModel(
        gamepadPortManager = gamepadPortManager,
        styleOverrideRepository = ControllerStyleOverrideRepositoryImpl(testDatabase),
        dispatchers = dispatchers,
        scope = scope,
        nowMs = { testDispatcher.scheduler.currentTime },
    )

    val gamepadMappingViewModel = GamepadMappingViewModel(
        gamepadMappingRepository = GamepadMappingRepositoryImpl(testDatabase),
        gamepadPortManager = gamepadPortManager,
        preferencesRepository = preferencesRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val socialRepo = FakeSocialRepository()

    val socialViewModel = SocialViewModel(
        getOnlineUsersUseCase = GetOnlineUsersUseCase(socialRepo),
        getActivityFeedUseCase = GetActivityFeedUseCase(socialRepo),
        getPublicProfileUseCase = GetPublicProfileUseCase(socialRepo),
        getPlayHeatmapUseCase = GetPlayHeatmapUseCase(socialRepo),
        getPublicShowcaseUseCase = GetPublicShowcaseUseCase(socialRepo),
        getUnlockedAchievementsUseCase = GetUnlockedAchievementsUseCase(socialRepo),
        updateShowcaseUseCase = UpdateShowcaseUseCase(socialRepo),
        authRepository = authRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val sharedSessionsViewModel = SharedSessionsViewModel(
        sharedSessionRepository = sharedSessionRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val userRepo = FakeUserRepository()

    val sharedSessionDetailViewModel = SharedSessionDetailViewModel(
        sharedSessionRepository = sharedSessionRepo,
        userRepository = userRepo,
        dispatchers = dispatchers,
        scope = scope,
        sessionRepository = sessionRepo,
    )

    val sessionDetailViewModel = SessionDetailViewModel(
        sessionRepository = sessionRepo,
        dispatchers = dispatchers,
        scope = scope,
        cheatRepository = cheatRepo,
        gameRepository = gameRepo,
    )

    val statsRepo = FakeStatsRepository()

    val statsViewModel = StatsViewModel(
        getMostPlayedGamesUseCase = GetMostPlayedGamesUseCase(statsRepo),
        getMostActivePlayersUseCase = GetMostActivePlayersUseCase(statsRepo),
        getUserStatsUseCase = GetUserStatsUseCase(gameStatsRepo),
        getMeshStatsUseCase = GetMeshStatsUseCase(federationRepo),
        getMeshAchieversUseCase = GetMeshAchieversUseCase(federationRepo),
        dispatchers = dispatchers,
        scope = scope,
    )

    val collectionsViewModel = CollectionsViewModel(
        getMyCollectionsUseCase = GetMyCollectionsUseCase(collectionRepo),
        getPublicCollectionsUseCase = GetPublicCollectionsUseCase(collectionRepo),
        getCollectionDetailUseCase = GetCollectionDetailUseCase(collectionRepo),
        createCollectionUseCase = CreateCollectionUseCase(collectionRepo),
        updateCollectionUseCase = UpdateCollectionUseCase(collectionRepo),
        deleteCollectionUseCase = DeleteCollectionUseCase(collectionRepo),
        removeGameFromCollectionUseCase = RemoveGameFromCollectionUseCase(collectionRepo),
        authRepository = authRepo,
        scrapeService = scrapeService,
        dispatchers = dispatchers,
        scope = scope,
    )

    val challengeListViewModel = ChallengeListViewModel(
        challengeRepository = challengeRepo,
        getConsolesUseCase = GetConsolesUseCase(gameRepo),
        dispatchers = dispatchers,
        scope = scope,
    )

    val challengeDetailViewModel = ChallengeDetailViewModel(
        challengeRepository = challengeRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val netplayRepo = FakeNetplayRepository()

    val netplayViewModel = NetplayViewModel(
        netplayRepository = netplayRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val netplayLobbyViewModel = NetplayLobbyViewModel(
        netplayRepository = netplayRepo,
        authRepository = authRepo,
        userRepository = userRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val topListsViewModel = TopListsViewModel(
        gameRepository = gameRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val exploreViewModel = ExploreViewModel(
        exploreRepository = exploreRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val globalSearchViewModel = GlobalSearchViewModel(
        searchRepository = searchRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    @Composable
    fun App(animationsEnabled: Boolean = false) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.spela.player.presentation.ui.components.LocalAnimationsEnabled provides animationsEnabled,
        ) {
        SpelaApp(
            SpelaAppDependencies(
                navigationViewModel = navigationViewModel,
                serverConnectionViewModel = serverConnectionViewModel,
                loginViewModel = loginViewModel,
                gameListViewModel = gameListViewModel,
                gameDetailViewModel = gameDetailViewModel,
                connectedServersViewModel = connectedServersViewModel,
                remoteGameDetailViewModel = remoteGameDetailViewModel,
                emulationViewModel = emulationViewModel,
                libretroController = libretroController,
                downloadsViewModel = downloadsViewModel,
                settingsViewModel = settingsViewModel,
                keyMappingViewModel = keyMappingViewModel,
                gamepadConfigViewModel = gamepadConfigViewModel,
                gamepadMappingViewModel = gamepadMappingViewModel,
                socialViewModel = socialViewModel,
                sharedSessionsViewModel = sharedSessionsViewModel,
                sharedSessionDetailViewModel = sharedSessionDetailViewModel,
                netplayViewModel = netplayViewModel,
                netplayLobbyViewModel = netplayLobbyViewModel,
                statsViewModel = statsViewModel,
                collectionsViewModel = collectionsViewModel,
                challengeListViewModel = challengeListViewModel,
                challengeDetailViewModel = challengeDetailViewModel,
                secondaryDisplay = secondaryDisplay,
                presenceService = presenceService,
                connectivityMonitor = connectivityMonitor,
                downloadRepository = downloadRepo,
                preferencesRepository = preferencesRepo,
                sessionDetailViewModel = sessionDetailViewModel,
                topListsViewModel = topListsViewModel,
                exploreViewModel = exploreViewModel,
                gamepadPortManager = gamepadPortManager,
                globalSearchViewModel = globalSearchViewModel,
            ),
        )
        }
    }
}

/** Real-FS FileStorage backed by a per-harness temp directory. Used so
 *  SaveManager's file-streaming load/save paths in #798 / #804 phase 6
 *  actually round-trip through disk in the harness — without this the
 *  load tests in InGameOverlayTest / SaveLoadStateTest can't see
 *  unserialize fire. The temp dir is leaked at test-class scope and
 *  cleaned up by the JVM's tmp-dir cleaner. */
private class HarnessFileStorage : com.spela.player.util.FileStorage {
    private val root: java.io.File = kotlin.io.path.createTempDirectory("spela-harness-").toFile()
    init { root.deleteOnExit() }
    override fun getGamesDir(): String = ensure("games")
    override fun getCoresDir(): String = ensure("cores")
    override fun getSavesDir(): String = ensure("saves")
    override fun getBiosDir(): String = ensure("bios")
    private fun ensure(name: String): String =
        java.io.File(root, name).apply { mkdirs() }.absolutePath
    override suspend fun createDirectory(path: String) { java.io.File(path).mkdirs() }
    override suspend fun writeFile(path: String, data: ByteArray) {
        val f = java.io.File(path)
        f.parentFile?.mkdirs()
        f.writeBytes(data)
    }
    override suspend fun readFile(path: String): ByteArray =
        runCatching { java.io.File(path).readBytes() }.getOrDefault(byteArrayOf())
    override suspend fun fileExists(path: String): Boolean = java.io.File(path).exists()
    override suspend fun deleteFile(path: String) { java.io.File(path).delete() }
    override suspend fun deleteDirectory(path: String) { java.io.File(path).deleteRecursively() }
    override suspend fun getDirectorySize(path: String): Long = 0
    override suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) {
        val f = java.io.File(path)
        f.parentFile?.mkdirs()
        val out = f.outputStream()
        try {
            writer { bytes, offset, length -> out.write(bytes, offset, length) }
        } finally {
            out.close()
        }
    }
    override suspend fun getFileSize(path: String): Long = java.io.File(path).length()
    override suspend fun listFiles(path: String): List<String> =
        java.io.File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    override suspend fun isDirectory(path: String): Boolean = java.io.File(path).isDirectory
    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0L
    override suspend fun extractTarFile(tarPath: String, destDir: String) {}
    override suspend fun sha256File(path: String): String? = null
}

/** In-memory pending-upload queue for the harness. SaveManager
 *  enqueues here on the deferred-sync path; the harness doesn't
 *  exercise drain so the queue is essentially write-only in tests. */
private class HarnessPendingSaveUploadRepository :
    com.spela.player.domain.repository.PendingSaveUploadRepository {
    private val rows = mutableListOf<com.spela.player.domain.model.PendingSaveUpload>()
    private var nextId = 1L
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
        rows.add(
            com.spela.player.domain.model.PendingSaveUpload(
                id, sessionId, kind, slot, name, coreName, compression,
                filePath, fileSize, screenshotPath, createdAt, 0, null,
            ),
        )
        return id
    }
    override suspend fun getAll() = rows.toList()
    override suspend fun getForSession(sessionId: String) =
        rows.filter { it.sessionId == sessionId }
    override suspend fun getById(id: Long) = rows.find { it.id == id }
    override suspend fun count(): Long = rows.size.toLong()
    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }
    override suspend fun markRetry(id: Long, lastError: String?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(
            retryCount = rows[idx].retryCount + 1,
            lastError = lastError,
        )
    }
}
