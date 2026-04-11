package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes

@Composable
fun SpProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = SpColor.SurfaceBright,
    progressColors: List<Color> = listOf(SpColor.Primary, SpColor.Accent),
    showPercentage: Boolean = false,
    label: String? = null,
    onGradient: Boolean = false,
) {
    val resolvedTrackColor = if (onGradient) Color.White.copy(alpha = 0.12f) else trackColor
    val resolvedProgressColors = if (onGradient) listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.90f)) else progressColors
    val resolvedLabelColor = if (onGradient) Color.White.copy(alpha = 0.70f) else SpColor.OnBackgroundSecondary

    var maxProgress by remember { mutableFloatStateOf(0f) }
    val clampedProgress = progress.coerceIn(0f, 1f)
    // Reset on new download (progress drops to near zero)
    if (clampedProgress < 0.01f) maxProgress = 0f
    maxProgress = maxOf(maxProgress, clampedProgress)

    val animatedProgress by animateFloatAsState(
        targetValue = maxProgress,
        animationSpec = tween(300),
        label = "progressBarAnimation",
    )

    Column(modifier = modifier) {
        if (label != null || showPercentage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = SpTypography.LabelSmall,
                        color = resolvedLabelColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showPercentage) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = SpTypography.LabelSmall,
                        color = resolvedLabelColor,
                    )
                }
            }
            Spacer(Modifier.height(SpSpacing.XSmall))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(resolvedTrackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(resolvedProgressColors)),
            )
        }
    }
}

@Composable
fun SpDownloadProgressBar(
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (progress < 0f) {
            if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SpColor.Accent,
                    trackColor = SpColor.SurfaceBright,
                )
            }
        } else {
            SpProgressBar(
                progress = progress,
                progressColors = listOf(SpColor.Accent, SpColor.AccentLight),
            )
        }
        Spacer(Modifier.height(SpSpacing.XSmall))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatBytes(bytesDownloaded),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (totalBytes < 0) "..." else formatBytes(totalBytes),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

