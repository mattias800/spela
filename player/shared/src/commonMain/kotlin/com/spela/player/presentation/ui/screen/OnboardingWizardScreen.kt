package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.presentation.ui.feature.onboarding.AllSetStepContent
import com.spela.player.presentation.ui.feature.onboarding.ConnectStepContent
import com.spela.player.presentation.ui.feature.onboarding.ControlsStepContent
import com.spela.player.presentation.ui.feature.onboarding.NameDeviceStepContent
import com.spela.player.presentation.ui.feature.onboarding.OnboardingTestTags
import com.spela.player.presentation.ui.feature.onboarding.OnboardingWizardChrome
import com.spela.player.presentation.ui.feature.onboarding.SignInStepContent
import com.spela.player.presentation.ui.feature.onboarding.WelcomeStepContent
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.OnboardingStep
import com.spela.player.presentation.viewmodel.OnboardingWizardIntent
import com.spela.player.presentation.viewmodel.OnboardingWizardViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionIntent
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel

/**
 * First-run setup wizard (#1448) — the "unified first-run journey". It composes
 * the existing [ServerConnectionViewModel] and [LoginViewModel] (both unchanged)
 * with a small [OnboardingWizardViewModel] for the wizard's own step + device
 * name state, framing each step in [OnboardingWizardChrome].
 *
 * This screen IS the glue: it owns the step graph (including skipping Connect
 * when a server is already known) and wires each auth flow's success signal to
 * the next step. The feature composables under `feature/onboarding/` know
 * nothing about navigation or ViewModels.
 *
 * @param restoredServerUrl set when bootstrap routed here from a NeedsLogin
 *   state (server known, token expired) — drives skipping the Connect step.
 * @param onComplete fired after the final step; the caller resets to Home,
 *   mirroring the normal post-login transition.
 */
@Composable
fun OnboardingWizardScreen(
    wizardViewModel: OnboardingWizardViewModel,
    serverConnectionViewModel: ServerConnectionViewModel,
    loginViewModel: LoginViewModel,
    gamepadConfigViewModel: GamepadConfigViewModel?,
    restoredServerUrl: String?,
    onComplete: () -> Unit,
) {
    val wizardState by wizardViewModel.state.collectAsState()
    val serverState by serverConnectionViewModel.state.collectAsState()
    val loginState by loginViewModel.state.collectAsState()

    val goTo: (OnboardingStep) -> Unit = { wizardViewModel.onIntent(OnboardingWizardIntent.GoTo(it)) }

    // A server is already known when bootstrap came from NeedsLogin, or once the
    // user adds one in the Connect step.
    val cameWithServer = !restoredServerUrl.isNullOrBlank()
    val hasServer = serverState.servers.isNotEmpty() || cameWithServer

    val serverUrl = serverState.servers.firstOrNull { it.id == serverState.selectedServerId }?.url
        ?: serverState.servers.lastOrNull()?.url
        ?: restoredServerUrl
        ?: ""

    val stepCount = OnboardingStep.entries.size
    val stepIndex = wizardState.step.ordinal

    Box(modifier = Modifier.fillMaxSize().testTag(OnboardingTestTags.SCREEN)) {
        when (wizardState.step) {
            OnboardingStep.Welcome -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = stepCount,
                title = "Welcome to Spela",
                subtitle = "Let's get this device set up — it only takes a moment.",
            ) {
                WelcomeStepContent(
                    onGetStarted = { goTo(if (hasServer) OnboardingStep.SignIn else OnboardingStep.Connect) },
                )
            }

            OnboardingStep.Connect -> {
                // addServer() has no explicit success event; on a fresh device the
                // server list is empty, so a non-empty list after a submit (and no
                // error / not validating) means the add succeeded. Select it active,
                // then advance.
                var connectAttempted by remember { mutableStateOf(false) }
                LaunchedEffect(serverState.servers.size, serverState.isValidating, serverState.error) {
                    if (connectAttempted && !serverState.isValidating &&
                        serverState.error == null && serverState.servers.isNotEmpty()
                    ) {
                        val added = serverState.servers.last()
                        serverConnectionViewModel.onIntent(ServerConnectionIntent.SelectServer(added.id))
                        connectAttempted = false
                        goTo(OnboardingStep.SignIn)
                    }
                }
                OnboardingWizardChrome(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    title = "Connect your server",
                    subtitle = "Point Spela at your self-hosted server to access your library.",
                    onBack = { goTo(OnboardingStep.Welcome) },
                ) {
                    ConnectStepContent(
                        name = serverState.newServerName,
                        url = serverState.newServerUrl,
                        onNameChange = { serverConnectionViewModel.onIntent(ServerConnectionIntent.SetNewServerName(it)) },
                        onUrlChange = { serverConnectionViewModel.onIntent(ServerConnectionIntent.SetNewServerUrl(it)) },
                        onConnect = {
                            connectAttempted = true
                            serverConnectionViewModel.onIntent(ServerConnectionIntent.AddServer)
                        },
                        isValidating = serverState.isValidating,
                        error = serverState.error,
                    )
                }
            }

            OnboardingStep.SignIn -> {
                LaunchedEffect(Unit) {
                    loginViewModel.onIntent(LoginIntent.Reset)
                    loginViewModel.onIntent(LoginIntent.SetServerUrl(serverUrl))
                }
                LaunchedEffect(serverUrl) { loginViewModel.onIntent(LoginIntent.SetServerUrl(serverUrl)) }
                LaunchedEffect(loginViewModel) {
                    loginViewModel.loginSucceeded.collect { goTo(OnboardingStep.NameDevice) }
                }
                OnboardingWizardChrome(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    title = "Sign in",
                    subtitle = "Use your Spela account on this server.",
                    onBack = { goTo(if (cameWithServer) OnboardingStep.Welcome else OnboardingStep.Connect) },
                ) {
                    SignInStepContent(
                        state = loginState,
                        serverUrl = serverUrl,
                        onUsernameChange = { loginViewModel.onIntent(LoginIntent.SetUsername(it)) },
                        onEmailChange = { loginViewModel.onIntent(LoginIntent.SetEmail(it)) },
                        onPasswordChange = { loginViewModel.onIntent(LoginIntent.SetPassword(it)) },
                        onSubmit = { loginViewModel.onIntent(LoginIntent.Submit) },
                        onToggleRegister = { loginViewModel.onIntent(LoginIntent.ToggleRegisterMode) },
                        onChangeServer = { goTo(OnboardingStep.Connect) },
                    )
                }
            }

            OnboardingStep.NameDevice -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = stepCount,
                title = "Name this device",
                subtitle = "So you can tell it apart from your other devices.",
            ) {
                val afterNaming =
                    if (gamepadConfigViewModel != null) OnboardingStep.Controls else OnboardingStep.AllSet
                NameDeviceStepContent(
                    deviceName = wizardState.deviceName,
                    onNameChange = { wizardViewModel.onIntent(OnboardingWizardIntent.SetDeviceName(it)) },
                    onContinue = { goTo(afterNaming) },
                    onSkip = { goTo(afterNaming) },
                )
            }

            OnboardingStep.Controls -> {
                if (gamepadConfigViewModel == null) {
                    LaunchedEffect(Unit) { goTo(OnboardingStep.AllSet) }
                } else {
                    val configState by gamepadConfigViewModel.state.collectAsState()
                    var selectedControllerId by remember { mutableStateOf<Int?>(null) }
                    OnboardingWizardChrome(
                        stepIndex = stepIndex,
                        stepCount = stepCount,
                        title = "Set up your controller",
                        subtitle = "Assign players, pick the controller type, and test the buttons — " +
                            "you can change this anytime in Settings.",
                    ) {
                        ControlsStepContent(
                            state = configState,
                            selectedDeviceId = selectedControllerId,
                            onSelectController = { selectedControllerId = it },
                            onBackToList = { selectedControllerId = null },
                            onIntent = { gamepadConfigViewModel.onIntent(it) },
                            onContinue = { goTo(OnboardingStep.AllSet) },
                        )
                    }
                }
            }

            OnboardingStep.AllSet -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = stepCount,
                title = "You're all set!",
                subtitle = "Your library is ready.",
            ) {
                AllSetStepContent(
                    onFinish = {
                        wizardViewModel.complete()
                        onComplete()
                    },
                )
            }
        }
    }
}
