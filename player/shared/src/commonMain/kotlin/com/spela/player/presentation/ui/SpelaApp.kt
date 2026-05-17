package com.spela.player.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.BottomNavTab
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.NavigationLayoutMode
import com.spela.player.presentation.ui.components.SpBottomNavBar
import com.spela.player.presentation.ui.components.SpNavigationRail
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.feature.ingame.DsPrimaryTouchOverlay
import com.spela.player.presentation.ui.screen.InGameOverlay
import com.spela.player.presentation.ui.feature.ingame.PlatformEmulationSurface
import com.spela.player.presentation.ui.feature.ingame.PlatformTouchControls

import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.gamepad.GamepadHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalIsForwardNavigation
import com.spela.player.presentation.ui.gamepad.LocalIsTabSwitch
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.components.LocalScrapeService
import com.spela.player.presentation.ui.theme.SpelaTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.PointerEventPass
import com.spela.player.libretro.ControllerStatusState
import com.spela.player.presentation.ui.components.SpSectionIndicator

@Composable
fun SpelaApp(deps: SpelaAppDependencies) = with(deps) {
    val currentTheme by settingsViewModel.selectedTheme.collectAsState()

    SpelaTheme(theme = currentTheme) {
    CompositionLocalProvider(
        LocalScrapeService provides scrapeService,
        LocalInputMode provides (gamepadPortManager?.inputMode?.collectAsState()?.value ?: InputMode.TOUCH),
    ) {
        val navState by navigationViewModel.state.collectAsState()

        // Nav style is driven by whether a physical gamepad is connected, not by
        // the in-app InputMode. Keyboard + mouse users (no gamepad) always see the
        // tab bar / side rail, so the nav doesn't flicker between styles as they
        // switch between typing and clicking. See #1187.
        val controllerStatus by gamepadPortManager?.controllerStatus?.collectAsState()
            ?: remember { mutableStateOf(ControllerStatusState.Empty) }
        val isGamepadMode = controllerStatus.connectedCount > 0
        val sectionIndicatorVisible = isGamepadMode

        // Indicator for E2E tests: exposes whether the libretro core is running.
        // Tests wait for "Core idle" instead of Thread.sleep after exiting games.
        // Uses 1dp size so it appears in the Android accessibility tree for UiAutomator.
        val coreIdleState by emulationViewModel.state.collectAsState()
        Box(
            modifier = Modifier
                .size(1.dp)
                .semantics {
                    contentDescription = if (coreIdleState.isRunning) "Core running" else "Core idle"
                },
        )

        // Show loading screen while session is being restored
        if (navState.isRestoringSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpColor.Background),
                contentAlignment = Alignment.Center,
            ) {
                if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                    CircularProgressIndicator(color = SpColor.Primary)
                }
            }
            return@CompositionLocalProvider
        }

        val isAuthenticated = navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login

        SpelaAppEffects(
            scrapeService = scrapeService,
            presenceService = presenceService,
            isAuthenticated = isAuthenticated,
            navigationEventBus = navigationEventBus,
            navigationViewModel = navigationViewModel,
            downloadRepository = downloadRepository,
        )

        val isGamepadScreen = navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login

        // Handle system back button for non-emulation screens (Android)
        val hasBackStack = navState.currentScreen !is SpScreen.Home &&
                navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login
        PlatformBackHandler(enabled = hasBackStack && !navState.showInGameOverlay) {
            navigationViewModel.onIntent(NavigationIntent.GoBack)
        }

        GamepadHandler(
            enabled = !navState.showInGameOverlay,
            onBack = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.GoBack) }
            } else null,
            onNextSection = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.NextSection) }
            } else null,
            onPreviousSection = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.PreviousSection) }
            } else null,
            onGamepadInput = { gamepadPortManager?.setInputMode(InputMode.GAMEPAD) },
            focusResetKey = Pair(navState.activeTab, navState.currentScreen),
            isGoingBack = navState.isGoingBack,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background)
                .onPreviewKeyEvent { keyEvent ->
                    // Cmd+K (macOS) / Ctrl+K (Windows/Linux) opens global search
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.K &&
                        (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) &&
                        !navState.showInGameOverlay
                    ) {
                        if (navState.currentScreen !is SpScreen.GlobalSearch) {
                            navigationViewModel.onIntent(
                                NavigationIntent.NavigateTo(SpScreen.GlobalSearch)
                            )
                        }
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) {
                                gamepadPortManager?.setInputMode(InputMode.TOUCH)
                            }
                        }
                    }
                },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val navLayoutMode = when {
                maxWidth > 840.dp -> NavigationLayoutMode.LABELED_RAIL
                maxWidth > 600.dp -> NavigationLayoutMode.ICON_RAIL
                else -> NavigationLayoutMode.BOTTOM_BAR
            }
            val showNavArea = !navState.showInGameOverlay && shouldShowBottomNav(navState.currentScreen)
            val showSideRail = navLayoutMode != NavigationLayoutMode.BOTTOM_BAR && showNavArea && !isGamepadMode

            Row(modifier = Modifier.fillMaxSize()) {
            // Side navigation rail (larger screens, touch mode only)
            if (showSideRail) {
                SpNavigationRail(
                    activeTab = navState.activeTab,
                    onTabSelected = { tab ->
                        val targetScreen = when (tab) {
                            BottomNavTab.HOME -> SpScreen.Home
                            BottomNavTab.EXPLORE -> SpScreen.Explore
                            BottomNavTab.CONSOLES -> SpScreen.Consoles
                            BottomNavTab.COLLECTIONS -> SpScreen.Collections
                            BottomNavTab.ACTIVITY -> SpScreen.Activity
                            BottomNavTab.SETTINGS -> SpScreen.Settings
                        }
                        navigationViewModel.onIntent(
                            NavigationIntent.SwitchTab(targetScreen)
                        )
                    },
                    showLabels = navLayoutMode == NavigationLayoutMode.LABELED_RAIL,
                    controllerStatus = controllerStatus,
                    onControllerStatusClick = {
                        navigationViewModel.onIntent(
                            NavigationIntent.SwitchTab(SpScreen.Settings)
                        )
                    },
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                var snackbarData by remember { mutableStateOf<SpSnackbarData?>(null) }

                ConnectionStateOverlay(
                    connectivityMonitor = connectivityMonitor,
                    navigationViewModel = navigationViewModel,
                    onBackOnline = {
                        snackbarData = SpSnackbarData(
                            message = "Back online",
                            type = SpSnackbarType.Success,
                            durationMs = 3000L,
                        )
                    },
                ) {

                Box(modifier = Modifier.weight(1f)) {
                    val animationsEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
                    val saveableStateHolder = rememberSaveableStateHolder()

                    // When navigating forward to a NEW screen, clear saved state so
                    // it starts fresh (e.g., scroll position at top).
                    // Preserve state when going back OR switching tabs.
                    //
                    // IMPORTANT: Only remove state ONCE per screen change. Running
                    // removeState on every recomposition continuously clears scroll
                    // state while the user is on the screen.
                    var lastClearedRoute by remember { mutableStateOf<String?>(null) }
                    val currentRoute = navState.currentScreen.route
                    if (currentRoute != lastClearedRoute) {
                        if (!navState.isGoingBack && !navState.isTabSwitch) {
                            saveableStateHolder.removeState(currentRoute)
                        }
                        lastClearedRoute = currentRoute
                    }

                    // In test mode (animations disabled), bypass AnimatedContent entirely.
                    // AnimatedContent transitions can interfere with Compose test framework's
                    // waitForIdle(), causing the new screen to be briefly composed then removed.
                    @Composable
                    fun ScreenContent(screen: com.spela.player.presentation.navigation.SpScreen) {
                        val isForward = !navState.isGoingBack && !navState.isTabSwitch
                        saveableStateHolder.SaveableStateProvider(screen.route) {
                        CompositionLocalProvider(
                            LocalIsForwardNavigation provides isForward,
                            LocalIsTabSwitch provides navState.isTabSwitch,
                        ) {
                        ScreenRouter(screen = screen, deps = deps, navState = navState)
                        } // CompositionLocalProvider (LocalIsForwardNavigation)
                        } // SaveableStateProvider
                    } // ScreenContent

                    if (animationsEnabled) {
                        AnimatedContent(
                            targetState = navState.currentScreen,
                            transitionSpec = {
                                if (navState.isTabSwitch) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else if (navState.isGoingBack) {
                                    (slideInHorizontally { -it / 3 } + fadeIn())
                                        .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
                                } else {
                                    (slideInHorizontally { it / 3 } + fadeIn())
                                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                                }
                            },
                        ) { screen ->
                            ScreenContent(screen)
                        }
                    } else {
                        // Test mode: render current screen directly without AnimatedContent.
                        // key() on the route forces Compose to fully dispose and recreate
                        // the screen composable when navigation changes. Without this,
                        // the composable tree can get "stuck" and stop observing StateFlow
                        // updates from ViewModels.
                        key(navState.currentScreen.route) {
                            ScreenContent(navState.currentScreen)
                        }
                    }

                    // Emulation surface + in-game overlay
                    if (navState.showInGameOverlay) {
                        // Touch-blocking background: prevents touches from leaking through
                        // to the navigation screens (e.g. GameDetail) rendered behind.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                },
                        )

                        LaunchedEffect(navState.overlayGameId, navState.overlaySharedSessionId, navState.overlayNetplaySessionId, navState.overlayChallengeId, navState.overlaySessionId, navState.overlaySkipAutoLoad, navState.overlayForceNewSession) {
                            navState.overlayGameId?.let { gameId ->
                                emulationViewModel.onIntent(
                                    EmulationIntent.StartGame(
                                        gameId = gameId,
                                        sharedSessionId = navState.overlaySharedSessionId,
                                        turnToken = navState.overlayTurnToken,
                                        netplaySessionId = navState.overlayNetplaySessionId,
                                        netplayLocalPort = navState.overlayNetplayLocalPort,
                                        netplayInputDelay = navState.overlayNetplayInputDelay,
                                        netplayIsHost = navState.overlayNetplayIsHost,
                                        challengeId = navState.overlayChallengeId,
                                        skipAutoLoad = navState.overlaySkipAutoLoad,
                                        forceNewSession = navState.overlayForceNewSession,
                                        sessionId = navState.overlaySessionId,
                                    )
                                )
                            }
                        }

                        // Intercept back button during emulation: toggle overlay
                        PlatformBackHandler {
                            emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
                        }

                        val emulationState by emulationViewModel.state.collectAsState()
                        val secondaryAvailable by secondaryDisplay.isAvailable.collectAsState()

                        // Notify ViewModel about secondary display availability changes
                        LaunchedEffect(secondaryAvailable) {
                            emulationViewModel.onIntent(
                                EmulationIntent.SecondaryDisplayAvailabilityChanged(secondaryAvailable)
                            )
                        }

                        // Handle auto-exit (when auto-save skips confirmation)
                        LaunchedEffect(emulationState.requestExit) {
                            if (emulationState.requestExit) {
                                emulationViewModel.onIntent(EmulationIntent.ClearExitRequest)
                                netplayViewModel.onIntent(NetplayIntent.ClearJoinedSession)
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            }
                        }

                        // Emulation video surface (renders behind the overlay)
                        PlatformEmulationSurface(
                            controller = libretroController,
                            selectedShader = emulationState.selectedShader,
                            onEscapePressed = {
                                emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
                            },
                        )

                        // Error overlay: shown when emulation fails to start
                        if (emulationState.error != null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(SpSpacing.XLarge),
                                ) {
                                    Text(
                                        text = emulationState.error ?: "",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.Error,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Large))
                                    SpButton(
                                        text = "Exit",
                                        onClick = {
                                            emulationViewModel.onIntent(EmulationIntent.StopGame)
                                            navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                                        },
                                    )
                                }
                            }
                        }

                        // Core download progress sheet — replaces the
                        // pre-#1192 opaque loading spinner with a
                        // foreground modal carrying "Updating Azahar —
                        // 12 / 38 MB". Non-null when prepareGameUseCase
                        // is fetching a fresh/updated core (including
                        // when reusing an in-flight prefetch).
                        emulationState.coreDownload?.let { progress ->
                            com.spela.player.presentation.ui.feature.coreupdate.CoreDownloadSheet(progress)
                        }

                        // Missing BIOS dialog (AC 4.3)
                        if (emulationState.showMissingBiosDialog) {
                            com.spela.player.presentation.ui.feature.gamedetail.MissingBiosDialog(
                                consoleName = emulationState.missingBiosConsoleName,
                                missingFiles = emulationState.missingBiosFiles,
                                onGoBack = {
                                    emulationViewModel.onIntent(EmulationIntent.DismissMissingBiosDialog)
                                    navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                                },
                                onTryAnyway = {
                                    emulationViewModel.onIntent(EmulationIntent.TryAnywayMissingBios)
                                },
                            )
                        }

                        // Semantic marker for E2E tests to detect that a game is running.
                        // Always on the primary display, regardless of dual-screen or controller type.
                        // Uses 1dp size so it appears in the Android accessibility tree
                        // (zero-size nodes are filtered out by UiAutomator).
                        if (emulationState.isRunning) {
                            Box(modifier = Modifier
                                .size(1.dp)
                                .semantics {
                                    contentDescription = "Game running"
                                })
                        }

                        // DS/3DS touch overlay on primary display (when secondary display is not active)
                        // Handles touch input on the bottom screen area of the rendered game
                        if (emulationState.isDualScreenConsole && !emulationState.secondaryDisplayActive
                            && emulationState.isRunning && !emulationState.showOverlay
                        ) {
                            DsPrimaryTouchOverlay(
                                controller = libretroController,
                                consoleId = emulationState.consoleId,
                                splitY = emulationState.dualScreenSplitY,
                            )
                        }

                        // Touch gamepad controls (Android only, no-op on desktop)
                        // Hidden when secondary display is active (controls move there)
                        // Also hidden for DS games (bottom screen touch replaces virtual buttons)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = emulationState.isRunning && !emulationState.showOverlay
                                && !emulationState.secondaryDisplayActive
                                && !emulationState.isDualScreenConsole,
                            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
                            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
                        ) {
                            PlatformTouchControls(
                                controller = libretroController,
                            )
                        }

                        InGameOverlay(
                            viewModel = emulationViewModel,
                            keyMappingViewModel = keyMappingViewModel,
                            gamepadConfigViewModel = gamepadConfigViewModel,
                            onExit = {
                                // Mirror the requestExit-driven
                                // LaunchedEffect's cleanup. Several
                                // InGameOverlay dialogs (Confirm Exit,
                                // Confirm Give Up, Netplay leave,
                                // Netplay session expired) dispatch
                                // their own intent that flips
                                // requestExit = true and then call
                                // onExit() before the LaunchedEffect
                                // can fire ClearExitRequest. Without
                                // an explicit clear here, requestExit
                                // stays true; the next time
                                // showInGameOverlay flips to true the
                                // LaunchedEffect re-fires HideOverlay
                                // immediately and the in-game overlay
                                // closes before the user can use it.
                                emulationViewModel.onIntent(EmulationIntent.ClearExitRequest)
                                netplayViewModel.onIntent(NetplayIntent.ClearJoinedSession)
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            },
                        )
                    }
                }

                // "Back online" snackbar
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    SpSnackbar(
                        data = snackbarData,
                        onDismiss = { snackbarData = null },
                    )
                }

                // Bottom navigation bar (phones only, hidden when in gamepad mode or side rail is showing)
                if (navLayoutMode == NavigationLayoutMode.BOTTOM_BAR && showNavArea && !isGamepadMode) {
                    SpBottomNavBar(
                        activeTab = navState.activeTab,
                        onTabSelected = { tab ->
                            val targetScreen = when (tab) {
                                BottomNavTab.HOME -> SpScreen.Home
                                BottomNavTab.EXPLORE -> SpScreen.Explore
                                BottomNavTab.CONSOLES -> SpScreen.Consoles
                                BottomNavTab.COLLECTIONS -> SpScreen.Collections
                                BottomNavTab.ACTIVITY -> SpScreen.Activity
                                BottomNavTab.SETTINGS -> SpScreen.Settings
                            }
                            navigationViewModel.onIntent(
                                NavigationIntent.SwitchTab(targetScreen)
                            )
                        },
                    )
                }
                } // ConnectionStateOverlay content slot
            } // Column (content + bottom bar)
            } // Row (side rail + content column)

            // Section indicator overlay (shown when in gamepad mode)
            if (showNavArea && isGamepadMode) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    SpSectionIndicator(
                        activeTab = navState.activeTab,
                        visible = sectionIndicatorVisible,
                        controllerStatus = controllerStatus,
                        modifier = Modifier.padding(top = SpSpacing.Default),
                    )
                }
            }

            // Floating controller mini-pill (phone layout, 2+ controllers, no gamepad pill visible)
            if (showNavArea && !isGamepadMode && navLayoutMode == NavigationLayoutMode.BOTTOM_BAR) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val animationsEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
                    AnimatedVisibility(
                        visible = controllerStatus.isMultiplayer,
                        enter = if (animationsEnabled) fadeIn() + slideInVertically(initialOffsetY = { -it }) else EnterTransition.None,
                        exit = if (animationsEnabled) fadeOut() + slideOutVertically(targetOffsetY = { -it }) else ExitTransition.None,
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = SpSpacing.Default)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(SpSpacing.RadiusXLarge),
                                )
                                .clickable {
                                    navigationViewModel.onIntent(
                                        NavigationIntent.SwitchTab(SpScreen.Settings)
                                    )
                                }
                                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
                                .semantics { contentDescription = "${controllerStatus.connectedCount} controllers connected" },
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SpSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.spela.player.presentation.ui.components.SpControllerStatusRow(
                                ports = controllerStatus.ports,
                                showEmptySlots = false,
                                dotSize = 8.dp,
                                spacing = SpSpacing.Small,
                            )
                        }
                    }
                }
            }
            } // BoxWithConstraints
        } // Box
        } // GamepadNavigation
    } // CompositionLocalProvider
    } // SpelaTheme
} // SpelaApp

private fun shouldShowBottomNav(screen: SpScreen): Boolean = when (screen) {
    is SpScreen.ServerConnection, is SpScreen.Login -> false
    else -> true
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
        )
    }
}
