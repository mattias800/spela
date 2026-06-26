package com.spela.player.presentation.ui.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.ConfirmButtonConvention
import com.spela.player.presentation.state.LoginState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpServerPill
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.gamepad.ControllerControls
import com.spela.player.presentation.ui.components.gamepad.ControllerDetail
import com.spela.player.presentation.ui.components.gamepad.GamepadInputTester
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
import com.spela.player.presentation.viewmodel.GamepadConfigState

/** Test tags for the first-run wizard, shared between the UI and the desktop
 *  E2E suite so assertions track renames. */
object OnboardingTestTags {
    const val SCREEN = "screen_onboarding_wizard"
    const val WELCOME_START = "onboarding_welcome_start"
    const val CONNECT_NAME = "onboarding_connect_name"
    const val CONNECT_URL = "onboarding_connect_url"
    const val CONNECT_SUBMIT = "onboarding_connect_submit"
    const val SIGNIN_USERNAME = "onboarding_signin_username"
    const val SIGNIN_EMAIL = "onboarding_signin_email"
    const val SIGNIN_PASSWORD = "onboarding_signin_password"
    const val SIGNIN_SUBMIT = "onboarding_signin_submit"
    const val NAME_DEVICE_INPUT = "onboarding_name_device_input"
    const val NAME_DEVICE_CONTINUE = "onboarding_name_device_continue"
    const val CONTROLS_CONTINUE = "onboarding_controls_continue"
    const val VERIFY_GOOD = "onboarding_verify_good"
    const val VERIFY_WRONG = "onboarding_verify_wrong"
    const val VERIFY_CONTINUE = "onboarding_verify_continue"
    const val CONVENTION_XBOX = "onboarding_convention_xbox"
    const val CONVENTION_NINTENDO = "onboarding_convention_nintendo"
    const val CONVENTION_CONTINUE = "onboarding_convention_continue"
    const val ALL_SET_FINISH = "onboarding_all_set_finish"
}

// ── Step 1: Welcome ───────────────────────────────────────────────────────

@Composable
fun WelcomeStepContent(onGetStarted: () -> Unit) {
    // Branding (logo + tagline) lives in the wizard chrome, shown on every step.
    SpButton(
        text = "Get started",
        onClick = onGetStarted,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.WELCOME_START),
    )
}

// ── Step 2: Connect server ────────────────────────────────────────────────

@Composable
fun ConnectStepContent(
    name: String,
    url: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    isValidating: Boolean,
    error: String?,
) {
    SpTextField(
        value = name,
        onValueChange = onNameChange,
        label = "Server name",
        placeholder = "My Home Server",
        enabled = !isValidating,
        modifier = Modifier.testTag(OnboardingTestTags.CONNECT_NAME),
    )
    Spacer(Modifier.height(SpSpacing.Default))
    SpTextField(
        value = url,
        onValueChange = onUrlChange,
        label = "Server address",
        placeholder = "https://spela.example.com",
        enabled = !isValidating,
        imeAction = ImeAction.Done,
        onImeAction = onConnect,
        modifier = Modifier.testTag(OnboardingTestTags.CONNECT_URL),
    )
    WizardError(error)
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = "Connect",
        onClick = onConnect,
        isLoading = isValidating,
        enabled = !isValidating,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.CONNECT_SUBMIT),
    )
}

// ── Step 3: Sign in ───────────────────────────────────────────────────────

@Composable
fun SignInStepContent(
    state: LoginState,
    serverUrl: String,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleRegister: () -> Unit,
    onChangeServer: () -> Unit,
) {
    SpServerPill(serverUrl = serverUrl, onClick = onChangeServer)
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        label = "Username",
        placeholder = "Enter your username",
        enabled = !state.isLoading,
        modifier = Modifier.testTag(OnboardingTestTags.SIGNIN_USERNAME),
    )
    // Registration requires an email; only shown (and required) in register mode.
    AnimatedVisibility(visible = state.isRegisterMode, enter = fadeIn(), exit = fadeOut()) {
        Column {
            Spacer(Modifier.height(SpSpacing.Default))
            SpTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "you@example.com",
                enabled = !state.isLoading,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.testTag(OnboardingTestTags.SIGNIN_EMAIL),
            )
        }
    }
    Spacer(Modifier.height(SpSpacing.Default))
    SpTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = "Password",
        placeholder = "Enter your password",
        isPassword = true,
        enabled = !state.isLoading,
        imeAction = ImeAction.Done,
        onImeAction = onSubmit,
        modifier = Modifier.testTag(OnboardingTestTags.SIGNIN_PASSWORD),
    )
    WizardError(state.error)
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = if (state.isRegisterMode) "Create Account" else "Sign In",
        onClick = onSubmit,
        isLoading = state.isLoading,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.SIGNIN_SUBMIT),
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    SpButton(
        text = if (state.isRegisterMode) "Already have an account? Sign In"
        else "Don't have an account? Register",
        onClick = onToggleRegister,
        style = SpButtonStyle.Ghost,
        enabled = !state.isLoading,
    )
}

// ── Step 4: Name this device ──────────────────────────────────────────────

@Composable
fun NameDeviceStepContent(
    deviceName: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    SpTextField(
        value = deviceName,
        onValueChange = onNameChange,
        label = "Device name",
        placeholder = "e.g. Living Room TV",
        imeAction = ImeAction.Done,
        onImeAction = onContinue,
        modifier = Modifier.testTag(OnboardingTestTags.NAME_DEVICE_INPUT),
    )
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = "Continue",
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NAME_DEVICE_CONTINUE),
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    SpButton(
        text = "Skip for now",
        onClick = onSkip,
        style = SpButtonStyle.Ghost,
    )
}

// ── Step 5: Controls ──────────────────────────────────────────────────────

/**
 * Reuses the exact Settings → Controls UI: the connected-controllers list, and
 * (drilled in) the per-controller detail with player assignment, type picker, and
 * the live input tester. The screen owns [selectedDeviceId] (list ↔ detail) and
 * maps the detail's callbacks to [GamepadConfigIntent]s.
 */
@Composable
fun ControlsStepContent(
    state: GamepadConfigState,
    selectedDeviceId: Int?,
    onSelectController: (Int) -> Unit,
    onBackToList: () -> Unit,
    onIntent: (GamepadConfigIntent) -> Unit,
    onContinue: () -> Unit,
) {
    val selected = selectedDeviceId?.let { id -> state.controllers.firstOrNull { it.deviceId == id } }
    if (selected != null) {
        ControllerDetail(
            controller = selected,
            pressedPositions = state.pressedPositions,
            onBack = onBackToList,
            onSelectStyle = { onIntent(GamepadConfigIntent.SetStyleOverrideForController(selected.deviceId, it)) },
            onAssignSlot = { onIntent(GamepadConfigIntent.AssignPlayer(selected.deviceId, it)) },
            onClear = { onIntent(GamepadConfigIntent.ClearPlayer(selected.deviceId)) },
            onTestActiveChange = { onIntent(GamepadConfigIntent.SetInputTestActive(selected.deviceId, it)) },
        )
        val conflict = state.conflict
        if (conflict != null) {
            SpConfirmDialog(
                title = "Switch player?",
                message = "Player ${conflict.slot + 1} is currently ${conflict.currentDeviceName}. " +
                    "Switch it to this controller? ${conflict.currentDeviceName} will become unassigned.",
                confirmText = "Switch",
                onConfirm = { onIntent(GamepadConfigIntent.ConfirmConflict) },
                onDismiss = { onIntent(GamepadConfigIntent.DismissConflict) },
            )
        }
    } else {
        ControllerControls(state = state, onSelectController = onSelectController)
        Spacer(Modifier.height(SpSpacing.Large))
        SpButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.CONTROLS_CONTINUE),
        )
    }
}

// ── Controls phase A: Verify ──────────────────────────────────────────────

/**
 * First controls page: show the detected controller and auto-focus the live
 * input tester so the user can press every button and confirm the mapping. Then
 * they pick "good" (→ button convention) or "wrong" (→ controller setup).
 */
@Composable
fun VerifyStepContent(
    detectedName: String?,
    pressedPositions: Set<GamepadPosition>,
    onTestActiveChange: (Boolean) -> Unit,
    onGood: () -> Unit,
    onWrong: () -> Unit,
    onContinueNoController: () -> Unit,
) {
    if (detectedName == null) {
        Text(
            text = "No controller detected. Connect one to verify it, or continue to set your " +
                "button preferences for when you do.",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SpSpacing.XLarge))
        SpButton(
            text = "Continue",
            onClick = onContinueNoController,
            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.VERIFY_CONTINUE),
        )
        return
    }

    Text(
        text = "Detected: $detectedName",
        style = SpTypography.BodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = SpColor.OnBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(SpSpacing.Large))

    // Auto-focus the tester so face-button presses light up positions immediately
    // (the D-pad still navigates away to the buttons below). Delay lets the
    // GamepadHandler's on-mount self-focus settle first, mirroring focusRestoreItem.
    val testerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        runCatching { testerFocus.requestFocus() }
    }
    GamepadInputTester(
        pressedPositions = pressedPositions,
        onActiveChange = onTestActiveChange,
        modifier = Modifier.focusRequester(testerFocus),
    )

    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = "Mapping is good!",
        onClick = onGood,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.VERIFY_GOOD),
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    SpButton(
        text = "Mapping is wrong",
        onClick = onWrong,
        style = SpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.VERIFY_WRONG),
    )
}

// ── Controls phase C: Button convention ───────────────────────────────────

/**
 * Lets the user choose the confirm/back button convention (#1448): bottom-button
 * confirms (Xbox/PlayStation/Steam Deck) vs right-button confirms (Nintendo).
 */
@Composable
fun ConventionStepContent(
    current: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    ConventionOption(
        selected = current == ConfirmButtonConvention.XBOX,
        title = "Bottom button confirms",
        description = "Press the bottom button to select, the right button to go back. " +
            "(Xbox, PlayStation, Steam Deck)",
        onClick = { onSelect(ConfirmButtonConvention.XBOX) },
        testTag = OnboardingTestTags.CONVENTION_XBOX,
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    ConventionOption(
        selected = current == ConfirmButtonConvention.NINTENDO,
        title = "Right button confirms",
        description = "Press the right button to select, the bottom button to go back. " +
            "(Nintendo)",
        onClick = { onSelect(ConfirmButtonConvention.NINTENDO) },
        testTag = OnboardingTestTags.CONVENTION_NINTENDO,
    )
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = "Continue",
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.CONVENTION_CONTINUE),
    )
}

@Composable
private fun ConventionOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpColor.OnGradientFill)
            .border(2.dp, if (selected) SpColor.PrimaryLight else Color.Transparent, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(SpSpacing.Default)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = SpTypography.BodyMedium, fontWeight = FontWeight.SemiBold, color = SpColor.OnBackground)
            Spacer(Modifier.height(SpSpacing.XSmall))
            Text(text = description, style = SpTypography.BodySmall, color = SpColor.OnBackgroundSecondary)
        }
        if (selected) {
            Spacer(Modifier.width(SpSpacing.Small))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = SpColor.PrimaryLight,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Step 6: All set ───────────────────────────────────────────────────────

@Composable
fun AllSetStepContent(onFinish: () -> Unit) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = SpColor.Success,
        modifier = Modifier.size(64.dp),
    )
    Spacer(Modifier.height(SpSpacing.XLarge))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        modifier = Modifier.padding(horizontal = SpSpacing.Medium),
    ) {
        Icon(
            imageVector = Icons.Filled.SportsEsports,
            contentDescription = null,
            tint = SpColor.OnBackgroundSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Connect a controller anytime — set up button mapping in Settings → Controls.",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundSecondary,
        )
    }
    Spacer(Modifier.height(SpSpacing.XLarge))
    SpButton(
        text = "Start playing",
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.ALL_SET_FINISH),
    )
}

// ── Shared ────────────────────────────────────────────────────────────────

@Composable
private fun WizardError(error: String?) {
    AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
        error?.let {
            Text(
                text = it,
                style = SpTypography.BodySmall,
                color = SpColor.Error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = SpSpacing.Small),
            )
        }
    }
}
