package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.domain.model.SaveState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun CreateChallengeDialog(
    gameTitle: String,
    saveStates: List<SaveState>,
    isSubmitting: Boolean,
    onSubmit: (saveStateId: String, name: String, description: String, type: String, difficulty: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("$gameTitle Challenge") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChallengeType.COMPLETION) }
    var selectedDifficulty by remember { mutableStateOf(ChallengeDifficulty.MEDIUM) }
    var selectedSaveState by remember { mutableStateOf<SaveState?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isSubmitting) onDismiss() },
            )
            .testTag("create_challenge_dialog"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = SpSpacing.RadiusXLarge, topEnd = SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(SpSpacing.XLarge)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Create Challenge",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(SpSpacing.XLarge))

            // Save state picker
            Text(
                text = "Save State",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SpSpacing.Small))
            if (saveStates.isEmpty()) {
                Text(
                    text = "No save states available. Play the game and save first.",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("no_saves_message"),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("save_state_picker"),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    saveStates.forEach { save ->
                        SpChip(
                            text = save.name,
                            isSelected = save == selectedSaveState,
                            onClick = { selectedSaveState = save },
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.Large))

            // Title field
            SpTextField(
                value = name,
                onValueChange = { name = it },
                label = "Title",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenge_title_field"),
            )

            Spacer(Modifier.height(SpSpacing.Medium))

            // Description field
            SpTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (optional)",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenge_description_field"),
            )

            Spacer(Modifier.height(SpSpacing.Large))

            // Type selector
            Text(
                text = "Type",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenge_type_selector"),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                ChallengeType.entries.forEach { type ->
                    SpChip(
                        text = type.displayName,
                        isSelected = type == selectedType,
                        onClick = { selectedType = type },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(SpSpacing.Large))

            // Difficulty selector
            Text(
                text = "Difficulty",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenge_difficulty_selector"),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                ChallengeDifficulty.entries.forEach { diff ->
                    SpChip(
                        text = diff.displayName,
                        isSelected = diff == selectedDifficulty,
                        onClick = { selectedDifficulty = diff },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(SpSpacing.XLarge))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenge_dialog_actions"),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                SpButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    style = SpButtonStyle.Outlined,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                )
                SpButton(
                    text = if (isSubmitting) "Creating..." else "Create",
                    onClick = {
                        val save = selectedSaveState
                        if (name.isNotBlank() && save != null) {
                            onSubmit(
                                save.id.toString(),
                                name,
                                description,
                                selectedType.apiId,
                                selectedDifficulty.apiId,
                            )
                        }
                    },
                    enabled = name.isNotBlank() && selectedSaveState != null && !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_challenge_submit"),
                )
            }
        }
    }
}
