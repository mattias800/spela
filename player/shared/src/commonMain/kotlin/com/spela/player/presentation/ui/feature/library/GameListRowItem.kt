package com.spela.player.presentation.ui.feature.library

import com.spela.player.util.formatBytes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameListRowItem(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onClick = onClick,
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        cornerRadius = SpSpacing.RadiusLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier
                    .height(SpSpacing.CoverSmallHeight),
                aspectRatio = game.coverAspectRatio,
            )

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (game.consoleName.isNotEmpty()) {
                    Text(
                        text = game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                        maxLines = 1,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (game.fileSize > 0) {
                        Text(
                            text = formatBytes(game.fileSize),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }

                    if (game.averageRating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = SpColor.Rating,
                                modifier = Modifier.height(SpSpacing.IconSmall).width(SpSpacing.IconSmall),
                            )
                            Spacer(Modifier.width(SpSpacing.XXSmall))
                            Text(
                                text = String.format("%.1f", game.averageRating),
                                style = SpTypography.LabelSmall,
                                color = SpColor.OnBackgroundTertiary,
                            )
                        }
                    }

                    if (game.variantCount > 1) {
                        val otherCount = game.variantCount - 1
                        Text(
                            text = "$otherCount ${if (otherCount == 1) "version" else "versions"}",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}