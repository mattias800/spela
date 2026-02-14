package com.spela.player.desktop.e2e

import androidx.compose.runtime.Composable
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.PresenceService
import com.spela.player.domain.usecase.*
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
import com.spela.player.platform.secondarydisplay.DesktopSecondaryDisplay
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
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
import kotlinx.coroutines.test.TestDispatcher

/**
 * Test harness that creates the full Spela app UI backed by fake repositories.
 * This allows Compose UI tests to exercise the complete app without any real
 * network, file system, or native library dependencies.
 */
class SpelaTestHarness(
    val testDispatcher: TestDispatcher,
) {
    val dispatchers = createTestDispatchers(testDispatcher)
    val scope = CoroutineScope(testDispatcher)

    val serverRepo = FakeServerRepository()
    val authRepo = FakeAuthRepository()
    val gameRepo = FakeGameRepository()
    val downloadRepo = FakeDownloadRepository()
    val saveRepo = FakeSaveRepository()
    val coreRepo = FakeCoreRepository()
    val libretroController = FakeLibretroController()
    val secondaryDisplay: PlatformSecondaryDisplay = DesktopSecondaryDisplay()

    private val fakeApiClient = createFakeApiClient()
    private val testDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        SpelaDatabase.Schema.create(it)
    }
    private val testDatabase = SpelaDatabase(testDriver)
    val deviceManager = DeviceManager(testDatabase, fakeApiClient)

    val navigationViewModel = NavigationViewModel(
        restoreSessionUseCase = RestoreSessionUseCase(authRepo, serverRepo, fakeApiClient),
        dispatchers = dispatchers,
        scope = scope,
    )

    init {
        // Flush restoreSession() so isRestoringSession becomes false before tests run.
        // With no active server, this resolves to NoSession → ServerConnection screen,
        // matching the original default NavigationState behavior.
        testDispatcher.scheduler.advanceUntilIdle()
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

    val gameListViewModel = GameListViewModel(
        getConsolesUseCase = GetConsolesUseCase(gameRepo),
        getGamesForConsoleUseCase = GetGamesForConsoleUseCase(gameRepo),
        searchGamesUseCase = SearchGamesUseCase(gameRepo),
        getRecentGamesUseCase = GetRecentGamesUseCase(gameRepo),
        getFavoriteGamesUseCase = GetFavoriteGamesUseCase(gameRepo),
        toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepo),
        getPlayLaterGamesUseCase = GetPlayLaterGamesUseCase(gameRepo),
        togglePlayLaterUseCase = TogglePlayLaterUseCase(gameRepo),
        dispatchers = dispatchers,
        scope = scope,
    )

    val ratingRepo = FakeRatingRepository()
    val sharedSaveRepo = FakeSharedSaveRepository()

    val gameDetailViewModel = GameDetailViewModel(
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepo),
        togglePlayLaterUseCase = TogglePlayLaterUseCase(gameRepo),
        downloadRepository = downloadRepo,
        saveRepository = saveRepo,
        ratingRepository = ratingRepo,
        sharedSaveRepository = sharedSaveRepo,
        apiClient = fakeApiClient,
        dispatchers = dispatchers,
        scope = scope,
    )

    private val stubEngineFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
    }
    val presenceService = PresenceService(fakeApiClient, stubEngineFactory, dispatchers, scope)

    val emulationViewModel = EmulationViewModel(
        prepareGameUseCase = PrepareGameUseCase(downloadRepo, coreRepo),
        saveGameStateUseCase = SaveGameStateUseCase(saveRepo),
        loadGameStateUseCase = LoadGameStateUseCase(saveRepo),
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        preferencesRepository = FakePreferencesRepository(),
        achievementsRepository = FakeAchievementsRepository(),
        achievementsController = FakeAchievementsController(),
        libretroController = libretroController,
        secondaryDisplay = secondaryDisplay,
        presenceService = presenceService,
        dispatchers = dispatchers,
        scope = scope,
    )

    val downloadsViewModel = DownloadsViewModel(
        downloadRepository = downloadRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val settingsViewModel = SettingsViewModel(
        authRepository = authRepo,
        downloadRepository = downloadRepo,
        preferencesRepository = FakePreferencesRepository(),
        gameRepository = gameRepo,
        serverRepository = serverRepo,
        achievementsRepository = FakeAchievementsRepository(),
        deviceManager = deviceManager,
        dispatchers = dispatchers,
        scope = scope,
    )

    val keyMappingRepo = object : KeyMappingRepository {
        override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? = null
        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> = emptyMap()
        override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {}
        override suspend fun resetToDefault(consoleId: String, port: Int) {}
        override fun getDefaultMapping(): Map<Int, Int> = emptyMap()
    }

    val keyMappingViewModel = KeyMappingViewModel(
        keyMappingRepository = keyMappingRepo,
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

    @Composable
    fun App() {
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
            socialViewModel = socialViewModel,
            secondaryDisplay = secondaryDisplay,
            presenceService = presenceService,
        )
    }
}
