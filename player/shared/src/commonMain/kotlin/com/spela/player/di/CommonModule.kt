package com.spela.player.di

import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.repository.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.*
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.viewmodel.*
import org.koin.core.qualifier.named
import com.spela.player.util.DefaultDispatcherProvider
import com.spela.player.util.DispatcherProvider
import com.spela.player.domain.controller.AchievementsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    /* Utilities */
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
    single { TokenManager() }

    /* Device */
    single { DeviceManager(get(), get()) }

    /* Repositories */
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<GameRepository> { GameRepositoryImpl(get()) }
    single<SaveRepository> { SaveRepositoryImpl(get()) }
    single<CoreRepository> { CoreRepositoryImpl(get(), get(), get()) }
    single<DownloadRepository> { DownloadRepositoryImpl(get(), get()) }
    single<ServerRepository> { ServerRepositoryImpl(get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(get(), get(), get()) }
    single<KeyMappingRepository> { KeyMappingRepositoryImpl(get(), get(named("platformDefaultMapping"))) }
    single<AchievementsRepository> { AchievementsRepositoryImpl(get()) }
    single { GamepadPortManager(get()) }

    /* Use Cases */
    factory { LoginUseCase(get(), get()) }
    factory { RegisterUseCase(get(), get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }
    factory { GetConsolesUseCase(get()) }
    factory { GetGamesForConsoleUseCase(get()) }
    factory { GetAllGamesUseCase(get()) }
    factory { SearchGamesUseCase(get()) }
    factory { GetGameDetailUseCase(get()) }
    factory { GetRecentGamesUseCase(get()) }
    factory { GetFavoriteGamesUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { PrepareGameUseCase(get(), get()) }
    factory { SaveGameStateUseCase(get()) }
    factory { LoadGameStateUseCase(get()) }
    factory { RestoreSessionUseCase(get(), get(), get()) }

    /* ViewModels */
    factory {
        LoginViewModel(
            loginUseCase = get(),
            registerUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        GameListViewModel(
            getConsolesUseCase = get(),
            getGamesForConsoleUseCase = get(),
            searchGamesUseCase = get(),
            getRecentGamesUseCase = get(),
            getFavoriteGamesUseCase = get(),
            toggleFavoriteUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        GameDetailViewModel(
            getGameDetailUseCase = get(),
            toggleFavoriteUseCase = get(),
            downloadRepository = get(),
            saveRepository = get(),
            apiClient = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        EmulationViewModel(
            prepareGameUseCase = get(),
            saveGameStateUseCase = get(),
            loadGameStateUseCase = get(),
            getGameDetailUseCase = get(),
            preferencesRepository = get(),
            achievementsRepository = get(),
            achievementsController = get(),
            libretroController = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    /* Navigation & UI ViewModels */
    single {
        NavigationViewModel(
            restoreSessionUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        ServerConnectionViewModel(
            serverRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        DownloadsViewModel(
            downloadRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        KeyMappingViewModel(
            keyMappingRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        SettingsViewModel(
            authRepository = get(),
            downloadRepository = get(),
            preferencesRepository = get(),
            gameRepository = get(),
            serverRepository = get(),
            achievementsRepository = get(),
            deviceManager = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
}

/**
 * Platform modules provide:
 * - HttpClientEngineFactory
 * - SpelaApiClient
 * - FileStorage
 * - LibretroController
 * - SqlDriver
 */
expect fun platformModule(): Module
