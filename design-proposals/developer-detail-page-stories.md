# Developer/Studio Detail Page -- Design Proposal & User Stories

## Current State

The developer detail page exists in both the **web frontend** (`web/src/pages/developer-detail-page.tsx`) and the **player app** (`ExploreDeveloperScreen.kt`). Both are functional but bare:

- **Header**: Developer name, game count, average rating, console list (text only)
- **Console filter**: Clickable chips to filter the flat game list by platform
- **Games**: Flat list/grid of all games (web uses a cover art grid; player app uses a vertical list with small cover art + title + rating)
- **No studio identity**: No logo, no description, no founding info, no country
- **No grouped presentation**: Games are a single undifferentiated list sorted by rating
- **No user context**: No play time stats, no favorite count, no "your history with this developer"
- **No visual richness**: No hero image, no screenshots, no visual hierarchy

The backend endpoint (`GET /api/explore/developers/:name`) returns only: `name`, `gameCount`, `avgRating`, `consoles[]`, and `games[]`. No company metadata.

## Available Data

**Already in the database (per game record):**
- `developer`, `publisher`, `title`, `coverUrl`, `screenshotUrls`, `rating`, `description`, `genre`, `releaseDate`, `players`, `heroUrl`, `logoUrl`
- Play history: `lastPlayed`, `playTime` (per user per game)
- Favorites, Play Later lists (per user)

**Available from IGDB Company API (NOT currently fetched):**
- `logo` (company logo image)
- `description` (company bio/description text)
- `country` (headquarters country code)
- `start_date` / `start_date_category` (founding date)
- `websites[]` (official site, Wikipedia, etc.)
- `developed[]` / `published[]` (game ID lists)

**Derivable from existing game data (no new API calls):**
- Games grouped by console/platform
- Genre breakdown (what genres does this developer focus on?)
- Release year timeline
- Rating distribution
- Total user play time across all developer's games
- User's favorites from this developer
- Most played games by this developer (for the current user)
- Hero image from highest-rated game's artwork
- Publisher relationship (which publishers has this developer worked with?)

## Design Principles

1. **Progressive enhancement** -- Phase 1 uses only data we already have. IGDB company data is additive in Phase 2.
2. **Graceful degradation** -- Every section handles missing data. No logo? Show a generated initial avatar. No description? Skip the bio section. No play history? Show invite-to-play messaging.
3. **Aligned experience** -- Web and player app show the same content sections in the same order, adapted to each platform's interaction model.
4. **Server-driven** -- The backend computes derived data (genre breakdown, timeline, stats) so both clients get the same results without duplicating logic.

---

## Phase 1: Rich Studio Profile (using existing data only)

**Goal:** Transform the flat game list into a rich, visually appealing studio profile page using data that is already in the database. No new scraping or external API calls.

### Story 1.1: Hero Banner with Developer Identity

**As a** user browsing my game library,
**I want** the developer detail page to open with a visually striking hero banner,
**so that** the page feels like a proper studio profile rather than a plain list.

**Acceptance criteria:**
- The page displays a hero banner at the top, using the hero artwork from the developer's highest-rated game that has artwork available.
- The developer name is displayed prominently over the hero image with a gradient overlay for readability.
- A generated avatar (first letter of the developer name in a colored circle) is shown next to the name when no logo is available.
- Below the name, show: game count, average rating (with star icon), and the number of platforms.
- If no hero artwork is available for any of the developer's games, fall back to a gradient background using the brand color palette.
- The banner is visually consistent between web and player app (same information, adapted layout).

### Story 1.2: Games Grouped by Platform

**As a** user exploring a developer's catalog,
**I want** games organized by platform rather than in a single flat list,
**so that** I can see what this developer made for each console I care about.

**Acceptance criteria:**
- Games are displayed in sections grouped by console, with each section headed by the console name and game count.
- Within each section, games are sorted by rating (descending), then title (ascending).
- Each section shows a horizontal scrollable row of game cards (web) or a vertical list of game items (player app, consistent with the platform's existing pattern).
- The console filter chips from the current design are retained at the top as a quick-jump/filter mechanism: tapping a chip scrolls to that section (or filters to show only that section).
- An "All" view is available that shows all games in a single grid/list (the current default behavior), for users who prefer that.
- The section ordering is: most games first, so the platform with the largest catalog is shown first.

### Story 1.3: "Highlight" Row -- Top-Rated Games

**As a** user landing on a developer page,
**I want** to immediately see the developer's best games,
**so that** I can quickly find the standout titles without scrolling through everything.

**Acceptance criteria:**
- A "Top Rated" section appears above the per-platform groupings.
- It shows up to 8 games with the highest ratings, displayed as larger cards with cover art, title, rating badge, and console badge.
- This section is only shown when the developer has more than 4 games (for small catalogs, the per-platform sections are sufficient).
- On web, this is a horizontal scrollable row of prominent game cards. On the player app, it follows the platform's card row pattern.

### Story 1.4: Genre Breakdown

**As a** user learning about a developer,
**I want** to see what genres this developer works in,
**so that** I understand their specialization and find games matching my tastes.

**Acceptance criteria:**
- A "Genres" section shows the breakdown of the developer's games by genre.
- Each genre is displayed as a chip/tag with the genre name and game count (e.g., "Platformer (12)", "RPG (8)").
- Genres are sorted by game count (most games first).
- Tapping a genre chip filters the game list to show only games of that genre.
- This section is only shown when the developer has games in at least 2 genres.

### Story 1.5: User's Relationship with This Developer

**As a** user who has been playing games,
**I want** to see a summary of my personal history with this developer,
**so that** I can see how much time I've invested and which of their games I've enjoyed.

**Acceptance criteria:**
- A "Your Stats" card or section is shown (only when the user has play history for at least one of this developer's games).
- It displays:
  - Total play time across all of this developer's games (formatted as hours/minutes).
  - Number of games played out of total available (e.g., "Played 7 of 23 games").
  - Number of favorites from this developer.
  - The user's most-played game by this developer (with cover art and play time).
- If the user has never played any games by this developer, this section is hidden entirely (not shown as empty).

### Story 1.6: Enhanced Backend Response

**As a** frontend developer,
**I want** the developer detail API to return richer computed data,
**so that** both web and player app can render the new sections without duplicating business logic.

**Acceptance criteria:**
- The `GET /api/explore/developers/:name` response is extended with new optional fields:
  - `heroUrl` (string, optional) -- hero artwork URL from the highest-rated game with artwork.
  - `topGames` (array of game responses) -- up to 8 highest-rated games.
  - `genreBreakdown` (array of `{genre: string, count: int}`) -- genre distribution sorted by count.
  - `platformBreakdown` (array of `{consoleName: string, consoleId: string, count: int}`) -- games per platform, sorted by count.
  - `userStats` (object, optional, only when authenticated):
    - `totalPlayTime` (int64, seconds)
    - `gamesPlayed` (int)
    - `favoriteCount` (int)
    - `mostPlayedGame` (game response, nullable)
- Existing fields (`name`, `gameCount`, `avgRating`, `consoles`, `games`) remain unchanged for backward compatibility.
- The same enrichment pattern is applied to `GET /api/explore/publishers/:name` for consistency.
- Response is cached for 5 minutes (`Cache-Control: private, max-age=300`), consistent with the current behavior.

---

## Phase 2: Studio Identity from IGDB (new data fetching)

**Goal:** Fetch and store company metadata from IGDB to give the developer page a real studio identity: logo, description, founding date.

### Story 2.1: IGDB Company Data Model & Scraping

**As a** system administrator,
**I want** company metadata (logo, description, country, founding date) to be fetched from IGDB and stored in the database,
**so that** developer pages can show rich studio identity information.

**Acceptance criteria:**
- A new `Company` model is added to the database with fields: `igdbCompanyId`, `name`, `description`, `logoUrl`, `country`, `foundedYear`, `websiteUrl`, `wikipediaUrl`.
- During game scraping, when an `InvolvedCompany` is encountered, the company's IGDB ID and name are stored. The full company metadata is fetched lazily (on first developer page visit) or as a batch background job.
- A new IGDB client method `GetCompany(igdbID int)` fetches: `name, description, logo.image_id, country, start_date, websites.url, websites.category`.
- Company logos are downloaded and stored locally (same pattern as game cover art).
- Company data is cached and refreshed no more frequently than once per month.
- If IGDB credentials are not configured, the system gracefully skips company enrichment (no errors, just missing data).

### Story 2.2: Studio Logo and Description Display

**As a** user viewing a developer page,
**I want** to see the studio's logo and a brief description,
**so that** I get context about who made these games.

**Acceptance criteria:**
- When a company logo is available, it replaces the generated initial avatar in the hero banner.
- The logo is displayed at a consistent size (not stretched), with a subtle background to ensure visibility against the hero image.
- When a company description is available, it is shown below the hero banner in a collapsible/expandable text block (collapsed to 3 lines by default on mobile, 5 lines on desktop).
- Founding year and country are shown as metadata below the description (e.g., "Founded 1979 -- Osaka, Japan").
- If the description is not available, the section is omitted (not shown as empty).

### Story 2.3: Publisher Relationship

**As a** user exploring a developer's catalog,
**I want** to see which publishers this developer has worked with,
**so that** I understand the business relationships behind the games.

**Acceptance criteria:**
- A "Publishers" section lists the publishers associated with this developer's games (derived from the `publisher` field on each game).
- Each publisher is shown as a clickable link/chip that navigates to the publisher detail page.
- Publishers are sorted by the number of games (most games first).
- This section is only shown when games have publisher data and there is at least one publisher.

---

## Phase 3: Statistics & Insights

**Goal:** Add deeper analytical views that make the developer page feel like a rich data dashboard for the curious user.

### Story 3.1: Release Timeline

**As a** user interested in a developer's history,
**I want** to see a visual timeline of when their games were released,
**so that** I can see their active periods and how their output has evolved.

**Acceptance criteria:**
- A "Timeline" section shows games organized by release year.
- The visualization is a horizontal scrollable strip where each year that has releases shows the year label and the game cover art thumbnails for that year.
- Years without releases are omitted (no empty gaps).
- On the web, hovering over a game thumbnail shows its title and rating in a tooltip. In the player app, tapping navigates to the game detail.
- Games without a release date are grouped in a separate "Unknown" bucket at the end.
- This section is only shown when games have release date data (at least 3 games with dates across at least 2 different years).

### Story 3.2: Rating Distribution

**As a** user evaluating a developer's quality,
**I want** to see how their games' ratings are distributed,
**so that** I can tell if they are consistently good or highly variable.

**Acceptance criteria:**
- A "Ratings" section shows a simple horizontal bar chart or distribution visual.
- The distribution groups ratings into buckets: 90-100 (Excellent), 70-89 (Good), 50-69 (Average), Below 50 (Poor), Unrated.
- Each bucket shows the count and a proportional bar.
- This section is only shown when at least 5 games have ratings.

### Story 3.3: Era Summary Stats

**As a** user exploring a developer's output,
**I want** to see summary statistics about their catalog at a glance,
**so that** I get a complete picture without scrolling through all games.

**Acceptance criteria:**
- An "At a Glance" stats row or card shows:
  - Total games in library
  - Active years (e.g., "1986 -- 2004")
  - Primary genre (the genre with the most games)
  - Number of platforms
  - Average rating across all rated games
- This is displayed as compact stat cards/pills in a row below the hero banner (or integrated into the hero banner).
- All values are derived server-side and included in the API response.

---

## Phase 4: Nice-to-Have Polish

**Goal:** Final refinements that add delight but are not essential for the core experience.

### Story 4.1: "Also Published" Section

**As a** user on a developer detail page,
**I want** to see other developers that share publishers with this developer,
**so that** I can discover related studios.

**Acceptance criteria:**
- An "Also Published by..." section shows other developers whose games share the same publishers.
- Limited to the top 5 related developers by shared game count.
- Each developer is shown as a clickable card with name and game count.
- This section is only shown when meaningful connections exist (at least 2 shared publishers or 5 shared games).

### Story 4.2: Animated Stat Counters

**As a** user,
**I want** the stats on the developer page to animate in when they first appear,
**so that** the page feels polished and dynamic.

**Acceptance criteria:**
- Stats (game count, average rating, play time) animate from 0 to their final value when the page loads, using a count-up animation.
- The animation duration is short (300-500ms) and uses an ease-out curve.
- The animation only plays once per page visit (not on re-renders).
- The animation is skipped if the user has reduced-motion preferences enabled.

### Story 4.3: Share Developer Profile

**As a** user who found an interesting developer,
**I want** to share a link to this developer's profile page,
**so that** I can show others what this studio has made.

**Acceptance criteria:**
- A share button is available on the developer detail page.
- On web, clicking it copies the page URL to the clipboard with a confirmation toast.
- On the player app, it uses the platform's native share sheet.
- The shared URL works for any authenticated user on the same server.

### Story 4.4: Skeleton Loading States for New Sections

**As a** user navigating to a developer page,
**I want** to see appropriately shaped skeleton placeholders while data loads,
**so that** the page doesn't jump around as content appears.

**Acceptance criteria:**
- Each new section (hero banner, top-rated row, genre breakdown, user stats, timeline) has a corresponding skeleton placeholder.
- Skeletons match the approximate shape and size of the final content.
- Skeletons are shown only during initial load, not during background refetches.
- The existing skeleton behavior for the games grid is preserved.

---

## Implementation Notes

**Phase 1** requires only backend changes to compute derived data and frontend changes to render new sections. No new external API calls, no new database tables, no new scraping. This is the highest-impact, lowest-risk phase.

**Phase 2** introduces a new `Company` database model and IGDB API integration. This is architecturally similar to existing enrichment patterns (themes, keywords, franchises) and follows the same scraping infrastructure.

**Phase 3** is primarily frontend work with minor backend additions (the backend already has all the data; it just needs to compute and return the statistics).

**Phase 4** is purely frontend polish with no backend changes.

## API Response Shape (Phase 1 target)

```json
{
  "name": "Capcom",
  "gameCount": 23,
  "avgRating": 82.3,
  "consoles": ["SNES", "GBA", "Genesis", "PS1"],
  "heroUrl": "/images/snes/42/hero.jpg",
  "topGames": [ ... ],
  "genreBreakdown": [
    { "genre": "Platformer", "count": 8 },
    { "genre": "Fighting", "count": 6 },
    { "genre": "RPG", "count": 4 }
  ],
  "platformBreakdown": [
    { "consoleName": "SNES", "consoleId": "snes", "count": 12 },
    { "consoleName": "GBA", "consoleId": "gba", "count": 6 }
  ],
  "userStats": {
    "totalPlayTime": 14400,
    "gamesPlayed": 7,
    "favoriteCount": 3,
    "mostPlayedGame": { ... }
  },
  "games": [ ... ]
}
```

## API Response Shape (Phase 2 additions)

```json
{
  "...all Phase 1 fields...",
  "logoUrl": "/images/companies/capcom-logo.png",
  "description": "Capcom Co., Ltd. is a Japanese video game developer...",
  "foundedYear": 1979,
  "country": "Japan",
  "websiteUrl": "https://www.capcom.com",
  "publishers": [
    { "name": "Capcom", "gameCount": 18 },
    { "name": "Nintendo", "gameCount": 3 }
  ]
}
```

## API Response Shape (Phase 3 additions)

```json
{
  "...all Phase 1+2 fields...",
  "activeYears": { "first": 1987, "last": 2003 },
  "ratingDistribution": {
    "excellent": 5,
    "good": 10,
    "average": 4,
    "poor": 1,
    "unrated": 3
  },
  "primaryGenre": "Platformer"
}
```
