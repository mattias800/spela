package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.ui.components.ScreenshotLightbox
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.social.SharedSaveItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun ScreenshotsSection(screenshots: List<String>) {
    if (screenshots.isEmpty()) return

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    SpTitledSection(
        title = "Screenshots",
        edgeToEdgeContent = true,
    ) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.Default),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(screenshots.size, key = { "${it}_${screenshots[it]}" }) { index ->
            SpInnerCard(
                modifier = Modifier.width(280.dp),
                onClick = { lightboxIndex = index },
            ) {
                AsyncImage(
                    model = screenshots[index],
                    contentDescription = "Game screenshot",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
    } // SpTitledSection
    ScreenshotLightbox(
        visible = lightboxIndex != null,
        screenshotUrls = screenshots,
        initialIndex = lightboxIndex ?: 0,
        onDismiss = { lightboxIndex = null },
    )
}

@Composable
fun InfoColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
        Spacer(Modifier.height(SpSpacing.XXSmall))
        Text(
            text = value,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground,
        )
    }
}

@Composable
fun CommunitySharesSection(
    sharedSaves: List<SharedSaveState>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPlayFromSave: ((String) -> Unit)? = null,
) {
    SpTitledSection(title = "Community Saves", icon = Icons.Outlined.Share) {
        if (sharedSaves.isEmpty()) {
            Text(
                text = "No community saves yet. Be the first to share!",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
            )
        } else {
            sharedSaves.forEach { save ->
                SharedSaveItem(
                    sharedSave = save,
                    onDownload = { onDownload(save.id) },
                    onDelete = { onDelete(save.id) },
                    onPlay = onPlayFromSave?.let { { it(save.id) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XSmall),
                )
            }
        }
    }
}

@Composable
fun ChallengesSection(
    gameTitle: String,
    onViewAll: () -> Unit,
    onCreateChallenge: (() -> Unit)? = null,
) {
    SpTitledSection(title = "Challenges", icon = Icons.Outlined.Flag) {
        Text(
            text = "Compete on community-created challenges for $gameTitle",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            SpButton(
                text = "View Challenges",
                onClick = onViewAll,
                style = SpButtonStyle.Secondary,
                modifier = Modifier.weight(1f).testTag("view_challenges_button"),
            )
            if (onCreateChallenge != null) {
                SpButton(
                    text = "Create New",
                    onClick = onCreateChallenge,
                    modifier = Modifier.weight(1f).testTag("create_challenge_button"),
                )
            }
        }
    }
}
