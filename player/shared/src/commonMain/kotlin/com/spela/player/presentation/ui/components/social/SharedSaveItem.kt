package com.spela.player.presentation.ui.components.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes

@Composable
fun SharedSaveItem(
    sharedSave: SharedSaveState,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?,
    onPlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val description = "${sharedSave.name} by ${sharedSave.username}"

    SpCard(onGradient = true, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // User avatar
            SpAvatar(
                username = sharedSave.username,
                avatarUrl = sharedSave.userAvatarUrl,
                size = 40.dp,
            )

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sharedSave.name,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "by ${sharedSave.username}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                if (sharedSave.description.isNotBlank()) {
                    Text(
                        text = sharedSave.description,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${sharedSave.downloadCount} downloads",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    Text(
                        text = " \u00B7 ",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    Text(
                        text = formatBytes(sharedSave.fileSize),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }

            // Play button
            if (onPlay != null) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play from ${sharedSave.name}",
                        tint = SpColor.Primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Download button
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download ${sharedSave.name}",
                    tint = SpColor.Accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Delete button (only shown for own saves)
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete ${sharedSave.name}",
                        tint = SpColor.Error,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

