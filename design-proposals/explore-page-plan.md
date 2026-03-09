# Explore Page — Implementation Plan

## Vision

Transform Spela from a "browse by console" library into a Netflix/Spotify-style discovery
experience that scales to tens of thousands of games. Beautiful, personalized, and social.

## Principles

- **Each phase ships a working feature** — no half-built scaffolding
- **One PR per phase** — reviewable, testable, revertable
- **Art-first design** — leverage SteamGridDB hero banners, logos, and IGDB artworks everywhere
- **Progressive enrichment** — early phases use existing data, later phases fetch new IGDB data
- **Both web and player app** — every phase covers both platforms

---

## Phase 1: Foundation — The Explore Page Shell + Hero Carousel

**Goal:** New "Explore" route/tab with a stunning hero carousel and basic curated rows.

### Backend
- [ ] New endpoint: `GET /api/explore/featured` — returns 5-8 featured games with hero art
  - Initially: highest-rated games that have SteamGridDB hero art + logo
  - Later: admin-curated featured items
- [ ] New endpoint: `GET /api/explore/rows` — returns multiple curated rows in one call:
  - "Top Rated" (cross-console, top 20 by IGDB rating)
  - "Recently Added" (newest games in library by creation date)
  - "Hidden Gems" (high IGDB rating + low play count across all server users)
  - "Most Played on Your Server" (highest total play time across users)
- [ ] Each row returns games with cover art, hero art, logo, console badge, rating

### Web Frontend
- [ ] New route: `/explore` — add to main navigation
- [ ] Hero carousel component:
  - Full-width SteamGridDB hero banner (1920x620)
  - Game logo overlaid (bottom-left)
  - Console badge, rating, genre chips
  - "Play" / "Add to Queue" action buttons
  - Auto-rotate every 8 seconds, pause on hover
  - Smooth crossfade transitions
  - Navigation dots + arrow buttons
- [ ] Horizontal scrollable shelf component (reusable for all rows):
  - Game cards with cover art, title, console badge, rating
  - Hover: reveal hero art background, quick actions (favorite, queue)
  - Scroll arrows on edges, smooth scroll
- [ ] Page layout: Hero → shelf rows stacked vertically
- [ ] Responsive: works at all viewport widths

### Player App
- [ ] New "Explore" tab in bottom navigation (or replace/augment existing home)
- [ ] Hero carousel composable with SteamGridDB hero art + logo overlay
- [ ] Horizontal LazyRow shelves with game cards
- [ ] Smooth transitions and loading skeletons

### Tests
- [ ] Backend: unit tests for explore endpoints, hidden gems algorithm, row assembly
- [ ] Web: E2E test — navigate to Explore, verify carousel renders, rows display games
- [ ] Player: desktop E2E test — Explore tab renders, carousel shows, shelves scroll

---

## Phase 2: IGDB Enrichment — Themes, Keywords, Franchises

**Goal:** Fetch and store richer IGDB metadata to power discovery features.

### Backend
- [ ] Extend IGDB client with new methods:
  - `GetGameFull(igdbID) → themes, keywords, franchises, collections, player_perspectives, artworks`
  - `GetCollection(collectionID) → name, games[]`
  - `GetFranchise(franchiseID) → name, games[]`
- [ ] New DB models:
  - `GameTheme` (game_id, igdb_theme_id, name) — many-to-many
  - `GameKeyword` (game_id, igdb_keyword_id, name) — many-to-many
  - `GamePlayerPerspective` (game_id, perspective_name) — e.g., "Top-down", "First-person"
  - `GameFranchise` (game_id, igdb_franchise_id, franchise_name)
  - `GameCollection` rename consideration — IGDB "collections" = game series (e.g., "Super Mario"),
    vs. our existing `GameCollection` = user-created collections. Use `GameSeries` for IGDB data.
  - `GameSeries` (igdb_collection_id, name) + `GameSeriesEntry` (series_id, game_id, igdb_game_id)
  - `GameArtworkImage` (game_id, igdb_image_id, url, width, height) — IGDB promotional art
- [ ] Extend scraper to fetch themes, keywords, perspectives, franchise, collection, artworks
  during game scraping (single enriched IGDB query per game)
- [ ] Backfill endpoint: `POST /api/admin/enrich-metadata` — re-scrape all games for new fields
- [ ] New API endpoints:
  - `GET /api/themes` — list all themes with game counts
  - `GET /api/themes/:id/games` — games for a theme
  - `GET /api/keywords` — list popular keywords with game counts
  - `GET /api/keywords/:id/games` — games for a keyword
  - `GET /api/series` — list all game series in library
  - `GET /api/series/:id` — series detail with all games (cross-console)
  - `GET /api/franchises` — list all franchises
  - `GET /api/franchises/:id` — franchise detail with all games
- [ ] Extend `GET /api/games` filters: `theme`, `keyword`, `perspective` params

### Web + Player
- [ ] No new UI yet — this phase is data enrichment only
- [ ] Verify new data appears correctly in API responses

### Tests
- [ ] Backend: unit tests for new IGDB client methods, enrichment scraping, new endpoints
- [ ] Backend: test backfill endpoint with mock IGDB responses

---

## Phase 3: Theme & Keyword Browsing

**Goal:** Browse games by theme (Sci-Fi, Horror, Fantasy) and keyword (time travel, zombies).

### Backend
- [ ] `GET /api/explore/themes` — top themes with representative hero art (pick highest-rated game
  per theme that has hero art)
- [ ] `GET /api/explore/keywords/popular` — popular keywords ranked by game count

### Web Frontend
- [ ] Theme grid section on Explore page:
  - Visual cards: theme name over hero art from the best game in that theme
  - E.g., "Sci-Fi" card shows a hero banner from a top sci-fi game with the theme name overlaid
  - Tap → theme detail page with all games, filterable and sortable
- [ ] Keyword tag cloud or keyword chips section:
  - Tappable keyword chips: "Time Travel", "Post-Apocalyptic", "Steampunk"
  - Tap → filtered game list
- [ ] Theme detail page: `/explore/themes/:id`
  - Hero banner from top game in theme
  - Grid of all games in theme with cover art
  - Sort: rating, release date, title

### Player App
- [ ] Theme grid section on Explore screen
- [ ] Keyword chips row (horizontal scroll)
- [ ] Theme detail screen with game grid

### Tests
- [ ] Web: E2E test — browse themes, tap theme, see correct games
- [ ] Player: desktop E2E test — theme grid renders, navigation works

---

## Phase 4: Franchise & Series Pages

**Goal:** Cross-console franchise browsing — "The Complete Zelda", "Every Final Fantasy".

### Backend
- [ ] `GET /api/series/:id` already exists from Phase 2
- [ ] Extend to return: timeline data (release dates sorted), console grouping, games in library
  vs. not in library, hero art for header
- [ ] `GET /api/explore/series/featured` — series with most games in library, for Explore page shelf

### Web Frontend
- [ ] "Franchise Collections" shelf on Explore page:
  - Horizontal row of franchise cards (e.g., "Mario — 12 games across 5 consoles")
  - Card shows montage or best hero art
- [ ] Franchise detail page: `/explore/series/:id`
  - Hero banner (best game's SteamGridDB art)
  - Visual timeline: games ordered by release date
  - Each game shows: cover art, title, console badge, release year, rating
  - Games in your library: full color. Not in library: dimmed/greyed.
  - "You own 8 of 15 games" progress indicator
- [ ] Link from game detail page: "Part of the [Franchise] series" → links to franchise page

### Player App
- [ ] Franchise shelf on Explore screen
- [ ] Franchise detail screen with timeline view
- [ ] "Part of [Series]" link on game detail screen

### Tests
- [ ] Web: E2E test — franchise shelf visible, tap into franchise, timeline renders correctly
- [ ] Player: desktop E2E test — franchise screen renders, games displayed

---

## Phase 5: Mood Picker & Contextual Entry Points

**Goal:** "What are you in the mood for?" quick-start cards.

### Backend
- [ ] `GET /api/explore/mood/:mood` — returns game list for each mood
- [ ] Mood definitions (server-side mapping):
  - "chill" → themes: fantasy, comedy; genres: puzzle, simulation; keywords: relaxing
  - "challenge" → keywords: difficult, hardcore; low achievement completion rates
  - "nostalgia" → user's most-played + era-matched games
  - "something-new" → unplayed games in library, sorted by rating
  - "quick" → games with avg session < 15 min (from play history)
  - "together" → game_modes: multiplayer, co-op, split-screen
  - "surprise" → random high-rated game

### Web Frontend
- [ ] Mood picker section on Explore page (below hero carousel):
  - Large, visually distinct cards with icons/illustrations
  - Each card has evocative background (subtle hero art collage)
  - Tap → dedicated results page or filtered game grid
- [ ] "Surprise Me" button with slot-machine animation:
  - Spins through cover art, lands on one
  - "Play Now" + "Try Another" buttons

### Player App
- [ ] Mood picker cards (horizontal scroll or 2-column grid)
- [ ] Surprise Me with animation
- [ ] Quick session: "15 minutes" → launch directly into a short-session game

### Tests
- [ ] Backend: unit test mood-to-filter mapping, each mood returns appropriate games
- [ ] Web: E2E test — mood cards visible, tap each mood, results make sense
- [ ] Player: desktop E2E test — mood picker renders, surprise me works

---

## Phase 6: Personalized Recommendations — "For You"

**Goal:** Personalized rows based on play history, favorites, and ratings.

### Backend
- [ ] `GET /api/explore/for-you` — returns personalized rows:
  - "Because you played [Game]" — IGDB similar games (already have this data)
  - "More [Genre] for you" — top-rated unplayed games in user's most-played genre
  - "Continue your [Developer] marathon" — if 3+ games from same developer played
  - "Your unfinished business" — played < 30 min, abandoned > 7 days ago
  - "Expand your horizons" — best games in genres user has never played
- [ ] `GET /api/user/taste-profile` — genre/theme breakdown of user's play history
  - Returns: `{ genres: [{ name: "RPG", percentage: 45, hoursPlayed: 120 }, ...], themes: [...] }`
- [ ] Collaborative filtering: `GET /api/explore/players-like-you`
  - Find users with most overlapping favorites, recommend their non-overlapping favorites
  - Simple approach: Jaccard similarity on favorites sets

### Web Frontend
- [ ] "For You" section on Explore page with personalized rows
- [ ] Each row labeled contextually: "Because you played Chrono Trigger" with that game's
  cover art as the row icon
- [ ] Taste profile visualization (optional — could be a separate "Profile" section):
  - Pie/donut chart or bar chart of genre breakdown
  - Tap genre segment → browse that genre
- [ ] "Expand Your Horizons" callout: "You've never tried a racing game. Here are the best ones."

### Player App
- [ ] "For You" section on Explore screen with same personalized rows
- [ ] Taste profile card on profile/stats screen
- [ ] "Expand Your Horizons" nudge

### Tests
- [ ] Backend: unit tests for recommendation algorithms, taste profile calculation
- [ ] Web: E2E test — for-you rows render, contextual labels correct
- [ ] Player: desktop E2E test — personalized rows render with fake play history

---

## Phase 7: Developer & Publisher Spotlight Pages

**Goal:** Tap any developer/publisher name → see their full catalog across all consoles.

### Backend
- [ ] `GET /api/developers` — list developers with game count, avg rating, consoles
- [ ] `GET /api/developers/:name/games` — all games by developer, cross-console
- [ ] `GET /api/publishers/:name/games` — all games by publisher
- [ ] `GET /api/explore/developers/spotlight` — featured developer (rotating weekly or admin-picked)
- [ ] Extend IGDB: optionally fetch company logos from `/companies` endpoint

### Web Frontend
- [ ] Developer detail page: `/explore/developers/:name`
  - Header with developer name (+ logo if available)
  - Stats: total games, avg rating, active years, consoles
  - Timeline of releases
  - Games grid sorted by rating
  - "Best of [Developer]" shelf
- [ ] Publisher detail page: same structure
- [ ] "Developer Spotlight" section on Explore page:
  - Featured studio with hero treatment
  - "Studios That Defined [Console]" shelf per console showcase
- [ ] Make all developer/publisher names clickable throughout the app

### Player App
- [ ] Developer detail screen
- [ ] Developer spotlight section on Explore
- [ ] Clickable developer/publisher names on game detail

### Tests
- [ ] Backend: unit tests for developer aggregation, spotlight selection
- [ ] Web: E2E test — developer page renders, games listed correctly
- [ ] Player: desktop E2E test — developer screen renders

---

## Phase 8: Console Showcase Pages

**Goal:** Premium landing page per console with its color theme and rich content.

### Backend
- [ ] `GET /api/consoles/:id/showcase` — aggregated console data:
  - Top-rated games (existing)
  - Hidden gems
  - Genre breakdown (game counts per genre for this console)
  - Top developers for this console
  - Recently played on this console (personal)
  - Era info (launch year, generation)

### Web Frontend
- [ ] Console showcase page: `/explore/consoles/:id`
  - Console color theme applied to page (existing color data)
  - Hero carousel of top games for this console
  - "Essentials" shelf — the must-plays
  - "Hidden Gems" shelf
  - Genre breakdown with visual cards
  - "Studios That Defined This Console" section
  - "Recently Played" personal shelf
- [ ] Console quick-jump row on Explore page:
  - Horizontal row of console icons/logos
  - Tap → console showcase page

### Player App
- [ ] Console showcase screen (or enhanced console detail screen)
- [ ] Console quick-jump on Explore screen

### Tests
- [ ] Web: E2E test — console showcase renders, shelves populated
- [ ] Player: desktop E2E test — console showcase renders

---

## Phase 9: Visual Browsing — Gallery & Art Modes

**Goal:** Discover games by how they look — screenshot gallery, box art wall, artwork showcase.

### Backend
- [ ] `GET /api/explore/screenshots` — paginated stream of screenshots with game metadata
  - Filters: console, genre, theme
- [ ] `GET /api/explore/artwork` — IGDB promotional artwork (from Phase 2 enrichment)
- [ ] `GET /api/explore/covers` — dense cover art feed with minimal metadata

### Web Frontend
- [ ] Screenshot Gallery page: `/explore/gallery`
  - Pinterest-style masonry grid of screenshots
  - No titles visible initially — pure visual
  - Hover: game title + console badge overlay
  - Click: navigate to game detail
  - Infinite scroll
  - Filter bar: console, genre, theme
- [ ] Box Art Wall: `/explore/covers`
  - Dense grid of cover art thumbnails
  - Zoom controls (small/medium/large)
  - Click → game detail
- [ ] Artwork Showcase section on Explore page:
  - Curated row of the most beautiful IGDB promotional artwork
  - Full-width art with game info overlay

### Player App
- [ ] Gallery mode as a section on Explore screen (or dedicated sub-screen)
- [ ] Artwork showcase row

### Tests
- [ ] Web: E2E test — gallery renders, hover reveals info, click navigates
- [ ] Player: desktop E2E test — gallery section renders

---

## Phase 10: Social & Community Discovery

**Goal:** See what your server community is playing, trending games, community ratings.

### Backend
- [ ] `GET /api/explore/trending` — games with biggest play-count increase in last 7 days
- [ ] `GET /api/explore/community-top` — highest user-rated games on server
- [ ] `GET /api/explore/cult-classics` — high user rating but moderate IGDB rating
- [ ] `GET /api/explore/recently-reviewed` — games with recent user reviews
- [ ] `GET /api/explore/active-now` — games with active shared sessions or challenges

### Web Frontend
- [ ] "Trending on Your Server" shelf on Explore page
  - Game cards with "played by X people this week" badge
- [ ] "Community Favorites" shelf — distinct from IGDB ratings
- [ ] "Cult Classics" shelf — "Your community rates these higher than the critics"
- [ ] "Active Right Now" shelf — live sessions and challenges
- [ ] "Recently Reviewed" shelf — latest user reviews

### Player App
- [ ] Same shelves on Explore screen
- [ ] "Active Now" with join buttons for shared sessions

### Tests
- [ ] Backend: unit tests for trending algorithm, cult classics detection
- [ ] Web: E2E test — social shelves render with seeded data
- [ ] Player: desktop E2E test — social sections render

---

## Phase 11: Temporal Discovery

**Goal:** "On This Day", "Best of [Year]", gaming anniversary features.

### Backend
- [ ] `GET /api/explore/on-this-day` — games released on today's date across all years
- [ ] `GET /api/explore/best-of-year/:year` — top-rated games from a specific year
- [ ] `GET /api/explore/your-anniversaries` — personal milestones ("1 year ago you played...")
- [ ] `GET /api/explore/decades/:decade` — best games of a decade (80s, 90s, 00s)

### Web Frontend
- [ ] "On This Day in Gaming" shelf on Explore page
  - "March 9: [games released on this date]"
  - Day/month matching on release dates
- [ ] "Best of [Year]" browsable section
  - Year selector (slider or dropdown spanning 1985–2005)
  - Grid of top games for selected year
- [ ] "Your Gaming Anniversaries" personal row
- [ ] "Decade Spotlight" section with era-appropriate styling

### Player App
- [ ] "On This Day" section on Explore screen
- [ ] Year browser
- [ ] Personal anniversaries

### Tests
- [ ] Backend: unit tests for on-this-day date matching, year filtering
- [ ] Web: E2E test — on-this-day renders, year browser works
- [ ] Player: desktop E2E test — temporal sections render

---

## Phase 12: Achievement & Challenge-Driven Discovery

**Goal:** Discover games through achievements and challenges.

### Backend
- [ ] `GET /api/explore/easy-to-complete` — high achievement completion rates
- [ ] `GET /api/explore/hardest-games` — low achievement completion rates
- [ ] `GET /api/explore/almost-done` — user's games at 80%+ achievement completion
- [ ] `GET /api/explore/fresh-challenges` — games with achievements user hasn't started
- [ ] `GET /api/explore/active-challenges` — currently open challenges

### Web Frontend
- [ ] Achievement-driven shelves on Explore page
- [ ] "Easy to 100%" and "Mount Everest" rows
- [ ] Personal "Almost Done" and "Fresh Challenges" rows
- [ ] Active challenges callout with join buttons

### Player App
- [ ] Same achievement shelves
- [ ] Direct navigation to game + achievement tracking

### Tests
- [ ] Backend: unit tests for completion rate calculations
- [ ] Web: E2E test — achievement rows render
- [ ] Player: desktop E2E test — achievement sections render

---

## Phase 13: Advanced Search & Multi-Faceted Filtering

**Goal:** Power-user search with combinable filters across all metadata dimensions.

### Backend
- [ ] Extend `GET /api/games` with all new filter dimensions:
  - `themes[]` (multi-select)
  - `keywords[]` (multi-select)
  - `perspectives[]` (multi-select)
  - `yearMin`, `yearMax` (range)
  - `ratingMin`, `ratingMax` (range)
  - `gameModes[]` (single-player, co-op, multiplayer)
  - `playStatus` (unplayed, played, favorited, queued)
  - `developer`, `publisher`
  - `ageRating`
  - `consoles[]` (multi-select)
  - `genres[]` (multi-select)
- [ ] `POST /api/user/saved-searches` — save filter combos
- [ ] `GET /api/user/saved-searches` — list saved searches
- [ ] `DELETE /api/user/saved-searches/:id`

### Web Frontend
- [ ] Advanced filter panel (collapsible sidebar or modal):
  - Multi-select dropdowns for genres, themes, consoles
  - Keyword search with autocomplete
  - Range sliders for year and rating
  - Toggle chips for play status and game modes
  - "X games match" live count
  - "Save this search" button
- [ ] Saved searches as dynamic collections on Explore page
- [ ] URL-based filter state (shareable links)

### Player App
- [ ] Filter panel (bottom sheet or dedicated filter screen)
- [ ] Saved searches in Explore
- [ ] Quick filter chips at top of game lists

### Tests
- [ ] Backend: unit tests for combined filtering, saved search CRUD
- [ ] Web: E2E test — apply multiple filters, results update, save search
- [ ] Player: desktop E2E test — filter panel works

---

## Phase 14: Wild Features — Surprise, Wizard, Gamification

**Goal:** Delight features that make discovery fun and addictive.

### Backend
- [ ] `GET /api/explore/random?console=&genre=&minRating=` — random game with optional constraints
- [ ] `GET /api/explore/wizard` — decision wizard flow (mood → era → vibe → results)
- [ ] `GET /api/user/explorer-badges` — badge system for breadth of play
  - "Played every console"
  - "Played 10 genres"
  - "Played games from 5 decades"
  - "100 games played"
- [ ] `GET /api/user/completionist-map` — per-console completion stats
  - `{ consoles: [{ id, name, totalGames, playedGames, percentage }, ...] }`

### Web Frontend
- [ ] "I'm Feeling Lucky" button with slot-machine animation
  - Cover art spins, lands on a game
  - "Play Now" / "Spin Again" buttons
- [ ] Decision Wizard: `/explore/wizard`
  - Step 1: mood cards
  - Step 2: era selection (decade cards with period artwork)
  - Step 3: vibe refinement (theme/keyword chips)
  - Result: 5 personalized recommendations with hero art
- [ ] Explorer Badges on profile page
  - Visual badge gallery
  - Progress bars for incomplete badges
  - "Next badge: Play a [Console] game" nudges
- [ ] Completionist's Map on profile/stats page
  - Visual map of consoles with fill percentage
  - Tap console → see which games you've played / haven't

### Player App
- [ ] Same features adapted for native UI
- [ ] "Feeling Lucky" with haptic feedback on landing
- [ ] Badge unlock celebrations (confetti/animation)

### Tests
- [ ] Backend: unit tests for random selection, badge calculation, wizard logic
- [ ] Web: E2E test — wizard flow works end to end, badges render
- [ ] Player: desktop E2E test — wizard and badges render

---

## Phase 15: Polish & Integration

**Goal:** Final integration pass — connect everything, optimize performance, refine UX.

### Tasks
- [ ] Explore page section ordering based on user engagement (most-clicked sections rise)
- [ ] Performance: lazy-load below-the-fold sections, image optimization, API response caching
- [ ] Skeleton loading states for every section
- [ ] Empty states: helpful messages when no data (e.g., "Play some games to get recommendations!")
- [ ] Deep linking: every Explore section has a shareable URL
- [ ] Admin panel: ability to feature/pin games, curate the hero carousel, set developer spotlight
- [ ] Analytics: track which Explore sections get most engagement (server-side, privacy-respecting)
- [ ] Accessibility pass: keyboard navigation, screen reader support, focus management
- [ ] Cross-link everything: game detail → franchise, developer, theme, similar;
  developer page → games → franchise; etc.

### Tests
- [ ] Full E2E regression suite for all Explore features
- [ ] Performance benchmarks: page load time, API response times
- [ ] Accessibility audit

---

## Data Dependencies Summary

| Phase | IGDB Data Needed | SteamGridDB Data Needed | New DB Models |
|-------|-----------------|------------------------|---------------|
| 1 | None (existing) | None (existing) | None |
| 2 | themes, keywords, collections, franchises, perspectives, artworks | None | GameTheme, GameKeyword, GamePlayerPerspective, GameSeries, GameSeriesEntry, GameArtworkImage |
| 3 | From Phase 2 | None | None |
| 4 | From Phase 2 | None | None |
| 5 | From Phase 2 | None | MoodDefinition (or config) |
| 6 | Similar games (existing) | None | TasteProfile (computed), SavedRecommendation (cache) |
| 7 | Companies (new) | None | None (uses existing developer/publisher fields) |
| 8 | None | None | None |
| 9 | Artworks (from Phase 2) | None | None |
| 10 | None | None | None (uses existing play history, ratings) |
| 11 | None | None | None (uses existing release dates) |
| 12 | None | None | None (uses existing achievements) |
| 13 | From Phase 2 | None | SavedSearch |
| 14 | None | None | ExplorerBadge, UserBadge |
| 15 | None | None | ExploreAnalytics (optional) |

---

## Estimated Scope

- **15 phases** — each is a self-contained PR
- **Backend:** ~15 new endpoints, ~6 new DB models, IGDB client extensions
- **Web:** New Explore page with ~15 sections, 5+ sub-pages, advanced filter panel
- **Player:** New Explore tab with matching sections and screens
- **Tests:** Full coverage per phase — unit + E2E for backend, web, and player

## Decisions

1. **Explore lives alongside Dashboard/Home.** Keep the existing Dashboard for now. We'll likely
   replace it later, but for now Explore is a new tab/route.

2. **No library size gate.** Explore is available to everyone regardless of library size. We may
   revisit this later, but for now keep it open.

3. **No admin curation.** Zero admin burden — the Explore page is fully algorithmic. Set up the
   server and it just works. Featured content is auto-selected (highest-rated with hero art, etc.).

4. **Respect IGDB/SteamGridDB rate limits. Cache everything locally.** The enrichment backfill
   (Phase 2) must be throttled to stay within IGDB's 4 req/sec limit. All IGDB and SteamGridDB
   data must be stored locally so the Explore page works even when external APIs are down. Data
   may be stale, but the feature must never break due to an external service outage.

5. **Always show games not in the library, but clearly indicate them.** Franchise pages, top-rated
   lists, and similar-games rows should include games the user doesn't own. These must be visually
   distinct — use a consistent design system approach:
   - **Web:** A shared component prop (e.g., `missingInLibrary`) that the design system uses to
     render the distinction (dimmed art, overlay icon, badge — the design system decides how).
   - **Player app:** An equivalent `Sp*` component parameter.
   - **Consistency is key:** The same visual treatment everywhere, decided by the shared component
     library, not by individual features or screens.
   - The existing "top rated" list on the console screen already dims missing games — we should
     unify this with the new shared approach and make it clearer to users what "missing" means
     (e.g., a small icon overlay or "Not in library" label).
