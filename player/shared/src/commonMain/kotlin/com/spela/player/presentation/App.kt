package com.spela.player.presentation

import androidx.compose.runtime.Composable
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.RelayDetailViewModel
import com.spela.player.presentation.viewmodel.RelaysViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import com.spela.player.presentation.viewmodel.SaveDataViewModel
import com.spela.player.presentation.viewmodel.NetplayViewModel
import com.spela.player.presentation.viewmodel.ChallengeDetailViewModel
import com.spela.player.presentation.viewmodel.ChallengeListViewModel
import com.spela.player.presentation.viewmodel.CollectionsViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.presentation.viewmodel.StatsViewModel
import com.spela.player.libretro.GamepadPortManager
import org.koin.compose.koinInject

/**
 * Root composable for the Spela player app.
 * Delegates to [SpelaApp] which handles full navigation, theming, and all screens.
 */
@Composable
fun App() {
    val navigationViewModel: NavigationViewModel = koinInject()
    val serverConnectionViewModel: ServerConnectionViewModel = koinInject()
    val loginViewModel: LoginViewModel = koinInject()
    val gameListViewModel: GameListViewModel = koinInject()
    val gameDetailViewModel: GameDetailViewModel = koinInject()
    val emulationViewModel: EmulationViewModel = koinInject()
    val libretroController: LibretroController = koinInject()
    val downloadsViewModel: DownloadsViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()
    val keyMappingViewModel: KeyMappingViewModel = koinInject()
    val socialViewModel: SocialViewModel = koinInject()
    val relaysViewModel: RelaysViewModel = koinInject()
    val relayDetailViewModel: RelayDetailViewModel = koinInject()
    val netplayViewModel: NetplayViewModel = koinInject()
    val netplayLobbyViewModel: NetplayLobbyViewModel = koinInject()
    val statsViewModel: StatsViewModel = koinInject()
    val collectionsViewModel: CollectionsViewModel = koinInject()
    val challengeListViewModel: ChallengeListViewModel = koinInject()
    val challengeDetailViewModel: ChallengeDetailViewModel = koinInject()
    val secondaryDisplay: PlatformSecondaryDisplay = koinInject()
    val presenceService: PresenceService = koinInject()
    val connectivityMonitor: ConnectivityMonitor = koinInject()
    val saveDataViewModel: SaveDataViewModel = koinInject()
    val navigationEventBus: NavigationEventBus = koinInject()
    val gamepadPortManager: GamepadPortManager = koinInject()

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
        relaysViewModel = relaysViewModel,
        relayDetailViewModel = relayDetailViewModel,
        netplayViewModel = netplayViewModel,
        netplayLobbyViewModel = netplayLobbyViewModel,
        statsViewModel = statsViewModel,
        collectionsViewModel = collectionsViewModel,
        challengeListViewModel = challengeListViewModel,
        challengeDetailViewModel = challengeDetailViewModel,
        secondaryDisplay = secondaryDisplay,
        presenceService = presenceService,
        connectivityMonitor = connectivityMonitor,
        saveDataViewModel = saveDataViewModel,
        navigationEventBus = navigationEventBus,
        gamepadPortManager = gamepadPortManager,
    )
}
