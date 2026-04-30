# Explore Page -- Phase 4 User Stories: Franchise & Series Pages

## Context

Phase 4 introduces franchise and series browsing -- letting users explore cross-console game collections like "The Complete Zelda" or "Every Final Fantasy". This builds on the data layer established in Phase 2 (IGDB enrichment) and the browsing patterns established in Phase 3 (theme and keyword pages).

The backend already has `GET /api/series`, `GET /api/series/:id`, `GET /api/franchises`, and `GET /api/franchises/:id/games` endpoints from Phase 2. The `GameSeries` model tracks IGDB collections with `GameSeriesEntry` rows that include both library and non-library games (with nullable `GameID`), plus `Name` and `CoverImageID` fields per entry. The `GameFranchise` model links games to franchise names.

This phase adds a featured series shelf to the Explore page, builds out series detail pages with visual timelines, and cross-links series information into the game detail page -- across both web and player app.

---

## Story 1: Featured series shelf on the Explore page

**As a** user browsing the Explore page,
**I want to** see a shelf of franchise/series collections (e.g., "Mario -- 12 games across 5 consoles"),
**so that** I can discover and explore complete game series that span multiple consoles.

### Acceptance Criteria

#### Content and presentation
- A new section titled "Franchise Collections" (or similar) appears on the Explore page, below the existing shelves.
- The section displays a horizontally scrollable row of series cards.
- Each card shows:
  - The series name (e.g., "Super Mario", "The Legend of Zelda", "Final Fantasy").
  - The number of games available in the library (e.g., "12 games").
  - The number of consoles represented (e.g., "across 5 consoles").
  - Representative artwork -- the best available hero art or cover art from a game in the series.
- Cards are sorted by the number of library games (series with the most local games first).
- Tapping/clicking a card navigates to the series detail page.

#### Filtering
- Only series that have at least 2 games in the local library are shown (a single-game "series" is not interesting to browse).
- If no series meet this threshold, the entire section is hidden -- no empty state within the Explore page flow.

#### Web-specific
- Left and right scroll arrows appear at the edges of the row when there are more cards than fit on screen.
- Scrolling is smooth.

#### Player app-specific
- The row is a horizontal scrollable list (LazyRow).
- Focus/selection state is clearly visible on the active card.

---

## Story 2: Series detail page with visual timeline

**As a** user who taps on a series card (e.g., "The Legend of Zelda"),
**I want to** see every game in that series laid out as a visual timeline ordered by release date,
**so that** I can see the complete history of the franchise and which games I own.

### Acceptance Criteria

#### Page structure

##### Web
- The series detail page lives at `/explore/series/:id`.
- The page has a hero banner at the top -- the best available hero art from a game in the series.
- Below the hero banner: the series name as a large heading, and an ownership summary (see Story 3).

##### Player app
- The series detail screen is navigable from the Explore screen and from game detail (see Story 5).
- The screen has a hero banner at the top with the same treatment as the web version.

#### Timeline view
- Games in the series are displayed in chronological order by release date.
- Each game entry shows:
  - Cover art (if available; placeholder if not).
  - Game title.
  - Console badge -- indicating which platform the game is for.
  - Release year.
  - Rating (IGDB rating, if available).
- Games that are in the user's library are displayed at full visual intensity.
- Games that are NOT in the library are visually dimmed/de-emphasized -- using the same shared "not in library" visual treatment used elsewhere in the app (consistent with Decision 5 in the Explore plan).
- The distinction between "in library" and "not in library" is immediately clear without reading labels. A user should be able to glance at the page and understand which games they own.

#### Interactions
- Tapping/clicking a game that IS in the library navigates to that game's detail page.
- Tapping/clicking a game that is NOT in the library does nothing (or shows a subtle indication that the game is not available). It does not navigate to a broken/empty page.

#### Edge cases
- If release dates are unavailable for some games, those games appear at the end of the timeline (after all dated games), sorted alphabetically.
- If only one game in the series is in the library, the page still renders normally. The timeline shows where that game fits among all the entries in the series.
- If the series has no games in the library at all, the page is still accessible (via direct URL) but all games are dimmed. This is an unlikely scenario since the shelf only shows series with library games.

---

## Story 3: Series ownership progress indicator

**As a** user viewing a series detail page,
**I want to** see how many games in the series I own (e.g., "You own 8 of 15 games"),
**so that** I can understand my collection completeness at a glance.

### Acceptance Criteria

- The series detail page displays a prominent ownership summary near the top, below the series name.
- The summary includes:
  - A textual count: "You own X of Y games" (where X is the number of series games in the library and Y is the total number of games in the series).
  - A visual progress indicator (progress bar, ring, or similar) showing the ratio of owned to total.
- The progress indicator uses color to convey completeness:
  - Full completion (X equals Y) has a distinct celebratory treatment (e.g., green, checkmark, "Complete collection!" label).
  - Partial completion shows proportional fill.
- The indicator updates correctly if the library changes (e.g., a new game is added via scrape).

---

## Story 4: Console grouping on the series detail page

**As a** user viewing a series that spans multiple consoles (e.g., "Final Fantasy" across NES, SNES, PS1, GBA),
**I want to** see which consoles are represented and optionally filter by console,
**so that** I can understand the breadth of the series and focus on the platforms I care about.

### Acceptance Criteria

- The series detail page displays the list of consoles represented in the series (e.g., console badges or icons near the top of the page).
- Each console badge shows the count of games for that console within the series.
- The user can filter the timeline by console:
  - Tapping/clicking a console badge filters the timeline to show only games for that console.
  - Tapping the active filter again (or a "Show all" option) removes the filter and shows all games.
  - The ownership progress indicator updates to reflect the filtered view (e.g., "You own 3 of 5 SNES games").
- If the series only spans a single console, the console filter is still visible but filtering is not necessary (only one option). The badges are informational.

---

## Story 5: "Part of [Series]" link on the game detail page

**As a** user viewing a game's detail page,
**I want to** see that the game belongs to a series (e.g., "Part of The Legend of Zelda series"),
**so that** I can navigate to the series page and explore related games.

### Acceptance Criteria

#### Web
- On the game detail page, if the game belongs to one or more series, a "Part of [Series Name]" link is displayed.
- The link is positioned in a contextually appropriate location (e.g., near the game's metadata: genre, developer, release date).
- Clicking the link navigates to `/explore/series/:id`.
- If the game belongs to multiple series (rare but possible), each series is listed as a separate link.

#### Player app
- The game detail screen shows the same "Part of [Series Name]" link.
- Tapping the link navigates to the series detail screen.

#### Edge cases
- If the game does not belong to any series, no link is shown. There is no "Not part of a series" message or empty placeholder.
- The link works correctly for games that are part of a series regardless of how many other games in that series are in the library.

---

## Story 6: "Part of [Franchise]" link on the game detail page

**As a** user viewing a game's detail page,
**I want to** see that the game belongs to a franchise (e.g., "Part of the Mario franchise"),
**so that** I understand the game's place in a broader franchise.

### Acceptance Criteria

#### Web
- On the game detail page, if the game has franchise associations, a "Part of [Franchise Name]" link is displayed alongside or near the series link (Story 5).
- Clicking the link navigates to a franchise detail view showing all games in that franchise that are in the library.

#### Player app
- The game detail screen shows the same "Part of [Franchise Name]" link.
- Tapping the link navigates to a franchise detail screen.

#### Distinction between series and franchise
- Series and franchise are different concepts. A game may belong to a series (e.g., "The Legend of Zelda" collection) AND a franchise (e.g., "Zelda" franchise) simultaneously. Both links are shown when applicable.
- A game may have a franchise but no series, or vice versa. Each link appears independently based on available data.

#### Edge cases
- If the game has multiple franchise associations, each is listed as a separate link.
- If the game has no franchise associations, no franchise link is shown.

---

## Story 7: Series detail page loading and error states

**As a** user navigating to a series detail page,
**I want to** see appropriate loading states while data loads and helpful messages if something goes wrong,
**so that** the page feels responsive and I am never confused by a blank screen.

### Acceptance Criteria

#### Loading states
- While the series data is loading, the page shows skeleton placeholders:
  - A skeleton for the hero banner area.
  - Skeleton cards for the timeline entries.
  - A skeleton for the ownership progress indicator.
- Skeletons animate subtly (shimmer or pulse) to indicate loading is in progress.
- The page does not show a full-page spinner.

#### Error states
- If the series is not found (invalid ID, deleted, etc.), the page shows a clear "Series not found" message with a link back to the Explore page.
- If the API request fails due to a network error, the page shows an error message with a "Retry" button.

---

## Story 8: Navigate back from series detail to Explore page

**As a** user who navigated from the Explore page to a series detail page,
**I want to** return to the Explore page without losing my scroll position,
**so that** I can continue browsing where I left off.

### Acceptance Criteria

#### Web
- The browser back button returns the user to the Explore page at the same scroll position.
- A breadcrumb or back link at the top of the series detail page provides an explicit navigation path back to Explore.

#### Player app
- The system back gesture/button returns the user to the Explore screen at the same scroll position.
- The screen uses the standard app navigation pattern (back arrow in top bar).

---

## Story 9: Series detail page shows hero art from the best game

**As a** user viewing a series detail page,
**I want** the hero banner to feature high-quality artwork from the best game in the series,
**so that** the page looks visually appealing and sets the tone for the franchise.

### Acceptance Criteria

- The hero banner uses SteamGridDB hero art from the highest-rated game in the series that has hero art available AND is in the local library.
- If no game in the series has hero art, the hero banner area uses a fallback treatment:
  - A gradient or styled background with the series name prominently displayed.
  - No broken image or empty space.
- If the best available hero art game is not in the library but another library game has hero art, the library game's art is preferred (showing art from a game the user actually has is more relevant).
- The hero art image scales to fill the banner area without distortion (object-fit: cover or equivalent).

---

## Story 10: Explore page layout with the new franchise shelf

**As a** user viewing the Explore page,
**I want** the new Franchise Collections shelf to be integrated into the page in a logical position,
**so that** the page layout feels cohesive and intentional.

### Acceptance Criteria

- The Explore page layout from top to bottom is:
  1. Hero carousel (if any games have hero art).
  2. "Top Rated" shelf.
  3. "Recently Added" shelf.
  4. "Hidden Gems" shelf (if applicable).
  5. "Most Played on Your Server" shelf (if applicable).
  6. Theme grid section (from Phase 3).
  7. Keyword chips section (from Phase 3).
  8. **"Franchise Collections" shelf (new in Phase 4).**
- The franchise shelf appears after the theme/keyword sections because it represents a different mode of discovery (collection-based rather than category-based).
- If the franchise shelf has no data (no series with 2+ library games), it is omitted entirely. No empty placeholder.
- The overall vertical scrolling behavior of the page is unchanged. The new section integrates naturally.

---

## Non-functional requirements

### Visual consistency
- Series cards in the shelf use the same shared card component patterns as other Explore page cards (consistent sizing, typography, hover/focus states).
- The "not in library" dimmed treatment on the timeline page uses the same shared component approach described in the Explore plan's Decision 5 -- not a one-off style.
- Console badges on the timeline view are the same component used on game cards throughout the app.

### Performance
- The series detail page should render above-the-fold content (hero banner, series name, progress indicator, first few timeline entries) within 2 seconds on a typical connection.
- Cover art images for timeline entries below the fold should lazy-load as the user scrolls.
- The featured series shelf on the Explore page adds at most one additional API call. It should not noticeably slow down the Explore page load.

### Accessibility
- All interactive elements on the series detail page (game cards, console filter badges, back link) are keyboard-accessible on web.
- The ownership progress indicator has appropriate ARIA attributes (e.g., `aria-valuenow`, `aria-valuemax`) so that screen readers can convey the progress.
- Dimmed "not in library" games are not hidden from screen readers -- they should be announced with their status (e.g., "The Legend of Zelda: Ocarina of Time -- not in library").
- The hero banner has appropriate alt text ("Hero art for [series name]").

### Data correctness
- Ownership counts (X of Y) are always accurate and reflect the current state of the library. If a game is added or removed via scrape, the count updates on the next page load.
- The timeline ordering uses release dates from IGDB data. If IGDB provided no release date for a game, it sorts to the end rather than appearing at an arbitrary position.
