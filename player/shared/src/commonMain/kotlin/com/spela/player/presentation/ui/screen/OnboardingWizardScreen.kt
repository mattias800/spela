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
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.presentation.ui.feature.onboarding.AllSetStepContent
import com.spela.player.presentation.ui.feature.onboarding.ConnectStepContent
import com.spela.player.presentation.ui.feature.onboarding.ControlsStepContent
import com.spela.player.presentation.ui.feature.onboarding.ConventionStepContent
import com.spela.player.presentation.ui.feature.onboarding.NameDeviceStepContent
import com.spela.player.presentation.ui.feature.onboarding.OnboardingTestTags
import com.spela.player.presentation.ui.feature.onboarding.OnboardingWizardChrome
import com.spela.player.presentation.ui.feature.onboarding.SignInStepContent
import com.spela.player.presentation.ui.feature.onboarding.VerifyStepContent
import com.spela.player.presentation.ui.feature.onboarding.WelcomeStepContent
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.OnboardingStep
import com.spela.player.presentation.viewmodel.OnboardingWizardIntent
import com.spela.player.presentation.viewmodel.OnboardingWizardViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionIntent
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel

/** Progress-dot milestones. The three controls pages (Verify / FixControls /
 *  Convention) collapse to one dot so the indicator stays stable. */
private const val MILESTONE_COUNT = 6

private fun milestoneIndex(step: OnboardingStep): Int = when (step) {
    OnboardingStep.Welcome -> 0
    OnboardingStep.Connect -> 1
    OnboardingStep.SignIn -> 2
    OnboardingStep.NameDevice -> 3
    OnboardingStep.Verify, OnboardingStep.FixControls, OnboardingStep.Convention -> 4
    OnboardingStep.AllSet -> 5
}

/**
 * First-run setup wizard (#1448) — the "unified first-run journey". It composes
 * the existing [ServerConnectionViewModel] and [LoginViewModel] (both unchanged)
 * with a small [OnboardingWizardViewModel] for the wizard's own page back-stack
 * and device-name state, framing each page in [OnboardingWizardChrome].
 *
 * This screen IS the glue: it owns the page graph (skipping Connect when a
 * server is known, the auth pages when already signed in, and FixControls unless
 * the user reports a bad mapping) and wires each flow's success signal to the
 * next page. Every page after the first offers Back (via the VM's back-stack).
 *
 * @param restoredServerUrl set when bootstrap came from NeedsLogin (server
 *   known, token expired) — drives skipping the Connect page.
 * @param alreadyAuthenticated true if the device is already signed in (the
 *   wizard then skips Connect + Sign in). False on a normal fresh-install run.
 * @param onComplete fired after the final page; the caller resets to Home.
 */
@Composable
fun OnboardingWizardScreen(
    wizardViewModel: OnboardingWizardViewModel,
    serverConnectionViewModel: ServerConnectionViewModel,
    loginViewModel: LoginViewModel,
    gamepadConfigViewModel: GamepadConfigViewModel?,
    preferencesRepository: PreferencesRepository,
    restoredServerUrl: String?,
    alreadyAuthenticated: Boolean,
    onComplete: () -> Unit,
) {
    val wizardState by wizardViewModel.state.collectAsState()
    val serverState by serverConnectionViewModel.state.collectAsState()
    val loginState by loginViewModel.state.collectAsState()

    val goTo: (OnboardingStep) -> Unit = { wizardViewModel.onIntent(OnboardingWizardIntent.GoTo(it)) }
    val backOrNull: (() -> Unit)? =
        if (wizardState.canGoBack) ({ wizardViewModel.onIntent(OnboardingWizardIntent.Back) }) else null

    val cameWithServer = !restoredServerUrl.isNullOrBlank()
    val hasServer = serverState.servers.isNotEmpty() || cameWithServer
    val serverUrl = serverState.servers.firstOrNull { it.id == serverState.selectedServerId }?.url
        ?: serverState.servers.lastOrNull()?.url
        ?: restoredServerUrl
        ?: ""

    val stepIndex = milestoneIndex(wizardState.step)
    // Where the controller phase begins (or is skipped) after naming.
    val afterNaming = if (gamepadConfigViewModel != null) OnboardingStep.Verify else OnboardingStep.AllSet

    // System / hardware back navigates the wizard's own page stack rather than
    // falling through to the OS (which would exit the app). On the first page
    // (Welcome) it's disabled, so back exits as usual (#1448).
    PlatformBackHandler(enabled = wizardState.canGoBack) {
        wizardViewModel.onIntent(OnboardingWizardIntent.Back)
    }

    Box(modifier = Modifier.fillMaxSize().testTag(OnboardingTestTags.SCREEN)) {
        when (wizardState.step) {
            OnboardingStep.Welcome -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = MILESTONE_COUNT,
                title = "Welcome to Spela",
                subtitle = "Let's get this device set up — it only takes a moment.",
                onBack = backOrNull,
            ) {
                WelcomeStepContent(
                    onGetStarted = {
                        goTo(
                            when {
                                alreadyAuthenticated -> OnboardingStep.NameDevice
                                hasServer -> OnboardingStep.SignIn
                                else -> OnboardingStep.Connect
                            },
                        )
                    },
                )
            }

            OnboardingStep.Connect -> {
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
                    stepCount = MILESTONE_COUNT,
                    title = "Connect your server",
                    subtitle = "Point Spela at your self-hosted server to access your library.",
                    onBack = backOrNull,
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
                    stepCount = MILESTONE_COUNT,
                    title = if (loginState.isRegisterMode) "Create account" else "Sign in",
                    subtitle = if (loginState.isRegisterMode) {
                        "Create your Spela account on this server."
                    } else {
                        "Use your Spela account on this server."
                    },
                    onBack = backOrNull,
                ) {
                    SignInStepContent(
                        state = loginState,
                        serverUrl = serverUrl,
                        onUsernameChange = { loginViewModel.onIntent(LoginIntent.SetUsername(it)) },
                        onPasswordChange = { loginViewModel.onIntent(LoginIntent.SetPassword(it)) },
                        onSubmit = { loginViewModel.onIntent(LoginIntent.Submit) },
                        onToggleRegister = { loginViewModel.onIntent(LoginIntent.ToggleRegisterMode) },
                        onChangeServer = { goTo(OnboardingStep.Connect) },
                    )
                }
            }

            OnboardingStep.NameDevice -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = MILESTONE_COUNT,
                title = "Name this device",
                subtitle = "So you can tell it apart from your other devices.",
                onBack = backOrNull,
            ) {
                NameDeviceStepContent(
                    deviceName = wizardState.deviceName,
                    onNameChange = { wizardViewModel.onIntent(OnboardingWizardIntent.SetDeviceName(it)) },
                    onContinue = { goTo(afterNaming) },
                    onSkip = { goTo(afterNaming) },
                )
            }

            OnboardingStep.Verify -> {
                val configVm = gamepadConfigViewModel
                if (configVm == null) {
                    LaunchedEffect(Unit) { goTo(OnboardingStep.AllSet) }
                } else {
                    val configState by configVm.state.collectAsState()
                    val detected = configState.controllers.firstOrNull()
                    OnboardingWizardChrome(
                        stepIndex = stepIndex,
                        stepCount = MILESTONE_COUNT,
                        title = "Verify your controller",
                        subtitle = "Press each button — the matching position should light up.",
                        onBack = backOrNull,
                    ) {
                        VerifyStepContent(
                            detectedName = detected?.deviceName?.ifBlank { "your controller" },
                            pressedPositions = configState.pressedPositions,
                            sticks = configState.testSticks,
                            confirmHeld = configState.confirmHeld,
                            onTestActiveChange = { active ->
                                detected?.deviceId?.let {
                                    configVm.onIntent(GamepadConfigIntent.SetInputTestActive(it, active))
                                }
                            },
                            onGood = { goTo(OnboardingStep.Convention) },
                            onWrong = { goTo(OnboardingStep.FixControls) },
                            onContinueNoController = { goTo(OnboardingStep.Convention) },
                        )
                    }
                }
            }

            OnboardingStep.FixControls -> {
                val configVm = gamepadConfigViewModel
                if (configVm == null) {
                    LaunchedEffect(Unit) { goTo(OnboardingStep.Convention) }
                } else {
                    val configState by configVm.state.collectAsState()
                    var selectedControllerId by remember { mutableStateOf<Int?>(null) }
                    OnboardingWizardChrome(
                        stepIndex = stepIndex,
                        stepCount = MILESTONE_COUNT,
                        title = "Set up your controller",
                        subtitle = "Assign players, pick the controller type, then re-test.",
                        // In the detail view the inner Back returns to the list;
                        // in the list view the wizard Back returns to Verify.
                        onBack = if (selectedControllerId != null) null else backOrNull,
                    ) {
                        ControlsStepContent(
                            state = configState,
                            selectedDeviceId = selectedControllerId,
                            onSelectController = { selectedControllerId = it },
                            onBackToList = { selectedControllerId = null },
                            onIntent = { configVm.onIntent(it) },
                            onContinue = { goTo(OnboardingStep.Convention) },
                        )
                    }
                }
            }

            OnboardingStep.Convention -> {
                var convention by remember { mutableStateOf(preferencesRepository.getConfirmButtonConvention()) }
                OnboardingWizardChrome(
                    stepIndex = stepIndex,
                    stepCount = MILESTONE_COUNT,
                    title = "Confirm & Back buttons",
                    subtitle = "Pick which button confirms and which goes back.",
                    onBack = backOrNull,
                ) {
                    ConventionStepContent(
                        current = convention,
                        onSelect = {
                            convention = it
                            preferencesRepository.setConfirmButtonConvention(it)
                        },
                        onContinue = { goTo(OnboardingStep.AllSet) },
                    )
                }
            }

            OnboardingStep.AllSet -> OnboardingWizardChrome(
                stepIndex = stepIndex,
                stepCount = MILESTONE_COUNT,
                title = "You're all set!",
                subtitle = "Your library is ready.",
                onBack = backOrNull,
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
