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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun SpProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = SpColor.SurfaceBright,
    progressColors: List<Color> = listOf(SpColor.Primary, SpColor.Accent),
    showPercentage: Boolean = false,
    label: String? = null,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
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
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showPercentage) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundSecondary,
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
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(progressColors)),
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
        SpProgressBar(
            progress = progress,
            progressColors = listOf(SpColor.Accent, SpColor.AccentLight),
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatBytes(bytesDownloaded),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatBytes(totalBytes),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
