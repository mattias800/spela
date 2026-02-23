package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.launch

@Composable
fun ScreenshotLightbox(
    visible: Boolean,
    screenshotUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { screenshotUrls.size },
        )
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(visible, initialIndex) {
            if (visible) {
                pagerState.scrollToPage(initialIndex)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // Image counter
            Text(
                text = "${pagerState.currentPage + 1} / ${screenshotUrls.size}",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = SpSpacing.XLarge),
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SpSpacing.Medium),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = SpSpacing.XXXLarge),
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = screenshotUrls[page],
                        contentDescription = "Screenshot ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = SpSpacing.XLarge)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* stop propagation */ },
                            ),
                    )
                }
            }

            // Navigation arrows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(horizontal = SpSpacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous screenshot",
                        tint = if (pagerState.currentPage > 0) SpColor.OnBackground else SpColor.OnBackgroundTertiary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    enabled = pagerState.currentPage < screenshotUrls.size - 1,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next screenshot",
                        tint = if (pagerState.currentPage < screenshotUrls.size - 1) SpColor.OnBackground else SpColor.OnBackgroundTertiary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            // Dismiss hint
            Text(
                text = "Tap to close",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SpSpacing.XLarge),
            )
        }
    }
}
