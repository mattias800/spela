package com.spela.player.presentation.ui.feature.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GameCollection
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import androidx.compose.foundation.background
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CollectionPickerDialog(
    collections: List<GameCollection>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onCreateCollection: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
        ) {
            Text(
                text = "Add to Collection",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )

            Spacer(Modifier.height(SpSpacing.Default))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading collections...")
                }
            } else if (collections.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "You don't have any collections yet",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    if (onCreateCollection != null) {
                        Spacer(Modifier.height(SpSpacing.Default))
                        SpButton(
                            text = "Create Collection",
                            onClick = {
                                onDismiss()
                                onCreateCollection()
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                ) {
                    items(collections, key = { it.id }) { collection ->
                        CollectionPickerItem(
                            collection = collection,
                            onClick = { onSelectCollection(collection.id) },
                        )
                        if (collection != collections.last()) {
                            HorizontalDivider(color = SpColor.Divider)
                        }
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.Default))

            SpButton(
                text = "Cancel",
                onClick = onDismiss,
                style = com.spela.player.presentation.ui.components.SpButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CollectionPickerItem(
    collection: GameCollection,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = SpSpacing.Medium, horizontal = SpSpacing.Small)
            .semantics {
                contentDescription = "${collection.name}, ${collection.gameCount} games"
                role = Role.Button
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = collection.name,
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = "${collection.gameCount} games",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
