# Explore Page -- Phase 1 User Stories

## Context

Phase 1 introduces a new "Explore" page alongside the existing Dashboard. The Explore page is a visually rich, art-forward discovery surface that highlights the best games in the library. It is fully algorithmic -- no admin curation required. All data is derived from existing metadata (IGDB ratings, SteamGridDB artwork, play history, library creation dates).

This document defines user stories and acceptance criteria from the user's perspective. It does not prescribe implementation details.

---

## Story 1: Navigate to the Explore page

**As a** logged-in user,
**I want to** find and navigate to an Explore page,
**so that** I can discover games beyond my personal play history.

### Acceptance Criteria

#### Web
- A new "Explore" link appears in the sidebar navigation, between "Dashboard" and "Library".
- Clicking it navigates to `/explore`.
- The link shows an appropriate icon and the label "Explore".
- The Explore link is visually distinct as a top-level navigation item (same prominence as Dashboard).
- The sidebar correctly highlights "Explore" when the user is on the Explore page.

#### Player App
- A new "Explore" tab appears in the bottom navigation bar, between "Home" and "Consoles".
- Tapping it navigates to the Explore screen.
- The tab shows an appropriate icon and the label "Explore".
- The tab is highlighted when the Explore screen is active.
- Controller navigation (L1/R1 tab cycling) includes the new Explore tab.

#### Both
- The existing Dashboard/Home page is unchanged and remains accessible.
- Navigation between Dashboard and Explore is instant (no full page reload).

---

## Story 2: Hero carousel -- featured games with hero art

**As a** user viewing the Explore page,
**I want to** see a visually stunning full-width carousel of featured games,
**so that** I am drawn to high-quality games I might want to play.

### Acceptance Criteria

#### Visual presentation
- The hero carousel is the first element on the Explore page, taking up significant visual space.
- Each slide displays:
  - A full-width hero banner image (the SteamGridDB hero art for the game).
  - The game's logo image overlaid in the bottom-left area (if available; fall back to the game title as text).
  - A console badge showing which platform the game is for.
  - The IGDB rating displayed as a visual indicator.
  - The game's genre(s) displayed as small chips/tags.
  - A primary action button to navigate to the game's detail page.
- The hero image fills the full width of the content area without letterboxing.

#### Carousel behavior
- The carousel auto-rotates to the next slide every 8 seconds.
- On web: auto-rotation pauses when the user hovers over the carousel, and resumes when the mouse leaves.
- On player app: auto-rotation pauses when the user interacts with the carousel (swipe/focus), and resumes after a delay.
- The user can manually navigate between slides:
  - Web: left/right arrow buttons on the edges of the carousel, plus navigation dots at the bottom.
  - Player app: horizontal swipe gesture, plus navigation dots.
- Transitions between slides are smooth crossfades (not abrupt cuts).
- The carousel wraps around: advancing past the last slide returns to the first.

#### Content selection
- The carousel displays between 3 and 8 featured games.
- Featured games are selected automatically: the highest-rated games (by IGDB rating) that have both hero art and are in the user's library.
- If fewer than 3 games have hero art, the carousel still renders with however many are available (even 1).
- Each featured game appears at most once in the carousel.

#### Edge case: No hero art available
- If zero games in the library have hero art, the hero carousel section is not displayed at all.
- The page begins with the first shelf row instead.
- No error message or broken layout is shown.

---

## Story 3: "Top Rated" shelf row

**As a** user viewing the Explore page,
**I want to** see a horizontal row of the highest-rated games across all consoles,
**so that** I can discover critically acclaimed games I might have overlooked.

### Acceptance Criteria

- A section titled "Top Rated" appears below the hero carousel.
- The section displays up to 20 games in a horizontally scrollable row.
- Games are sorted by IGDB rating, highest first, across all consoles in the library.
- Each game card in the row shows:
  - Cover art.
  - Game title.
  - Console badge (so the user knows which platform it belongs to).
  - Rating indicator.
- Tapping/clicking a game card navigates to that game's detail page.

#### Web-specific
- Left and right scroll arrow buttons appear at the edges of the row when there are more games than fit on screen.
- Scrolling is smooth (not jump-by-page).

#### Player app-specific
- The row is a horizontal scrollable list.
- Focus/selection state is clearly visible on the active card.

---

## Story 4: "Recently Added" shelf row

**As a** user viewing the Explore page,
**I want to** see a row of the most recently added games,
**so that** I can quickly find new additions to the library.

### Acceptance Criteria

- A section titled "Recently Added" appears on the Explore page.
- The section displays up to 20 games in a horizontally scrollable row.
- Games are sorted by the date they were added to the library (newest first).
- Each game card shows cover art, title, console badge, and rating.
- Tapping/clicking a game card navigates to that game's detail page.
- The same scroll/navigation affordances as the Top Rated row apply (arrows on web, swipe on player).

---

## Story 5: "Hidden Gems" shelf row

**As a** user viewing the Explore page,
**I want to** see a row of highly rated games that few people on the server have played,
**so that** I can discover underappreciated games.

### Acceptance Criteria

- A section titled "Hidden Gems" appears on the Explore page.
- The section displays up to 20 games in a horizontally scrollable row.
- A game qualifies as a "hidden gem" if it has a high IGDB rating but low total play count across all users on the server.
- Games that no one has played are included (zero play count is valid for a hidden gem).
- Each game card shows cover art, title, console badge, and rating.
- Tapping/clicking a game card navigates to that game's detail page.

#### Edge case: New server with no play history
- If no one has played any games yet, all highly rated games qualify. The row still appears, showing the highest rated games (effectively identical to Top Rated in this degenerate case, which is acceptable).

#### Edge case: Server with very few games
- If the library has fewer than 5 games total, this section is hidden (it adds no value when the library is tiny).

---

## Story 6: "Most Played on Your Server" shelf row

**As a** user viewing the Explore page,
**I want to** see which games are most popular on this server,
**so that** I can see what the community enjoys and find games with social proof.

### Acceptance Criteria

- A section titled "Most Played on Your Server" appears on the Explore page.
- The section displays up to 20 games in a horizontally scrollable row.
- Games are sorted by total play time across all users on the server, highest first.
- Each game card shows cover art, title, console badge, and rating.
- Tapping/clicking a game card navigates to that game's detail page.

#### Edge case: No play history on server
- If no games have any play history, this section is hidden entirely.
- No empty state message is shown; the section simply does not appear.

---

## Story 7: Explore page layout and ordering

**As a** user viewing the Explore page,
**I want** the sections to be arranged in a logical, visually appealing order,
**so that** the page feels curated and intentional.

### Acceptance Criteria

- The page layout from top to bottom is:
  1. Hero carousel (if any games have hero art).
  2. "Top Rated" shelf.
  3. "Recently Added" shelf.
  4. "Hidden Gems" shelf (if applicable).
  5. "Most Played on Your Server" shelf (if any play history exists).
- Sections that have no data are omitted entirely (no empty placeholders, no "No games found" messages within the page flow).
- The page scrolls vertically through all sections.
- On web: the page is responsive and works at all viewport widths (mobile through ultrawide).
- On player app: the page renders correctly in both portrait and landscape orientations.

---

## Story 8: Loading states

**As a** user navigating to the Explore page,
**I want to** see loading indicators while data is being fetched,
**so that** I know the page is working and not broken.

### Acceptance Criteria

- While the hero carousel data is loading, a skeleton placeholder of the appropriate size is shown (full-width, same height as the carousel would be).
- While shelf rows are loading, skeleton card placeholders are shown in a horizontal row matching the expected card size and count.
- Skeleton states animate subtly (shimmer or pulse) to indicate loading is in progress.
- The page does not show a single full-page spinner. Each section loads its own skeleton independently.
- If one section loads faster than another, it renders immediately while others continue showing skeletons.

---

## Story 9: Empty library state

**As a** user whose server has no games in the library,
**I want to** see a helpful message on the Explore page,
**so that** I understand why the page is empty and what to do about it.

### Acceptance Criteria

- If the library contains zero games, the Explore page shows a single centered empty state:
  - A relevant icon or illustration.
  - A title like "Nothing to explore yet".
  - A description explaining that games need to be added to the library first.
  - For admin users: a link or button to the library scan page.
  - For non-admin users: a message suggesting they contact an admin.
- No hero carousel, no shelf rows, no skeleton states -- just the empty state.

---

## Story 10: Game cards show console badge across consoles

**As a** user browsing cross-console shelf rows (Top Rated, Hidden Gems, etc.),
**I want to** see which console each game belongs to,
**so that** I can tell at a glance whether a game is for SNES, GBA, PS1, etc.

### Acceptance Criteria

- Every game card in every Explore shelf row includes a console badge.
- The console badge displays the console abbreviation (e.g., "SNES", "GBA", "PS1") and/or the console icon.
- The badge uses the console's color theme for visual distinction.
- The badge is consistently positioned on all game cards (same location, same size).
- The badge does not obscure the cover art in a way that makes the game unrecognizable.

---

## Story 11: Clicking a featured game navigates to its detail page

**As a** user who sees an interesting game in the hero carousel or a shelf row,
**I want to** click/tap on it and arrive at the game's full detail page,
**so that** I can learn more about it, see screenshots, and start playing.

### Acceptance Criteria

- Clicking/tapping a game card in any shelf row navigates to `/games/{id}` (web) or the GameDetail screen (player app).
- Clicking/tapping the action button in the hero carousel navigates to the game's detail page.
- Navigation is smooth (no full page reload on web; normal screen transition on player app).
- The back button/gesture returns the user to the Explore page, scrolled to the same position they left.

---

## Story 12: Shelf row game cards show favorite and play-later status

**As a** user browsing shelf rows on the Explore page,
**I want to** see which games I have already favorited or added to my Play Later queue,
**so that** I can quickly identify games I have already marked for later.

### Acceptance Criteria

- Game cards in shelf rows reflect the user's favorite status (heart icon or similar indicator, consistent with how favorites are shown elsewhere in the app).
- Game cards in shelf rows reflect the user's Play Later status (clock/bookmark icon or similar indicator, consistent with the rest of the app).
- On web: hovering over a game card reveals quick actions to toggle favorite and Play Later status (consistent with game card behavior on other pages).
- On player app: the favorite/Play Later indicators are visible on the card without requiring interaction.

---

## Story 13: Hero carousel handles missing logo gracefully

**As a** user viewing a featured game in the hero carousel,
**I want** the slide to look good even if the game's logo image is missing,
**so that** the page never looks broken.

### Acceptance Criteria

- If a featured game has hero art but no logo image:
  - The game's title is displayed as styled text in the position where the logo would be.
  - The text is clearly readable against the hero art background (text shadow, gradient overlay, or similar treatment).
- If a featured game has both hero art and a logo image, the logo image is used (no text fallback).
- There is no broken image icon or layout shift in either case.

---

## Story 14: Explore page data is cached and works when external APIs are down

**As a** a user,
**I want** the Explore page to load quickly and work reliably,
**so that** I am never blocked from discovering games.

### Acceptance Criteria

- All data powering the Explore page (ratings, artwork URLs, play counts) comes from the server's local database, not from live external API calls.
- The Explore page loads successfully even if IGDB and SteamGridDB are completely unreachable.
- If artwork images fail to load (CDN down, URL expired), the game card degrades gracefully:
  - A placeholder or the cover art is shown instead of a broken image.
  - The card remains interactive and navigable.
- Response times for the Explore page API calls are fast enough that the user does not perceive a significant delay (comparable to loading the Dashboard).

---

## Non-functional requirements

### Performance
- The Explore page should render its above-the-fold content (hero carousel + first visible shelf row) within 2 seconds of navigation on a typical connection.
- Images below the fold should lazy-load as the user scrolls.

### Accessibility
- All interactive elements (carousel controls, game cards, navigation dots) are keyboard-accessible on web.
- Carousel auto-rotation respects `prefers-reduced-motion`: if the user has this setting enabled, auto-rotation is disabled by default.
- All images have appropriate alt text (game title for cover art, "Hero art for [game title]" for hero banners).
- Navigation dots and arrow buttons have accessible labels.

### Consistency
- Game cards on the Explore page use the same shared game card component used on the Dashboard, Console Detail, and other pages. No one-off card designs.
- The visual language (spacing, typography, colors) matches the rest of the application.
