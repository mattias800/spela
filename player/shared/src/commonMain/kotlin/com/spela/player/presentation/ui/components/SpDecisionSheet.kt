package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Visual style for a single action on an [SpDecisionSheet]. Kept narrow
 * (three choices) because the sheet is a decision surface — cognitive
 * load is the thing we're fighting.
 */
enum class SpDecisionActionStyle { Primary, Secondary, Ghost }

/**
 * One row in the sheet's action column. Label is mandatory; the VM
 * handles the transition, so [onClick] should be a direct intent send.
 */
data class SpDecisionAction(
    val label: String,
    val onClick: () -> Unit,
    val style: SpDecisionActionStyle = SpDecisionActionStyle.Primary,
)

/**
 * CONTENT component (Layer 2 in the design-system hierarchy) — a modal
 * decision surface used by flows that need to present a considered
 * choice before taking an action. First caller is the #672
 * core-upgrade decision flow (Sheet A / B / C / D), but the shape is
 * reusable: title + body + optional meta line + primary action +
 * optional secondary + optional "More options" disclosure holding
 * additional actions.
 *
 * **Does not accept [Modifier]** — the layout is strict and controlled
 * by the component itself. Callers parameterise via copy and actions,
 * not layout.
 *
 * Copy discipline: the sheet hosts user-facing strings verbatim. Don't
 * bake in tone adjustments (`"!"`, "WARNING:", capitalisation shifts)
 * here — do them in the caller so every sheet renders consistently.
 *
 * See `design-proposals/core-upgrade-decision-ux.md` for the design
 * rationale; this component retires the raw `Box + Scrim + clickable +
 * SurfaceElevated + RoundedCornerShape` pattern in
 * `CoreMismatchDialog.kt`.
 */
@Composable
fun SpDecisionSheet(
    title: String,
    body: String,
    primary: SpDecisionAction,
    onDismiss: () -> Unit,
    secondary: SpDecisionAction? = null,
    metaLine: String? = null,
    icon: (@Composable () -> Unit)? = null,
    moreOptions: List<SpDecisionAction> = emptyList(),
    testTagName: String? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .then(
                    if (testTagName != null) Modifier.testTag(testTagName)
                    else Modifier,
                )
                .animateContentSize(),
        ) {
            if (icon != null) {
                Box(Modifier.padding(bottom = SpSpacing.Small)) { icon() }
            }

            Text(
                text = title,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Small))

            Text(
                text = body,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )

            if (!metaLine.isNullOrBlank()) {
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = metaLine,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.testTag("sp-decision-meta"),
                )
            }

            Spacer(Modifier.height(SpSpacing.XLarge))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                ActionButton(primary, Modifier.testTag("sp-decision-primary"))
                if (secondary != null) {
                    ActionButton(secondary, Modifier.testTag("sp-decision-secondary"))
                }
                if (moreOptions.isNotEmpty()) {
                    MoreOptionsSection(moreOptions)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: SpDecisionAction, modifier: Modifier = Modifier) {
    val style = when (action.style) {
        SpDecisionActionStyle.Primary -> SpButtonStyle.Primary
        SpDecisionActionStyle.Secondary -> SpButtonStyle.Secondary
        SpDecisionActionStyle.Ghost -> SpButtonStyle.Ghost
    }
    SpButton(
        text = action.label,
        onClick = action.onClick,
        style = style,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Inline disclosure: a Ghost [SpButton] that flips its chevron when
 * tapped, expanding to reveal the [moreOptions] actions beneath. Uses
 * [SpButton] rather than a hand-rolled Box so focus/hover/press +
 * gamepad traversal come for free.
 */
@Composable
private fun MoreOptionsSection(moreOptions: List<SpDecisionAction>) {
    var expanded by remember { mutableStateOf(false) }

    SpButton(
        text = "More options",
        onClick = { expanded = !expanded },
        style = SpButtonStyle.Ghost,
        leadingIcon = {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = SpColor.OnBackgroundSecondary,
                modifier = Modifier.size(SpSpacing.IconSmall),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sp-decision-more-toggle"),
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sp-decision-more-options"),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            moreOptions.forEachIndexed { index, action ->
                ActionButton(
                    action,
                    Modifier.testTag("sp-decision-more-$index"),
                )
            }
        }
    }
}
