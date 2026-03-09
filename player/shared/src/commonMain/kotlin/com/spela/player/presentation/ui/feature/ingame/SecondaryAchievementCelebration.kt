package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementEventType
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

private const val CELEBRATION_DISPLAY_DURATION_MS = 6_000L
private const val ENTRANCE_ANIMATION_MS = 400
private const val EXIT_ANIMATION_MS = 300
private const val GLOW_PULSE_DURATION_MS = 1_500
private val BANNER_CORNER_RADIUS = SpSpacing.RadiusLarge
private val BANNER_MAX_WIDTH = 340.dp
private val GLOW_STROKE_WIDTH = SpSpacing.XXSmall

/**
 * Achievement celebration overlay for the secondary screen.
 *
 * Displays a visually rich banner when achievements are unlocked or the game is completed.
 * Events are queued so rapid unlocks show one at a time, each for its full duration.
 *
 * This composable does NOT consume pointer events, so the user can still interact
 * with the underlying pager/controls while the celebration is visible.
 *
 * @param achievementEvent The current achievement event from [EmulationState], or null.
 * @param onCelebrationStarted Called when a new celebration begins displaying, so the
 *   parent can reset burn-in timers or perform other side effects.
 */
@Composable
fun SecondaryAchievementCelebration(
    achievementEvent: AchievementEvent?,
    onCelebrationStarted: () -> Unit = {},
) {
    // Queue of pending celebrations. We collect events into this list and display
    // them one at a time. Each event is shown for CELEBRATION_DISPLAY_DURATION_MS
    // before being removed, which triggers the next one in the queue.
    val celebrationQueue = remember { mutableStateListOf<AchievementEvent>() }
    var currentCelebration by remember { mutableStateOf<AchievementEvent?>(null) }

    // Enqueue new achievement events when the parameter changes.
    // We key on the event identity so each new event fires exactly once.
    // The event may be cleared from EmulationState by the primary screen's
    // AchievementPopup, but we've already captured our own copy in the queue.
    LaunchedEffect(achievementEvent) {
        val event = achievementEvent ?: return@LaunchedEffect
        if (event.type == AchievementEventType.ACHIEVEMENT_TRIGGERED ||
            event.type == AchievementEventType.GAME_COMPLETED
        ) {
            celebrationQueue.add(event)
        }
    }

    // Promote the next queued item to current when there is no active celebration.
    LaunchedEffect(celebrationQueue.size, currentCelebration) {
        if (currentCelebration == null && celebrationQueue.isNotEmpty()) {
            currentCelebration = celebrationQueue.removeFirst()
        }
    }

    // Auto-dismiss the current celebration after the display duration.
    LaunchedEffect(currentCelebration) {
        val celebration = currentCelebration ?: return@LaunchedEffect
        onCelebrationStarted()
        // Keep the celebration visible for its full duration, then clear it
        // so the next queued item can be promoted.
        delay(CELEBRATION_DISPLAY_DURATION_MS)
        // Only clear if it's still the same celebration (defensive check)
        if (currentCelebration == celebration) {
            currentCelebration = null
        }
    }

    val isVisible = currentCelebration != null

    // Cache the last non-null celebration so the banner content persists during the
    // exit fade-out animation (when currentCelebration has already been set to null).
    var lastCelebration by remember { mutableStateOf<AchievementEvent?>(null) }
    LaunchedEffect(currentCelebration) {
        if (currentCelebration != null) lastCelebration = currentCelebration
    }

    // The overlay does NOT fill the entire screen and does NOT consume pointer events.
    // It sits in a Box that fills the parent but uses contentAlignment to center the banner.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(ENTRANCE_ANIMATION_MS)) +
                scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(ENTRANCE_ANIMATION_MS),
                ),
            exit = fadeOut(tween(EXIT_ANIMATION_MS)),
        ) {
            val celebration = lastCelebration ?: return@AnimatedVisibility
            CelebrationBanner(event = celebration)
        }
    }
}

/**
 * The visual banner card displayed for a single achievement celebration.
 */
@Composable
private fun CelebrationBanner(event: AchievementEvent) {
    val isGameCompleted = event.type == AchievementEventType.GAME_COMPLETED
    val accentColor = if (isGameCompleted) SpColor.Success else SpColor.Warning
    val headerText = if (isGameCompleted) "GAME COMPLETED" else "ACHIEVEMENT UNLOCKED"

    // Subtle pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "celebrationGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val cornerRadiusPx = BANNER_CORNER_RADIUS
    val strokeWidthPx = GLOW_STROKE_WIDTH

    Column(
        modifier = Modifier
            .width(BANNER_MAX_WIDTH)
            .clip(RoundedCornerShape(cornerRadiusPx))
            .background(SpColor.Surface.copy(alpha = 0.95f))
            .drawBehind {
                // Pulsing glow border
                drawRoundRect(
                    color = accentColor.copy(alpha = glowAlpha),
                    cornerRadius = CornerRadius(cornerRadiusPx.toPx()),
                    style = Stroke(width = strokeWidthPx.toPx()),
                )
            }
            .padding(SpSpacing.Default)
            .semantics {
                contentDescription = if (isGameCompleted) {
                    "Game completed celebration: ${event.title}"
                } else {
                    "Achievement unlocked: ${event.title}, ${event.points} points"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header label
        Text(
            text = headerText,
            style = SpTypography.LabelMedium.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = accentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(SpSpacing.Medium))

        // Achievement title
        if (event.title.isNotEmpty()) {
            Text(
                text = event.title,
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SpSpacing.Small))
        }

        // Achievement description
        if (event.description.isNotEmpty()) {
            Text(
                text = event.description,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SpSpacing.Medium))
        }

        // Points badge or completion indicator
        if (isGameCompleted) {
            CompletionIndicator()
        } else if (event.points > 0) {
            PointsBadge(points = event.points, accentColor = accentColor)
        }
    }
}

/**
 * Points display for achievement unlocks, shown as "25 pts" with the accent color.
 */
@Composable
private fun PointsBadge(points: Int, accentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusPill))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
    ) {
        Text(
            text = "$points",
            style = SpTypography.HeadlineMedium,
            color = accentColor,
        )
        Spacer(Modifier.width(SpSpacing.XSmall))
        Text(
            text = "pts",
            style = SpTypography.LabelMedium,
            color = accentColor.copy(alpha = 0.8f),
        )
    }
}

/**
 * Special "100%" indicator shown for game completion events.
 */
@Composable
private fun CompletionIndicator() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusPill))
            .background(SpColor.Success.copy(alpha = 0.15f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "100%",
            style = SpTypography.HeadlineMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = SpColor.Success,
        )
    }
}
