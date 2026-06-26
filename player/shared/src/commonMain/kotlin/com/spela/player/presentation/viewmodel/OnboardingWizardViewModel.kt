package com.spela.player.presentation.viewmodel

import com.spela.player.data.device.DeviceManager
import com.spela.player.domain.repository.OnboardingHintKeys
import com.spela.player.domain.repository.OnboardingRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ordered steps of the first-run setup wizard (#1448). The screen owns the
 * step *graph* (it skips [Connect] when a server is already active), so this
 * enum is just the vocabulary — navigation is driven via
 * [OnboardingWizardIntent.GoTo].
 */
enum class OnboardingStep { Welcome, Connect, SignIn, NameDevice, AllSet }

data class OnboardingWizardState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val deviceName: String = "",
)

sealed interface OnboardingWizardIntent {
    data class GoTo(val step: OnboardingStep) : OnboardingWizardIntent
    data class SetDeviceName(val name: String) : OnboardingWizardIntent
}

/**
 * Owns the wizard's own state — the current [OnboardingStep] and the
 * device-name the user is typing. It deliberately does NOT own the connect or
 * login flows: those stay in [ServerConnectionViewModel] / [LoginViewModel] and
 * are composed by `OnboardingWizardScreen` (the glue). This keeps the wizard a
 * thin coordinator over existing, unchanged auth ViewModels.
 *
 * [complete] is the single side-effecting exit: it persists the device name
 * (locally + best-effort to the server) and records the first-run flag so the
 * bootstrap never routes here again. The screen fires `ResetToHome` afterwards,
 * mirroring the normal post-login transition.
 */
class OnboardingWizardViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val deviceManager: DeviceManager,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        OnboardingWizardState(deviceName = deviceManager.getDeviceName()),
    )
    val state: StateFlow<OnboardingWizardState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingWizardIntent) {
        when (intent) {
            is OnboardingWizardIntent.GoTo -> _state.update { it.copy(step = intent.step) }
            is OnboardingWizardIntent.SetDeviceName -> _state.update { it.copy(deviceName = intent.name) }
        }
    }

    /**
     * Persist the chosen device name and mark the wizard complete. Safe to call
     * once at the end of the flow; the device name is only written when the user
     * actually provided one (an empty entry leaves the existing/default name).
     */
    fun complete() {
        val name = _state.value.deviceName.trim()
        scope.launch(dispatchers.io) {
            // By the time the user reaches this step they have already signed in,
            // and LoginUseCase registered the device with the server (under the
            // default name). So naming here is a server *update*, not the initial
            // registration — updateDeviceNameOnServer needs the server device id
            // that login populated. It's best-effort (runCatching) because an
            // offline finish should still complete the wizard locally.
            if (name.isNotEmpty() && name != deviceManager.getDeviceName()) {
                deviceManager.setDeviceName(name)
                runCatching { deviceManager.updateDeviceNameOnServer(name) }
            }
            onboardingRepository.markDismissed(OnboardingHintKeys.FIRST_RUN_WIZARD_COMPLETED)
        }
    }
}
