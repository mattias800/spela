package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.social.SharedSaveItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun ScreenshotsSection(screenshots: List<String>) {
    if (screenshots.isEmpty()) return

    Text(
        text = "Screenshots",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(screenshots, key = { it }) { screenshotUrl ->
            SpCard(
                modifier = Modifier
                    .width(240.dp)
                    .height(160.dp),
            ) {
                AsyncImage(
                    model = screenshotUrl,
                    contentDescription = "Game screenshot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
    Spacer(Modifier.height(SpSpacing.XLarge))
}

@Composable
internal fun SaveStatesSection(saveStates: List<SaveState>) {
    Text(
        text = "Save States",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))

    if (saveStates.isEmpty()) {
        SpEmptyStates.NoSaveStates(modifier = Modifier.fillMaxWidth())
    } else {
        saveStates.forEach { save ->
            SaveStateItem(
                saveState = save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XSmall),
            )
        }
    }
}

@Composable
internal fun InfoColumn(label: String, value: String) {
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
internal fun CommunitySharesSection(
    sharedSaves: List<SharedSaveState>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Spacer(Modifier.height(SpSpacing.XLarge))
    Text(
        text = "Community Saves",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XSmall),
            )
        }
    }
}

@Composable
private fun SaveStateItem(
    saveState: SaveState,
    modifier: Modifier = Modifier,
) {
    SpCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium)
                .semantics {
                    contentDescription = "${saveState.name}, ${if (saveState.isAuto) "auto save" else "manual save"}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
                    .background(SpColor.SurfaceBright),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saveState.isAuto) "A" else "S",
                    style = SpTypography.LabelLarge,
                    color = SpColor.OnBackgroundTertiary,
                )
            }

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saveState.name,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                )
                Text(
                    text = if (saveState.isAuto) "Auto Save" else "Manual Save",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}

@Composable
internal fun ChallengesSection(
    gameTitle: String,
    onViewAll: () -> Unit,
) {
    Spacer(Modifier.height(SpSpacing.XLarge))
    Text(
        text = "Challenges",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Small))
    Text(
        text = "Compete on community-created challenges for $gameTitle",
        style = SpTypography.BodyMedium,
        color = SpColor.OnBackgroundSecondary,
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    SpButton(
        text = "View Challenges",
        onClick = onViewAll,
        style = SpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}
