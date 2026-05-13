package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.GameVariant
import com.spela.player.domain.model.ParentGame
import com.spela.player.domain.model.RomHackGame
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpRegionChip
import com.spela.player.presentation.ui.components.SpStatusChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Sibling sections for the game-detail screen that surface related
 * ROMs — alternate versions, parent ROM-hack, and derived ROM-hacks.
 * Extracted together from `GameDetailScreen` in the #695 refactor
 * because they share the same card layout and only differ in header
 * label + data source.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariantsSection(
    title: String,
    variants: List<GameVariant>,
    onVariantSelected: ((String) -> Unit)?,
    currentGameId: String? = null,
) {
    SpTitledSection(
        title = title,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            variants.forEach { variant ->
                val isCurrent = currentGameId != null && variant.id == currentGameId
                // The current game row isn't clickable — tapping it
                // would just navigate to itself. Leave as a visual
                // marker instead, identified by the "This game" chip
                // below.
                val cardOnClick: (() -> Unit)? = if (isCurrent) {
                    null
                } else {
                    { onVariantSelected?.invoke(variant.id) }
                }
                SpCard(
                    onClick = cardOnClick,
                    onGradient = true,
                    cornerRadius = SpSpacing.RadiusMedium,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        Text(
                            text = variant.title,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                        ) {
                            if (isCurrent) {
                                // Marker for the entry that matches
                                // the game currently being shown — so
                                // the list shows the full landscape
                                // including the one you're on. Success
                                // colour matches other "this is the
                                // active thing" badges in the app.
                                SpStatusChip(text = "This game")
                            }
                            variant.region?.takeIf { it.isNotBlank() }?.let { region ->
                                SpRegionChip(region = region, onGradient = true)
                            }
                            variant.revision?.takeIf { it.isNotBlank() }?.let { revision ->
                                SpChip(text = revision, onGradient = true)
                            }
                            if (variant.fileSize > 0) {
                                SpChip(
                                    text = formatVariantFileSize(variant.fileSize),
                                    onGradient = true,
                                )
                            }
                            VerificationChip(
                                verificationStatus = variant.verificationStatus,
                            )
                            if (variant.isPreRelease) {
                                SpChip(text = "Pre-release", onGradient = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatVariantFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Composable
fun BasedOnSection(
    parentGame: ParentGame,
    onNavigateToGame: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = "Based on",
    ) {
        SpCard(
            onClick = { onNavigateToGame?.invoke(parentGame.id) },
            onGradient = true,
            cornerRadius = SpSpacing.RadiusMedium,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpCoverArt(
                    imageUrl = parentGame.coverUrl,
                    contentDescription = "${parentGame.title} cover art",
                    modifier = Modifier.size(width = SpSpacing.CoverSmallWidth, height = SpSpacing.CoverSmallHeight),
                )
                Text(
                    text = parentGame.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun RomHacksSection(
    romHacks: List<RomHackGame>,
    onNavigateToGame: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = "ROM Hacks",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            romHacks.forEach { hack ->
                SpCard(
                    onClick = { onNavigateToGame?.invoke(hack.id) },
                    onGradient = true,
                    cornerRadius = SpSpacing.RadiusMedium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpCoverArt(
                            imageUrl = hack.coverUrl,
                            contentDescription = "${hack.title} cover art",
                            modifier = Modifier.size(width = SpSpacing.CoverSmallWidth, height = SpSpacing.CoverSmallHeight),
                        )
                        Text(
                            text = hack.title,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
