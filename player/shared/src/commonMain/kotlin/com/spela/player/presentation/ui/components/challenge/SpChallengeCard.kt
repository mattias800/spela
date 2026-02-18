package com.spela.player.presentation.ui.components.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.Challenge
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpChallengeCard(
    challenge: Challenge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.SurfaceElevated)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${challenge.name}, ${challenge.difficulty.displayName} ${challenge.type.displayName} challenge for ${challenge.gameTitle}"
                role = Role.Button
            },
    ) {
        // Screenshot hero (16:10 aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(topStart = SpSpacing.RadiusLarge, topEnd = SpSpacing.RadiusLarge))
                .background(SpColor.SurfaceVariant),
        ) {
            if (challenge.screenshotUrl != null) {
                SubcomposeAsyncImage(
                    model = challenge.screenshotUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        SpShimmer(modifier = Modifier.matchParentSize())
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(SpSpacing.IconXLarge).align(Alignment.Center),
                )
            }

            // Chips overlay at bottom-left
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(SpSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
            ) {
                SpDifficultyChip(difficulty = challenge.difficulty)
                SpChallengeTypeChip(type = challenge.type)
            }
        }

        // Info section
        Column(
            modifier = Modifier.padding(SpSpacing.Medium),
        ) {
            Text(
                text = challenge.name,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(SpSpacing.XXSmall))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Creator avatar
                SpAvatar(
                    username = challenge.creatorUsername,
                    avatarUrl = challenge.creatorAvatarUrl,
                    size = SpSpacing.AvatarSmall,
                )

                Spacer(Modifier.width(SpSpacing.XXSmall))

                Text(
                    text = challenge.creatorUsername,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(SpSpacing.Small))

                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(SpSpacing.IconSmall),
                )
                Spacer(Modifier.width(SpSpacing.XXSmall))
                Text(
                    text = "${challenge.attemptCount}",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}

@Composable
fun SpChallengeCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.SurfaceElevated),
    ) {
        SpShimmer(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
        )
        Column(modifier = Modifier.padding(SpSpacing.Medium)) {
            SpShimmer(width = 120.dp, height = 14.dp)
            Spacer(Modifier.height(SpSpacing.Small))
            SpShimmer(width = 80.dp, height = 10.dp)
        }
    }
}
