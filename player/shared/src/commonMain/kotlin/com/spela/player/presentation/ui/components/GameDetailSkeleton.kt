package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Loading skeleton for the game detail screen.
 *
 * Uses [GameDetailLayout] so the shimmer placeholders occupy exactly the
 * same positions as the real content — giving a smooth perceived-loading
 * experience and preventing layout jumps.
 */
@Composable
fun GameDetailSkeleton(
    onBack: () -> Unit,
) {
    GameDetailLayout(
        topBar = {
            SpTopBar(
                title = "",
                showBack = true,
                onBack = onBack,
            )
        },
        coverArt = { modifier, _ ->
            SpShimmer(modifier = modifier, width = 0.dp, height = 0.dp)
        },
        coverExtra = { isPortrait ->
            // Rating skeleton below cover (landscape only)
            if (!isPortrait) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    repeat(5) {
                        SpShimmer(width = 28.dp, height = 28.dp)
                    }
                }
            }
        },
        sections = {
            // Info section skeleton
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                ) {
                    // Title
                    SpShimmer(width = 220.dp, height = 28.dp)
                    Spacer(Modifier.height(SpSpacing.Small))

                    // Chips row (console + verification + region)
                    Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                        SpShimmer(width = 64.dp, height = 24.dp)
                        SpShimmer(width = 80.dp, height = 24.dp)
                        SpShimmer(width = 56.dp, height = 24.dp)
                    }
                    Spacer(Modifier.height(SpSpacing.XLarge))

                    // Action buttons row (split button + actions menu)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        SpShimmer(
                            modifier = Modifier.weight(1f),
                            width = 0.dp,
                            height = 44.dp,
                        )
                        SpShimmer(width = 44.dp, height = 44.dp)
                    }
                    Spacer(Modifier.height(SpSpacing.XLarge))

                    // Description heading
                    SpShimmer(width = 60.dp, height = 20.dp)
                    Spacer(Modifier.height(SpSpacing.Small))

                    // Description lines
                    SpShimmer(modifier = Modifier.fillMaxWidth(), width = 0.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(modifier = Modifier.fillMaxWidth(), width = 0.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(width = 240.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XLarge))

                    // Metadata grid skeleton (2 columns x 2 rows)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XLarge),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SpShimmer(width = 64.dp, height = 12.dp)
                            Spacer(Modifier.height(SpSpacing.XXSmall))
                            SpShimmer(width = 100.dp, height = 16.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SpShimmer(width = 64.dp, height = 12.dp)
                            Spacer(Modifier.height(SpSpacing.XXSmall))
                            SpShimmer(width = 100.dp, height = 16.dp)
                        }
                    }
                    Spacer(Modifier.height(SpSpacing.Medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XLarge),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SpShimmer(width = 64.dp, height = 12.dp)
                            Spacer(Modifier.height(SpSpacing.XXSmall))
                            SpShimmer(width = 100.dp, height = 16.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SpShimmer(width = 64.dp, height = 12.dp)
                            Spacer(Modifier.height(SpSpacing.XXSmall))
                            SpShimmer(width = 100.dp, height = 16.dp)
                        }
                    }
                    Spacer(Modifier.height(SpSpacing.XLarge))
                }
            }

            // Save states section skeleton
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                ) {
                    SpShimmer(width = 100.dp, height = 20.dp)
                    Spacer(Modifier.height(SpSpacing.Medium))
                    SpShimmer(modifier = Modifier.fillMaxWidth(), width = 0.dp, height = 64.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(modifier = Modifier.fillMaxWidth(), width = 0.dp, height = 64.dp)
                }
            }
        },
    )
}
