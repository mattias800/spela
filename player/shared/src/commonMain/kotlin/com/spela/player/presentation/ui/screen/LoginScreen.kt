package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    serverUrl: String,
    onLoginSuccess: () -> Unit,
    onChangeServer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(serverUrl) {
        viewModel.onIntent(LoginIntent.SetServerUrl(serverUrl))
    }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            onLoginSuccess()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        val isLandscape = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (isLandscape) SpSpacing.XLarge else 100.dp))

            // Branding
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SpColor.PrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S",
                    style = SpTypography.DisplaySmall,
                    color = SpColor.Primary,
                )
            }

            Spacer(Modifier.height(SpSpacing.Default))

            Text(
                text = if (state.isRegisterMode) "Create Account" else "Welcome Back",
                style = SpTypography.DisplaySmall,
                color = SpColor.OnBackground,
            )

            Text(
                text = if (state.isRegisterMode) "Set up your player account"
                else "Sign in to start playing",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )

            Spacer(Modifier.height(if (isLandscape) SpSpacing.XLarge else SpSpacing.XXXLarge))

            // Constrain form width in landscape
            Column(
                modifier = Modifier
                    .then(if (isLandscape) Modifier.widthIn(max = 450.dp) else Modifier.fillMaxWidth()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Server URL indicator (tappable to change server)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                        .background(SpColor.SurfaceVariant)
                        .clickable { onChangeServer() }
                        .padding(SpSpacing.Medium),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = serverUrl.ifEmpty { "No server configured" },
                            style = SpTypography.BodySmall,
                            color = SpColor.OnBackgroundTertiary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Tap to change server",
                            style = SpTypography.LabelSmall,
                            color = SpColor.Primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(SpSpacing.XLarge))

                SpTextField(
                    value = state.username,
                    onValueChange = { viewModel.onIntent(LoginIntent.SetUsername(it)) },
                    label = "Username",
                    placeholder = "Enter your username",
                    enabled = !state.isLoading,
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
                    modifier = Modifier.fillMaxWidth(),
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
        }
    }
}
