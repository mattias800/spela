package com.spela.player.desktop.e2e

import androidx.compose.runtime.Composable
import com.spela.player.domain.usecase.*
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
import com.spela.player.presentation.viewmodel.*
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

    val navigationViewModel = NavigationViewModel()

    val serverConnectionViewModel = ServerConnectionViewModel(
        serverRepository = serverRepo,
        dispatchers = dispatchers,
        scope = scope,
    )

    val loginViewModel = LoginViewModel(
        loginUseCase = LoginUseCase(authRepo),
        registerUseCase = RegisterUseCase(authRepo),
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
        dispatchers = dispatchers,
        scope = scope,
    )

    val gameDetailViewModel = GameDetailViewModel(
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepo),
        downloadRepository = downloadRepo,
        saveRepository = saveRepo,
        apiClient = createFakeApiClient(),
        dispatchers = dispatchers,
        scope = scope,
    )

    val emulationViewModel = EmulationViewModel(
        prepareGameUseCase = PrepareGameUseCase(downloadRepo, coreRepo),
        saveGameStateUseCase = SaveGameStateUseCase(saveRepo),
        loadGameStateUseCase = LoadGameStateUseCase(saveRepo),
        getGameDetailUseCase = GetGameDetailUseCase(gameRepo),
        libretroController = libretroController,
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
        )
    }
}
