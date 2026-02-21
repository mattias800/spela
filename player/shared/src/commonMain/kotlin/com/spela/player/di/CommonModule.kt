package com.spela.player.di

import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.ScrapeService
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.interceptor.AuthEventBus
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
import com.spela.player.domain.controller.ScreenshotCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    /* Utilities */
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
    single { TokenManager() }
    single { AuthEventBus() }

    /* Device */
    single { DeviceManager(get(), get()) }

    /* Scraping */
    single { ScrapeService(get(), get(), get()) }

    /* Connectivity */
    single { ConnectivityMonitor(get(), get(), get()) }

    /* Sync Engine */
    single { SyncEngine(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    /* Presence */
    single { PresenceService(get(), get(), get(), get(), get()) }

    /* Repositories */
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<GameRepository> { GameRepositoryImpl(get(), get(), get()) }
    single<SaveRepository> { SaveRepositoryImpl(get(), get(), get(), get()) }
    single<SaveDataRepository> { SaveDataRepositoryImpl(get(), get(), get(), get()) }
    single<CoreRepository> { CoreRepositoryImpl(get(), get(), get()) }
    single<DownloadRepository> { DownloadRepositoryImpl(get(), get()) }
    single<ServerRepository> { ServerRepositoryImpl(get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(get(), get(), get(), get()) }
    single<KeyMappingRepository> { KeyMappingRepositoryImpl(get(), get(named("platformDefaultMapping"))) }
    single<AchievementsRepository> { AchievementsRepositoryImpl(get()) }
    single<SocialRepository> { SocialRepositoryImpl(get()) }
    single<RatingRepository> { RatingRepositoryImpl(get()) }
    single<SharedSaveRepository> { SharedSaveRepositoryImpl(get()) }
    single<RelayRepository> { RelayRepositoryImpl(get()) }
    single<CollectionRepository> { CollectionRepositoryImpl(get()) }
    single<StatsRepository> { StatsRepositoryImpl(get()) }
    single<NetplayRepository> { NetplayRepositoryImpl(get()) }
    single<ChallengeRepository> { ChallengeRepositoryImpl(get()) }
    single<GameStatsRepository> { GameStatsRepositoryImpl(get()) }
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
    factory { GetGameStatsUseCase(get()) }
    factory { GetGameAchievementsUseCase(get()) }
    factory { GetAchievementProgressUseCase(get()) }
    factory { GetAchievementTimelineUseCase(get()) }
    factory { GetAchievementLeaderboardUseCase(get()) }
    factory { GetUserStatsUseCase(get()) }
    factory { GetRecentAchievementsUseCase(get()) }

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
            getUserStatsUseCase = get(),
            getRecentAchievementsUseCase = get(),
            challengeRepository = get(),
            scrapeService = get(),
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
            saveDataRepository = get(),
            ratingRepository = get(),
            sharedSaveRepository = get(),
            getMyCollectionsUseCase = get(),
            addGameToCollectionUseCase = get(),
            createCollectionUseCase = get(),
            getGameStatsUseCase = get(),
            gameStatsRepository = get(),
            challengeRepository = get(),
            relayRepository = get(),
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
            challengeRepository = get(),
            saveDataRepository = get(),
            connectivityMonitor = get(),
            screenshotCapture = getOrNull<ScreenshotCapture>(),
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
        ChallengeListViewModel(
            challengeRepository = get(),
            getConsolesUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        ChallengeDetailViewModel(
            challengeRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        StatsViewModel(
            getMostPlayedGamesUseCase = get(),
            getMostActivePlayersUseCase = get(),
            getUserStatsUseCase = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        CollectionsViewModel(
            getMyCollectionsUseCase = get(),
            getPublicCollectionsUseCase = get(),
            getCollectionDetailUseCase = get(),
            createCollectionUseCase = get(),
            updateCollectionUseCase = get(),
            deleteCollectionUseCase = get(),
            removeGameFromCollectionUseCase = get(),
            authRepository = get(),
            scrapeService = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    factory {
        SaveDataViewModel(
            saveDataRepository = get(),
            dispatchers = get(),
            scope = get(),
        )
    }

    /* Navigation & UI ViewModels */
    single {
        NavigationViewModel(
            restoreSessionUseCase = get(),
            connectivityMonitor = get(),
            syncEngine = get(),
            authEventBus = get(),
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
            preferencesRepository = get(),
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
            keyMappingRepository = get(),
            deviceManager = get(),
            syncEngine = get(),
            connectivityMonitor = get(),
            apiClient = get(),
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
