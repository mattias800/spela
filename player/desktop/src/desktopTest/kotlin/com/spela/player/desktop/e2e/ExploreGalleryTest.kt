package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.ArtworkItem
import com.spela.player.domain.model.ScreenshotItem
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for Phase 9: Visual Browsing — Gallery & Art Modes.
 *
 * Covers:
 * - Artwork showcase section renders on Explore screen
 * - Gallery screen renders with screenshots
 * - Gallery shows game titles
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreGalleryTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleArtwork = listOf(
        ArtworkItem(
            url = "https://example.com/art1.jpg",
            width = 1920,
            height = 1080,
            gameId = "game-1",
            gameTitle = "Super Mario World",
            consoleName = "Super Nintendo",
            consoleAbbreviation = "SNES",
            consoleColor = "#6B21A8",
        ),
        ArtworkItem(
            url = "https://example.com/art2.jpg",
            width = 1920,
            height = 1080,
            gameId = "game-2",
            gameTitle = "The Legend of Zelda",
            consoleName = "Nintendo Entertainment System",
            consoleAbbreviation = "NES",
            consoleColor = "#DC2626",
        ),
    )

    private val sampleScreenshots = listOf(
        ScreenshotItem(
            url = "https://example.com/ss1.jpg",
            gameId = "game-1",
            gameTitle = "Super Mario World",
            consoleName = "Super Nintendo",
            consoleAbbreviation = "SNES",
            consoleColor = "#6B21A8",
        ),
        ScreenshotItem(
            url = "https://example.com/ss2.jpg",
            gameId = "game-2",
            gameTitle = "The Legend of Zelda",
            consoleName = "Nintendo Entertainment System",
            consoleAbbreviation = "NES",
            consoleColor = "#DC2626",
        ),
        ScreenshotItem(
            url = "https://example.com/ss3.jpg",
            gameId = "game-3",
            gameTitle = "Chrono Trigger",
            consoleName = "Super Nintendo",
            consoleAbbreviation = "SNES",
            consoleColor = "#6B21A8",
        ),
    )

    // --- Artwork showcase section renders on Explore screen ---

    @Test
    fun artworkShowcaseSectionRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.artworkItems = sampleArtwork

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()

        // Scroll to find the artwork section (it may be off-screen)
        onNodeWithTag("explore_artwork_section").assertExists()
        onNodeWithText("Visual Discovery").assertExists()
        onNodeWithTag("artwork_showcase").assertExists()
    }

    // --- Artwork showcase shows game titles ---

    @Test
    fun artworkShowcaseShowsGameTitles() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.artworkItems = sampleArtwork

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("artwork_card_game-1").assertExists()
        onNodeWithTag("artwork_card_game-2").assertExists()
    }

    // --- Gallery screen renders with screenshots ---

    @Test
    fun galleryScreenRendersWithScreenshots() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.screenshotItems = sampleScreenshots

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreGallery)
        )
        advanceFully(harness)

        onNodeWithTag("gallery_screen").assertIsDisplayed()
        onNodeWithTag("gallery_grid").assertExists()
        onNodeWithTag("gallery_screenshot_game-1").assertExists()
        onNodeWithTag("gallery_screenshot_game-2").assertExists()
    }

    // --- Gallery shows game titles ---

    @Test
    fun galleryScreenShowsGameTitles() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.screenshotItems = sampleScreenshots

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreGallery)
        )
        advanceFully(harness)

        onNodeWithTag("gallery_title_game-1", useUnmergedTree = true).assertExists()
        onNodeWithTag("gallery_title_game-2", useUnmergedTree = true).assertExists()
    }

    // --- Gallery empty state ---

    @Test
    fun galleryScreenShowsEmptyStateWhenNoScreenshots() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.screenshotItems = emptyList()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreGallery)
        )
        advanceFully(harness)

        onNodeWithTag("gallery_screen").assertIsDisplayed()
        onNodeWithTag("gallery_empty_state").assertExists()
    }

    // --- Navigation from Explore to Gallery ---

    @Test
    fun navigationToGalleryScreenWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.screenshotItems = sampleScreenshots

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreGallery)
        )
        advanceFully(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_gallery", navState.currentScreen.route)
    }
}
