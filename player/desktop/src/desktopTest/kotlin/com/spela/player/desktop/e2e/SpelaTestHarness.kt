package com.spela.player.desktop.e2e

import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.SyncEngine
import com.spela.player.domain.usecase.*
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
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
        apiClient = fakeApiClient,
        dispatchers = dispatchers,
        scope = scope,
        biosRepository = biosRepo,
        cheatRepository = cheatRepo,
        sessionRepository = sessionRepo,
        exploreRepository = exploreRepo,
    )

    private val stubEngineFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
    }
    val presenceService = PresenceService(fakeApiClient, stubEngineFactory, dispatchers, scope)

    val keyMappingRepo = FakeKeyMappingRepository()
    val gamepadPortManager = GamepadPortManager(keyMappingRepo)

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

    val emulationViewModel = EmulationViewModel(
        prepareGameUseCase = PrepareGameUseCase(downloadRepo, coreRepo),
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        preferencesRepository = preferencesRepo,
        achievementsRepository = FakeAchievementsRepository(),
        achievementsController = FakeAchievementsController(),
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
        dispatchers = dispatchers,
        scope = scope,
    )

    val socialRepo = FakeSocialRepository()

    val socialViewModel = SocialViewModel(
        getOnlineUsersUseCase = GetOnlineUsersUseCase(socialRepo),
        getActivityFeedUseCase = GetActivityFeedUseCase(socialRepo),
        getPublicProfileUseCase = GetPublicProfileUseCase(socialRepo),
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

    @Composable
    fun App() {
        androidx.compose.runtime.CompositionLocalProvider(
            com.spela.player.presentation.ui.components.LocalAnimationsEnabled provides false,
        ) {
        SpelaApp(
            navigationViewModel = navigationViewModel,
            serverConnectionViewModel = serverConnectionViewModel,
            loginViewModel = loginViewModel,
            gameListViewModel = gameListViewModel,
            gameDetailViewModel = gameDetailViewModel,
            emulationViewModel = emulationViewModel,
            libretroController = libretroController,
            downloadsViewModel = downloadsViewModel,
            settingsViewModel = settingsViewModel,
            keyMappingViewModel = keyMappingViewModel,
            gamepadConfigViewModel = gamepadConfigViewModel,
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
            sessionDetailViewModel = sessionDetailViewModel,
            topListsViewModel = topListsViewModel,
            exploreViewModel = exploreViewModel,
            gamepadPortManager = gamepadPortManager,
        )
        }
    }
}
