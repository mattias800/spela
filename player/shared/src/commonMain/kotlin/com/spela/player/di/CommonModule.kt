package com.spela.player.di

import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.PresenceService
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

    /* Presence */
    single { PresenceService(get(), get(), get(), get()) }

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
    single<SocialRepository> { SocialRepositoryImpl(get()) }
    single<RatingRepository> { RatingRepositoryImpl(get()) }
    single<SharedSaveRepository> { SharedSaveRepositoryImpl(get()) }
    single<RelayRepository> { RelayRepositoryImpl(get()) }
    single<CollectionRepository> { CollectionRepositoryImpl(get()) }
    single<StatsRepository> { StatsRepositoryImpl(get()) }
    single<NetplayRepository> { NetplayRepositoryImpl(get()) }
    single { BiosRepository(get(), get()) }
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
    factory { GetPlayLaterGamesUseCase(get()) }
    factory { TogglePlayLaterUseCase(get()) }
    factory { PrepareGameUseCase(get(), get()) }
    factory { SaveGameStateUseCase(get()) }
    factory { LoadGameStateUseCase(get()) }
    factory { RestoreSessionUseCase(get(), get(), get()) }
    factory { GetOnlineUsersUseCase(get()) }
    factory { GetActivityFeedUseCase(get()) }
    factory { GetPublicProfileUseCase(get()) }
    factory { GetMyCollectionsUseCase(get()) }
    factory { GetPublicCollectionsUseCase(get()) }
    factory { GetCollectionDetailUseCase(get()) }
    factory { CreateCollectionUseCase(get()) }
    factory { UpdateCollectionUseCase(get()) }
    factory { DeleteCollectionUseCase(get()) }
    factory { AddGameToCollectionUseCase(get()) }
    factory { RemoveGameFromCollectionUseCase(get()) }
    factory { GetMostPlayedGamesUseCase(get()) }
    factory { GetMostActivePlayersUseCase(get()) }

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
            getPlayLaterGamesUseCase = get(),
            togglePlayLaterUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    factory {
        GameDetailViewModel(
            getGameDetailUseCase = get(),
            toggleFavoriteUseCase = get(),
            togglePlayLaterUseCase = get(),
            downloadRepository = get(),
            saveRepository = get(),
            ratingRepository = get(),
            sharedSaveRepository = get(),
            apiClient = get(),
            dispatchers = get(),
            scope = get(),
        )
    }
    single {
        EmulationViewModel(
            prepareGameUseCase = get(),
            saveGameStateUseCase = get(),
            loadGameStateUseCase = get(),
            getGameDetailUseCase = get(),
            preferencesRepository = get(),
            achievementsRepository = get(),
            achievementsController = get(),
            libretroController = get(),
            secondaryDisplay = get(),
            presenceService = get(),
            relayRepository = get(),
            apiClient = get(),
            engineFactory = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        SocialViewModel(
            getOnlineUsersUseCase = get(),
            getActivityFeedUseCase = get(),
            getPublicProfileUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        RelaysViewModel(
            relayRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        RelayDetailViewModel(
            relayRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        NetplayViewModel(
            netplayRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        NetplayLobbyViewModel(
            netplayRepository = get(),
            authRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        StatsViewModel(
            getMostPlayedGamesUseCase = get(),
            getMostActivePlayersUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        CollectionsViewModel(
            getMyCollectionsUseCase = get(),
            getPublicCollectionsUseCase = get(),
            getCollectionDetailUseCase = get(),
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
