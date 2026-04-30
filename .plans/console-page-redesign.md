# Plan: Console Page Redesign (Issues 2+3)

## Goal
Match the web UI: console page shows curated showcase content only, with a "Browse All Games" button linking to a separate filterable game list screen.

## Current State
- Single ConsoleScreen shows both showcase sections AND a flat game grid
- Genre breakdown takes up too much space
- No filters, no alphabet jump, no pagination on the game list
- Sort is client-side only

## Design

### Phase 1: Clean up Console Page (curated content only)
Remove the inline game grid from ConsoleScreen. Keep only:
1. Console hero banner
2. Continue Playing (recently played on this console)
3. Essentials (GameShelf)
4. Hidden Gems (GameShelf)
5. Top Developers
6. Recently Added (new section — needs adding to ConsoleShowcase model)
7. "Browse All Games" button → navigates to new AllGamesScreen with consoleId filter

Genre Breakdown is removed from the console page — it becomes a filter in the game list.

### Phase 2: Filterable Game List Screen
New screen `ConsoleGamesScreen` (or reuse existing `AllGamesScreen` with a consoleId param):
- Server-side filtered game list via `GET /api/consoles/{id}/games`
- Filters: search, genre, sort (title/rating/release date/recently played), sort order
- Alphabet quick-jump bar (A-Z)
- Pagination (server-side, load-more or page controls)
- Game count header

### Phase 3 (optional): Advanced filters
- Year range
- Rating range
- Play status (unplayed/played/favorited)
- Developer/publisher text search

## Implementation Steps

### Step 1: Remove game grid from ConsoleScreen
**File:** `player/.../ui/screen/ConsoleScreen.kt`
- Remove the `LazyVerticalGrid` that shows all games
- Remove the sort dropdown
- Remove the "X games" heading
- Add "Browse All {n} Games" button at the bottom → navigates to `SpScreen.ConsoleGames(consoleId)`

### Step 2: Add ConsoleGames screen route
**File:** `player/.../navigation/SpNavigation.kt`
- Add `data class ConsoleGames(val consoleId: String) : SpScreen("console_games/$consoleId")`

**File:** `player/.../ui/SpelaApp.kt`
- Add `is SpScreen.ConsoleGames` branch rendering the new screen

### Step 3: Create ConsoleGamesScreen
**File:** `player/.../ui/screen/ConsoleGamesScreen.kt` (new)
- LazyVerticalGrid of games
- Top bar with search field
- Sort dropdown (Title, Rating, Release Date)
- Alphabet bar (horizontal, scrollable)
- Server-side pagination (load more on scroll)
- Uses `GameListViewModel` with consoleId filter

### Step 4: Add server-side filtering to GameListViewModel
**File:** `player/.../viewmodel/GameListViewModel.kt`
- Add `LoadConsoleGames(consoleId, filters)` intent
- Call server API with query params: `?consoleId=...&search=...&sortBy=...&letter=...&page=...`

### Step 5: Genre filter integration
Instead of a standalone genre breakdown section, add genre as a filter chip bar:
- Fetch genres from `ConsoleShowcase.genreBreakdown`
- Display as horizontally scrollable chips above the game grid
- Tapping a genre chip adds `&genres=Action` to the API query

## Files to Create/Modify
| File | Action |
|------|--------|
| `SpNavigation.kt` | Add `ConsoleGames` screen |
| `SpelaApp.kt` | Add routing for ConsoleGames |
| `ConsoleScreen.kt` | Remove game grid, add "Browse All" button |
| `ConsoleGamesScreen.kt` | **New** — filterable game list |
| `GameListViewModel.kt` | Add filtered console games loading |
| `GameListIntent.kt` | Add new intents for filtering |
| `GameListState.kt` | Add filter state fields |

## Not in Scope
- View mode toggle (grid/list) — grid only for now
- Advanced filters (year range, rating range, play status) — Phase 3
- Region/perspective/keyword filters — too niche for mobile
