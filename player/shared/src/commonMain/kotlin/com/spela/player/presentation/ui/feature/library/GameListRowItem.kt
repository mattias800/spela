package com.spela.player.presentation.ui.feature.library

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
                    .height(SpSpacing.CoverSmallHeight)
                    .width(SpSpacing.CoverSmallWidth),
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
                            text = formatFileSize(game.fileSize),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }

                    if (game.averageRating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = SpColor.Warning,
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
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
