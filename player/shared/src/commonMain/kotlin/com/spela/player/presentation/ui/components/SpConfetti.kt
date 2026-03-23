package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.spela.player.presentation.ui.theme.SpColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CONFETTI_COLORS = listOf(
    SpColor.Primary,
    SpColor.Secondary,
    SpColor.Accent,
    Color(0xFFFFD700), // Gold
    SpColor.Success,
    Color(0xFFFF6B81), // Rose
    Color(0xFF00D2FF), // Cyan
)

private data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float,
    val isRect: Boolean,
)

/**
 * DESIGN component — canvas-based confetti particle animation overlay.
 *
 * Renders animated confetti particles that burst from the center and fall with
 * gravity. Used for game completion and legendary achievement celebrations.
 *
 * @param particleCount Number of confetti particles to render.
 * @param durationMs Total animation duration in milliseconds.
 */
@Composable
fun SpConfetti(
    modifier: Modifier = Modifier,
    particleCount: Int = 60,
    durationMs: Int = 3000,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMs, easing = LinearEasing),
        )
    }

    val particles = remember {
        val rng = Random(System.nanoTime())
        List(particleCount) {
            val angle = rng.nextDouble(0.0, 2 * PI).toFloat()
            val speed = rng.nextFloat() * 400f + 150f
            ConfettiParticle(
                startX = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f,
                startY = 0.45f + (rng.nextFloat() - 0.5f) * 0.1f,
                velocityX = cos(angle) * speed,
                velocityY = sin(angle) * speed - 300f, // bias upward
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 720f,
                color = CONFETTI_COLORS[rng.nextInt(CONFETTI_COLORS.size)],
                size = rng.nextFloat() * 6f + 4f,
                isRect = rng.nextBoolean(),
            )
        }
    }

    val t = progress.value

    Canvas(modifier = modifier.fillMaxSize()) {
        val gravity = 600f
        val w = size.width
        val h = size.height

        for (p in particles) {
            val elapsed = t * (durationMs / 1000f)
            val x = p.startX * w + p.velocityX * elapsed
            val y = p.startY * h + p.velocityY * elapsed + 0.5f * gravity * elapsed * elapsed
            val alpha = (1f - t).coerceIn(0f, 1f)
            val rotation = p.rotation + p.rotationSpeed * elapsed

            if (x < -50f || x > w + 50f || y > h + 50f) continue

            rotate(rotation, pivot = Offset(x, y)) {
                if (p.isRect) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size, y - p.size * 0.4f),
                        size = Size(p.size * 2f, p.size * 0.8f),
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size * 0.6f,
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}
