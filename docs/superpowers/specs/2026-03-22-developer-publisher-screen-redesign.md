# Developer/Publisher Screen Redesign

**Date:** 2026-03-22
**Status:** Draft

## Problem

The developer/publisher detail screen has 11 sections in a single endless scroll. Users must scroll extensively to find anything. The screen uses custom card implementations instead of shared design system components, causing visual inconsistency.

## Design

### Approach: Curated Profile + Games Subscreen

Inspired by Spotify artist pages — show a curated taste of the developer, not a data dump. Users who want the full game list navigate to a dedicated subscreen.

### Screen 1: Developer Profile (curated overview)

Refactors the existing `ExploreDeveloperScreen.kt` in place (same file, same route). Uses `SpSectionList` for consistent spacing, replacing the current raw `LazyColumn` with manual `Spacer` calls.

**1. Hero Banner** (always shown, full-width above SpSectionList)
- Company logo from IGDB (circular, 48dp) with letter avatar fallback
- Developer/publisher name (DisplaySmall)
- Stats row: game count, avg rating, platform count
- Related developers/publishers as tappable `SpChip`s below stats (uses existing `onDeveloperSelected`/`onPublisherSelected` callbacks — these live outside the hero composable, not inside it)

**2. About Section** (`SpTitledSection`)
- IGDB company description, collapsible (3 lines with "Show more")
- Metadata line: "Founded {year} · {country}"
- External links: Website, Wikipedia (if available from IGDB)

**3. At a Glance** (horizontal stat cards row)
- Compact stat cards using `SpCard`: total games, active years, primary genre, avg rating
- Scrollable `LazyRow`, no section card wrapper needed

**4. Top Rated** (`SpTitledSection` with `edgeToEdgeContent`)
- Horizontal carousel of highest-rated games (show all available, up to 8)
- Each game uses a role component (e.g. `DeveloperTopRatedCard`) that delegates to `SpGameCard`
- Title trailing: "See all N games →" as `SpButton(style = Ghost)` navigating to games subscreen
- Hidden entirely if developer has fewer than 3 rated games
- "See all" link hidden if no games in library (nothing to browse)

**5. Your Stats** (`SpTitledSection`, conditional)
- Only shown if the user has played games by this developer
- Compact stats: total play time, games played, favorites count
- Accent-tinted card background (brand color)

**States:**
- **Loading:** `DeveloperDetailSkeleton` (already exists) for initial load
- **Error:** `SpEmptyState` with retry action + `SpSnackbar` for transient errors (same pattern as current implementation)
- **Empty:** `SpEmptyState` with "Developer not found" / "Publisher not found" message

### Screen 2: Developer Games (filterable list)

A new `DeveloperGamesScreen.kt` that extracts the filterable game grid pattern from `ConsoleGamesScreen`. Rather than importing `ConsoleGamesScreen` directly (it's tightly coupled to `GameListViewModel` and console-based filtering), we copy the layout pattern: search bar, sort options, paginated grid.

**Route:** New `SpScreen.DeveloperGames(name: String, isDeveloper: Boolean)` in `SpNavigation.kt`.

**ViewModel:** Extends `ExploreViewModel` with a new intent/state for paginated developer game browsing, keeping the data source consistent with the profile screen. This avoids creating a new ViewModel and keeps developer data in one place.

**Layout:**
1. `SpTopBar` with developer name and back button
2. Search bar (filters game list by title)
3. Sort chips (title, rating, release date)
4. Paginated game grid using `SpGameCard` via a role component
5. Loading: skeleton grid (same pattern as `ConsoleGamesScreen`)
6. Empty: `SpEmptyState` with "No games found" message

**Navigation:** accessed via "See all N games →" in the Top Rated section. Back button returns to the profile screen (standard back-stack behavior — `DeveloperGamesScreen` is a proper `SpScreen` route entry).

**Data retention:** Both screens use `ExploreViewModel`, so profile data is retained when navigating to/from the games subscreen.

### Dropped Sections

These sections are removed entirely (analytics that few users care about):

- **Release Timeline** — year-grouped release history
- **Rating Distribution** — stacked bar chart
- **Genre Breakdown** — genre chips with counts

The composables in `DeveloperDetailComponents.kt` for these sections are deleted.

### Moved Sections

- **Related Developers/Publishers** — moved below hero banner as tappable chips
- **Full game list by platform** — replaced by dedicated games subscreen
- **Publishers section** — moved below hero banner as chips

### Component Discipline

All UI must follow the Design → Content → Role hierarchy:

- `SpSectionList` for the profile's section layout (replaces raw `LazyColumn`)
- `SpTitledSection` for About, Top Rated, Your Stats sections
- Role components (delegating to `SpGameCard`) for all game cards — screens never use `SpGameCard` directly
- `SpCard` for At a Glance stat cards
- `SpChip` for related developer/publisher chips
- No custom card implementations in the screen files
- All interactive elements must be focusable for gamepad navigation (use `SpButton` or `SpChip` with `onClick`, not raw `Modifier.clickable` on text)

### Navigation

```
GameDetailScreen (tap Developer/Publisher in metadata)
  → ExploreDeveloperScreen (curated profile, existing route)
    → DeveloperGamesScreen (tap "See all N games →", new route)
      → GameDetailScreen (tap a game)
```

Back navigation returns to the previous screen in the stack. Scroll position is preserved via `SaveableStateHolder` (existing pattern).

### Shared Between Developer and Publisher

Both developer and publisher use the same screens with an `isDeveloper` flag, as they do today. The API endpoints are separate but return the same response shape.

### Test Coverage

Existing test tags must be maintained or updated. Key tags:
- `developer_detail_screen`, `developer_hero_banner`, `developer_company_description_section`
- `developer_top_rated_section`, `developer_user_stats`, `developer_at_a_glance_section`
- New tags for: `developer_games_screen`, `developer_related_chips`, `developer_see_all_games`

## Scope

- Refactor `ExploreDeveloperScreen.kt` into curated profile (in place, same route)
- Refactor `DeveloperDetailComponents.kt` — keep hero, about, at-a-glance, top rated, your stats; delete timeline, rating dist, genre breakdown composables
- Create `DeveloperGamesScreen.kt` with filterable game grid
- Add `SpScreen.DeveloperGames` route to `SpNavigation.kt` and wire in `SpelaApp.kt`
- Extend `ExploreViewModel` with developer games browsing state
- Ensure all components use shared design system
- Maintain test tag coverage

## Out of Scope

- Web app developer/publisher page changes (separate task)
- New IGDB data fetching (all data already available)
- Changes to the server API
- Landscape-specific layout (current behavior is acceptable)
