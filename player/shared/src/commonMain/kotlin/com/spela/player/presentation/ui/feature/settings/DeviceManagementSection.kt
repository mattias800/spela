package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.data.remote.dto.DeviceDto
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun DeviceManagementSection(
    devices: List<DeviceDto>,
    isLoading: Boolean,
    showDeleteConfirmId: Long?,
    onRename: (deviceId: Long, newName: String) -> Unit,
    onDelete: (deviceId: Long) -> Unit,
    onShowDeleteConfirm: (deviceId: Long) -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isLoading && devices.isEmpty()) {
            SpCard(onGradient = true) {
                SpLoadingIndicator(
                    message = "Loading devices...",
                    modifier = Modifier.padding(SpSpacing.Default),
                )
            }
        } else if (devices.isEmpty()) {
            SpCard(onGradient = true) {
                Text(
                    text = "No registered devices",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier.padding(SpSpacing.Default),
                )
            }
        } else {
            SpCard(onGradient = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.Small),
                ) {
                    devices.forEachIndexed { index, device ->
                        DeviceRow(
                            device = device,
                            onRename = { newName -> onRename(device.id, newName) },
                            onDelete = { onShowDeleteConfirm(device.id) },
                        )
                        if (index < devices.size - 1) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmId != null) {
        val deviceName = devices.find { it.id == showDeleteConfirmId }?.name ?: "this device"
        SpConfirmDialog(
            title = "Remove Device",
            message = "Are you sure you want to remove \"$deviceName\"? The device will need to re-register on next connection.",
            onDismiss = onDismissDeleteConfirm,
            onConfirm = { onDelete(showDeleteConfirmId) },
            confirmText = "Remove",
            isDestructive = true,
        )
    }
}

@Composable
private fun DeviceRow(
    device: DeviceDto,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .semantics { contentDescription = "Device: ${device.name}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = SpTypography.TitleMedium,
                color = SpColor.OnCard,
            )
            Text(
                text = device.platform,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
            if (device.lastSeenAt.isNotEmpty()) {
                Text(
                    text = "Last seen: ${device.lastSeenAt}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
        Spacer(Modifier.width(SpSpacing.Small))
        SpIconButton(
            icon = Icons.Outlined.Edit,
            contentDescription = "Rename ${device.name}",
            onClick = { showRenameDialog = true },
        )
        SpIconButton(
            icon = Icons.Outlined.Delete,
            contentDescription = "Remove ${device.name}",
            onClick = onDelete,
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(device.name) }
        SpDialog(
            title = "Rename Device",
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                if (newName.isNotBlank()) {
                    onRename(newName.trim())
                    showRenameDialog = false
                }
            },
            confirmText = "Save",
        ) {
            SpTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "Device Name",
                placeholder = "My device",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
