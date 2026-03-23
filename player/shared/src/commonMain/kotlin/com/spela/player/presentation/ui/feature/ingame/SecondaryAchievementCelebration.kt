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
import androidx.compose.ui.graphics.Brush
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
import com.spela.player.presentation.ui.components.SpConfetti
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

private const val ENTRANCE_ANIMATION_MS = 400
private const val EXIT_ANIMATION_MS = 300
private val BANNER_CORNER_RADIUS = SpSpacing.RadiusLarge
private val BANNER_MAX_WIDTH = 340.dp

// Rarity tier thresholds (matching server-side definition)
private enum class RarityTier(val label: String) {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    ULTRA_RARE("Ultra Rare"),
    LEGENDARY("Legendary"),
}

private fun rarityTier(percent: Double): RarityTier = when {
    percent < 1.0 -> RarityTier.LEGENDARY
    percent < 5.0 -> RarityTier.ULTRA_RARE
    percent < 20.0 -> RarityTier.RARE
    percent < 50.0 -> RarityTier.UNCOMMON
    else -> RarityTier.COMMON
}

private val SILVER = Color(0xFFC0C0C0)
private val GOLD = Color(0xFFFFD700)

private fun tierAccentColor(tier: RarityTier): Color = when (tier) {
    RarityTier.COMMON -> SpColor.Warning
    RarityTier.UNCOMMON -> SILVER
    RarityTier.RARE -> GOLD
    RarityTier.ULTRA_RARE -> SpColor.Primary
    RarityTier.LEGENDARY -> SpColor.Primary
}

private fun tierDisplayDuration(tier: RarityTier, isGameCompleted: Boolean): Long = when {
    isGameCompleted -> 8_000L
    tier == RarityTier.LEGENDARY -> 7_000L
    tier == RarityTier.ULTRA_RARE -> 6_500L
    tier == RarityTier.RARE -> 6_000L
    else -> 5_000L
}

private fun tierGlowPulseDuration(tier: RarityTier): Int = when (tier) {
    RarityTier.LEGENDARY -> 800
    RarityTier.ULTRA_RARE -> 1_000
    RarityTier.RARE -> 1_200
    else -> 1_500
}

private fun tierStrokeWidth(tier: RarityTier): Float = when (tier) {
    RarityTier.LEGENDARY -> 4f
    RarityTier.ULTRA_RARE -> 3f
    RarityTier.RARE -> 2.5f
    else -> 2f
}

private fun tierShowConfetti(tier: RarityTier, isGameCompleted: Boolean): Boolean =
    isGameCompleted || tier == RarityTier.LEGENDARY

private fun tierConfettiCount(isGameCompleted: Boolean): Int =
    if (isGameCompleted) 80 else 50

/**
 * Achievement celebration overlay for the secondary screen.
 *
 * Displays a visually rich banner when achievements are unlocked or the game is completed.
 * Events are queued so rapid unlocks show one at a time, each for its full duration.
 * Celebration intensity scales with the achievement's rarity tier.
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
    val celebrationQueue = remember { mutableStateListOf<AchievementEvent>() }
    var currentCelebration by remember { mutableStateOf<AchievementEvent?>(null) }

    LaunchedEffect(achievementEvent) {
        val event = achievementEvent ?: return@LaunchedEffect
        if (event.type == AchievementEventType.ACHIEVEMENT_TRIGGERED ||
            event.type == AchievementEventType.GAME_COMPLETED
        ) {
            celebrationQueue.add(event)
        }
    }

    LaunchedEffect(celebrationQueue.size, currentCelebration) {
        if (currentCelebration == null && celebrationQueue.isNotEmpty()) {
            currentCelebration = celebrationQueue.removeFirst()
        }
    }

    LaunchedEffect(currentCelebration) {
        val celebration = currentCelebration ?: return@LaunchedEffect
        onCelebrationStarted()
        val isGameCompleted = celebration.type == AchievementEventType.GAME_COMPLETED
        val tier = rarityTier(celebration.rarityPercent)
        val duration = tierDisplayDuration(tier, isGameCompleted)
        delay(duration)
        if (currentCelebration == celebration) {
            currentCelebration = null
        }
    }

    val isVisible = currentCelebration != null

    var lastCelebration by remember { mutableStateOf<AchievementEvent?>(null) }
    LaunchedEffect(currentCelebration) {
        if (currentCelebration != null) lastCelebration = currentCelebration
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Confetti layer (behind the banner)
        val celebration = lastCelebration
        if (isVisible && celebration != null) {
            val isGameCompleted = celebration.type == AchievementEventType.GAME_COMPLETED
            val tier = rarityTier(celebration.rarityPercent)
            if (tierShowConfetti(tier, isGameCompleted)) {
                SpConfetti(
                    particleCount = tierConfettiCount(isGameCompleted),
                    durationMs = if (isGameCompleted) 4000 else 3000,
                )
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(ENTRANCE_ANIMATION_MS)) +
                scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(ENTRANCE_ANIMATION_MS),
                ),
            exit = fadeOut(tween(EXIT_ANIMATION_MS)),
        ) {
            val event = lastCelebration ?: return@AnimatedVisibility
            CelebrationBanner(event = event)
        }
    }
}

/**
 * The visual banner card displayed for a single achievement celebration.
 * Visual intensity scales with rarity tier.
 */
@Composable
private fun CelebrationBanner(event: AchievementEvent) {
    val isGameCompleted = event.type == AchievementEventType.GAME_COMPLETED
    val tier = rarityTier(event.rarityPercent)
    val accentColor = if (isGameCompleted) SpColor.Success else tierAccentColor(tier)
    val headerText = if (isGameCompleted) {
        "100% COMPLETE"
    } else when (tier) {
        RarityTier.LEGENDARY -> "LEGENDARY ACHIEVEMENT"
        RarityTier.ULTRA_RARE -> "ULTRA RARE ACHIEVEMENT"
        RarityTier.RARE -> "RARE ACHIEVEMENT"
        else -> "ACHIEVEMENT UNLOCKED"
    }

    val glowPulseDuration = tierGlowPulseDuration(tier)
    val strokeWidth = tierStrokeWidth(tier)

    val infiniteTransition = rememberInfiniteTransition(label = "celebrationGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(glowPulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    // Animated gradient rotation for legendary tier
    val gradientAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradientAngle",
    )

    val cornerRadiusPx = BANNER_CORNER_RADIUS

    Column(
        modifier = Modifier
            .width(BANNER_MAX_WIDTH)
            .clip(RoundedCornerShape(cornerRadiusPx))
            .background(SpColor.Surface.copy(alpha = 0.95f))
            .drawBehind {
                val cr = CornerRadius(cornerRadiusPx.toPx())
                val sw = strokeWidth.dp.toPx()
                if (tier == RarityTier.LEGENDARY && !isGameCompleted) {
                    // Animated gradient border for legendary
                    val colors = listOf(
                        SpColor.Primary.copy(alpha = glowAlpha),
                        GOLD.copy(alpha = glowAlpha),
                        SpColor.Primary.copy(alpha = glowAlpha),
                    )
                    val brush = Brush.linearGradient(
                        colors = colors,
                        start = androidx.compose.ui.geometry.Offset(
                            size.width * gradientAngle,
                            0f,
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            size.width * (1f - gradientAngle),
                            size.height,
                        ),
                    )
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = cr,
                        style = Stroke(width = sw),
                    )
                } else {
                    drawRoundRect(
                        color = accentColor.copy(alpha = glowAlpha),
                        cornerRadius = cr,
                        style = Stroke(width = sw),
                    )
                }
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

        // Points badge / rarity chip / completion indicator
        if (isGameCompleted) {
            CompletionIndicator()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (event.points > 0) {
                    PointsBadge(points = event.points, accentColor = accentColor)
                }
                if (event.rarityPercent > 0 && tier != RarityTier.COMMON) {
                    RarityBadge(tier = tier, percent = event.rarityPercent, accentColor = accentColor)
                }
            }
        }
    }
}

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

@Composable
private fun RarityBadge(tier: RarityTier, percent: Double, accentColor: Color) {
    val formattedPercent = if (percent < 0.1) "<0.1%" else "${String.format("%.1f", percent)}%"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusPill))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
    ) {
        Text(
            text = "${tier.label} \u00B7 $formattedPercent",
            style = SpTypography.LabelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
        )
    }
}

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
