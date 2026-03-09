package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.Cheat
import com.spela.player.domain.model.GameAchievement
import com.spela.player.presentation.state.SessionAchievementUnlock
import com.spela.player.presentation.ui.components.EmulationActionButton
import com.spela.player.presentation.ui.components.SpSwitch
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlin.time.Clock

private val ACTION_BUTTON_SIZE = 48.dp
private val ACTION_ICON_SIZE = 24.dp

/** Which sub-panel is currently displayed on the dashboard. */
private enum class DashboardPanel {
    DASHBOARD,
    CHEATS,
    ACHIEVEMENTS,
}

/**
 * Dashboard page for the secondary screen companion.
 *
 * Shows stat cards (FPS, save slot, cheats, and optionally achievements) and a quick action row
 * (save, load, screenshot, fast forward, rewind).
 */
@Composable
fun SecondaryDashboardPage(
    fps: Float,
    frameTime: Float,
    activeSlot: Int,
    hasCheats: Boolean,
    enabledCheatCount: Int,
    cheats: List<Cheat>,
    hasAchievements: Boolean,
    achievementUnlockedCount: Int,
    achievementTotalCount: Int,
    achievementEarnedPoints: Int,
    achievementTotalPoints: Int,
    achievements: List<GameAchievement>,
    achievementProgress: List<AchievementProgress>,
    sessionAchievementUnlocks: List<SessionAchievementUnlock>,
    sessionElapsedSeconds: Long,
    isFastForward: Boolean,
    rewindEnabled: Boolean,
    // Challenge mode
    challengeId: String? = null,
    challengeObjective: String = "",
    challengeElapsedMs: Long = 0,
    onCompleteChallenge: () -> Unit = {},
    onRestartChallenge: () -> Unit = {},
    onGiveUpChallenge: () -> Unit = {},
    // Netplay mode
    isNetplayMode: Boolean = false,
    netplayPeerUsername: String? = null,
    netplayPeerLatencyMs: Int = 0,
    netplayPeerDisconnected: Boolean = false,
    netplayPausedByUsername: String? = null,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit,
    onToggleCheat: (cheatId: String, enabled: Boolean) -> Unit,
) {
    val isChallengeActive = challengeId != null
    var activePanel by remember { mutableStateOf(DashboardPanel.DASHBOARD) }

    // Determine the effective layout: challenge mode overrides the dashboard panel
    data class DashboardTargetState(val panel: DashboardPanel, val isChallengeActive: Boolean)
    val targetState = DashboardTargetState(activePanel, isChallengeActive)

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 4 })
                .togetherWith(fadeOut() + slideOutVertically { -it / 4 })
        },
        label = "dashboardPanelTransition",
    ) { state ->
        when {
            state.panel == DashboardPanel.CHEATS -> {
                SecondaryCheatsPanel(
                    cheats = cheats,
                    onToggleCheat = onToggleCheat,
                    onDismiss = { activePanel = DashboardPanel.DASHBOARD },
                )
            }
            state.panel == DashboardPanel.ACHIEVEMENTS -> {
                SecondaryAchievementsPanel(
                    achievements = achievements,
                    achievementProgress = achievementProgress,
                    achievementUnlockedCount = achievementUnlockedCount,
                    achievementTotalCount = achievementTotalCount,
                    achievementEarnedPoints = achievementEarnedPoints,
                    achievementTotalPoints = achievementTotalPoints,
                    sessionAchievementUnlocks = sessionAchievementUnlocks,
                    sessionElapsedSeconds = sessionElapsedSeconds,
                    onDismiss = { activePanel = DashboardPanel.DASHBOARD },
                )
            }
            state.isChallengeActive -> {
                // Challenge mode dashboard layout
                ChallengeModeDashboard(
                    fps = fps,
                    frameTime = frameTime,
                    challengeElapsedMs = challengeElapsedMs,
                    challengeObjective = challengeObjective,
                    onCompleteChallenge = onCompleteChallenge,
                    onRestartChallenge = onRestartChallenge,
                    onGiveUpChallenge = onGiveUpChallenge,
                )
            }
            else -> {
                // Normal dashboard layout
                NormalDashboard(
                    fps = fps,
                    frameTime = frameTime,
                    activeSlot = activeSlot,
                    hasCheats = hasCheats,
                    enabledCheatCount = enabledCheatCount,
                    hasAchievements = hasAchievements,
                    achievementUnlockedCount = achievementUnlockedCount,
                    achievementTotalCount = achievementTotalCount,
                    isNetplayMode = isNetplayMode,
                    netplayPeerUsername = netplayPeerUsername,
                    netplayPeerLatencyMs = netplayPeerLatencyMs,
                    netplayPeerDisconnected = netplayPeerDisconnected,
                    netplayPausedByUsername = netplayPausedByUsername,
                    isFastForward = isFastForward,
                    rewindEnabled = rewindEnabled,
                    onCheatsClick = if (hasCheats) {
                        { activePanel = DashboardPanel.CHEATS }
                    } else {
                        null
                    },
                    onAchievementsClick = { activePanel = DashboardPanel.ACHIEVEMENTS },
                    onSave = onSave,
                    onLoad = onLoad,
                    onScreenshot = onScreenshot,
                    onToggleFastForward = onToggleFastForward,
                    onRewind = onRewind,
                )
            }
        }
    }
}

/**
 * Challenge mode dashboard layout.
 * Shows a small FPS card at the top, a large centered timer, the challenge objective,
 * and three action buttons (Complete, Restart, Give Up).
 */
@Composable
private fun ChallengeModeDashboard(
    fps: Float,
    frameTime: Float,
    challengeElapsedMs: Long,
    challengeObjective: String,
    onCompleteChallenge: () -> Unit,
    onRestartChallenge: () -> Unit,
    onGiveUpChallenge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = "Challenge mode dashboard" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Small FPS card at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            StatCard(
                value = "%.0f".format(fps),
                valueColor = fpsColor(fps),
                label = "%.1fms".format(frameTime),
                contentDesc = "%.0f FPS, %.1f ms frame time".format(fps, frameTime),
                modifier = Modifier.weight(1f),
            )
        }

        // Center area with timer and objective
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatChallengeTimer(challengeElapsedMs),
                style = SpTypography.DisplayMedium,
                color = SpColor.Primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = "Challenge timer: ${formatChallengeTimer(challengeElapsedMs)}"
                },
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = challengeObjective,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics {
                    contentDescription = "Challenge objective: $challengeObjective"
                },
            )
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmulationActionButton(
                icon = Icons.Filled.Check,
                label = "Complete",
                isActive = true,
                onClick = onCompleteChallenge,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = true,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.Refresh,
                label = "Restart",
                onClick = onRestartChallenge,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = true,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.Close,
                label = "Give Up",
                onClick = onGiveUpChallenge,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = true,
                useFocusRing = false,
            )
        }
    }
}

/**
 * Normal (non-challenge) dashboard layout.
 * Shows stat cards (FPS, save slot, cheats/netplay, and optionally achievements)
 * and a quick action row (save, load, screenshot, fast forward, rewind).
 */
@Composable
private fun NormalDashboard(
    fps: Float,
    frameTime: Float,
    activeSlot: Int,
    hasCheats: Boolean,
    enabledCheatCount: Int,
    hasAchievements: Boolean,
    achievementUnlockedCount: Int,
    achievementTotalCount: Int,
    isNetplayMode: Boolean,
    netplayPeerUsername: String?,
    netplayPeerLatencyMs: Int,
    netplayPeerDisconnected: Boolean,
    netplayPausedByUsername: String?,
    isFastForward: Boolean,
    rewindEnabled: Boolean,
    onCheatsClick: (() -> Unit)?,
    onAchievementsClick: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Stat cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            StatCard(
                value = "%.0f".format(fps),
                valueColor = fpsColor(fps),
                label = "%.1fms".format(frameTime),
                contentDesc = "%.0f FPS, %.1f ms frame time".format(fps, frameTime),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "Slot $activeSlot",
                valueColor = SpColor.OnBackground,
                label = "Save slot",
                contentDesc = "Active save slot $activeSlot",
                modifier = Modifier.weight(1f),
            )
            if (isNetplayMode) {
                // Netplay card replaces cheats card
                NetplayStatCard(
                    peerUsername = netplayPeerUsername,
                    latencyMs = netplayPeerLatencyMs,
                    isDisconnected = netplayPeerDisconnected,
                    pausedByUsername = netplayPausedByUsername,
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatCard(
                    value = if (hasCheats) "$enabledCheatCount active" else "No cheats",
                    valueColor = if (hasCheats && enabledCheatCount > 0) SpColor.Warning else SpColor.OnBackgroundTertiary,
                    label = if (hasCheats) "Cheats \u2022 Tap" else "Cheats",
                    contentDesc = if (hasCheats) "$enabledCheatCount cheats active, tap to manage" else "No cheats available",
                    modifier = Modifier.weight(1f),
                    onClick = onCheatsClick,
                )
            }
            if (hasAchievements) {
                StatCard(
                    value = "$achievementUnlockedCount/$achievementTotalCount",
                    valueColor = if (achievementUnlockedCount > 0) SpColor.Success else SpColor.OnBackgroundTertiary,
                    label = "Trophies \u2022 Tap",
                    contentDesc = "$achievementUnlockedCount of $achievementTotalCount achievements unlocked, tap to view",
                    modifier = Modifier.weight(1f),
                    onClick = onAchievementsClick,
                )
            }
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        // Quick action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmulationActionButton(
                icon = Icons.Filled.Save,
                label = "Save",
                onClick = onSave,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.FolderOpen,
                label = "Load",
                onClick = onLoad,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.CameraAlt,
                label = "Screenshot",
                onClick = onScreenshot,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                label = if (isFastForward) "Normal" else "Fast",
                isActive = isFastForward,
                onClick = onToggleFastForward,
                buttonSize = ACTION_BUTTON_SIZE,
                iconSize = ACTION_ICON_SIZE,
                showLabel = false,
                useFocusRing = false,
            )
            if (rewindEnabled) {
                EmulationActionButton(
                    icon = Icons.Filled.FastRewind,
                    label = "Rewind",
                    onClick = onRewind,
                    buttonSize = ACTION_BUTTON_SIZE,
                    iconSize = ACTION_ICON_SIZE,
                    showLabel = false,
                    useFocusRing = false,
                )
            }
        }
    }
}

/**
 * Netplay info stat card showing peer username, latency, and connection status.
 * Replaces the cheats card when a netplay session is active.
 */
@Composable
private fun NetplayStatCard(
    peerUsername: String?,
    latencyMs: Int,
    isDisconnected: Boolean,
    pausedByUsername: String?,
    modifier: Modifier = Modifier,
) {
    val displayValue: String
    val displayValueColor: Color
    val displayLabel: String
    val displayContentDesc: String

    when {
        isDisconnected -> {
            displayValue = "Offline"
            displayValueColor = SpColor.Error
            displayLabel = "Netplay"
            displayContentDesc = "Netplay peer disconnected"
        }
        pausedByUsername != null -> {
            displayValue = peerUsername ?: "Peer"
            displayValueColor = SpColor.Warning
            displayLabel = "Paused by $pausedByUsername"
            displayContentDesc = "Netplay paused by $pausedByUsername"
        }
        else -> {
            displayValue = peerUsername ?: "Peer"
            displayValueColor = SpColor.OnBackground
            displayLabel = "${latencyMs}ms"
            displayContentDesc = "Netplay peer: ${peerUsername ?: "unknown"}, latency ${latencyMs}ms"
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(SpColor.Card)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = displayContentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isDisconnected) Icons.Filled.WifiOff else Icons.Filled.Wifi,
                contentDescription = null,
                tint = if (isDisconnected) SpColor.Error else SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(SpSpacing.IconSmall),
            )
            Spacer(Modifier.width(SpSpacing.XSmall))
            Text(
                text = displayValue,
                style = SpTypography.HeadlineSmall,
                color = displayValueColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = displayLabel,
            style = SpTypography.LabelSmall,
            color = if (isDisconnected) SpColor.OnBackgroundTertiary else latencyColor(latencyMs),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Formats a challenge elapsed time in milliseconds into a readable timer string.
 * Uses MM:SS.cc format for durations under an hour, HH:MM:SS for longer durations.
 */
private fun formatChallengeTimer(elapsedMs: Long): String {
    val safeElapsedMs = elapsedMs.coerceAtLeast(0)
    val totalSeconds = safeElapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (safeElapsedMs % 1000) / 10  // centiseconds
    return if (minutes >= 60) {
        val hours = minutes / 60
        "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    } else {
        "%d:%02d.%02d".format(minutes, seconds, millis)
    }
}

/**
 * Returns a color for the given latency value:
 * green (<50ms), yellow (50-100ms), red (>100ms).
 */
private fun latencyColor(latencyMs: Int): Color = when {
    latencyMs < 50 -> SpColor.Success
    latencyMs < 100 -> SpColor.Warning
    else -> SpColor.Error
}

@Composable
private fun StatCard(
    value: String,
    valueColor: Color,
    label: String,
    contentDesc: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(SpColor.Card)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = contentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = SpTypography.HeadlineSmall,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Compact cheats toggle panel for the secondary dashboard.
 * Shows a scrollable list of cheats with toggle switches and a close button.
 */
@Composable
private fun SecondaryCheatsPanel(
    cheats: List<Cheat>,
    onToggleCheat: (cheatId: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
    ) {
        // Header row with title and close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SpSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cheats",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Close cheats panel"
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = SpColor.OnBackground,
                )
            }
        }

        if (cheats.isEmpty()) {
            Text(
                text = "No cheats available for this game.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.padding(vertical = SpSpacing.Medium),
            )
        } else {
            // Scrollable cheat list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                cheats.forEach { cheat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                            .background(SpColor.Card)
                            .clickable { onToggleCheat(cheat.id, !cheat.enabled) }
                            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium)
                            .semantics {
                                contentDescription = "Cheat: ${cheat.description}, ${if (cheat.enabled) "enabled" else "disabled"}"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cheat.description,
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackground,
                            modifier = Modifier.weight(1f).padding(end = SpSpacing.Small),
                            maxLines = 2,
                        )
                        SpSwitch(
                            checked = cheat.enabled,
                            onCheckedChange = { enabled ->
                                onToggleCheat(cheat.id, enabled)
                            },
                        )
                    }
                    Spacer(Modifier.height(SpSpacing.Small))
                }
            }
        }
    }
}

/**
 * Achievement tracker panel for the secondary dashboard.
 * Shows progress summary, optional session feed, and a scrollable list of all achievements.
 */
@Composable
private fun SecondaryAchievementsPanel(
    achievements: List<GameAchievement>,
    achievementProgress: List<AchievementProgress>,
    achievementUnlockedCount: Int,
    achievementTotalCount: Int,
    achievementEarnedPoints: Int,
    achievementTotalPoints: Int,
    sessionAchievementUnlocks: List<SessionAchievementUnlock>,
    sessionElapsedSeconds: Long,
    onDismiss: () -> Unit,
) {
    val unlockedIds = remember(achievementProgress) {
        achievementProgress.filter { it.unlockedAt != null }.map { it.achievementId }.toSet()
    }

    // Sort: locked first (by displayOrder), then unlocked
    val sortedAchievements = remember(achievements, unlockedIds) {
        val locked = achievements.filter { it.id !in unlockedIds }
            .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        val unlocked = achievements.filter { it.id in unlockedIds }
            .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
        locked + unlocked
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = "Achievements panel" },
    ) {
        // Header row with title and close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SpSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Achievements",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Close achievements panel"
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = SpColor.OnBackground,
                )
            }
        }

        // Progress summary
        Text(
            text = "$achievementUnlockedCount / $achievementTotalCount achievements \u2022 $achievementEarnedPoints / $achievementTotalPoints pts",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundSecondary,
            modifier = Modifier
                .padding(bottom = SpSpacing.Small)
                .semantics {
                    contentDescription = "$achievementUnlockedCount of $achievementTotalCount achievements, $achievementEarnedPoints of $achievementTotalPoints points"
                },
        )

        // Scrollable content: session feed + full list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Session achievement feed
            SessionAchievementFeed(
                sessionUnlocks = sessionAchievementUnlocks,
                sessionElapsedSeconds = sessionElapsedSeconds,
            )

            // Full achievement list
            sortedAchievements.forEach { achievement ->
                val isUnlocked = achievement.id in unlockedIds
                AchievementRow(
                    achievement = achievement,
                    isUnlocked = isUnlocked,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
            }
        }
    }
}

/**
 * "This Session" section showing achievements unlocked during the current play session.
 */
@Composable
private fun SessionAchievementFeed(
    sessionUnlocks: List<SessionAchievementUnlock>,
    sessionElapsedSeconds: Long,
) {
    Text(
        text = "This Session",
        style = SpTypography.TitleSmall,
        color = SpColor.OnBackgroundTertiary,
        modifier = Modifier.padding(bottom = SpSpacing.XSmall),
    )

    if (sessionUnlocks.isEmpty()) {
        Text(
            text = "No unlocks yet this session",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
            modifier = Modifier
                .padding(bottom = SpSpacing.Medium)
                .semantics { contentDescription = "No achievements unlocked this session" },
        )
    } else {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        sessionUnlocks.reversed().forEach { unlock ->
            val agoText = formatRelativeTime(nowMs - unlock.unlockedAtMs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                    .background(SpColor.SuccessContainer)
                    .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium)
                    .semantics {
                        contentDescription = "Unlocked: ${unlock.title}, ${unlock.points} points, $agoText"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unlock.title,
                        style = SpTypography.BodyMedium,
                        color = SpColor.Success,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${unlock.points} pts \u2022 $agoText",
                        style = SpTypography.LabelSmall,
                        color = SpColor.Success.copy(alpha = 0.7f),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SpColor.Success,
                    modifier = Modifier.size(SpSpacing.IconDefault),
                )
            }
            Spacer(Modifier.height(SpSpacing.XSmall))
        }
        Spacer(Modifier.height(SpSpacing.Small))
    }
}

/**
 * A single achievement row in the tracker list.
 * Locked achievements are full opacity; unlocked achievements are de-emphasized.
 */
@Composable
private fun AchievementRow(
    achievement: GameAchievement,
    isUnlocked: Boolean,
) {
    val alpha = if (isUnlocked) 0.7f else 1f
    val bgColor = if (isUnlocked) SpColor.Card.copy(alpha = 0.6f) else SpColor.Card

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(bgColor)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium)
            .semantics {
                contentDescription = "${achievement.title}, ${achievement.points} points, ${if (isUnlocked) "unlocked" else "locked"}"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = achievement.title,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = "${achievement.points} pts",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary.copy(alpha = alpha),
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Icon(
            imageVector = if (isUnlocked) Icons.Filled.Check else Icons.Filled.Lock,
            contentDescription = if (isUnlocked) "Unlocked" else "Locked",
            tint = if (isUnlocked) SpColor.Success.copy(alpha = alpha) else SpColor.OnBackgroundTertiary.copy(alpha = alpha),
            modifier = Modifier.size(SpSpacing.IconSmall),
        )
    }
}

/**
 * Formats a duration in milliseconds into a human-readable relative time string.
 */
private fun formatRelativeTime(durationMs: Long): String {
    val seconds = durationMs / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        else -> "${seconds / 3600}h ago"
    }
}
