# Global Search in the Player App -- User Stories

**Status:** Draft -- ready for team discussion
**Author:** Product Owner
**Date:** 2026-03-13

---

## Background

Users of the Spela player app currently have two ways to find content:

1. **Browse** -- navigate through the Home, Explore, and Consoles tabs, which surface curated sections (trending, for you, series, moods, themes, etc.) and allow browsing by console.
2. **Advanced game search** -- available from the Explore tab via the "Advanced Search & Filters" chip, which opens a dedicated screen where users can filter games by console, genre, developer, publisher, year, rating, and play status.

What is missing is the ability to **quickly find something specific by name** across the entire library. A user who thinks "I want to play Castlevania" has no fast path to type a name and jump directly to it. They must either scroll through browse sections hoping to spot it, or open advanced search and use filters to narrow down results. The web frontend already has this via a Cmd+K command palette that searches across games, consoles, developers, publishers, collections, series, and franchises simultaneously.

The player app needs an equivalent feature, but the design must account for the three very different input contexts the app runs on:

- **Touch devices** (phones, tablets): on-screen keyboard, tap-driven
- **Gaming handhelds** (AYN Thor, Retroid, etc.): gamepad-only, D-pad navigation, no physical keyboard in some form factors
- **Desktop** (macOS, Linux, Windows): full keyboard and mouse

---

## Design Decisions

### Where does search live?

Search should be a **dedicated full-screen experience** (a new screen, not an overlay/modal), accessible from a persistent entry point. The reasons:

1. The player app has no toolbar/app bar that persists across all tabs (unlike the web sidebar). A floating overlay would feel alien to the native app patterns.
2. Full-screen works equally well on phones, handhelds, and desktop.
3. It allows room for the text input, the results list, recent searches, and potential future enhancements (voice search, filter refinements).

### Entry points

- **Explore tab**: Replace the current "Advanced Search & Filters" chip with a prominent search bar at the top. Tapping it navigates to the global search screen. The advanced filters remain available within the search screen as a secondary action.
- **Home tab**: Add a search icon button in the header row (next to the existing download indicator). This gives a one-tap shortcut from the most-visited screen.
- **Keyboard shortcut (desktop)**: Cmd+K / Ctrl+K opens the search screen, matching the web convention.
- **Gamepad**: No dedicated trigger button (shoulder buttons are already used for tab switching). Users navigate to the search entry point via D-pad and press A.

### Gamepad input for text

On gaming handhelds with no physical keyboard, Android provides a system soft keyboard when a text field gains focus -- even on gamepad-only devices. This is the same mechanism every other Android app uses (streaming apps, emulators with search, etc.). The search screen's text field will be auto-focused on entry, so the system keyboard appears immediately. This is usable but slow for long queries -- which is fine, because game searches are typically short (2-8 characters to narrow down). We do NOT need to build a custom virtual keyboard or voice search for MVP.

### Relationship to advanced game search

Global search and advanced game search serve different purposes:

- **Global search**: "I know what I am looking for, let me type a name" -- fast, cross-category.
- **Advanced search**: "I want to discover games matching certain criteria" -- filter-driven, game-only.

They should coexist. The global search screen should include an entry point to the existing advanced search screen (e.g., a "Filters" chip or button).

---

## MVP Stories (Phase 1)

These stories deliver a working global search that covers the core use case on all platforms.

### Story 1: Global Search Screen

**As a** player app user,
**I want to** type a search query and see results from across my entire library,
**so that** I can quickly find a specific game, console, developer, series, or other entity by name without browsing.

**Acceptance Criteria:**

- A new "Global Search" screen exists in the app navigation.
- The screen shows a text input field at the top, auto-focused when the screen opens.
- As the user types (debounced 250ms), results are fetched from `GET /api/search?q=...&limit=5`.
- Results are grouped by category with section headers: Games, Consoles, Developers, Publishers, Collections, Series, Franchises.
- Only categories with results are shown.
- Each category shows a count of total matches when there are more results than displayed.
- Game results show: cover art thumbnail, title, console badge, developer name.
- Console results show: console icon, name, game count.
- Developer/Publisher results show: name, game count, average rating.
- Collection results show: name, owner username, game count.
- Series/Franchise results show: name, "X of Y in library" count.
- A minimum of 2 characters is required before a search is issued. Below that, a hint message is shown ("Type at least 2 characters").
- While results are loading, a loading indicator is shown.
- When no results match, a "No results" empty state is shown.
- When the text field is empty, the screen shows a helpful prompt (e.g., "Search games, consoles, developers...").
- Tapping/clicking a game result navigates to that game's detail screen.
- Tapping/clicking a console result navigates to that console's screen.
- Tapping/clicking a developer or publisher result navigates to the corresponding Explore developer/publisher screen.
- Tapping/clicking a collection result navigates to the collection detail screen.
- Tapping/clicking a series result navigates to the Explore series screen.
- Standard back navigation (back button, swipe, Escape key, gamepad B) returns to the previous screen.

### Story 2: Search Entry Point from Explore Tab

**As a** player app user on the Explore tab,
**I want** a visible search bar at the top of the screen,
**so that** I can start a search with one tap instead of hunting for a search button.

**Acceptance Criteria:**

- The Explore screen shows a tappable search bar (styled like a text input but not editable in place) near the top, above the current hero carousel.
- The search bar displays placeholder text: "Search games, consoles, developers..."
- Tapping the search bar navigates to the Global Search screen (Story 1).
- The "Advanced Search & Filters" chip is removed from the Explore screen. (Advanced filters are accessible from within the global search screen instead -- see Story 5.)
- Gamepad users can focus the search bar with D-pad and activate it with the A button.

### Story 3: Search Entry Point from Home Screen

**As a** player app user on the Home screen,
**I want** a search icon in the header,
**so that** I can start searching from the screen I see most often.

**Acceptance Criteria:**

- A search icon button appears in the Home screen header row, next to the existing downloads indicator.
- Tapping/clicking it navigates to the Global Search screen.
- The icon uses the standard search (magnifying glass) icon from the design system.
- Gamepad users can focus and activate this icon via D-pad navigation.

### Story 4: Keyboard Shortcut for Search (Desktop)

**As a** desktop player app user,
**I want to** press Cmd+K (macOS) or Ctrl+K (Windows/Linux) to open search,
**so that** I can start searching without using the mouse, matching the convention from the web app.

**Acceptance Criteria:**

- On desktop, pressing Cmd+K (macOS) or Ctrl+K (other platforms) navigates to the Global Search screen from any screen in the app, except when the in-game overlay is active.
- If the user is already on the Global Search screen, the shortcut focuses the text input.
- This shortcut does not conflict with any existing keyboard mappings.

### Story 5: Access Advanced Filters from Global Search

**As a** player app user who wants to filter games by console, genre, year, or other criteria,
**I want** a way to access the existing advanced game search from the global search screen,
**so that** I do not lose the powerful filter functionality when global search replaces the direct chip on the Explore screen.

**Acceptance Criteria:**

- The Global Search screen includes a clearly visible action (button or chip) to open the existing Advanced Search screen.
- This action is always visible, whether or not a search query has been entered.
- The advanced search screen continues to function exactly as it does today.

---

## Phase 2 Stories (Post-MVP)

These stories enhance the search experience after the core functionality ships.

### Story 6: Recent Searches

**As a** player app user,
**I want to** see my recent search queries when I open the search screen,
**so that** I can quickly repeat a previous search.

**Acceptance Criteria:**

- When the search screen text field is empty, a "Recent searches" section appears showing up to 5 recent queries.
- Tapping a recent search query fills it into the text field and triggers the search.
- Each recent search has a remove button (X) to delete it individually.
- A "Clear all" action clears the entire recent search history.
- Recent searches are stored locally on the device (not synced to the server).
- Recent searches persist across app restarts.

### Story 7: Franchise Navigation from Search Results

**As a** player app user who taps a franchise result in search,
**I want to** see the games in that franchise,
**so that** I can explore the franchise's library without having to search again.

**Acceptance Criteria:**

- Tapping a franchise result in global search navigates to a franchise detail screen (or an appropriate existing screen that lists the franchise's games).
- If no franchise detail screen exists yet, this story includes creating one, following the same pattern as the existing Explore Series screen.

### Story 8: Search Result Counts and "See More"

**As a** player app user who searches for a broad term (e.g., "Mario"),
**I want to** know how many total results exist in each category and be able to see more,
**so that** I can explore beyond the initial 5 results per category.

**Acceptance Criteria:**

- When a category has more results than the initial limit (5), a "See all X results" link appears at the bottom of that category section.
- Tapping "See all" for Games navigates to a full results screen showing all matching games in a scrollable grid.
- Tapping "See all" for other categories navigates to an appropriate screen showing all results for that category.
- The full results screen supports pagination or infinite scroll for large result sets.

---

## Phase 3 Stories (Future)

### Story 9: Search from Anywhere via Gamepad Shortcut

**As a** player using a gamepad (gaming handheld or controller on desktop/Android TV),
**I want** a dedicated button combination to open search quickly,
**so that** I do not have to navigate to the search bar with D-pad every time.

**Acceptance Criteria:**

- A gamepad button combination (e.g., Select + D-pad Up, or a configurable button) opens the Global Search screen from any browsing screen.
- The shortcut is discoverable (shown in a help/tips section or on first use).
- The shortcut does not conflict with in-game controls (only active when not in the emulation overlay).

*Note: This story is lower priority because the D-pad navigation to the search bar entry point (Stories 2 and 3) already works. This is a convenience optimization for power users.*

### Story 10: Search Suggestions / Autocomplete

**As a** player app user who starts typing,
**I want** quick suggestions as I type (before pressing search),
**so that** I can find what I want with fewer keystrokes.

**Acceptance Criteria:**

- After typing 2+ characters, suggestions appear inline below the text field based on popular/matching results.
- Suggestions update in real-time as the user types.
- Selecting a suggestion fills the text field and navigates directly to the result.

---

## Out of Scope

The following are explicitly NOT part of this feature:

- **Voice search**: Not a priority given the complexity and the limited benefit over short text queries.
- **Custom virtual keyboard for gamepad**: The Android system keyboard works adequately for short search queries. Building a custom D-pad-navigable keyboard is high effort for low incremental value.
- **Replacing the existing advanced game search**: The filter-based search serves a different purpose (discovery vs. lookup) and will continue to exist as a separate screen.
- **Searching downloaded games only / offline search**: Global search requires an API call to the server. Offline scenarios are handled by the existing browse UI which uses cached data.

---

## Technical Notes for Discussion

*The product owner does not prescribe implementation, but wants to highlight considerations for the dev team:*

- The backend API already exists and returns all 7 categories. No backend work is needed.
- The web frontend implementation (`web/src/features/search/`, `web/src/hooks/use-search.ts`) can serve as a reference for the API contract and debounce strategy.
- The player app's existing `ExploreSearchScreen` and `ExploreViewModel` handle game-only search with filters. The global search is a different concern and likely warrants its own ViewModel and repository.
- Navigation to franchise detail screens may require a new `SpScreen` entry if one does not exist yet.
- The Cmd+K shortcut on desktop needs to be handled at the `GamepadHandler` / key event level, likely in `SpelaApp.kt`.

---

## Priority and Sequencing

1. **Phase 1 (MVP)**: Stories 1-5. This delivers a complete, usable global search on all platforms.
2. **Phase 2**: Stories 6-8. Polish and depth -- recent searches and expanded results.
3. **Phase 3**: Stories 9-10. Convenience features for power users.

The recommendation is to implement Phase 1 as a single feature branch, with all 5 stories delivered together since they form one cohesive feature.
