package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.PendingUploadKind
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.TestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveSyncQueueSettingsTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToStorageSync(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Settings))
        advance(harness)
        onNodeWithContentDescription("Storage & Sync").performClick()
        advanceQuick(harness)
    }

    @Test
    fun failedQueuedSaveIsVisibleInStorageSync() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val queuedId = runBlocking {
            val id = harness.pendingUploadRepository.enqueue(
                sessionId = "session-offline",
                kind = PendingUploadKind.Auto,
                slot = null,
                name = "Auto Save",
                coreName = "nestopia",
                compression = "",
                filePath = "/tmp/offline-auto",
                fileSize = 2_048L,
                screenshotPath = null,
                createdAt = 1_700_000_000_000L,
            )
            harness.pendingUploadRepository.markRetry(id, "offline")
            id
        }

        setContent { harness.App() }
        navigateToStorageSync(harness)

        onNodeWithTag("settings_category_content_list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag(TestTags.SETTINGS_SAVE_SYNC_QUEUE_SUMMARY))
        advanceQuick(harness)

        onNodeWithTag(TestTags.SETTINGS_SAVE_SYNC_QUEUE_SUMMARY, useUnmergedTree = true)
            .assertExists()
        onNodeWithText("Save uploads").assertIsDisplayed()
        onNodeWithText("1 pending - 1 retrying - 0 stuck").assertIsDisplayed()
        onNodeWithTag(TestTags.settingsSaveSyncJob(queuedId), useUnmergedTree = true)
            .assertExists()
        onNodeWithText("Auto-save").assertIsDisplayed()
        onNodeWithText("Session session-offline").assertIsDisplayed()
        onNodeWithText("2 KB - Retries: 1", substring = true).assertIsDisplayed()
        onNodeWithText("Last error: offline").assertIsDisplayed()
    }

    @Test
    fun failedQueuedPlayActivityIsVisibleInStorageSync() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val queuedId = runBlocking {
            val id = harness.pendingPlayTimeSyncRepository.enqueue(
                clientReportId = "report-offline",
                serverUrl = "http://server",
                userId = "user-1",
                gameId = "42",
                gameTitle = "Metroid Prime",
                durationSeconds = 125L,
                playedAt = 1_700_000_000_000L,
                createdAt = 1_700_000_030_000L,
            )
            harness.pendingPlayTimeSyncRepository.markRetry(id, "offline")
            id
        }

        setContent { harness.App() }
        navigateToStorageSync(harness)

        onNodeWithTag("settings_category_content_list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag(TestTags.SETTINGS_PLAY_TIME_SYNC_QUEUE_SUMMARY))
        advanceQuick(harness)

        onNodeWithTag(TestTags.SETTINGS_PLAY_TIME_SYNC_QUEUE_SUMMARY, useUnmergedTree = true)
            .assertExists()
        onNodeWithText("Play activity").assertIsDisplayed()
        onNodeWithText("1 pending - 2m 5s queued - 1 retrying - 0 stuck").assertIsDisplayed()
        onNodeWithTag(TestTags.settingsPlayTimeSyncJob(queuedId), useUnmergedTree = true)
            .assertExists()
        onNodeWithText("Metroid Prime").assertIsDisplayed()
        onNodeWithText("2m 5s play time - Retries: 1").assertIsDisplayed()
        onNodeWithText("Played ", substring = true).assertIsDisplayed()
        onNodeWithText(" - Queued ", substring = true).assertIsDisplayed()
        onNodeWithText("Last error: offline").assertIsDisplayed()
    }
}
