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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    val resolvedTrackColor = if (onGradient) SpColor.OnGradientTrack else trackColor
    val resolvedProgressColors = if (onGradient) listOf(SpColor.OnGradientSecondary, SpColor.OnGradientPrimary) else progressColors
    // Original alpha here was 0.70f, slightly stronger than
    // OnGradientSecondary (0.65). Keeping the literal preserves visual
    // parity — consolidating on 0.65 is a separate design call.
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
    onGradient: Boolean = false,
    /** Smoothed bytes-per-second. 0 hides the speed label so a stalled
     *  download doesn't show a stale value (#801). */
    bytesPerSecond: Long = 0,
    /** Paused/interrupted-but-resumable: tints the bar amber and labels it
     *  "Paused" instead of showing a (stale) speed. (#1296) */
    paused: Boolean = false,
) {
    val byteLabelColor = if (onGradient) SpColor.OnGradientSecondary else SpColor.OnBackgroundTertiary
    val indeterminateTrackColor = if (onGradient) SpColor.OnGradientTrack else SpColor.SurfaceBright
    val indeterminateColor = if (onGradient) SpColor.OnGradientPrimary else SpColor.Accent

    // Latch the determinate path once we see a non-negative progress
    // value. Without this, a transient `progress = -1f` emission (e.g.
    // a brief IDLE re-emit, or a multi-disc transition where totalBytes
    // momentarily resets to -1) unmounts the inner SpProgressBar — which
    // loses its remembered maxProgress and animates back from 0 the next
    // frame. That's the same shape of flicker as #797: the *bar* doesn't
    // change visibility, but its height drops from 8.dp (determinate) to
    // 6.dp (indeterminate) and back, giving the page a "jumping every
    // frame" feel. Once we have a real progress value, stick with the
    // determinate bar even if subsequent emissions briefly go negative.
    // See #894.
    var sawDeterminate by remember { mutableStateOf(false) }
    if (progress >= 0f) sawDeterminate = true

    Column(modifier = modifier) {
        if (!sawDeterminate) {
            if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("sp_download_progress_bar_indeterminate"),
                    color = indeterminateColor,
                    trackColor = indeterminateTrackColor,
                )
            }
        } else {
            Column(modifier = Modifier.testTag("sp_download_progress_bar_determinate")) {
                SpProgressBar(
                    progress = progress.coerceAtLeast(0f),
                    progressColors = if (paused) {
                        listOf(SpColor.DownloadPaused, SpColor.DownloadPaused)
                    } else {
                        listOf(SpColor.Accent, SpColor.AccentLight)
                    },
                    onGradient = onGradient,
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.XSmall))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatBytes(bytesDownloaded),
                style = SpTypography.LabelSmall,
                color = byteLabelColor,
            )
            if (paused) {
                Text(
                    text = " · Paused",
                    style = SpTypography.LabelSmall,
                    color = byteLabelColor,
                )
            } else if (bytesPerSecond > 0) {
                Text(
                    text = " · ${formatBytes(bytesPerSecond)}/s",
                    style = SpTypography.LabelSmall,
                    color = byteLabelColor,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (totalBytes < 0) "..." else formatBytes(totalBytes),
                style = SpTypography.LabelSmall,
                color = byteLabelColor,
            )
        }
    }
}

