package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ControllerUi
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
import com.spela.player.presentation.viewmodel.GamepadConfigState

/**
 * Per-controller list for Settings → Controls (#1359): every connected controller,
 * each row drilling into [ControllerDetailScreen] (a real navigation page, #1372)
 * to edit its profile/type, assign or clear its player number, and test its buttons
 * live. Selecting a row calls [onSelectController] with the controller's device id.
 */
@Composable
fun ControllerControls(
    state: GamepadConfigState,
    onSelectController: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ControllerList(
        controllers = state.controllers,
        onSelect = onSelectController,
        modifier = modifier,
    )
}

/**
 * Per-controller detail PAGE (#1372): profile/type, player assignment, and the
 * live input tester for one controller. A real navigation screen, so gamepad B
 * pops it like any other screen (no in-screen back special-casing). Pops back
 * automatically if the controller disconnects while open.
 */
@Composable
fun ControllerDetailScreen(
    deviceId: Int,
    state: GamepadConfigState,
    onIntent: (GamepadConfigIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler { onBack() }
    val controller = state.controllers.find { it.deviceId == deviceId }
    SpScreen {
        if (controller == null) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            SpScrollableContent(modifier = modifier) {
                // Clear the section-indicator pill in gamepad mode (no-op in touch),
                // matching sibling detail screens like ConsoleSettingsScreen.
                SpScreenTopSpacer()
                ControllerDetail(
                    controller = controller,
                    pressedPositions = state.pressedPositions,
                    sticks = state.testSticks,
                    confirmHeld = state.confirmHeld,
                    onBack = onBack,
                    onSelectStyle = { style ->
                        onIntent(GamepadConfigIntent.SetStyleOverrideForController(deviceId, style))
                    },
                    onAssignSlot = { slot -> onIntent(GamepadConfigIntent.AssignPlayer(deviceId, slot)) },
                    onClear = { onIntent(GamepadConfigIntent.ClearPlayer(deviceId)) },
                    onTestActiveChange = { active -> onIntent(GamepadConfigIntent.SetInputTestActive(deviceId, active)) },
                )
            }

            val conflict = state.conflict
            if (conflict != null) {
                SpConfirmDialog(
                    title = "Switch player?",
                    message = "${playerLabel(conflict.slot)} is currently ${conflict.currentDeviceName}. " +
                        "Switch ${playerLabel(conflict.slot)} to this controller? " +
                        "${conflict.currentDeviceName} will become unassigned.",
                    confirmText = "Switch",
                    onConfirm = { onIntent(GamepadConfigIntent.ConfirmConflict) },
                    onDismiss = { onIntent(GamepadConfigIntent.DismissConflict) },
                )
            }
        }
    }
}

@Composable
private fun ControllerList(
    controllers: List<ControllerUi>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(SpSpacing.Default)) {
        Text(
            text = "Controllers",
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { contentDescription = "Controllers heading" },
        )
        Spacer(Modifier.height(SpSpacing.Medium))

        if (controllers.isEmpty()) {
            Text(
                text = "No controllers connected. Connect a controller to assign a player " +
                    "number and test its buttons.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("controller_list_empty"),
            )
        } else {
            controllers.forEachIndexed { index, controller ->
                ControllerListRow(controller = controller, onClick = { onSelect(controller.deviceId) })
                if (index < controllers.lastIndex) Spacer(Modifier.height(SpSpacing.Small))
            }
        }
    }
}

@Composable
private fun ControllerListRow(
    controller: ControllerUi,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // Transparent-white overlay (derives color from the parent's
            // background) instead of an opaque grey, so the row blends with
            // gradient/colored backgrounds — same token SpInnerCard uses (#1449).
            .background(SpColor.OnGradientFill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .testTag("controller_row_${controller.deviceId}")
            .semantics {
                role = Role.Button
                contentDescription = "${controllerIdentity(controller)}, " +
                    (if (controller.slot != null) playerLabel(controller.slot) else "not assigned") +
                    (if (controller.slot != null && controller.isActive) ", active" else "")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerBadge(slot = controller.slot)
        Spacer(Modifier.width(SpSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = controllerIdentity(controller),
                style = SpTypography.BodyMedium,
                color = SpColor.OnCard,
            )
            Text(
                text = if (controller.slot != null) playerLabel(controller.slot) else "Not assigned",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
        if (controller.slot != null) {
            ActivityDot(isActive = controller.isActive)
            Spacer(Modifier.width(SpSpacing.Medium))
        }
        Text(text = "›", style = SpTypography.TitleMedium, color = SpColor.OnBackgroundTertiary)
    }
}

/**
 * The per-controller detail body — type/profile, player assignment, and the live
 * input tester. `internal` (not private) so the first-run wizard's Controls step
 * (#1448) can embed the same UI inside its own chrome without the
 * [ControllerDetailScreen]'s `SpScreen` wrapper. The caller owns the slot-conflict
 * dialog (see [ControllerDetailScreen] and the wizard for the two call sites).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControllerDetail(
    controller: ControllerUi,
    pressedPositions: Set<com.spela.player.domain.model.GamepadPosition>,
    sticks: com.spela.player.libretro.GamepadTestSticks,
    confirmHeld: Boolean,
    onBack: () -> Unit,
    onSelectStyle: (ControllerStyle?) -> Unit,
    onAssignSlot: (Int) -> Unit,
    onClear: () -> Unit,
    onTestActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStylePicker by remember { mutableStateOf(false) }
    var showSlotPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(SpSpacing.Default)) {
        // On-screen back affordance (touch). Gamepad B pops the page via the nav
        // stack; it's also the first focusable, so moveFocus(Next) lands here.
        BackRow(onBack = onBack)
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = controllerIdentity(controller),
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("controller_detail_title"),
        )

        Spacer(Modifier.height(SpSpacing.Large))

        // --- Controller type / profile ---
        DetailSectionLabel("Controller type")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = controller.style.displayName,
                style = SpTypography.BodyMedium,
                color = SpColor.OnCard,
                modifier = Modifier.weight(1f),
            )
            SpButton(
                text = if (controller.styleOverride != null) "Type: ${controller.styleOverride.shortLabel}" else "Type: Auto",
                onClick = { showStylePicker = true },
                style = SpButtonStyle.Ghost,
                modifier = Modifier.testTag("controller_detail_change_type"),
            )
        }

        Spacer(Modifier.height(SpSpacing.Large))

        // --- Player number ---
        DetailSectionLabel("Player")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (controller.slot != null) playerLabel(controller.slot) else "Not assigned",
                style = SpTypography.BodyMedium,
                // PrimaryLight (not Primary) reads clearly on the dark card; the
                // P-badge already carries the indigo accent (#1361).
                color = if (controller.slot != null) SpColor.PrimaryLight else SpColor.OnBackgroundTertiary,
                modifier = Modifier.weight(1f).testTag("controller_detail_player"),
            )
            SpButton(
                text = "Change",
                onClick = { showSlotPicker = true },
                style = SpButtonStyle.Secondary,
                modifier = Modifier.testTag("controller_detail_change_player"),
            )
            if (controller.slot != null) {
                Spacer(Modifier.width(SpSpacing.Small))
                SpButton(
                    text = "Clear",
                    onClick = onClear,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier.testTag("controller_detail_clear_player"),
                )
            }
        }

        Spacer(Modifier.height(SpSpacing.Large))

        // --- Live input tester (per-device) ---
        DetailSectionLabel("Test input")
        Text(
            text = "If a button lights up the wrong position, change the Type above.",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundSecondary,
            modifier = Modifier.padding(bottom = SpSpacing.Small),
        )
        GamepadInputTester(
            pressedPositions = pressedPositions,
            confirmHeld = confirmHeld,
            onActiveChange = onTestActiveChange,
            sticks = sticks,
        )
    }

    if (showStylePicker) {
        ControllerStylePickerDialog(
            detectedStyle = controller.detectedStyle,
            currentOverride = controller.styleOverride,
            onSelect = { style ->
                onSelectStyle(style)
                showStylePicker = false
            },
            onDismiss = { showStylePicker = false },
        )
    }
    if (showSlotPicker) {
        PlayerSlotPickerDialog(
            currentSlot = controller.slot,
            onSelect = { slot ->
                onAssignSlot(slot)
                showSlotPicker = false
            },
            onDismiss = { showSlotPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSlotPickerDialog(
    currentSlot: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val initialFocusSlot = currentSlot ?: 0
    com.spela.player.presentation.ui.components.SpDialog(
        title = "Assign player",
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
        modifier = Modifier.testTag("player_slot_picker"),
        initialFocusRequester = initialFocusRequester,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            for (slot in 0 until GamepadPortManager.MAX_PORTS) {
                SlotChip(
                    slot = slot,
                    selected = slot == currentSlot,
                    onClick = { onSelect(slot) },
                    modifier = if (slot == initialFocusSlot) Modifier.focusRequester(initialFocusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SlotChip(
    slot: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) SpColor.Primary else SpColor.SurfaceElevated)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .testTag("slot_chip_$slot")
            .semantics {
                role = Role.Button
                contentDescription = playerLabel(slot)
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        Text(
            text = playerLabel(slot),
            style = SpTypography.BodyMedium,
            color = if (selected) SpColor.OnPrimary else SpColor.OnCard,
        )
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onBack)
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.Small)
            .testTag("controller_detail_back")
            .semantics { role = Role.Button; contentDescription = "Back to controllers" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "‹", style = SpTypography.TitleMedium, color = SpColor.OnCard)
        Spacer(Modifier.width(SpSpacing.Small))
        Text(text = "Back", style = SpTypography.BodyMedium, color = SpColor.OnCard)
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = SpTypography.TitleSmall,
        color = SpColor.OnBackgroundSecondary,
        modifier = Modifier.padding(bottom = SpSpacing.Small),
    )
}

@Composable
private fun PlayerBadge(slot: Int?) {
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (slot != null) SpColor.Primary else SpColor.SurfaceElevated)
            .padding(horizontal = SpSpacing.Small, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (slot != null) "P${slot + 1}" else "—",
            style = SpTypography.TitleMedium,
            color = if (slot != null) SpColor.OnPrimary else SpColor.OnBackgroundTertiary,
        )
    }
}

@Composable
private fun ActivityDot(isActive: Boolean) {
    val animationsEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
    val pulseAlpha = if (isActive && animationsEnabled) {
        val transition = rememberInfiniteTransition()
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        alpha
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(pulseAlpha)
            .clip(CircleShape)
            .background(if (isActive) SpColor.Success else SpColor.OnBackgroundTertiary.copy(alpha = 0.3f))
            .semantics {
                contentDescription = if (isActive) "Activity indicator active" else "Activity indicator inactive"
            },
    )
}

private fun controllerIdentity(controller: ControllerUi): String =
    if (controller.style == ControllerStyle.Generic) controller.deviceName else controller.style.displayName

private fun playerLabel(slot: Int): String = "Player ${slot + 1}"
