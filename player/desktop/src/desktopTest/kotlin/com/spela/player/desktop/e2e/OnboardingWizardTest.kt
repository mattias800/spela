package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.repository.OnboardingHintKeys
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.feature.onboarding.OnboardingTestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop E2E tests for the first-run setup wizard (#1448). Drives the wizard
 * directly via navigation (the bootstrap gating that decides *whether* to show
 * it is covered separately in NavigationViewModelTest) and walks the full
 * Welcome → Connect → Sign in → Name device → All set journey against fake repos.
 *
 * Buttons are clicked by test tag because several step titles share copy with
 * their primary button (e.g. the "Sign in" title and the "Sign in" button).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class OnboardingWizardTest {

    private fun createWizardHarness(serverKnown: Boolean = false): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        if (serverKnown) {
            harness.serverRepo.preAddServer("Local", "http://localhost:8080", active = true)
        }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.OnboardingWizard))
        return harness
    }

    private fun ComposeUiTest.showWizard(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness)
    }

    @Test
    fun fullWizardFlowConnectsSignsInNamesDeviceAndCompletes() = runComposeUiTest {
        val harness = createWizardHarness()
        harness.serverRepo.validateServerResult = true

        showWizard(harness)

        // Step 1: Welcome
        onNodeWithText("Welcome to Spela").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).performClick()
        advanceQuick(harness)

        // Step 2: Connect server
        onNodeWithText("Connect your server").assertIsDisplayed()
        onNodeWithText("Server name").performClick()
        onNodeWithText("Server name").performTextInput("My Home Server")
        onNodeWithText("Server address").performClick()
        onNodeWithText("Server address").performTextInput("http://localhost:8080")
        onNodeWithTag(OnboardingTestTags.CONNECT_SUBMIT).performClick()
        advance(harness)

        // Step 3: Sign in (Username label is unique; the "Sign in" copy is not)
        onNodeWithText("Username").assertIsDisplayed()
        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")
        onNodeWithTag(OnboardingTestTags.SIGNIN_SUBMIT).performClick()
        advance(harness)

        // Step 4: Name this device
        onNodeWithText("Name this device").assertIsDisplayed()
        onNodeWithText("Device name").performClick()
        onNodeWithText("Device name").performTextInput("Living Room TV")
        onNodeWithTag(OnboardingTestTags.NAME_DEVICE_CONTINUE).performClick()
        advanceQuick(harness)

        // Step 5: Controls — Verify (no controller) → button convention → finish
        onNodeWithText("Verify your controller").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.VERIFY_CONTINUE).performClick()
        advanceQuick(harness)
        onNodeWithText("Confirm & Back buttons").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.CONVENTION_CONTINUE).performClick()
        advanceQuick(harness)

        // Step 6: All set
        onNodeWithText("You're all set!").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.ALL_SET_FINISH).performClick()
        advance(harness)

        // Lands on Home and the wizard is marked complete so it never shows again.
        onNodeWithText("Spela").assertIsDisplayed()
        assertTrue(harness.onboardingRepo.isDismissedNow(OnboardingHintKeys.FIRST_RUN_WIZARD_COMPLETED))
    }

    @Test
    fun wizardSkipsConnectBacksAndRegistersWithEmail() = runComposeUiTest {
        val harness = createWizardHarness(serverKnown = true)

        showWizard(harness)

        onNodeWithText("Back").assertDoesNotExist()
        onNodeWithText("Welcome to Spela").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.WELCOME_START).performClick()
        advanceQuick(harness)

        // Goes straight to Sign in — the Connect step is skipped.
        onNodeWithText("Username").assertIsDisplayed()
        onNodeWithText("Connect your server").assertDoesNotExist()

        // Back returns to Welcome.
        onNodeWithText("Back").performClick()
        advanceQuick(harness)
        onNodeWithText("Welcome to Spela").assertIsDisplayed()

        onNodeWithTag(OnboardingTestTags.WELCOME_START).performClick()
        advanceQuick(harness)

        // Toggle to register mode — the email field must appear (CR #1 fix).
        onNodeWithText("Don't have an account? Register").performClick()
        advanceQuick(harness)
        onNodeWithText("Email").assertIsDisplayed()

        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("newplayer")
        onNodeWithText("Email").performClick()
        onNodeWithText("Email").performTextInput("new@example.com")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("secret123")
        onNodeWithTag(OnboardingTestTags.SIGNIN_SUBMIT).performClick()
        advance(harness)

        // Registration succeeds → advances to the device-naming step.
        onNodeWithText("Name this device").assertIsDisplayed()
    }

    @Test
    fun wizardCanSkipDeviceNamingAndPersistButtonConvention() = runComposeUiTest {
        val harness = createWizardHarness(serverKnown = true)

        showWizard(harness)

        onNodeWithTag(OnboardingTestTags.WELCOME_START).performClick()
        advanceQuick(harness)

        // Sign in (Connect skipped — server already active)
        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")
        onNodeWithTag(OnboardingTestTags.SIGNIN_SUBMIT).performClick()
        advance(harness)

        // Skip device naming → Controls (Verify, no controller) → convention → All set
        onNodeWithText("Skip for now").performClick()
        advanceQuick(harness)
        onNodeWithText("Verify your controller").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.VERIFY_CONTINUE).performClick()
        advanceQuick(harness)

        // Choose Nintendo convention and finish; it must persist.
        onNodeWithTag(OnboardingTestTags.CONVENTION_NINTENDO).performClick()
        advanceQuick(harness)
        onNodeWithTag(OnboardingTestTags.CONVENTION_CONTINUE).performClick()
        advance(harness)
        onNodeWithText("You're all set!").assertIsDisplayed()
        assertEquals("nintendo", harness.preferencesRepo.getConfirmButtonConvention())
    }

    @Test
    fun wizardControllerStepListsConnectedControllerAndOpensDetail() = runComposeUiTest {
        val harness = createWizardHarness(serverKnown = true)
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")

        showWizard(harness)

        onNodeWithTag(OnboardingTestTags.WELCOME_START).performClick()
        advanceQuick(harness)

        // Sign in (Connect skipped — server already active)
        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")
        onNodeWithTag(OnboardingTestTags.SIGNIN_SUBMIT).performClick()
        advance(harness)

        // Name device → Controls
        onNodeWithTag(OnboardingTestTags.NAME_DEVICE_CONTINUE).performClick()
        advanceQuick(harness)

        // Verify shows the detected controller; "Mapping is wrong" opens setup.
        onNodeWithText("Verify your controller").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.VERIFY_WRONG).performClick()
        advanceQuick(harness)

        // The connected controller is listed; drilling in shows its detail.
        onNodeWithText("Set up your controller").assertIsDisplayed()
        onNodeWithTag("controller_row_1").assertIsDisplayed()
        onNodeWithTag("controller_row_1").performClick()
        advanceQuick(harness)
        onNodeWithTag("controller_detail_title").assertIsDisplayed()

        // Back to the list, continue to convention, then finish.
        onNodeWithTag("controller_detail_back").performClick()
        advanceQuick(harness)
        onNodeWithTag(OnboardingTestTags.CONTROLS_CONTINUE).performClick()
        advanceQuick(harness)
        onNodeWithText("Confirm & Back buttons").assertIsDisplayed()
        onNodeWithTag(OnboardingTestTags.CONVENTION_CONTINUE).performClick()
        advanceQuick(harness)
        onNodeWithText("You're all set!").assertIsDisplayed()
    }
}
