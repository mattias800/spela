package com.spela.player.presentation.ui

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.ScrapeService
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.viewmodel.ChallengeDetailViewModel
import com.spela.player.presentation.viewmodel.ChallengeListViewModel
import com.spela.player.presentation.viewmodel.CollectionsViewModel
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.GlobalSearchViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import com.spela.player.presentation.viewmodel.NetplayViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.SessionDetailViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.SharedSessionDetailViewModel
import com.spela.player.presentation.viewmodel.SharedSessionsViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.presentation.viewmodel.StatsViewModel
import com.spela.player.presentation.viewmodel.TopListsViewModel

/**
 * Dependency bag for [SpelaApp]. The composable root needs a lot of
 * ViewModels + platform services to wire every screen in its
 * `when (screen)` router, and the flat 30-parameter signature was
 * making the call sites unreadable. Grouping them here gives a
 * single place to add a new dependency and lets the two callers
 * (`presentation/App.kt` and `desktopTest/SpelaTestHarness.kt`)
 * both build the same shape without copy-pasting a long argument
 * list.
 *
 * Nullability on the trailing fields matches the original
 * parameter defaults — tests and the slim initial-boot path can
 * omit services that aren't exercised. All non-nullable fields
 * are genuinely required by the main run.
 *
 * Part of #693 — the first of a four-PR split. Subsequent PRs
 * move the screen router, connection-state overlay, and init
 * effect grouping.
 */
data class SpelaAppDependencies(
    // ── ViewModels (always required) ─────────────────────────────
    val navigationViewModel: NavigationViewModel,
    val serverConnectionViewModel: ServerConnectionViewModel,
    val loginViewModel: LoginViewModel,
    val gameListViewModel: GameListViewModel,
    val gameDetailViewModel: GameDetailViewModel,
    val emulationViewModel: EmulationViewModel,
    val libretroController: LibretroController,
    val downloadsViewModel: DownloadsViewModel,
    val settingsViewModel: SettingsViewModel,
    val keyMappingViewModel: KeyMappingViewModel,
    val socialViewModel: SocialViewModel,
    val sharedSessionsViewModel: SharedSessionsViewModel,
    val sharedSessionDetailViewModel: SharedSessionDetailViewModel,
    val netplayViewModel: NetplayViewModel,
    val netplayLobbyViewModel: NetplayLobbyViewModel,
    val statsViewModel: StatsViewModel,
    val collectionsViewModel: CollectionsViewModel,
    val challengeListViewModel: ChallengeListViewModel,
    val challengeDetailViewModel: ChallengeDetailViewModel,

    // ── Platform / services (always required) ────────────────────
    val secondaryDisplay: PlatformSecondaryDisplay,
    val presenceService: PresenceService,
    val connectivityMonitor: ConnectivityMonitor,
    val downloadRepository: DownloadRepository,
    val preferencesRepository: PreferencesRepository,

    // ── Optional slots ──────────────────────────────────────────
    // These matched `= null` defaults on the original `SpelaApp`
    // signature. Keeping them nullable so tests + simpler host
    // apps can omit services the scenario doesn't exercise.
    val gamepadConfigViewModel: GamepadConfigViewModel? = null,
    val sessionDetailViewModel: SessionDetailViewModel? = null,
    val topListsViewModel: TopListsViewModel? = null,
    val exploreViewModel: ExploreViewModel? = null,
    val navigationEventBus: NavigationEventBus? = null,
    val gamepadPortManager: GamepadPortManager? = null,
    val globalSearchViewModel: GlobalSearchViewModel? = null,
    val scrapeService: ScrapeService? = null,
)
