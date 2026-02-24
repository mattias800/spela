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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.ui.components.ScreenshotLightbox
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.social.SharedSaveItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun ScreenshotsSection(screenshots: List<String>) {
    if (screenshots.isEmpty()) return

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    Spacer(Modifier.height(SpSpacing.XXLarge))
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
        items(screenshots.size, key = { screenshots[it] }) { index ->
            SpCard(
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
    ScreenshotLightbox(
        visible = lightboxIndex != null,
        screenshotUrls = screenshots,
        initialIndex = lightboxIndex ?: 0,
        onDismiss = { lightboxIndex = null },
    )
}

@Composable
internal fun SaveStatesSection(
    saveStates: List<SaveState>,
    onDelete: ((Long) -> Unit)? = null,
) {
    SpTitledSection(title = "Save States") {
        if (saveStates.isEmpty()) {
        SpEmptyStates.NoSaveStates(modifier = Modifier.fillMaxWidth())
        } else {
            saveStates.forEach { save ->
                SaveStateItem(
                    saveState = save,
                    onDelete = onDelete?.let { { it(save.id) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XSmall),
                )
            }
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
    SpTitledSection(title = "Community Saves") {
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
}

@Composable
private fun SaveStateItem(
    saveState: SaveState,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Save State") },
            text = { Text("Are you sure you want to delete \"${saveState.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = SpColor.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

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

            if (onDelete != null) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Delete ${saveState.name}"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChallengesSection(
    gameTitle: String,
    onViewAll: () -> Unit,
    onCreateChallenge: (() -> Unit)? = null,
) {
    SpTitledSection(title = "Challenges") {
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
