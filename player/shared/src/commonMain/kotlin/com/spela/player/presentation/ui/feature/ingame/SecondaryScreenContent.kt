package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.compose.koinInject

private const val BURN_IN_IDLE_TIMEOUT_MS = 15_000L
private const val BURN_IN_FADE_DURATION_MS = 5_000
private const val PAGE_COUNT = 4
private const val PAGE_ART = 0
private const val PAGE_CONTROLS = 1
private const val PAGE_DASHBOARD = 2
private const val PAGE_SAVE_SLOTS = 3
private val GRADIENT_LINE_HEIGHT = 2.dp
private val DOT_SIZE_INACTIVE = 6.dp
private val DOT_SIZE_ACTIVE = 8.dp
private val DOT_SPACING = 4.dp
private const val ACTIVE_PAGE_LABEL_HOLD_MS = 2000L
private val PAGE_NAMES = arrayOf("Art", "Controls", "Dashboard", "Save Slots")

/**
 * Content composable displayed on the secondary screen during gameplay.
 *
 * Layout for ~3.92" screen with swipeable pages:
 * ```
 * ┌─────────────────────────────┐
 * │ ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀ │  <- 2dp console gradient line
 * │  Game Title    00:45:12 SNES │  <- Persistent header
 * ├─────────────────────────────┤
 * │                             │
 * │   [ Swipeable Page Content ] │  <- HorizontalPager
 * │                             │
 * ├─────────────────────────────┤
 * │          *  o               │  <- Page indicator dots
 * └─────────────────────────────┘
 * ```
 *
 * Pages:
 * - Page 0: Art Display — hero artwork or focus mode
 * - Page 1: Controls — platform touch gamepad
 * - Page 2: Dashboard — stat cards + quick actions
 * - Page 3: Save Slots — visual save slot selection
 */
@Composable
fun SecondaryScreenContent(
    viewModel: EmulationViewModel = koinInject(),
    controller: LibretroController = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val contentAlpha = if (state.isPaused) 0.4f else 1f

    // OLED burn-in protection: fade to black after idle timeout (single-screen games only)
    var touchResetKey by remember { mutableIntStateOf(0) }
    var isDimmed by remember { mutableStateOf(false) }
    val burnInAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0f else 1f,
        animationSpec = if (isDimmed) tween(BURN_IN_FADE_DURATION_MS) else snap(),
        label = "burnInAlpha",
    )

    val initialPage = remember(state.defaultSecondScreenPage, state.consoleId, state.scummvmTrackpadOnboarded) {
        // Mouse-driven cores (ScummVM today; DOSBox / NDS-with-mouse later)
        // land on Controls *only* until the user has produced their first
        // trackpad drag. After that, the OnboardingRepository row makes
        // the override silent and the user's saved default page wins. See
        // #861.
        val isScummvm = state.consoleId.equals("scummvm", ignoreCase = true)
        if (isScummvm && !state.scummvmTrackpadOnboarded) {
            PAGE_CONTROLS
        } else {
            when (state.defaultSecondScreenPage) {
                "controls" -> PAGE_CONTROLS
                "dashboard" -> PAGE_DASHBOARD
                "save_slots" -> PAGE_SAVE_SLOTS
                else -> PAGE_ART
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { PAGE_COUNT },
    )

    // Reset burn-in timer when swiping between pages
    if (!state.isDualScreenConsole) {
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collectLatest {
                touchResetKey++
            }
        }

        LaunchedEffect(touchResetKey, state.isPaused) {
            isDimmed = false
            delay(BURN_IN_IDLE_TIMEOUT_MS)
            isDimmed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .then(
                if (!state.isDualScreenConsole) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                touchResetKey++
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        if (state.isDualScreenConsole) {
            // DS/3DS mode: only show the bottom screen, no UI chrome
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha },
            ) {
                PlatformDsTouchScreen(
                    controller = controller,
                    splitY = state.dualScreenSplitY,
                    selectedShader = state.selectedShader,
                    bottomScreenWidth = state.dualScreenBottomWidth,
                    bottomScreenOffsetX = state.dualScreenBottomOffsetX,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha * burnInAlpha },
            ) {
                // Persistent header with console gradient accent
                CompanionHeader(
                    gameTitle = state.gameTitle,
                    sessionElapsedSeconds = state.sessionElapsedSeconds,
                    consoleId = state.consoleId,
                    consoleName = state.consoleName,
                    consoleColorTheme = state.consoleColorTheme,
                )

                // Swipeable page content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    when (page) {
                        PAGE_ART -> {
                            SecondaryArtPage(
                                heroUrl = state.heroUrl,
                                gameTitle = state.gameTitle,
                                sessionElapsedSeconds = state.sessionElapsedSeconds,
                                consoleId = state.consoleId,
                                consoleName = state.consoleName,
                                consoleColorTheme = state.consoleColorTheme,
                                gameDescription = state.gameDescription,
                                gameDeveloper = state.gameDeveloper,
                                gamePublisher = state.gamePublisher,
                                gameReleaseDate = state.gameReleaseDate,
                                gameGenre = state.gameGenre,
                                gameRating = state.gameRating,
                                gamePlayers = state.gamePlayers,
                            )
                        }
                        PAGE_CONTROLS -> {
                            SecondaryControlsPage(
                                controller = controller,
                                touchControlPort = state.touchControlPort,
                                selectedTab = state.selectedControlTab,
                                consoleId = state.consoleId,
                                onSelectPort = { port ->
                                    viewModel.onIntent(EmulationIntent.SelectTouchControlPort(port))
                                },
                                onSelectTab = { tab ->
                                    viewModel.onIntent(EmulationIntent.SelectControlTab(tab))
                                },
                                onKeyDown = { key ->
                                    controller.setKeyboardKey(key, true)
                                },
                                onKeyUp = { key ->
                                    controller.setKeyboardKey(key, false)
                                },
                                onMouseMove = { dx, dy ->
                                    controller.setMouse(0, dx.toInt().toShort(), dy.toInt().toShort(), false, false)
                                    // #861 — first ScummVM trackpad drag of any
                                    // session dismisses the override hint, so
                                    // future ScummVM launches respect the
                                    // user's saved default page.
                                    if (state.consoleId.equals("scummvm", ignoreCase = true) &&
                                        !state.scummvmTrackpadOnboarded
                                    ) {
                                        viewModel.onIntent(EmulationIntent.MarkScummVmTrackpadOnboarded)
                                    }
                                },
                                onMouseButton = { left, right ->
                                    controller.setMouse(0, 0, 0, left, right)
                                },
                                // Resolution-stable cursor speed: pass the
                                // core's framebuffer width/height so a
                                // ⅓-of-trackpad swipe traverses ⅓ of the
                                // game viewport regardless of native res.
                                // See #858.
                                gameWidth = controller.getVideoWidth(),
                                gameHeight = controller.getVideoHeight(),
                            )
                        }
                        PAGE_DASHBOARD -> {
                            SecondaryDashboardPage(
                                fps = state.fps,
                                frameTime = state.frameTime,
                                activeSlot = state.activeSlot,
                                hasCheats = state.hasCheats,
                                enabledCheatCount = state.enabledCheatCount,
                                cheats = state.cheats,
                                hasAchievements = state.hasAchievements,
                                achievementUnlockedCount = state.achievementUnlockedCount,
                                achievementTotalCount = state.achievements.size,
                                achievementEarnedPoints = state.achievementEarnedPoints,
                                achievementTotalPoints = state.achievementTotalPoints,
                                achievements = state.achievements,
                                achievementProgress = state.achievementProgress,
                                sessionAchievementUnlocks = state.sessionAchievementUnlocks,
                                sessionElapsedSeconds = state.sessionElapsedSeconds,
                                isFastForward = state.isFastForward,
                                rewindEnabled = state.rewindEnabled,
                                // Challenge mode
                                challengeId = state.challengeId,
                                challengeObjective = state.challengeObjective,
                                challengeElapsedMs = state.challengeElapsedMs,
                                onCompleteChallenge = { viewModel.onIntent(EmulationIntent.CompleteChallenge) },
                                onRestartChallenge = { viewModel.onIntent(EmulationIntent.RestartChallenge) },
                                onGiveUpChallenge = { viewModel.onIntent(EmulationIntent.ShowGiveUpConfirm) },
                                // Netplay mode
                                isNetplayMode = state.isNetplayMode,
                                netplayPeerUsername = state.netplayPeerUsername,
                                netplayPeerLatencyMs = state.netplayPeerLatencyMs,
                                netplayPeerDisconnected = state.netplayPeerDisconnected,
                                netplayPausedByUsername = state.netplayPausedByUsername,
                                onSave = { viewModel.onIntent(EmulationIntent.SaveState) },
                                onLoad = { viewModel.onIntent(EmulationIntent.LoadState) },
                                onScreenshot = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                                onToggleFastForward = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                                onRewind = { viewModel.onIntent(EmulationIntent.ToggleRewind) },
                                onToggleCheat = { cheatId, enabled ->
                                    viewModel.onIntent(EmulationIntent.ToggleCheatInGame(cheatId, enabled))
                                },
                            )
                        }
                        PAGE_SAVE_SLOTS -> {
                            // Page-3 content adapts based on the active core
                            // (#863):
                            //   1. Save states supported → existing slot picker.
                            //   2. ScummVM (no libretro save states, but it
                            //      has a rich in-game F5/F7/F8 menu) → tools
                            //      page that fires those keycodes.
                            //   3. Other cores without save states → honest
                            //      "save states not available" explainer.
                            when {
                                state.supportsSaveStates -> {
                                    SecondarySaveSlotsPage(
                                        activeSlot = state.activeSlot,
                                        saveSlots = state.saveSlots,
                                        onSelectSlot = { slot ->
                                            viewModel.onIntent(EmulationIntent.SelectSlot(slot))
                                        },
                                        onSaveToSlot = { slot ->
                                            viewModel.onIntent(EmulationIntent.SaveToSlot(slot))
                                        },
                                        onLoadFromSlot = { slot ->
                                            viewModel.onIntent(EmulationIntent.LoadFromSlot(slot))
                                        },
                                    )
                                }
                                state.consoleId.equals("scummvm", ignoreCase = true) -> {
                                    SecondaryScummVmToolsPage(
                                        onMenu = {
                                            controller.setKeyboardKey(286, true)  // RETROK_F5
                                            controller.setKeyboardKey(286, false)
                                        },
                                        onQuickLoad = {
                                            controller.setKeyboardKey(288, true)  // RETROK_F7
                                            controller.setKeyboardKey(288, false)
                                        },
                                        onQuickSave = {
                                            controller.setKeyboardKey(289, true)  // RETROK_F8
                                            controller.setKeyboardKey(289, false)
                                        },
                                    )
                                }
                                else -> {
                                    SecondarySaveStatesUnsupportedPage()
                                }
                            }
                        }
                    }
                }

                // Page indicator dots
                PageIndicatorDots(
                    pagerState = pagerState,
                    pageCount = PAGE_COUNT,
                )
            }
        }

        // Save/load toast overlay — positioned above page dots, below celebration
        SecondaryToast(
            toast = state.secondaryToast,
            onToastShown = {
                // Reset the OLED burn-in timer so the screen stays lit
                // while the toast is visible
                touchResetKey++
            },
            onDismiss = {
                viewModel.onIntent(EmulationIntent.ClearSecondaryToast)
            },
        )

        // Achievement celebration overlay — shows on top of all pages
        SecondaryAchievementCelebration(
            achievementEvent = state.achievementEvent,
            onCelebrationStarted = {
                // Reset the OLED burn-in timer so the screen stays lit
                // during the celebration
                touchResetKey++
            },
        )

        // Paused overlay with scrim for readability over art page
        if (state.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpColor.Scrim)
                    .graphicsLayer { alpha = burnInAlpha }
                    .semantics {
                        contentDescription = if (state.netplayPausedByUsername != null) {
                            "Game paused by ${state.netplayPausedByUsername}"
                        } else {
                            "Game paused"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                PauseOverlayContent(
                    netplayPausedByUsername = state.netplayPausedByUsername,
                    netplayPauseElapsedSeconds = state.netplayPauseElapsedSeconds,
                    sessionElapsedSeconds = state.sessionElapsedSeconds,
                    activeSlot = state.activeSlot,
                )
            }
        }

        // Touch-blocking overlay when screen is dimmed (prevents accidental button presses)
        if (burnInAlpha < 0.5f && !state.isDualScreenConsole) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .semantics {
                        contentDescription = "Screen dimmed for OLED protection"
                    },
            )
        }
    }
}

/**
 * Persistent header with console gradient accent line, game title, session timer,
 * and console badge.
 */
@Composable
private fun CompanionHeader(
    gameTitle: String,
    sessionElapsedSeconds: Long,
    consoleId: String,
    consoleName: String,
    consoleColorTheme: String?,
) {
    val timeText = formatSessionDuration(sessionElapsedSeconds)
    val (gradientFrom, gradientTo) = getConsoleGradient(consoleColorTheme)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Now playing: $gameTitle"
            },
    ) {
        // Thin 2dp gradient accent line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GRADIENT_LINE_HEIGHT)
                .background(
                    Brush.horizontalGradient(listOf(gradientFrom, gradientTo))
                ),
        )

        // Header content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpColor.SurfaceVariant)
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = gameTitle,
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SpSpacing.Small))
            Text(
                text = timeText,
                style = SpTypography.LabelLarge,
                color = SpColor.OnBackgroundTertiary,
            )
            if (consoleName.isNotEmpty()) {
                Spacer(Modifier.width(SpSpacing.Small))
                Text(
                    text = consoleName,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Enhanced pause overlay showing contextual information:
 * - Session duration and active save slot
 * - Netplay pause attribution when paused by a peer
 */
@Composable
private fun PauseOverlayContent(
    netplayPausedByUsername: String?,
    netplayPauseElapsedSeconds: Long,
    sessionElapsedSeconds: Long,
    activeSlot: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main pause title — attributed to peer in netplay
        val pauseTitle = if (netplayPausedByUsername != null) {
            "PAUSED BY ${netplayPausedByUsername.uppercase()}"
        } else {
            "PAUSED"
        }
        Text(
            text = pauseTitle,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground.copy(alpha = 0.8f),
        )

        Spacer(Modifier.height(SpSpacing.Small))

        // Pause duration for netplay, or session info for local pause
        if (netplayPausedByUsername != null && netplayPauseElapsedSeconds > 0) {
            Text(
                text = "Paused for ${formatPauseDuration(netplayPauseElapsedSeconds)}",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.semantics {
                    contentDescription = "Paused for ${formatPauseDuration(netplayPauseElapsedSeconds)}"
                },
            )
        } else {
            // Session info: duration and active slot
            Text(
                text = "Session: ${formatSessionDuration(sessionElapsedSeconds)}  ·  Slot $activeSlot",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.semantics {
                    contentDescription = "Session ${formatSessionDuration(sessionElapsedSeconds)}, save slot $activeSlot"
                },
            )
        }
    }
}

/**
 * Format pause duration as "M:SS" (minutes:seconds).
 */
private fun formatPauseDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:%02d".format(seconds)
}

/**
 * Horizontal row of page indicator dots. Active page dot uses [SpColor.Primary],
 * inactive dots use [SpColor.OnBackgroundTertiary] at reduced alpha.
 */
/**
 * Save Slots page replacement for ScummVM (#863 state 3). Replaces
 * the libretro slot picker (which doesn't apply — ScummVM declares
 * savestate=false) with three quick-tap tiles for the most-used
 * ScummVM keyboard shortcuts:
 *
 *   F5  — Open ScummVM main menu (full save / load / options / quit)
 *   F7  — Quick load
 *   F8  — Quick save
 *
 * The tiles fire RETROK_F5/F7/F8 via the libretro keyboard pipeline.
 * The keyboard plumbing is the same one used by the gamepad mapper
 * defaults from #859 — for a user with a gamepad, Y / L1 / R1 already
 * fire these. The tiles are for pure-touch users without a gamepad.
 */
@Composable
private fun SecondaryScummVmToolsPage(
    onMenu: () -> Unit,
    onQuickLoad: () -> Unit,
    onQuickSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpSpacing.Default),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Default),
    ) {
        Text(
            text = "ScummVM tools",
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackground,
        )
        ScummVmToolTile(
            label = "Menu",
            description = "Save, load, options, quit",
            onClick = onMenu,
        )
        ScummVmToolTile(
            label = "Quick load",
            description = "Load the auto-save",
            onClick = onQuickLoad,
        )
        ScummVmToolTile(
            label = "Quick save",
            description = "Save over the auto-save",
            onClick = onQuickSave,
        )
    }
}

@Composable
private fun ScummVmToolTile(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(SpColor.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(SpSpacing.Default)
            .semantics { contentDescription = "$label: $description" },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall)) {
            Text(
                text = label,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
            )
            Text(
                text = description,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}

/**
 * Empty-state for the Save Slots page when the active core declares
 * `savestate = "false"` (ScummVM, DOSBox, ~60 others). The slot picker
 * doesn't apply — those cores persist via their own on-disk save_dir,
 * which Spela now bundles per-session (#864). Show the user honestly
 * that libretro save states aren't a thing here, rather than a dead
 * grid of empty slots. See #863.
 *
 * State 4 ("terminal-unsupported") of the resolution order in #863.
 * States 2 (user opted out) and 3 (ScummVM Tools page) are filed as
 * follow-ups — this MVP just covers the unconditional case.
 */
@Composable
private fun SecondarySaveStatesUnsupportedPage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpSpacing.Default),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            Text(
                text = "Save states not available",
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
            )
            Text(
                text = "This core writes its saves to disk through the game's own menu (try F5 in-game). They're preserved across sessions by Spela.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Page indicator at the bottom of the secondary-screen pager. Two
 * upgrades from the original dot strip (#861):
 *
 * 1. Active dot is 8dp / inactive 6dp (was uniformly 6dp at low alpha).
 *    The size delta makes the active page readable at arm's length
 *    while a game plays on the primary screen.
 * 2. On page change the active dot expands into a labelled pill
 *    ("Art", "Controls", "Dashboard", "Saves"), holds for 2s, then
 *    collapses back to a dot. Tells the user "this strip is named
 *    and interactive" without permanent label clutter.
 *
 * The pill is visual-only (it doesn't intercept clicks) and the
 * inactive dots stay dot-shaped throughout — only the active one
 * ever expands.
 */
@Composable
private fun PageIndicatorDots(
    pagerState: PagerState,
    pageCount: Int,
) {
    val currentPage = pagerState.currentPage
    var showActiveLabel by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        // Show the labelled pill briefly on every page change. The
        // very-first composition also fires this so the user sees the
        // initial page's name once, which is part of the "this is a
        // pager" signal.
        showActiveLabel = true
        delay(ACTIVE_PAGE_LABEL_HOLD_MS)
        showActiveLabel = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.Small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = currentPage == index
            val color = if (isActive) {
                SpColor.Primary
            } else {
                SpColor.OnBackgroundTertiary.copy(alpha = 0.55f)
            }
            if (index > 0) {
                Spacer(Modifier.width(DOT_SPACING))
            }
            val pageName = PAGE_NAMES.getOrElse(index) { "Page" }
            if (isActive && showActiveLabel) {
                // Pill: rounded rect with the page name inside,
                // tinted with the same Primary as the active dot so
                // the visual continuity reads as "the dot grew a
                // label" rather than "a separate widget appeared".
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color)
                        .padding(horizontal = SpSpacing.Small, vertical = 2.dp)
                        .semantics {
                            contentDescription = "$pageName, ${index + 1} of $pageCount, active"
                        },
                ) {
                    Text(
                        text = pageName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackground,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isActive) DOT_SIZE_ACTIVE else DOT_SIZE_INACTIVE)
                        .clip(CircleShape)
                        .background(color)
                        .semantics {
                            contentDescription = if (isActive) {
                                "$pageName, ${index + 1} of $pageCount, active"
                            } else {
                                "$pageName, ${index + 1} of $pageCount"
                            }
                        },
                )
            }
        }
    }
}
