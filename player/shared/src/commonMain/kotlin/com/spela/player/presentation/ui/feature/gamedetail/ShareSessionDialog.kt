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
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Bottom-sheet dialog for #885 "Share session" — opens from the
 * per-session "…" actions menu when the source session has a save
 * worth sharing (capability-gated upstream on
 * [com.spela.player.domain.model.PlaySemantics.ResumesFromSaveState]).
 *
 * The form is the same shape as the web Create Shared Session modal
 * (Name + Description) but without the game-search step — the source
 * session already pins the game. Submitting kicks off
 * [com.spela.player.presentation.intent.GameDetailIntent.CreateSharedSessionFromSession]
 * which posts to the server's `sourceSessionId` path; on success the
 * VM populates `state.shareSessionCreatedId` so the host screen can
 * navigate the user into the new shared session's detail with the
 * invite sheet auto-opened.
 *
 * Visual pattern matches `CreateChallengeDialog`: scrim + bottom-
 * anchored card so the dialog is dismissable by tapping outside but
 * the form itself stays put.
 */
@Composable
internal fun ShareSessionDialog(
    gameTitle: String,
    sourceSessionName: String,
    isSubmitting: Boolean,
    onSubmit: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("$gameTitle — shared") }
    var description by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isSubmitting) onDismiss() },
            )
            .testTag("share_session_dialog"),
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
        ) {
            Text(
                text = "Share session",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(SpSpacing.XSmall))
            Text(
                text = "Invite a friend into a copy of \"$sourceSessionName\". " +
                    "They'll start from your latest save.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            Spacer(Modifier.height(SpSpacing.Large))

            SpTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_session_name_input"),
            )
            Spacer(Modifier.height(SpSpacing.Default))
            SpTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (optional)",
                singleLine = false,
                minLines = 2,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_session_description_input"),
            )

            Spacer(Modifier.height(SpSpacing.XLarge))
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SpSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_session_cancel_button"),
                )
                SpButton(
                    text = if (isSubmitting) "Creating…" else "Create & invite",
                    onClick = {
                        if (name.isNotBlank() && !isSubmitting) {
                            onSubmit(name.trim(), description.trim())
                        }
                    },
                    enabled = !isSubmitting && name.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_session_submit_button"),
                )
            }
        }
    }
}
