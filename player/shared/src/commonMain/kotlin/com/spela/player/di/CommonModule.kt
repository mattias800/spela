package com.spela.player.di

import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.repository.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.*
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.viewmodel.*
import com.spela.player.util.DefaultDispatcherProvider
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    /* Utilities */
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
    single { TokenManager() }

    /* Repositories */
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<GameRepository> { GameRepositoryImpl(get()) }
    single<SaveRepository> { SaveRepositoryImpl(get()) }
    single<CoreRepository> { CoreRepositoryImpl(get(), get()) }
    single<DownloadRepository> { DownloadRepositoryImpl(get(), get()) }
    single<ServerRepository> { ServerRepositoryImpl() }

    /* Use Cases */
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
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
            libretroController = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    /* Navigation & UI ViewModels */
    single { NavigationViewModel() }
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
        SettingsViewModel(
            authRepository = get(),
            downloadRepository = get(),
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
