package com.spela.player.presentation.ui.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.state.LoginState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpServerPill
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

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

// ── Step 5: All set ───────────────────────────────────────────────────────

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
