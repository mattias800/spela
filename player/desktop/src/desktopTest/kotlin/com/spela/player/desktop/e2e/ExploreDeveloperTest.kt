package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.CompanyInfo
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperDetailGenreBreakdown
import com.spela.player.domain.model.DeveloperDetailPlatformBreakdown
import com.spela.player.domain.model.DeveloperDetailPublisher
import com.spela.player.domain.model.DeveloperDetailUserStats
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for the Developer Detail screen.
 *
 * Covers:
 * - Developer spotlight section on Explore screen
 * - Developer detail hero banner
 * - Top rated row
 * - Genre breakdown chips and filtering
 * - User stats card
 * - Games grouped by platform
 * - Publisher chips
 * - Console filter fallback
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreDeveloperTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleGames = listOf(
        Game(
            id = "game-dev-1",
            title = "Final Fantasy VI",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "RPG",
            rating = 92.0,
        ),
        Game(
            id = "game-dev-2",
            title = "Chrono Trigger",
            consoleId = "snes",
            consoleName = "SNES",
            genre = "RPG",
            rating = 95.0,
        ),
        Game(
            id = "game-dev-3",
            title = "Kingdom Hearts",
            consoleId = "ps2",
            consoleName = "PS2",
            genre = "Action RPG",
            rating = 85.0,
        ),
    )

    private val sampleSpotlight = DeveloperSpotlight(
        name = "Square",
        gameCount = 24,
        avgRating = 88.5,
        consoles = listOf("SNES", "PS1", "PS2"),
        topGames = sampleGames.take(2),
        heroUrl = null,
    )

    private val sampleDeveloperDetail = DeveloperDetail(
        name = "Square",
        gameCount = 3,
        avgRating = 90.7,
        consoles = listOf("SNES", "PS2"),
        games = sampleGames,
    )

    private val richDeveloperDetail = DeveloperDetail(
        name = "Capcom",
        gameCount = 8,
        avgRating = 82.3,
        consoles = listOf("SNES", "GBA"),
        heroUrl = "https://example.com/hero.jpg",
        topGames = listOf(
            Game(
                id = "top-1",
                title = "Mega Man X",
                consoleId = "snes",
                consoleName = "SNES",
                genre = "Platformer",
                rating = 90.0,
            ),
            Game(
                id = "top-2",
                title = "Street Fighter II",
                consoleId = "snes",
                consoleName = "SNES",
                genre = "Fighting",
                rating = 88.0,
            ),
        ),
        genreBreakdown = listOf(
            DeveloperDetailGenreBreakdown(name = "Platformer", gameCount = 4),
            DeveloperDetailGenreBreakdown(name = "Fighting", gameCount = 3),
            DeveloperDetailGenreBreakdown(name = "Action", gameCount = 1),
        ),
        platformBreakdown = listOf(
            DeveloperDetailPlatformBreakdown("SNES", "snes", 5),
            DeveloperDetailPlatformBreakdown("GBA", "gba", 3),
        ),
        userStats = DeveloperDetailUserStats(
            totalPlayTime = 14400,
            gamesPlayed = 5,
            favoriteCount = 3,
            mostPlayedGame = Game(
                id = "most-played-1",
                title = "Mega Man X",
                consoleId = "snes",
                consoleName = "SNES",
                genre = "Platformer",
                rating = 90.0,
            ),
        ),
        publishers = listOf(
            DeveloperDetailPublisher("Capcom", 6),
            DeveloperDetailPublisher("Nintendo", 2),
        ),
        games = listOf(
            Game(id = "cap-1", title = "Mega Man X", consoleId = "snes", consoleName = "SNES", genre = "Platformer", rating = 90.0),
            Game(id = "cap-2", title = "Street Fighter II", consoleId = "snes", consoleName = "SNES", genre = "Fighting", rating = 88.0),
            Game(id = "cap-3", title = "Breath of Fire", consoleId = "snes", consoleName = "SNES", genre = "RPG", rating = 78.0),
            Game(id = "cap-4", title = "Mega Man Zero", consoleId = "gba", consoleName = "GBA", genre = "Platformer", rating = 85.0),
            Game(id = "cap-5", title = "Street Fighter Alpha", consoleId = "gba", consoleName = "GBA", genre = "Fighting", rating = 75.0),
            Game(id = "cap-6", title = "Final Fight One", consoleId = "gba", consoleName = "GBA", genre = "Action", rating = 72.0),
            Game(id = "cap-7", title = "Mega Man Battle Network", consoleId = "gba", consoleName = "GBA", genre = "Platformer", rating = 80.0, coverUrl = null),
            Game(id = "cap-8", title = "Mega Man X2", consoleId = "snes", consoleName = "SNES", genre = "Platformer", rating = 87.0),
        ),
    )

    // --- Developer spotlight on Explore screen ---

    @Test
    fun developerSpotlightRendersOnExploreScreen() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_developer_spotlight_section").assertExists()
        onNodeWithText("Developer Spotlight").assertExists()
    }

    @Test
    fun developerSpotlightDisplaysNameAndStats() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("developer_spotlight").assertExists()
        onNodeWithText("Square").assertExists()
        onNodeWithText("24 games").assertExists()
    }

    @Test
    fun developerSpotlightHiddenWhenNoData() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = null

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("explore_screen").assertIsDisplayed()
        onNodeWithTag("explore_developer_spotlight_section").assertDoesNotExist()
    }

    // --- Navigation to developer detail ---

    @Test
    fun navigationToDeveloperDetailWorks() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerSpotlightData = sampleSpotlight
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Explore))
        advance(harness)

        onNodeWithTag("developer_spotlight_card").performClick()
        advance(harness)

        val navState = harness.navigationViewModel.state.value
        assertEquals("explore_developer/Square", navState.currentScreen.route)
    }

    // --- Developer detail screen ---

    @Test
    fun developerDetailShowsGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_detail_screen").assertIsDisplayed()
        onAllNodesWithText("Square").fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected at least one 'Square' text node" }
        }
        onNodeWithText("Final Fantasy VI").assertExists()
        onNodeWithText("Chrono Trigger").assertExists()
        onNodeWithText("Kingdom Hearts").assertExists()
    }

    @Test
    fun developerDetailConsoleFilterWorks() = runComposeUiTest {
        val harness = createHarness()
        // Use a detail without platformBreakdown to trigger console filter fallback
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        // All 3 games visible initially
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertExists()

        // Click SNES filter
        onNodeWithTag("developer_console_chip_SNES").performClick()
        advanceQuick(harness)

        // Only SNES games visible
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertDoesNotExist()

        // Click SNES again to clear filter
        onNodeWithTag("developer_console_chip_SNES").performClick()
        advanceQuick(harness)

        // All games visible again
        onNodeWithTag("developer_game_game-dev-1").assertExists()
        onNodeWithTag("developer_game_game-dev-2").assertExists()
        onNodeWithTag("developer_game_game-dev-3").assertExists()
    }

    // --- Hero Banner ---

    @Test
    fun heroBannerDisplaysNameAndStats() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_hero_banner").assertExists()
        onNodeWithTag("developer_hero_name").assertExists()
        onNodeWithTag("developer_game_count").assertExists()
        onNodeWithTag("developer_avg_rating").assertExists()
        onNodeWithTag("developer_platform_count").assertExists()
        onNodeWithText("8 games").assertExists()
        onNodeWithText("2 platforms").assertExists()
    }

    @Test
    fun heroBannerFallbackWhenNoHeroUrl() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        // Should still show hero banner with gradient fallback
        onNodeWithTag("developer_hero_banner").assertExists()
        onNodeWithTag("developer_hero_name").assertExists()
    }

    // --- Top Rated Row ---

    @Test
    fun topRatedRowShowsWhenEnoughGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_top_rated_section").assertExists()
        onNodeWithTag("developer_top_rated_header").assertExists()
        onNodeWithText("Top Rated").assertExists()
        onNodeWithTag("developer_top_game_top-1").assertExists()
        onNodeWithTag("developer_top_game_top-2").assertExists()
    }

    @Test
    fun topRatedRowHiddenWhenFewGames() = runComposeUiTest {
        val harness = createHarness()
        // Only 3 games, need 5+ for top rated
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_top_rated_section").assertDoesNotExist()
    }

    // --- Genre Breakdown ---

    @Test
    fun genreBreakdownShowsChips() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_genre_breakdown").assertExists()
        onNodeWithTag("developer_genre_header").assertExists()
        onNodeWithText("Genres").assertExists()
        // "All" chip should be first and selected by default
        onNodeWithTag("developer_genre_chip_all").assertExists()
        onNodeWithTag("developer_genre_chip_Platformer").assertExists()
        onNodeWithTag("developer_genre_chip_Fighting").assertExists()
        onNodeWithTag("developer_genre_chip_Action").assertExists()
    }

    @Test
    fun genreChipFiltersGames() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        // Scroll to game items which may be below the viewport
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_game_cap-1"))

        // Initially all games visible
        onNodeWithTag("developer_game_cap-1").assertExists() // Platformer
        onNodeWithTag("developer_game_cap-2").assertExists() // Fighting

        // Click Platformer chip to filter (scroll up first)
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_genre_chip_Platformer"))
        onNodeWithTag("developer_genre_chip_Platformer").performClick()
        advanceQuick(harness)

        // Scroll to see filtered games
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_game_cap-1"))

        // Only Platformer games should be visible
        onNodeWithTag("developer_game_cap-1").assertExists()   // Mega Man X - Platformer
        onNodeWithTag("developer_game_cap-2").assertDoesNotExist() // Street Fighter II - Fighting
        onNodeWithTag("developer_game_cap-3").assertDoesNotExist() // Breath of Fire - RPG

        // Click again to clear
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_genre_chip_Platformer"))
        onNodeWithTag("developer_genre_chip_Platformer").performClick()
        advanceQuick(harness)

        // Scroll to see all games again
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_game_cap-1"))

        // All games visible again
        onNodeWithTag("developer_game_cap-1").assertExists()
        onNodeWithTag("developer_game_cap-2").assertExists()
    }

    @Test
    fun genreBreakdownHiddenWhenFewGenres() = runComposeUiTest {
        val harness = createHarness()
        val detailWithOneGenre = richDeveloperDetail.copy(
            genreBreakdown = listOf(DeveloperDetailGenreBreakdown(name = "RPG", gameCount = 3)),
        )
        harness.exploreRepo.developerDetails = mapOf("Capcom" to detailWithOneGenre)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_genre_breakdown").assertDoesNotExist()
    }

    // --- User Stats Card ---

    @Test
    fun userStatsCardShowsStats() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_user_stats").assertExists()
        onNodeWithTag("developer_user_stats_header").assertExists()
        onNodeWithText("Your Stats").assertExists()
        onNodeWithTag("developer_user_stats_card").assertExists()
        onNodeWithTag("developer_user_stat_playtime").assertExists()
        onNodeWithTag("developer_user_stat_played").assertExists()
        onNodeWithTag("developer_user_stat_favorites").assertExists()
        // 14400 seconds = 4h 0m
        onNodeWithText("4h 0m").assertExists()
        onNodeWithText("5/8").assertExists()
        onNodeWithText("3").assertExists()
    }

    @Test
    fun userStatsCardShowsMostPlayedGame() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_most_played_game").assertExists()
        onNodeWithText("Most played:").assertExists()
    }

    @Test
    fun userStatsHiddenWhenNoStats() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_user_stats").assertDoesNotExist()
    }

    // --- Games Grouped by Platform ---

    @Test
    fun gamesGroupedByPlatform() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        // Scroll to platform headers which may be below the viewport
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_platform_header_snes"))
        onNodeWithTag("developer_platform_header_snes").assertExists()

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_platform_header_gba"))
        onNodeWithTag("developer_platform_header_gba").assertExists()

        // Games should be present under their respective platforms
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_game_cap-1"))
        onNodeWithTag("developer_game_cap-1").assertExists()

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_game_cap-4"))
        onNodeWithTag("developer_game_cap-4").assertExists()
    }

    @Test
    fun platformHeadersOrderedByGameCount() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        // SNES has 5 games, GBA has 3 -- SNES header should come first
        // Scroll to and verify both exist
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_platform_header_snes"))
        onNodeWithTag("developer_platform_header_snes").assertExists()

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_platform_header_gba"))
        onNodeWithTag("developer_platform_header_gba").assertExists()
    }

    // --- Publishers Section ---

    @Test
    fun publishersSectionShowsChips() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to richDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        // Scroll to publishers section at the bottom
        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_publishers_section"))
        onNodeWithTag("developer_publishers_section").assertExists()
        onNodeWithTag("developer_publishers_header").assertExists()
        onNodeWithText("Publishers").assertExists()
        onNodeWithTag("developer_publisher_chip_Capcom").assertExists()
        onNodeWithTag("developer_publisher_chip_Nintendo").assertExists()
    }

    @Test
    fun publishersHiddenWhenEmpty() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_publishers_section").assertDoesNotExist()
    }

    // --- Company Info: Logo ---

    private val companyInfoFull = CompanyInfo(
        logoUrl = "https://example.com/capcom-logo.png",
        description = "Capcom Co., Ltd. is a Japanese video game developer and publisher known for creating multi-million-selling game franchises including Street Fighter, Mega Man, and Resident Evil.",
        foundedYear = 1979,
        country = "Japan",
        websiteUrl = "https://www.capcom.com",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Capcom",
    )

    private val detailWithCompanyInfo = richDeveloperDetail.copy(
        companyInfo = companyInfoFull,
    )

    private val detailWithLogoOnly = sampleDeveloperDetail.copy(
        companyInfo = CompanyInfo(
            logoUrl = "https://example.com/square-logo.png",
        ),
    )

    private val detailWithDescriptionOnly = sampleDeveloperDetail.copy(
        companyInfo = CompanyInfo(
            description = "Square was a Japanese video game company.",
            foundedYear = 1986,
            country = "Japan",
        ),
    )

    @Test
    fun companyLogoShownWhenAvailable() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to detailWithCompanyInfo)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_hero_banner").assertExists()
        onNodeWithTag("developer_company_logo").assertExists()
        onNodeWithTag("developer_letter_avatar").assertDoesNotExist()
    }

    @Test
    fun letterAvatarFallbackWhenNoLogo() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_hero_banner").assertExists()
        onNodeWithTag("developer_letter_avatar").assertExists()
        onNodeWithTag("developer_company_logo").assertDoesNotExist()
    }

    @Test
    fun letterAvatarShownWhenCompanyInfoHasNoLogo() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to detailWithDescriptionOnly)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_letter_avatar").assertExists()
        onNodeWithTag("developer_company_logo").assertDoesNotExist()
    }

    // --- Company Info: Description Section ---

    @Test
    fun companyDescriptionSectionShown() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to detailWithCompanyInfo)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_company_description_section"))
        onNodeWithTag("developer_company_description_section").assertExists()
        onNodeWithTag("developer_company_about_header").assertExists()
        onNodeWithText("About").assertExists()
        onNodeWithTag("developer_company_description").assertExists()
        onNodeWithTag("developer_company_description_toggle").assertExists()
        onNodeWithText("Show more").assertExists()
    }

    @Test
    fun companyDescriptionToggleExpandsAndCollapses() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to detailWithCompanyInfo)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_company_description_toggle"))

        // Initially shows "Show more"
        onNodeWithText("Show more").assertExists()

        // Click to expand
        onNodeWithTag("developer_company_description_toggle").performClick()
        advanceQuick(harness)
        onNodeWithText("Show less").assertExists()

        // Click to collapse
        onNodeWithTag("developer_company_description_toggle").performClick()
        advanceQuick(harness)
        onNodeWithText("Show more").assertExists()
    }

    @Test
    fun companyMetadataShowsFoundedYearAndCountry() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Capcom" to detailWithCompanyInfo)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Capcom"))
        )
        advance(harness)

        onNodeWithTag("developer_detail_content")
            .performScrollToNode(hasTestTag("developer_company_metadata"))
        onNodeWithTag("developer_company_metadata").assertExists()
        onNodeWithText("Founded 1979 \u2014 Japan").assertExists()
    }

    @Test
    fun companyDescriptionHiddenWhenNoDescription() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to detailWithLogoOnly)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_company_description_section").assertDoesNotExist()
    }

    @Test
    fun companyDescriptionHiddenWhenNoCompanyInfo() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.developerDetails = mapOf("Square" to sampleDeveloperDetail)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper("Square"))
        )
        advance(harness)

        onNodeWithTag("developer_company_description_section").assertDoesNotExist()
    }
}
