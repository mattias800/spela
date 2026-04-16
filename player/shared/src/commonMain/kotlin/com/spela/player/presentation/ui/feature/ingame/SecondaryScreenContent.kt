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
import androidx.compose.foundation.shape.CircleShape
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
private val DOT_SIZE = 6.dp
private val DOT_SPACING = 4.dp
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

    val initialPage = remember(state.defaultSecondScreenPage) {
        when (state.defaultSecondScreenPage) {
            "controls" -> PAGE_CONTROLS
            "dashboard" -> PAGE_DASHBOARD
            "save_slots" -> PAGE_SAVE_SLOTS
            else -> PAGE_ART
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
                                },
                                onMouseButton = { left, right ->
                                    controller.setMouse(0, 0, 0, left, right)
                                },
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
    val (gradientFrom, gradientTo) = getConsoleGradient(consoleId, consoleColorTheme)

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
@Composable
private fun PageIndicatorDots(
    pagerState: PagerState,
    pageCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.Small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = pagerState.currentPage == index
            val color = if (isActive) SpColor.Primary else SpColor.OnBackgroundTertiary.copy(alpha = 0.4f)
            if (index > 0) {
                Spacer(Modifier.width(DOT_SPACING))
            }
            val pageName = PAGE_NAMES.getOrElse(index) { "Page" }
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(color)
                    .semantics {
                        contentDescription = if (isActive) "$pageName, ${index + 1} of $pageCount, active" else "$pageName, ${index + 1} of $pageCount"
                    },
            )
        }
    }
}
