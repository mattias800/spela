# Carousel Card Redesign: Static Height, Image-Driven Width

## Problem

Game carousels in the web UI use fixed-width cards (`w-40 sm:w-44 lg:w-48`) with
a forced `3:4` aspect ratio. Cover art with different aspect ratios gets cropped
via `object-cover`. The player app solved this by making carousel cards
fixed-height with image-driven width — the full box art is always visible and the
card expands or contracts to fit. The web UI should match this approach.

## Scope

- **In scope:** All horizontal scroll shelves that contain game cards or cover
  cards. Also the shared scroll container extraction.
- **Out of scope:** Grid views (`GameGrid`, `BestOfYearSection`), `HeroCarousel`
  (opacity transitions, not scrolling), navigation elements (`ConsoleQuickJump`,
  `KeywordChips`), `GameSummaryCard`.

## Design

### Centralized Constants

A single file for carousel sizing — one place to tweak.

```ts
// web/src/lib/carousel-constants.ts
export const CAROUSEL_CARD_HEIGHT = 240;            // px
export const CAROUSEL_DEFAULT_ASPECT_RATIO = 3 / 4; // fallback for placeholder/skeleton
```

### ScrollShelf (Design Layer)

A shared horizontal scroll container extracted from the 6+ duplicated copies
currently spread across `game-shelf.tsx`, `series-shelf.tsx`,
`artwork-showcase.tsx`, `developer-spotlight.tsx`, `temporal-shelves.tsx`,
`achievement-shelves.tsx`, and `social-shelves.tsx`.

**File:** `web/src/components/scroll-shelf.tsx`

**Owns:** Horizontal overflow scroll, scroll arrow buttons (appear on hover),
scroll state tracking (`canScrollLeft`/`canScrollRight`), scrollbar hiding,
`data-comp="ScrollShelf"`.

**Props:**

| Prop | Type | Description |
|------|------|-------------|
| `title` | `string` | Section heading |
| `subtitle` | `string?` | Secondary text below title |
| `icon` | `LucideIcon?` | Icon next to title |
| `testId` | `string` | `data-testid` for the section |
| `isLoading` | `boolean` | Show skeleton state |
| `isEmpty` | `boolean` | Return null when true |
| `children` | `ReactNode` | Scroll row content |
| `loadingSkeleton` | `ReactNode?` | Custom skeleton (defaults to 6 game card skeletons) |
| `headerRight` | `ReactNode?` | Extra content in the header row (links, etc.) |

**Does NOT own:** Card sizing, card content, gap between cards. These are
controlled by the children.

### GameCard Carousel Mode

The existing `GameCard` (Content layer) gets a new optional prop:
`coverHeight?: number`.

**When `coverHeight` is set (carousel mode):**

- The image container gets a fixed pixel height via
  `style={{ height: coverHeight }}`.
- The `<img>` renders with `height: 100%` and `width: auto` — the image's
  natural aspect ratio drives the card width.
- No `object-cover`, no cropping — full box art is always visible.
- The card has `flex-shrink-0` to prevent collapse in flex rows.

**When `coverHeight` is NOT set (grid mode):**

- No change to existing behavior. Card fills parent width, placeholder uses
  `aspectRatio`.

**Placeholder / missing cover (carousel mode):**

- Uses `game.coverAspectRatio` (from the API, per-console) combined with
  `coverHeight` to size the placeholder via CSS `aspect-ratio` + fixed height.
- Falls back to `CAROUSEL_DEFAULT_ASPECT_RATIO` if the API value is missing.
- Shows the existing gradient + first-letter fallback.

### CoverCard Carousel Mode

Same pattern as `GameCard`. `CoverCard` (Content layer) gets an optional
`coverHeight?: number` prop with identical behavior: fixed height, image-driven
width, no crop.

This is used by `TopRatedGameCard` (Role layer) in the `TopRatedRow`.

### GameCardSkeleton

`GameCardSkeleton` also accepts an optional `coverHeight` prop. When set, it
uses `CAROUSEL_DEFAULT_ASPECT_RATIO` combined with the height to derive skeleton
dimensions so loading states match the expected card shape.

### Shelf Migration

All shelves migrate to use the shared `ScrollShelf` and pass `coverHeight` to
their card components.

**Game shelves (ScrollShelf + GameCard with coverHeight):**

| Shelf | File | Notes |
|-------|------|-------|
| `GameShelf` | `game-shelf.tsx` | Delete inline scroll logic |
| `ForYouSection` | `for-you-section.tsx` | Per-row shelves |
| `PlayersLikeYouShelf` | `players-like-you-shelf.tsx` | |
| `DeveloperSpotlight` | `developer-spotlight.tsx` | Scroll row is nested inside a hero card. Use `ScrollShelf` with title/icon/subtitle hidden (empty string title, no icon) so it only provides scroll mechanics. The hero card wrapper stays as-is. |
| `OnThisDayShelf` | `temporal-shelves.tsx` | Delete internal ScrollShelf |
| `AnniversariesShelf` | `temporal-shelves.tsx` | |
| `DecadeSpotlight` | `temporal-shelves.tsx` | |
| `EasyToCompleteShelf` | `achievement-shelves.tsx` | Delete internal ScrollShelf |
| `HardestGamesShelf` | `achievement-shelves.tsx` | |
| `AlmostDoneShelf` | `achievement-shelves.tsx` | |
| `FreshChallengesShelf` | `achievement-shelves.tsx` | |
| `TrendingShelf` | `social-shelves.tsx` | Delete internal ScrollShelf |
| `CommunityTopShelf` | `social-shelves.tsx` | |
| `CultClassicsShelf` | `social-shelves.tsx` | |
| `ActiveNowShelf` | `social-shelves.tsx` | |
| `TopRatedRow` | `top-rated-row.tsx` | Switch from TitledSection to ScrollShelf, pass coverHeight through TopRatedGameCard to CoverCard |

Per-card wrapper changes from:

```tsx
<div className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
  <GameCard game={game} />
</div>
```

to:

```tsx
<div className="flex-shrink-0" role="listitem">
  <GameCard game={game} coverHeight={CAROUSEL_CARD_HEIGHT} />
</div>
```

**Non-game shelves (use ScrollShelf, keep current card sizing):**

| Shelf | File | Card type |
|-------|------|-----------|
| `SeriesShelf` | `series-shelf.tsx` | Custom hero cards (`w-56`/`w-60`/`w-64`, `h-36`) |
| `ArtworkShowcase` | `artwork-showcase.tsx` | Screenshot cards (`w-80`/`w-96`, `16:9`) |
| `RecentlyReviewedShelf` | `social-shelves.tsx` | Hybrid cover + text (`w-56`/`w-60`) |
| `ActiveChallengesShelf` | `achievement-shelves.tsx` | Text-only cards (`w-64`) |

These adopt `ScrollShelf` for the scroll container (eliminating duplicated scroll
logic) but keep their existing fixed-width card sizing since their content isn't
standard box art.

### What Doesn't Change

- **Grid views** — `GameGrid`, `BestOfYearSection`, any grid layout.
- **HeroCarousel** — opacity transition slides, not scrolling.
- **ConsoleQuickJump, KeywordChips** — nav elements.
- **GameSummaryCard** — horizontal layout card for lists/detail pages.
- **GameCard without `coverHeight`** — when used outside carousels, behaves
  exactly as today. This change is purely additive.

## Component Hierarchy Summary

```
Design layer:
  ScrollShelf          — shared scroll container with arrows
  CoverImage           — image with lazy loading + fallback (unchanged)

Content layer:
  GameCard             — game cover card, now supports coverHeight for carousel mode
  CoverCard            — generic cover card, now supports coverHeight for carousel mode
  GameCardSkeleton     — loading placeholder, supports coverHeight

Role layer:
  GameShelf            — thin wrapper: ScrollShelf + GameCard with coverHeight
  TopRatedGameCard     — maps TopRatedGame to CoverCard with coverHeight
  TrendingShelf        — ScrollShelf + GameCard + player count label
  (... all other shelf components)
```

## Testing

- Visual: verify carousels render with varying-width cards across consoles with
  different box art aspect ratios (SNES 7:10, Genesis 28:39, Game Boy 1:1, etc.)
- Verify placeholder/skeleton sizing matches loaded card dimensions
- Verify scroll arrows still work
- Verify grid views are unaffected
- Verify hover effects (scale, shadow, translate) still work in carousel mode
- Existing E2E tests for explore page should continue to pass
