package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Set to `false` in tests to replace infinite shimmer/pulse animations with
 * static placeholders. This prevents Compose test `waitForIdle()` from
 * hanging on `rememberInfiniteTransition`.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

@Composable
fun SpLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
    color: Color = SpColor.Primary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpPulseDots(color = color)
        if (message != null) {
            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = message,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}

@Composable
fun SpPulseDots(
    modifier: Modifier = Modifier,
    color: Color = SpColor.Primary,
    dotSize: Dp = 10.dp,
) {
    val animationsEnabled = LocalAnimationsEnabled.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (animationsEnabled) {
            val transition = rememberInfiniteTransition(label = "pulseDots")
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 200),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseDotAlpha$index",
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        } else {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .alpha(0.6f)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
fun SpShimmer(
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
    height: Dp = 16.dp,
) {
    val alpha = if (LocalAnimationsEnabled.current) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shimmerAlpha",
        )
        animatedAlpha
    } else {
        0.35f
    }

    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
            .background(SpColor.SurfaceBright.copy(alpha = alpha)),
    )
}

@Composable
fun SpGameCardSkeleton(
    modifier: Modifier = Modifier,
) {
    SpCard(onGradient = true, modifier = modifier) {
        Column {
            SpShimmer(
                modifier = Modifier.fillMaxWidth(),
                width = SpSpacing.CoverMediumWidth,
                height = SpSpacing.CoverMediumHeight,
            )
            Column(modifier = Modifier.fillMaxWidth().height(SpSpacing.XXXLarge)) {
                Spacer(Modifier.height(SpSpacing.Small))
                SpShimmer(width = 100.dp, height = 14.dp)
                Spacer(Modifier.height(SpSpacing.XSmall))
                SpShimmer(width = 60.dp, height = 12.dp)
            }
        }
    }
}
