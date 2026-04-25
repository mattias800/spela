package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.components.SpGradientBackground
import com.spela.player.presentation.ui.components.SpLogo
import com.spela.player.presentation.ui.components.SpServerPill
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.state.LoginState
import com.spela.player.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    serverUrl: String,
    onLoginSuccess: () -> Unit,
    onChangeServer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    // Wipe transient state on every fresh mount so the credentials
    // and isLoggedIn flag from a prior session don't leak into the
    // next login attempt. The LoginViewModel is held at activity
    // scope, so without this Reset the screen comes up with stale
    // values when the user signs out and revisits.
    LaunchedEffect(Unit) {
        viewModel.onIntent(LoginIntent.Reset)
        viewModel.onIntent(LoginIntent.SetServerUrl(serverUrl))
    }

    LaunchedEffect(serverUrl) {
        viewModel.onIntent(LoginIntent.SetServerUrl(serverUrl))
    }

    // Navigate on a one-shot SharedFlow event rather than the sticky
    // `state.isLoggedIn` flag. The flow only emits when a submit
    // succeeds; a stale `true` from an earlier session will not
    // re-emit on screen mount, so the user actually sees the form.
    LaunchedEffect(viewModel) {
        viewModel.loginSucceeded.collect { onLoginSuccess() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag(TestTags.SCREEN_LOGIN)) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            SplitLoginLayout(
                viewModel = viewModel,
                state = state,
                serverUrl = serverUrl,
                onChangeServer = onChangeServer,
            )
        } else {
            MobileLoginLayout(
                viewModel = viewModel,
                state = state,
                serverUrl = serverUrl,
                onChangeServer = onChangeServer,
            )
        }
    }
}

// ────────────────────────────────────────────────────────
// Mobile layout: single column, logo on top
// ────────────────────────────────────────────────────────

@Composable
private fun MobileLoginLayout(
    viewModel: LoginViewModel,
    state: com.spela.player.presentation.state.LoginState,
    serverUrl: String,
    onChangeServer: () -> Unit,
) {
    SpGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(100.dp))

            SpLogo(size = 384.dp)

            Spacer(Modifier.height(SpSpacing.XLarge))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginForm(
                    viewModel = viewModel,
                    state = state,
                    serverUrl = serverUrl,
                    onChangeServer = onChangeServer,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Split layout: hero panel + form panel side by side
// ────────────────────────────────────────────────────────

@Composable
private fun SplitLoginLayout(
    viewModel: LoginViewModel,
    state: com.spela.player.presentation.state.LoginState,
    serverUrl: String,
    onChangeServer: () -> Unit,
) {
    SpGradientBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left hero panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.40f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SpLogo(size = 240.dp)
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = "\"Nu spelar vi!\"",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Right form panel
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = SpSpacing.XLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoginForm(
                        viewModel = viewModel,
                        state = state,
                        serverUrl = serverUrl,
                        onChangeServer = onChangeServer,
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Shared form content
// ────────────────────────────────────────────────────────

@Composable
private fun LoginForm(
    viewModel: LoginViewModel,
    state: com.spela.player.presentation.state.LoginState,
    serverUrl: String,
    onChangeServer: () -> Unit,
) {
    SpServerPill(
        serverUrl = serverUrl,
        onClick = onChangeServer,
        modifier = Modifier.testTag(TestTags.LOGIN_SERVER_PILL),
    )

    Spacer(Modifier.height(SpSpacing.XLarge))

    SpTextField(
        value = state.username,
        onValueChange = { viewModel.onIntent(LoginIntent.SetUsername(it)) },
        label = "Username",
        placeholder = "Enter your username",
        enabled = !state.isLoading,
        modifier = Modifier.testTag(TestTags.LOGIN_USERNAME_INPUT),
    )

    Spacer(Modifier.height(SpSpacing.Default))

    SpTextField(
        value = state.password,
        onValueChange = { viewModel.onIntent(LoginIntent.SetPassword(it)) },
        label = "Password",
        placeholder = "Enter your password",
        isPassword = true,
        enabled = !state.isLoading,
        imeAction = ImeAction.Done,
        onImeAction = { viewModel.onIntent(LoginIntent.Submit) },
        modifier = Modifier.testTag(TestTags.LOGIN_PASSWORD_INPUT),
    )

    // Error display
    AnimatedVisibility(
        visible = state.error != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        state.error?.let { error ->
            Text(
                text = error,
                style = SpTypography.BodySmall,
                color = SpColor.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpSpacing.Small),
                textAlign = TextAlign.Center,
            )
        }
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    SpButton(
        text = if (state.isRegisterMode) "Create Account" else "Sign In",
        onClick = { viewModel.onIntent(LoginIntent.Submit) },
        modifier = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_SUBMIT_BUTTON),
        isLoading = state.isLoading,
        enabled = !state.isLoading,
    )

    Spacer(Modifier.height(SpSpacing.Medium))

    SpButton(
        text = if (state.isRegisterMode) "Already have an account? Sign In"
        else "Don't have an account? Register",
        onClick = { viewModel.onIntent(LoginIntent.ToggleRegisterMode) },
        style = SpButtonStyle.Ghost,
        enabled = !state.isLoading,
    )

    Spacer(Modifier.height(SpSpacing.XLarge))
}
