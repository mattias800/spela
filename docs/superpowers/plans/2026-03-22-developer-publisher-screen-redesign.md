# Developer/Publisher Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 11-section developer/publisher endless scroll with a curated Spotify-style profile + dedicated games subscreen.

**Architecture:** Refactor `ExploreDeveloperScreen.kt` in place to show only 5 curated sections. Delete unused composables from `DeveloperDetailComponents.kt`. Add a new `DeveloperGamesScreen.kt` for the filterable game list, reachable via "See all games" link. Extend `ExploreViewModel` with developer game browsing state.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, shared design system components (SpSectionList, SpTitledSection, SpGameCard, SpChip)

**Spec:** `docs/superpowers/specs/2026-03-22-developer-publisher-screen-redesign.md`

**Important notes:**
- The `Game` model uses `rating` (Double) for IGDB rating. Verify field names when passing to `SpGameCard`.
- `SpGameCard` does not accept a `modifier` parameter. Use `testTag` parameter for test tags.
- The Top Rated threshold is changing from `gameCount >= 5` (current) to `topGames.size >= 3` (new). Existing tests asserting the old threshold must be updated.
- `detail.gameCount` = total games this developer made on the server. `detail.games.size` = games available in user's library. "See all" link should use `detail.games` (library games), not `gameCount` (IGDB total).

---

### Task 1: Add DeveloperGames navigation route

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/navigation/SpNavigation.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/navigation/NavigationViewModel.kt`

- [ ] **Step 1: Add DeveloperGames route to SpNavigation.kt**

Add after the existing `ExplorePublisher` route (line 40):

```kotlin
data class DeveloperGames(val name: String, val isDeveloper: Boolean = true) : SpScreen("developer_games/$name/$isDeveloper")
```

- [ ] **Step 2: Register route in NavigationViewModel**

In `NavigationViewModel.kt`, find the `when` block that lists non-tab screens (around line 212 where `ExploreDeveloper` and `ExplorePublisher` are listed). Add `is SpScreen.DeveloperGames` to the same group.

- [ ] **Step 3: Build to verify**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
feat: add DeveloperGames navigation route
```

---

### Task 2: Refactor ExploreDeveloperScreen into curated profile and delete unused composables

This is a single task to avoid creating an uncompilable intermediate state. The screen refactor removes references to composables, and the composable deletions happen in the same commit.

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/ExploreDeveloperScreen.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/explore/DeveloperDetailComponents.kt`

- [ ] **Step 1: Add `onNavigateToGames` callback parameter**

Add to `ExploreDeveloperScreen` signature:

```kotlin
onNavigateToGames: ((name: String, isDeveloper: Boolean) -> Unit)? = null,
```

- [ ] **Step 2: Replace LazyColumn with SpSectionList**

Replace the raw `LazyColumn` (around line 97-314) with `SpSectionList`. The hero banner stays as the first item (full-width via negative padding or outside the section list).

Keep only these sections in this order:

1. **Hero Banner** — `DeveloperHeroBanner(detail)`
2. **Related chips** — `FlowRow` of `SpChip`s for publishers and related developers (conditional on non-empty). Uses `onPublisherSelected`/`onDeveloperSelected` callbacks.
3. **About** — `DeveloperCompanyDescription(companyInfo)` wrapped in `SpTitledSection(title = "About")`, conditional on `companyInfo?.description != null`
4. **At a Glance** — `DeveloperAtAGlance(detail)`, no section wrapper
5. **Top Rated** — `SpTitledSection(title = "Top Rated", edgeToEdgeContent = true)` with `DeveloperTopRatedRow`. Conditional on `detail.topGames.size >= 3` (threshold changed from `gameCount >= 5`). Title trailing: `SpButton(text = "See all", style = Ghost)` navigating to games subscreen, shown only when `detail.games.isNotEmpty()` (library games exist).
6. **Your Stats** — `DeveloperUserStatsCard` wrapped in `SpTitledSection(title = "Your Stats")`, conditional on `detail.userStats != null`

- [ ] **Step 3: Remove all deleted section references from ExploreDeveloperScreen**

Remove the `item {}` blocks for: timeline, rating distribution, genre breakdown, games by platform, publishers section, related developers section. Remove the private composables `DeveloperPlatformHeader` and `DeveloperConsoleFilterRow` from this file.

- [ ] **Step 4: Delete unused composables from DeveloperDetailComponents.kt**

Delete these functions and their private helpers:
- `DeveloperTimeline` and helpers (`TimelineYearColumn`, `TimelineGameThumb`)
- `DeveloperRatingDistribution` and helper (`RatingBar`)
- `DeveloperGenreBreakdown`
- `DeveloperPublishersSection`
- `DeveloperRelatedDevelopersSection` and helper (`RelatedDeveloperCard`)
- `DeveloperGameItem` — will be replaced by role component in Task 5

- [ ] **Step 5: Clean up unused imports in both files**

Remove imports for deleted composables and now-unused types.

- [ ] **Step 6: Build to verify**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
refactor: convert developer screen to curated profile, delete unused sections
```

---

### Task 3: Update and fix broken tests

**Files:**
- Modify: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/ExploreDeveloperTest.kt`

- [ ] **Step 1: Identify broken tests**

Run: `cd player && ./run-desktop-tests.sh`
Collect all failing tests. These will include tests for deleted features: genre breakdown, publishers section, platform grouping, console filter, game item display.

- [ ] **Step 2: Delete tests for removed features**

Delete test functions that assert on removed sections:
- Genre breakdown tests (`genreBreakdownShowsChips`, `genreChipFiltersGames`, `genreBreakdownHiddenWhenFewGenres`)
- Publishers section tests (`publishersSectionShowsChips`, `publishersHiddenWhenEmpty`)
- Platform grouping tests (`gamesGroupedByPlatform`, `platformHeadersOrderedByGameCount`)
- Console filter tests (`developerDetailConsoleFilterWorks`)
- Game list tests that assert game items in the main screen (`developerDetailShowsGames` — games moved to subscreen)

- [ ] **Step 3: Update Top Rated threshold test**

Update `topRatedRowHiddenWhenFewGames` to use the new threshold of 3 rated games (was 5).

- [ ] **Step 4: Run tests to verify all pass**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All tests pass

- [ ] **Step 5: Commit**

```
test: update developer detail tests for curated profile redesign
```

---

### Task 4: Wire up navigation in SpelaApp.kt

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt`

- [ ] **Step 1: Pass `onNavigateToGames` to ExploreDeveloperScreen**

In both the `is SpScreen.ExploreDeveloper` block (~line 667) and `is SpScreen.ExplorePublisher` block (~line 695), add:

```kotlin
onNavigateToGames = { devName, isDev ->
    navigationViewModel.onIntent(
        NavigationIntent.NavigateTo(SpScreen.DeveloperGames(devName, isDev))
    )
},
```

- [ ] **Step 2: Add DeveloperGames screen rendering block**

After the ExplorePublisher block, add a new `is SpScreen.DeveloperGames` case. For now, render a placeholder — the real screen is built in Task 5:

```kotlin
is SpScreen.DeveloperGames -> {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Developer Games — coming soon")
    }
}
```

- [ ] **Step 3: Build, run app, verify navigation**

Run: `cd player && ./gradlew :desktop:run`
Verify: Navigate to a developer profile → "See all games" link appears → tapping it navigates to the placeholder screen → back button returns to profile.

- [ ] **Step 4: Commit**

```
feat: wire DeveloperGames navigation in SpelaApp
```

---

### Task 5: Create DeveloperGamesScreen

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/DeveloperGamesScreen.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt` (replace placeholder)
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/ExploreViewModel.kt`

- [ ] **Step 1: Add search/sort state to ExploreViewModel**

Add fields to `DeveloperDetailState`:

```kotlin
val gamesSearchQuery: String = "",
val gamesSortBy: String = "title",  // "title", "rating", "releaseDate"
```

Add functions to `ExploreViewModel`:

```kotlin
fun setDeveloperGamesSearch(query: String) {
    _developerDetailState.update { it.copy(gamesSearchQuery = query) }
}

fun setDeveloperGamesSort(sortBy: String) {
    _developerDetailState.update { it.copy(gamesSortBy = sortBy) }
}
```

Add a computed property to `DeveloperDetailState` for sorted/filtered games:

```kotlin
val sortedFilteredGames: List<Game> get() {
    val filtered = if (gamesSearchQuery.length >= 2) {
        filteredGames.filter { it.title.contains(gamesSearchQuery, ignoreCase = true) }
    } else filteredGames
    return when (gamesSortBy) {
        "rating" -> filtered.sortedByDescending { it.rating }
        "releaseDate" -> filtered.sortedBy { it.releaseDate ?: "" }
        else -> filtered.sortedBy { it.title.lowercase() }
    }
}
```

- [ ] **Step 2: Create DeveloperGamesScreen.kt**

Follow `ConsoleGamesScreen.kt` pattern. Key structure:

```kotlin
@Composable
fun DeveloperGamesScreen(
    name: String,
    isDeveloper: Boolean = true,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
)
```

Layout:
1. `SpTopBar` with title "$name" and back button
2. Search field with toggle visibility (same pattern as ConsoleGamesScreen)
3. Sort dropdown (title, rating, release date)
4. `LazyVerticalGrid` with `GridCells.Adaptive(SpSpacing.GridCellMinWidth)` — each game uses a role component delegating to `SpGameCard`
5. Loading: `SpLoadingIndicator` if `state.isLoading && state.detail == null`
6. Empty: `SpEmptyState` with "No games found"
7. Data source: `state.sortedFilteredGames`
8. If `state.detail == null && !state.isLoading`, trigger load: `LaunchedEffect` calling `viewModel.loadDeveloperDetail(name)` or `loadPublisherDetail(name)`

Test tags: `developer_games_screen`, `developer_games_search`, `developer_games_grid`

- [ ] **Step 3: Replace placeholder in SpelaApp.kt**

```kotlin
is SpScreen.DeveloperGames -> {
    if (exploreViewModel != null) {
        DeveloperGamesScreen(
            name = screen.name,
            isDeveloper = screen.isDeveloper,
            viewModel = exploreViewModel,
            onGameSelected = { gameId ->
                navigationViewModel.onIntent(
                    NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                )
            },
            onBack = {
                navigationViewModel.onIntent(NavigationIntent.GoBack)
            },
        )
    }
}
```

- [ ] **Step 4: Build and run**

Run: `cd player && ./gradlew :desktop:run`
Verify: Developer profile → "See all games" → filterable game grid with search and sort → tap a game → game detail → back returns to game list → back returns to profile.

- [ ] **Step 5: Commit**

```
feat: add DeveloperGamesScreen with search, sort, and game grid
```

---

### Task 6: Update DeveloperTopRatedRow to use role component

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/explore/DeveloperDetailComponents.kt`

- [ ] **Step 1: Refactor TopRatedGameCard to delegate to SpGameCard**

Replace the existing `TopRatedGameCard` body to delegate to `SpGameCard`. Rename to `DeveloperTopRatedCard` for clarity:

```kotlin
/** ROLE component — developer's top-rated game card. Delegates to [SpGameCard]. */
@Composable
internal fun DeveloperTopRatedCard(
    game: Game,
    onGameSelected: (String) -> Unit,
) {
    SpGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = { onGameSelected(game.id) },
        rating = game.rating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        testTag = "developer_top_game_${game.id}",
    )
}
```

- [ ] **Step 2: Update DeveloperTopRatedRow to use the role component**

Update calls from `TopRatedGameCard` to `DeveloperTopRatedCard`. Clean up old code.

- [ ] **Step 3: Build and verify visually**

Run: `cd player && ./gradlew :desktop:run`
Verify: Developer profile → Top Rated row shows game cards matching the app's design system.

- [ ] **Step 4: Commit**

```
refactor: convert DeveloperTopRatedCard to delegate to SpGameCard
```

---

### Task 7: Wrap remaining sections in SpTitledSection

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/ExploreDeveloperScreen.kt`

- [ ] **Step 1: Verify About is wrapped in SpTitledSection**

If not already done in Task 2, wrap:
```kotlin
SpTitledSection(title = "About") {
    DeveloperCompanyDescription(companyInfo = companyInfo)
}
```

- [ ] **Step 2: Verify Your Stats is wrapped in SpTitledSection**

```kotlin
SpTitledSection(title = "Your Stats") {
    DeveloperUserStatsCard(userStats = detail.userStats, ...)
}
```

- [ ] **Step 3: Verify At a Glance has no section wrapper**

Per spec: standalone `LazyRow` of `SpCard` items, no `SpTitledSection`.

- [ ] **Step 4: Verify all interactive elements use focusable components**

- "See all games" uses `SpButton(style = Ghost)`
- Related dev/publisher chips use `SpChip(onClick = ...)`
- No raw `Modifier.clickable` on text

- [ ] **Step 5: Build, run, verify visual consistency**

Run: `cd player && ./gradlew :desktop:run`

- [ ] **Step 6: Commit**

```
refactor: ensure developer profile sections use SpTitledSection consistently
```

---

### Task 8: Final cleanup, test tags, and full test run

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/ExploreDeveloperScreen.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/DeveloperGamesScreen.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/explore/DeveloperDetailComponents.kt`

- [ ] **Step 1: Verify test tags on profile screen**

Ensure these test tags exist:
- `developer_detail_screen` on the root
- `developer_hero_banner` on the hero
- `developer_company_description_section` on the about section
- `developer_at_a_glance_section` on the stats row
- `developer_top_rated_section` on the top rated section
- `developer_user_stats` on the user stats card
- `developer_related_chips` on the related devs/publishers chip row
- `developer_see_all_games` on the "See all games" button

- [ ] **Step 2: Verify test tags on DeveloperGamesScreen**

- `developer_games_screen` on the root
- `developer_games_search` on the search field
- `developer_games_grid` on the grid

- [ ] **Step 3: Remove dead imports across all modified files**

- [ ] **Step 4: Run full desktop tests**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All tests pass

- [ ] **Step 5: Build and run final verification**

Run: `cd player && ./gradlew :desktop:run`
Verify the complete flow:
1. Game detail → tap Developer → curated profile (5 sections only)
2. Profile → "See all games" → filterable game grid
3. Game grid → tap game → game detail
4. Back navigation works at each step
5. Publisher flow works the same way

- [ ] **Step 6: Commit**

```
chore: final cleanup and test tag verification for developer screen redesign
```
