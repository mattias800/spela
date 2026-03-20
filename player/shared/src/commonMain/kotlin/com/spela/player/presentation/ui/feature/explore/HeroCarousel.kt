package com.spela.player.presentation.ui.feature.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.presentation.ui.components.LocalAnimationsEnabled
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating
import com.spela.player.util.parseHexColor
import kotlinx.coroutines.delay

private val HERO_HEIGHT = 320.dp
private const val AUTO_ADVANCE_DELAY_MS = 8_000L

@Composable
fun HeroCarousel(
    featuredGames: List<FeaturedGame>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (featuredGames.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var dotTapKey by remember { mutableStateOf(0) }
    val animationsEnabled = LocalAnimationsEnabled.current

    // Auto-advance
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused && animationsEnabled) {
            delay(AUTO_ADVANCE_DELAY_MS)
            currentIndex = (currentIndex + 1) % featuredGames.size
        }
    }

    // Resume auto-advance after dot tap pause
    LaunchedEffect(dotTapKey) {
        if (dotTapKey > 0) {
            delay(AUTO_ADVANCE_DELAY_MS)
            isPaused = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .testTag("hero_carousel")
            .pointerInput(featuredGames.size) {
                var dragConsumed = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        isPaused = true
                        dragConsumed = false
                    },
                    onDragEnd = {
                        isPaused = false
                        dragConsumed = false
                    },
                ) { _, dragAmount ->
                    if (dragConsumed) return@detectHorizontalDragGestures
                    if (dragAmount < -40f) {
                        currentIndex = (currentIndex + 1) % featuredGames.size
                        dragConsumed = true
                    } else if (dragAmount > 40f) {
                        currentIndex = (currentIndex - 1 + featuredGames.size) % featuredGames.size
                        dragConsumed = true
                    }
                }
            },
    ) {
        // Hero image with crossfade
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "heroCarousel",
        ) { index ->
            val game = featuredGames[index]
            HeroSlide(
                game = game,
                onGameSelected = { onGameSelected(game.gameId) },
            )
        }

        // Navigation dots at bottom center
        if (featuredGames.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SpSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                featuredGames.forEachIndexed { index, _ ->
                    val isActive = index == currentIndex
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) SpColor.Primary
                                else SpColor.OnBackground.copy(alpha = 0.4f),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 12.dp),
                            ) {
                                currentIndex = index
                                isPaused = true
                                dotTapKey++
                            }
                            .semantics {
                                contentDescription = "Slide ${index + 1} of ${featuredGames.size}"
                                role = Role.Button
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSlide(
    game: FeaturedGame,
    onGameSelected: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("hero_slide_${game.gameId}")
            .semantics {
                contentDescription = "Featured: ${game.title}"
            },
    ) {
        // Hero art background
        if (game.heroUrl != null) {
            SubcomposeAsyncImage(
                model = game.heroUrl,
                contentDescription = "Hero art for ${game.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpColor.SurfaceVariant),
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpColor.SurfaceVariant),
                    )
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpColor.SurfaceVariant),
            )
        }

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SpColor.Background.copy(alpha = 0.3f),
                            SpColor.Background.copy(alpha = 0.85f),
                            SpColor.Background,
                        ),
                    ),
                ),
        )

        // Content overlay (centered)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(SpSpacing.XLarge)
                .padding(bottom = SpSpacing.XLarge)
                .fillMaxWidth(),
        ) {
            // Logo or title text fallback
            if (game.logoUrl != null) {
                SubcomposeAsyncImage(
                    model = game.logoUrl,
                    contentDescription = "${game.title} logo",
                    modifier = Modifier
                        .height(80.dp)
                        .fillMaxWidth(0.7f),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Text(
                            text = game.title,
                            style = SpTypography.DisplaySmall,
                            color = SpColor.OnBackground,
                        )
                    },
                    error = {
                        Text(
                            text = game.title,
                            style = SpTypography.DisplaySmall,
                            color = SpColor.OnBackground,
                        )
                    },
                )
            } else {
                Text(
                    text = game.title,
                    style = SpTypography.DisplaySmall,
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(SpSpacing.Small))

            // Metadata row: console badge, rating, genre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                // Console badge — full name since the hero has plenty of space
                val consoleColor = parseHexColor(game.consoleColor, SpColor.Primary)
                SpChip(
                    text = game.consoleName.ifEmpty { game.consoleAbbreviation.uppercase() },
                    color = consoleColor,
                    isSelected = true,
                    onGradient = true,
                )

                // Rating
                if (game.rating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(SpSpacing.IconSmall),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelMedium,
                            color = SpColor.OnBackground,
                        )
                    }
                }

                // Genre
                if (game.genre.isNotEmpty()) {
                    SpChip(text = game.genre, onGradient = true)
                }
            }

            Spacer(Modifier.height(SpSpacing.Medium))

            // Action button
            SpButton(
                text = "View Game",
                onClick = onGameSelected,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(SpSpacing.IconDefault),
                    )
                },
            )
        }
    }
}

@Composable
fun HeroCarouselSkeleton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .testTag("hero_carousel_skeleton"),
    ) {
        SpShimmer(
            modifier = Modifier.fillMaxSize(),
            width = 400.dp,
            height = HERO_HEIGHT,
        )
    }
}

