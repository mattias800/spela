package com.spela.player.presentation.ui.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.spela.player.presentation.ui.components.SpSwitch
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun CollectionFormDialog(
    title: String,
    confirmText: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, isPublic: Boolean) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    initialIsPublic: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var isPublic by remember { mutableStateOf(initialIsPublic) }
    var nameError by remember { mutableStateOf(false) }

    SpDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = if (isLoading) "$confirmText..." else confirmText,
        isLoading = isLoading,
        onConfirm = {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                nameError = true
            } else {
                onConfirm(
                    trimmedName,
                    description.trim().ifBlank { null },
                    isPublic,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium)) {
            SpTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 100) {
                        name = it
                        if (nameError && it.trim().isNotBlank()) nameError = false
                    }
                },
                label = "Name",
                placeholder = "My collection",
                isError = nameError,
                errorMessage = if (nameError) "Name is required" else null,
                modifier = Modifier.fillMaxWidth(),
            )

            SpTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                placeholder = "Describe your collection",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Make public"
                        role = Role.Switch
                        stateDescription = if (isPublic) "On" else "Off"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Make public",
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnBackground,
                )
                SpSwitch(
                    checked = isPublic,
                    onCheckedChange = { isPublic = it },
                )
            }
        }
    }
}
