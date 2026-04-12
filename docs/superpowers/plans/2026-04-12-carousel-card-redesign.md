# Carousel Card Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make game cards in web carousels use static height with image-driven width (no cropping), matching the player app, and extract a shared ScrollShelf component to eliminate duplicated scroll logic.

**Architecture:** New `ScrollShelf` (Design layer) replaces 6+ duplicated scroll containers. `GameCard` and `CoverCard` (Content layer) get an optional `coverHeight` prop — when set, the card renders at fixed height and lets the image's natural aspect ratio drive its width. All carousel shelf components (Role layer) migrate to use `ScrollShelf` and pass `coverHeight` from centralized constants.

**Tech Stack:** React, TypeScript, Tailwind CSS, Vitest + React Testing Library

**Spec:** `docs/superpowers/specs/2026-04-12-carousel-card-redesign.md`

---

### Task 1: Centralized Carousel Constants

**Files:**
- Create: `web/src/lib/carousel-constants.ts`

- [ ] **Step 1: Create the constants file**

```ts
// web/src/lib/carousel-constants.ts

/** Fixed height (px) for game cards in carousel shelves. */
export const CAROUSEL_CARD_HEIGHT = 240;

/** Default aspect ratio (width/height) for placeholder/skeleton cards. */
export const CAROUSEL_DEFAULT_ASPECT_RATIO = 3 / 4;
```

- [ ] **Step 2: Verify it compiles**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/lib/carousel-constants.ts
git commit -m "feat: add centralized carousel card constants"
```

---

### Task 2: ScrollShelf Component (Design Layer)

Extract the scroll container that is duplicated across `game-shelf.tsx`, `series-shelf.tsx`, `artwork-showcase.tsx`, `developer-spotlight.tsx`, `players-like-you-shelf.tsx`, `for-you-section.tsx`, `temporal-shelves.tsx`, `achievement-shelves.tsx`, and `social-shelves.tsx`.

**Files:**
- Create: `web/src/components/scroll-shelf.tsx`
- Create: `web/src/components/__tests__/scroll-shelf.test.tsx`

- [ ] **Step 1: Write the failing tests**

```tsx
// web/src/components/__tests__/scroll-shelf.test.tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Star } from "lucide-react";
import { ScrollShelf } from "../scroll-shelf";

describe("ScrollShelf", () => {
  it("renders title and children", () => {
    render(
      <ScrollShelf title="Test Shelf" testId="test-shelf" isLoading={false} isEmpty={false}>
        <div>Child 1</div>
        <div>Child 2</div>
      </ScrollShelf>,
    );
    expect(screen.getByRole("heading", { name: "Test Shelf", level: 2 })).toBeInTheDocument();
    expect(screen.getByText("Child 1")).toBeInTheDocument();
    expect(screen.getByText("Child 2")).toBeInTheDocument();
  });

  it("renders subtitle when provided", () => {
    render(
      <ScrollShelf title="Shelf" subtitle="Some subtitle" testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByText("Some subtitle")).toBeInTheDocument();
  });

  it("renders icon when provided", () => {
    render(
      <ScrollShelf title="Shelf" icon={Star} testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    // lucide renders an svg with the Star icon
    const section = screen.getByTestId("s");
    expect(section.querySelector("svg")).toBeInTheDocument();
  });

  it("renders headerRight slot", () => {
    render(
      <ScrollShelf title="Shelf" testId="s" isLoading={false} isEmpty={false} headerRight={<span>View all</span>}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByText("View all")).toBeInTheDocument();
  });

  it("renders scrollable list with title as aria label", () => {
    render(
      <ScrollShelf title="My Shelf" testId="s" isLoading={false} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByRole("list", { name: "My Shelf" })).toBeInTheDocument();
  });

  it("returns null when isEmpty is true", () => {
    const { container } = render(
      <ScrollShelf title="Shelf" testId="s" isLoading={false} isEmpty={true}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders loading skeleton when isLoading", () => {
    render(
      <ScrollShelf title="Shelf" testId="s" isLoading={true} isEmpty={false}>
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByTestId("s-skeleton")).toBeInTheDocument();
  });

  it("renders custom loading skeleton when provided", () => {
    render(
      <ScrollShelf
        title="Shelf"
        testId="s"
        isLoading={true}
        isEmpty={false}
        loadingSkeleton={<div data-testid="custom-skeleton">Loading...</div>}
      >
        <div>Item</div>
      </ScrollShelf>,
    );
    expect(screen.getByTestId("custom-skeleton")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd web && npx vitest run src/components/__tests__/scroll-shelf.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement ScrollShelf**

```tsx
// web/src/components/scroll-shelf.tsx
import { useRef, useState, useEffect, useCallback } from "react";
import type { ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { GameCardSkeleton } from "@/components/ui";

interface ScrollShelfProps {
  title: string;
  subtitle?: string;
  icon?: LucideIcon;
  testId: string;
  isLoading: boolean;
  isEmpty: boolean;
  children: ReactNode;
  loadingSkeleton?: ReactNode;
  headerRight?: ReactNode;
}

export function ScrollShelf({
  title,
  subtitle,
  icon: Icon,
  testId,
  isLoading,
  isEmpty,
  children,
  loadingSkeleton,
  headerRight,
}: ScrollShelfProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    updateScrollState();
    el.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", updateScrollState);
    return () => {
      el.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", updateScrollState);
    };
  }, [updateScrollState, children]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (isLoading) {
    return (
      <section data-testid={`${testId}-skeleton`}>
        <div className="flex items-center gap-2 mb-1">
          {Icon && <Icon className="h-5 w-5 text-surface-400" />}
          <div className="h-7 w-60 rounded bg-surface-800 animate-pulse" />
        </div>
        {subtitle && <div className="h-4 w-40 rounded bg-surface-800/60 animate-pulse mt-1 mb-5" />}
        {loadingSkeleton ?? (
          <div className="flex gap-5 overflow-hidden mt-4">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
                <GameCardSkeleton />
              </div>
            ))}
          </div>
        )}
      </section>
    );
  }

  if (isEmpty) return null;

  return (
    <section data-testid={testId} data-comp="ScrollShelf" className="group/shelf relative">
      <div className="flex items-center gap-2 mb-1">
        {Icon && <Icon className="h-5 w-5 text-brand-400" />}
        <h2 className="text-xl font-bold text-surface-100">{title}</h2>
        {headerRight && <div className="ml-auto">{headerRight}</div>}
      </div>
      {subtitle && <p className="text-sm text-surface-400 mb-4">{subtitle}</p>}

      <div className="relative">
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
            aria-label={`Scroll ${title} left`}
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
            aria-label={`Scroll ${title} right`}
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label={title}
        >
          {children}
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd web && npx vitest run src/components/__tests__/scroll-shelf.test.tsx`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/components/scroll-shelf.tsx web/src/components/__tests__/scroll-shelf.test.tsx
git commit -m "feat: add shared ScrollShelf component (Design layer)"
```

---

### Task 3: GameCard Carousel Mode

Add `coverHeight` prop to `GameCard`. When set, the card uses fixed height with image-driven width.

**Files:**
- Modify: `web/src/components/game-card.tsx`

- [ ] **Step 1: Add `coverHeight` prop to the interface and update the image container**

In `web/src/components/game-card.tsx`, add `coverHeight?: number` to `GameCardProps`:

```tsx
interface GameCardProps {
  game: Game;
  aspectRatio?: number;
  coverHeight?: number;
  showConsoleBadge?: boolean;
  hideConsoleName?: boolean;
  subtitle?: string;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}
```

Add `coverHeight` to the destructured props:

```tsx
export function GameCard({
  game,
  aspectRatio,
  coverHeight,
  showConsoleBadge,
  hideConsoleName,
  subtitle,
  onToggleFavorite,
  onTogglePlayLater,
}: GameCardProps) {
```

- [ ] **Step 2: Update the image rendering for carousel mode**

Replace the image container `<div>` (the one with `className="relative rounded-2xl overflow-hidden..."`) to handle both modes. The key change is the image rendering:

Replace the current image/placeholder block inside the container:

```tsx
{game.coverUrl ? (
  <img
    src={game.coverUrl}
    alt={game.title}
    className="w-full transition-transform duration-500 group-hover:scale-105"
    loading="lazy"
  />
) : (
  <div className="flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900" style={{ aspectRatio: game.coverAspectRatio ?? aspectRatio ?? 3 / 4 }}>
```

With this updated version:

```tsx
{game.coverUrl ? (
  <img
    src={game.coverUrl}
    alt={game.title}
    className={
      coverHeight
        ? "h-full w-auto transition-transform duration-500 group-hover:scale-105"
        : "w-full transition-transform duration-500 group-hover:scale-105"
    }
    loading="lazy"
  />
) : (
  <div
    className="flex items-center justify-center bg-gradient-to-br from-surface-800 to-surface-900"
    style={
      coverHeight
        ? { height: coverHeight, aspectRatio: game.coverAspectRatio ?? aspectRatio ?? 3 / 4 }
        : { aspectRatio: game.coverAspectRatio ?? aspectRatio ?? 3 / 4 }
    }
  >
```

- [ ] **Step 3: Add flex-shrink-0 when in carousel mode**

Update the outermost `<Link>` wrapper to include `flex-shrink-0` when `coverHeight` is set. Change:

```tsx
<Link ref={ref} to={`/games/${game.id}`} data-comp="GameCard" className="group block space-y-3">
```

to:

```tsx
<Link ref={ref} to={`/games/${game.id}`} data-comp="GameCard" className={`group block space-y-3${coverHeight ? " flex-shrink-0" : ""}`}>
```

- [ ] **Step 4: Set fixed height on the image container when coverHeight is provided**

Update the image container div to apply the fixed height. Change the opening tag of the container:

```tsx
<div
  className="relative rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1"
>
```

to:

```tsx
<div
  className="relative rounded-2xl overflow-hidden bg-surface-900 border border-surface-800/50 transition-all duration-300 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30 group-hover:-translate-y-1"
  style={coverHeight ? { height: coverHeight } : undefined}
>
```

- [ ] **Step 5: Verify the existing game-shelf test still passes**

Run: `cd web && npx vitest run src/features/explore/components/__tests__/game-shelf.test.tsx`
Expected: All tests PASS (GameCard is used without `coverHeight` here, so behavior is unchanged).

- [ ] **Step 6: Verify type checking passes**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 7: Commit**

```bash
git add web/src/components/game-card.tsx
git commit -m "feat: add coverHeight prop to GameCard for carousel mode"
```

---

### Task 4: CoverCard Carousel Mode

Add `coverHeight` prop to `CoverCard` with the same pattern.

**Files:**
- Modify: `web/src/components/cover-card.tsx`

- [ ] **Step 1: Add `coverHeight` prop and update sizing logic**

In `web/src/components/cover-card.tsx`, add `coverHeight?: number` to the interface:

```tsx
interface CoverCardProps {
  imageUrl?: string | null;
  title: string;
  subtitle?: string;
  linkTo?: string;
  dimmed?: boolean;
  aspectRatio?: string;
  width?: string;
  coverHeight?: number;
  children?: ReactNode;
}
```

Add `coverHeight` to the destructured props:

```tsx
export function CoverCard({
  imageUrl,
  title,
  subtitle,
  linkTo,
  dimmed = false,
  aspectRatio = "3/4",
  width = "w-36",
  coverHeight,
  children,
}: CoverCardProps) {
```

- [ ] **Step 2: Update the content rendering for carousel mode**

Replace the `content` variable definition. Change the outer container from always using `width` to being flexible when `coverHeight` is set:

```tsx
  const content = (
    <div data-comp="CoverCard" className={cn(coverHeight ? "flex-shrink-0" : width, dimmed && "opacity-50")}>
      <div
        className="w-full border border-surface-800/50"
        style={coverHeight ? { height: coverHeight } : { aspectRatio }}
      >
        <CoverImage
          src={imageUrl}
          alt={title}
          className={
            coverHeight
              ? "h-full w-auto rounded-xl"
              : "w-full h-full rounded-xl"
          }
        />
      </div>
      <div className="mt-2 px-0.5">
        <p className="text-sm font-medium text-surface-200 truncate">
          {title}
        </p>
        {subtitle && (
          <p className="text-xs text-surface-500 truncate mt-0.5">
            {subtitle}
          </p>
        )}
        {children && <div className="mt-1">{children}</div>}
      </div>
    </div>
  );
```

- [ ] **Step 3: Verify type checking passes**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/cover-card.tsx
git commit -m "feat: add coverHeight prop to CoverCard for carousel mode"
```

---

### Task 5: GameCardSkeleton Carousel Mode

Update `GameCardSkeleton` to support `coverHeight` so loading states match.

**Files:**
- Modify: `web/src/components/ui/skeleton.tsx`

- [ ] **Step 1: Update GameCardSkeleton to accept coverHeight**

In `web/src/components/ui/skeleton.tsx`, update the `GameCardSkeleton` function. Change:

```tsx
export function GameCardSkeleton({
  aspectRatio,
}: {
  aspectRatio?: number;
}) {
  return (
    <div className="space-y-3">
      <Skeleton
        className="w-full rounded-2xl"
        style={{ aspectRatio: aspectRatio ?? 3 / 4 }}
      />
```

to:

```tsx
export function GameCardSkeleton({
  aspectRatio,
  coverHeight,
}: {
  aspectRatio?: number;
  coverHeight?: number;
}) {
  return (
    <div className={`space-y-3${coverHeight ? " flex-shrink-0" : ""}`}>
      <Skeleton
        className="rounded-2xl"
        style={
          coverHeight
            ? { height: coverHeight, aspectRatio: aspectRatio ?? 3 / 4 }
            : { aspectRatio: aspectRatio ?? 3 / 4, width: "100%" }
        }
      />
```

- [ ] **Step 2: Verify type checking passes**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/ui/skeleton.tsx
git commit -m "feat: add coverHeight support to GameCardSkeleton"
```

---

### Task 6: Migrate GameShelf

Replace inline scroll logic with `ScrollShelf` and pass `coverHeight` to `GameCard`.

**Files:**
- Modify: `web/src/features/explore/components/game-shelf.tsx`
- Modify: `web/src/features/explore/components/__tests__/game-shelf.test.tsx`

- [ ] **Step 1: Rewrite game-shelf.tsx to use ScrollShelf**

Replace the entire file content with:

```tsx
// web/src/features/explore/components/game-shelf.tsx
import type { LucideIcon } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type { Game } from "@/types/api";

interface GameShelfProps {
  title: string;
  icon?: LucideIcon;
  games: Game[] | undefined;
  isLoading: boolean;
  hideConsoleName?: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function GameShelf({
  title,
  icon,
  games,
  isLoading,
  hideConsoleName,
  onToggleFavorite,
  onTogglePlayLater,
}: GameShelfProps) {
  return (
    <ScrollShelf
      title={title}
      icon={icon}
      testId={`shelf-${title}`}
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge={!hideConsoleName}
            hideConsoleName={hideConsoleName}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 2: Update the test for the new skeleton testId**

The `ScrollShelf` skeleton uses `${testId}-skeleton` which produces `shelf-Top Rated-skeleton`. Update the loading test in `game-shelf.test.tsx`. Change:

```tsx
  it("renders loading skeleton when loading", () => {
    renderShelf({ isLoading: true, games: undefined });
    expect(
      screen.getByTestId("shelf-skeleton-Top Rated"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Top Rated", level: 2 }),
    ).toBeInTheDocument();
  });
```

to:

```tsx
  it("renders loading skeleton when loading", () => {
    renderShelf({ isLoading: true, games: undefined });
    expect(
      screen.getByTestId("shelf-Top Rated-skeleton"),
    ).toBeInTheDocument();
  });
```

Also update the shimmer test — the skeleton structure changed, so update the selector. Change:

```tsx
  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderShelf({ isLoading: true, games: undefined });
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });
```

to:

```tsx
  it("renders loading skeleton with shimmer animation", () => {
    const { container } = renderShelf({ isLoading: true, games: undefined });
    const shimmer = container.querySelectorAll("[class*='animate-pulse']");
    expect(shimmer.length).toBeGreaterThan(0);
  });
```

- [ ] **Step 3: Run tests**

Run: `cd web && npx vitest run src/features/explore/components/__tests__/game-shelf.test.tsx`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/explore/components/game-shelf.tsx web/src/features/explore/components/__tests__/game-shelf.test.tsx
git commit -m "refactor: migrate GameShelf to ScrollShelf + coverHeight"
```

---

### Task 7: Migrate Temporal Shelves

Replace the internal `ScrollShelf` with the shared one and add `coverHeight`.

**Files:**
- Modify: `web/src/features/explore/components/temporal-shelves.tsx`

- [ ] **Step 1: Replace imports and delete the internal ScrollShelf**

In `temporal-shelves.tsx`, replace the imports at the top:

```tsx
import { useRef, useState, useEffect, useCallback } from "react";
import { getReleaseYear } from "@/lib/date-utils";
import {
  ChevronLeft,
  ChevronRight,
  Calendar,
  Trophy,
  Heart,
  Clock,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Skeleton } from "@/components/ui";
```

with:

```tsx
import { getReleaseYear } from "@/lib/date-utils";
import { Calendar, Trophy, Heart, Clock } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
```

- [ ] **Step 2: Delete the entire internal `ScrollShelf` function**

Remove the `function ScrollShelf(...)` block (the function starting at `// --- Shared scroll shelf wrapper` and ending just before `// --- On This Day Shelf ---`). This is approximately lines 22–140.

- [ ] **Step 3: Update OnThisDayShelf to use shared ScrollShelf + coverHeight**

Replace the `OnThisDayShelf` render. Change the `ScrollShelf` usage and the card wrapper:

```tsx
export function OnThisDayShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: OnThisDayShelfProps) {
  const dateLabel = data?.date ?? "";

  return (
    <ScrollShelf
      title={dateLabel ? `On This Day in Gaming — ${dateLabel}` : "On This Day in Gaming"}
      subtitle="Games released on this date across the years"
      icon={Calendar}
      testId="on-this-day-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            subtitle={getReleaseYear(game.releaseDate) ? `Released ${getReleaseYear(game.releaseDate)}` : undefined}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 4: Update AnniversariesShelf**

```tsx
export function AnniversariesShelf({
  anniversaries,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: AnniversariesShelfProps) {
  return (
    <ScrollShelf
      title="Your Gaming Anniversaries"
      subtitle="Milestones from your play history"
      icon={Heart}
      testId="anniversaries-shelf"
      isLoading={isLoading}
      isEmpty={!anniversaries || anniversaries.length === 0}
    >
      {anniversaries?.map((item) => (
        <div key={`${item.game.id}-${item.yearsAgo}`} className="flex-shrink-0" role="listitem">
          <GameCard
            game={item.game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p className="text-xs text-amber-400 mt-1.5 font-medium" data-testid="anniversary-label">
            {item.yearsAgo} year{item.yearsAgo !== 1 ? "s" : ""} ago you played this
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 5: Update DecadeSpotlight**

```tsx
export function DecadeSpotlight({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: DecadeSpotlightProps) {
  return (
    <ScrollShelf
      title={data?.label ?? "Decade Spotlight"}
      subtitle={`The defining games of the ${data?.decade ?? "era"}`}
      icon={Clock}
      testId="decade-spotlight"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
```

Note: `BestOfYearSection` is a grid, NOT a carousel — leave it completely unchanged. Its `Skeleton` import is still needed for the year selector skeleton.

- [ ] **Step 6: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No type errors, all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add web/src/features/explore/components/temporal-shelves.tsx
git commit -m "refactor: migrate temporal shelves to shared ScrollShelf + coverHeight"
```

---

### Task 8: Migrate Achievement Shelves

**Files:**
- Modify: `web/src/features/explore/components/achievement-shelves.tsx`

- [ ] **Step 1: Replace imports and delete internal ScrollShelf**

Replace the imports:

```tsx
import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  Award,
  Mountain,
  Target,
  Sparkles,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Skeleton } from "@/components/ui";
```

with:

```tsx
import { Link } from "react-router-dom";
import { Award, Mountain, Target, Sparkles, Swords } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
```

Delete the internal `ScrollShelf` function (lines 25–142 approximately).

- [ ] **Step 2: Update EasyToCompleteShelf**

```tsx
export function EasyToCompleteShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: EasyToCompleteShelfProps) {
  return (
    <ScrollShelf
      title="Easy to 100%"
      subtitle="Games with the highest achievement completion rates"
      icon={Award}
      testId="easy-to-complete-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games.map((item) => (
        <div key={item.game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={item.game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p className="text-xs text-surface-400 mt-1.5" data-testid="avg-completion">
            {item.avgCompletion}% avg completion
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 3: Update HardestGamesShelf**

Same pattern — replace `ScrollShelf` usage and card wrapper. Change `className="w-40 sm:w-44 lg:w-48 flex-shrink-0"` to `className="flex-shrink-0"` and add `coverHeight={CAROUSEL_CARD_HEIGHT}` to each `GameCard`.

- [ ] **Step 4: Update AlmostDoneShelf**

Same pattern — replace wrapper class and add `coverHeight`.

- [ ] **Step 5: Update FreshChallengesShelf**

Same pattern.

- [ ] **Step 6: Update ActiveChallengesShelf**

This shelf does NOT use `GameCard` — it uses custom text cards with `w-64`. Keep the `w-64 flex-shrink-0` wrapper. Only change the `ScrollShelf` reference to the shared import:

```tsx
export function ActiveChallengesShelf({
  data,
  isLoading,
}: ActiveChallengesShelfProps) {
  return (
    <ScrollShelf
      title="Active Challenges"
      subtitle="Open challenges from the community"
      icon={Swords}
      testId="active-challenges-shelf"
      isLoading={isLoading}
      isEmpty={!data?.challenges || data.challenges.length === 0}
    >
      {data?.challenges.map((ch) => (
        <div key={ch.id} className="w-64 flex-shrink-0" role="listitem">
          <Link to={`/challenges/${ch.id}`} className="block">
            <div className="bg-surface-800 rounded-lg p-4 hover:bg-surface-700 transition-colors">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-brand-500/20 text-brand-400">
                  {ch.type}
                </span>
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-surface-600 text-surface-300">
                  {ch.difficulty}
                </span>
              </div>
              <h3 className="text-sm font-semibold text-surface-100 truncate" data-testid="challenge-name">
                {ch.name}
              </h3>
              <p className="text-xs text-surface-400 truncate mt-1">{ch.gameTitle}</p>
              <div className="flex items-center gap-3 mt-3 text-xs text-surface-400">
                <span>{ch.attemptCount} attempts</span>
                <span>{ch.completionCount} completed</span>
              </div>
            </div>
          </Link>
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 7: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No errors, all tests PASS.

- [ ] **Step 8: Commit**

```bash
git add web/src/features/explore/components/achievement-shelves.tsx
git commit -m "refactor: migrate achievement shelves to shared ScrollShelf + coverHeight"
```

---

### Task 9: Migrate Social Shelves

**Files:**
- Modify: `web/src/features/explore/components/social-shelves.tsx`

- [ ] **Step 1: Replace imports and delete internal ScrollShelf**

Replace the imports:

```tsx
import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  TrendingUp,
  Star,
  Gem,
  Radio,
  MessageSquare,
  Users,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, Badge, Skeleton } from "@/components/ui";
```

with:

```tsx
import { Link } from "react-router-dom";
import { TrendingUp, Star, Gem, Radio, MessageSquare, Users, Swords } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { Badge } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
```

Delete the internal `ScrollShelf` function.

- [ ] **Step 2: Update TrendingShelf, CommunityTopShelf, CultClassicsShelf, ActiveNowShelf**

For each of these four shelves:
- Replace `className="w-40 sm:w-44 lg:w-48 flex-shrink-0"` with `className="flex-shrink-0"`
- Add `coverHeight={CAROUSEL_CARD_HEIGHT}` to each `GameCard`

- [ ] **Step 3: Update RecentlyReviewedShelf**

This shelf uses a hybrid layout (small cover + review text) with `w-56 sm:w-60` cards. It does NOT use `GameCard` in a standard carousel way. Keep the fixed widths on the wrapper. Only change the `ScrollShelf` reference:

```tsx
export function RecentlyReviewedShelf({
  reviews,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: RecentlyReviewedShelfProps) {
  return (
    <ScrollShelf
      title="Recently Reviewed"
      subtitle="Latest reviews from your community"
      icon={MessageSquare}
      testId="recently-reviewed-shelf"
      isLoading={isLoading}
      isEmpty={!reviews || reviews.length === 0}
    >
      {reviews?.map((item) => (
        <div key={`${item.game.id}-${item.reviewerName}`} className="w-56 sm:w-60 flex-shrink-0" role="listitem">
          {/* ... existing hybrid card content unchanged ... */}
        </div>
      ))}
    </ScrollShelf>
  );
}
```

The inner card content (the `<div className="flex gap-3">` block with the small GameCard and review text) stays exactly as-is.

- [ ] **Step 4: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No errors, all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/explore/components/social-shelves.tsx
git commit -m "refactor: migrate social shelves to shared ScrollShelf + coverHeight"
```

---

### Task 10: Migrate PlayersLikeYouShelf

**Files:**
- Modify: `web/src/features/explore/components/players-like-you-shelf.tsx`
- Modify: `web/src/features/explore/components/__tests__/players-like-you-shelf.test.tsx`

- [ ] **Step 1: Rewrite to use ScrollShelf**

Replace the entire file with:

```tsx
// web/src/features/explore/components/players-like-you-shelf.tsx
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type { Game, GameSummary } from "@/types/api";

interface PlayersLikeYouShelfProps {
  games: GameSummary[] | undefined;
  isLoading: boolean;
  similarUsersCount: number;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function PlayersLikeYouShelf({
  games,
  isLoading,
  similarUsersCount,
  onToggleFavorite,
  onTogglePlayLater,
}: PlayersLikeYouShelfProps) {
  const subtitle =
    similarUsersCount > 0
      ? `Based on ${similarUsersCount} player${similarUsersCount !== 1 ? "s" : ""} with similar taste`
      : undefined;

  return (
    <ScrollShelf
      title="Players like you also enjoyed"
      subtitle={subtitle}
      testId="players-like-you-shelf"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
    >
      {games?.map((game) => (
        <div key={game.id} className="flex-shrink-0" role="listitem">
          <GameCard
            game={game}
            coverHeight={CAROUSEL_CARD_HEIGHT}
            showConsoleBadge
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 2: Update the test for new skeleton testId**

In `players-like-you-shelf.test.tsx`, the skeleton `data-testid` changes from `players-like-you-skeleton` to `players-like-you-shelf-skeleton` (because `ScrollShelf` uses `${testId}-skeleton`). Update:

```tsx
  it("shows skeleton when loading", () => {
    renderComponent({ isLoading: true });

    expect(screen.getByTestId("players-like-you-shelf-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("players-like-you-shelf")).not.toBeInTheDocument();
  });
```

Also, the subtitle `data-testid="similar-users-count"` was on the `<p>` tag in the old component. In the new version, `ScrollShelf` renders the subtitle as a plain `<p>` without that testId. Update the subtitle test to search by text instead:

```tsx
  it("shows similar users count", () => {
    const games = [makeGame({ id: "p1", title: "Super Metroid" })];

    renderComponent({ games, similarUsersCount: 12 });

    expect(screen.getByText("Based on 12 players with similar taste")).toBeInTheDocument();
  });

  it("shows singular form for 1 player", () => {
    const games = [makeGame({ id: "p1", title: "Super Metroid" })];

    renderComponent({ games, similarUsersCount: 1 });

    expect(screen.getByText("Based on 1 player with similar taste")).toBeInTheDocument();
  });
```

Remove the `data-testid="similar-users-count"` assertion.

- [ ] **Step 3: Run tests**

Run: `cd web && npx vitest run src/features/explore/components/__tests__/players-like-you-shelf.test.tsx`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/explore/components/players-like-you-shelf.tsx web/src/features/explore/components/__tests__/players-like-you-shelf.test.tsx
git commit -m "refactor: migrate PlayersLikeYouShelf to ScrollShelf + coverHeight"
```

---

### Task 11: Migrate ForYouSection

**Files:**
- Modify: `web/src/features/explore/components/for-you-section.tsx`

- [ ] **Step 1: Rewrite ForYouShelf to use ScrollShelf**

Replace the imports:

```tsx
import { useRef, useState, useEffect, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
```

with:

```tsx
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
```

- [ ] **Step 2: Replace the internal ForYouShelf component**

Replace the `ForYouShelf` function with:

```tsx
function ForYouShelf({
  row,
  onToggleFavorite,
  onTogglePlayLater,
}: {
  row: ForYouRow;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}) {
  if (!row.games || row.games.length === 0) {
    return null;
  }

  const testId = `for-you-row-${row.type}`;

  const header = (
    <div className="flex items-center gap-3 mb-5">
      {row.type === "because_you_played" && row.sourceGame?.coverUrl && (
        <img
          src={row.sourceGame.coverUrl}
          alt={row.sourceGame.title}
          className="h-8 w-6 rounded object-cover flex-shrink-0"
          data-testid="source-game-cover"
        />
      )}
      <div>
        <h3 className="text-xl font-bold text-surface-100">{row.title}</h3>
        {row.type === "expand_horizons" && row.genre && (
          <p className="text-sm text-surface-400 mt-0.5">
            Try something from <span className="text-brand-400 font-medium">{row.genre}</span>
          </p>
        )}
      </div>
    </div>
  );

  return (
    <section data-testid={testId} className="group/shelf relative">
      {header}
      <ScrollShelf
        title={row.title}
        testId={`${testId}-scroll`}
        isLoading={false}
        isEmpty={false}
      >
        {row.games.map((game) => (
          <div key={game.id} className="flex-shrink-0" role="listitem">
            <GameCard
              game={game}
              coverHeight={CAROUSEL_CARD_HEIGHT}
              showConsoleBadge
              onToggleFavorite={onToggleFavorite}
              onTogglePlayLater={onTogglePlayLater}
            />
          </div>
        ))}
      </ScrollShelf>
    </section>
  );
}
```

Note: `ForYouShelf` has a custom header (with source game thumbnail, genre subtitle) that doesn't fit `ScrollShelf`'s title pattern. So we render the header ourselves and use `ScrollShelf` only for the scroll row. The `ScrollShelf` title and heading will be present but we need to handle this — actually, since `ForYouShelf` already has its own header, we should use `ScrollShelf` without rendering its own title. The simplest approach: set `title` to an empty string and omit the icon, so `ScrollShelf` renders its `h2` as empty (effectively invisible). However, the `aria-label` on the scroll list still uses the title, which is good for accessibility.

Actually, a cleaner approach: since `ForYouShelf` has such a custom header, keep the outer `<section>` with custom header and only use the scroll mechanics. The `ScrollShelf` title is used for the `aria-label` so we still pass `row.title`. But the `h2` will be visible. Let me reconsider — `ScrollShelf` always renders a heading. For `ForYouShelf`, we need the scroll container without the heading because we render our own.

The simplest fix: wrap just the scroll part. But `ScrollShelf` is a section-level component. For `ForYouSection`, keep the inline scroll logic for now and just add `coverHeight`. This avoids forcing `ScrollShelf` to have an optional title.

Revised approach — keep the `ForYouShelf` structure but replace the scroll logic with a simpler pattern. Actually, let me just add `coverHeight` and remove the fixed-width wrappers, keeping the existing scroll logic. This component has a custom header that doesn't fit `ScrollShelf`.

Replace the file with:

```tsx
// web/src/features/explore/components/for-you-section.tsx
import { useRef, useState, useEffect, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton } from "@/components/ui";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type { ForYouRow, Game } from "@/types/api";

interface ForYouSectionProps {
  rows: ForYouRow[] | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

function ForYouSkeleton() {
  return (
    <div data-testid="for-you-skeleton" className="space-y-10">
      {Array.from({ length: 3 }, (_, i) => (
        <section key={i}>
          <div className="h-7 w-64 rounded bg-surface-800 animate-pulse mb-5" />
          <div className="flex gap-5 overflow-hidden">
            {Array.from({ length: 6 }, (_, j) => (
              <GameCardSkeleton key={j} coverHeight={CAROUSEL_CARD_HEIGHT} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function ForYouShelf({
  row,
  onToggleFavorite,
  onTogglePlayLater,
}: {
  row: ForYouRow;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    updateScrollState();
    el.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", updateScrollState);
    return () => {
      el.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", updateScrollState);
    };
  }, [updateScrollState, row.games]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (!row.games || row.games.length === 0) {
    return null;
  }

  const testId = `for-you-row-${row.type}`;

  return (
    <section data-testid={testId} className="group/shelf relative">
      <div className="flex items-center gap-3 mb-5">
        {row.type === "because_you_played" && row.sourceGame?.coverUrl && (
          <img
            src={row.sourceGame.coverUrl}
            alt={row.sourceGame.title}
            className="h-8 w-6 rounded object-cover flex-shrink-0"
            data-testid="source-game-cover"
          />
        )}
        <div>
          <h3 className="text-xl font-bold text-surface-100">{row.title}</h3>
          {row.type === "expand_horizons" && row.genre && (
            <p className="text-sm text-surface-400 mt-0.5">
              Try something from <span className="text-brand-400 font-medium">{row.genre}</span>
            </p>
          )}
        </div>
      </div>

      <div className="relative">
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${row.title} left`}
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label={`Scroll ${row.title} right`}
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label={row.title}
        >
          {row.games.map((game) => (
            <div key={game.id} className="flex-shrink-0" role="listitem">
              <GameCard
                game={game}
                coverHeight={CAROUSEL_CARD_HEIGHT}
                showConsoleBadge
                onToggleFavorite={onToggleFavorite}
                onTogglePlayLater={onTogglePlayLater}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export function ForYouSection({
  rows,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: ForYouSectionProps) {
  if (isLoading) {
    return <ForYouSkeleton />;
  }

  if (!rows || rows.length === 0) {
    return null;
  }

  return (
    <div data-testid="for-you-section" className="space-y-10">
      {rows.map((row, index) => (
        <ForYouShelf
          key={`${row.type}-${index}`}
          row={row}
          onToggleFavorite={onToggleFavorite}
          onTogglePlayLater={onTogglePlayLater}
        />
      ))}
    </div>
  );
}
```

The key changes vs the original: removed `w-40 sm:w-44 lg:w-48` from card wrappers, added `coverHeight={CAROUSEL_CARD_HEIGHT}` to `GameCard`, and updated skeleton to use `coverHeight`. The scroll logic stays because `ForYouShelf` has a custom header with a source game thumbnail and genre subtitle that doesn't fit `ScrollShelf`'s title/subtitle/icon props. Same situation as `DeveloperSpotlight` — both have custom headers with the scroll row nested inside.

- [ ] **Step 3: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No errors, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/explore/components/for-you-section.tsx
git commit -m "refactor: add coverHeight to ForYouSection game cards"
```

---

### Task 12: Migrate DeveloperSpotlight

**Files:**
- Modify: `web/src/features/explore/components/developer-spotlight.tsx`

- [ ] **Step 1: Add coverHeight import and update card wrappers**

Add the import at the top:

```tsx
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
```

In the game card mapping, change the wrapper and add `coverHeight`. Replace:

```tsx
{spotlight.topGames.map((game) => (
  <div
    key={game.id}
    className="w-40 sm:w-44 lg:w-48 flex-shrink-0"
    role="listitem"
  >
    <GameCard
      game={game}
      showConsoleBadge
      onToggleFavorite={onToggleFavorite}
      onTogglePlayLater={onTogglePlayLater}
    />
  </div>
))}
```

with:

```tsx
{spotlight.topGames.map((game) => (
  <div key={game.id} className="flex-shrink-0" role="listitem">
    <GameCard
      game={game}
      coverHeight={CAROUSEL_CARD_HEIGHT}
      showConsoleBadge
      onToggleFavorite={onToggleFavorite}
      onTogglePlayLater={onTogglePlayLater}
    />
  </div>
))}
```

Note: This component has a custom hero card layout with the scroll row nested inside it. The scroll logic stays inline because the scroll arrows need `group-hover/spotlight` (not `group-hover/shelf`). Only the card sizing changes.

- [ ] **Step 2: Verify type checking passes**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/features/explore/components/developer-spotlight.tsx
git commit -m "refactor: add coverHeight to DeveloperSpotlight game cards"
```

---

### Task 13: Migrate SeriesShelf (ScrollShelf only, no coverHeight)

**Files:**
- Modify: `web/src/features/explore/components/series-shelf.tsx`
- Modify: `web/src/features/explore/components/__tests__/series-shelf.test.tsx`

- [ ] **Step 1: Rewrite to use ScrollShelf**

Replace the entire file with:

```tsx
// web/src/features/explore/components/series-shelf.tsx
import { Link } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import type { FeaturedSeries } from "@/types/api";

interface SeriesShelfProps {
  series: FeaturedSeries[] | undefined;
  isLoading: boolean;
}

function SeriesSkeletonContent() {
  return (
    <div className="flex gap-4 overflow-hidden">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="w-56 sm:w-60 lg:w-64 h-36 flex-shrink-0 rounded-2xl" />
      ))}
    </div>
  );
}

export function SeriesShelf({ series, isLoading }: SeriesShelfProps) {
  return (
    <ScrollShelf
      title="Browse by Series"
      testId="series-shelf"
      isLoading={isLoading}
      isEmpty={!series || series.length === 0}
      loadingSkeleton={<SeriesSkeletonContent />}
    >
      {series?.map((s) => (
        <Link
          key={s.id}
          to={`/explore/series/${s.id}`}
          className="w-56 sm:w-60 lg:w-64 flex-shrink-0 rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
          role="listitem"
        >
          <div className="relative h-36 rounded-2xl overflow-hidden border border-white/[0.06] transition-all duration-300 hover:border-white/[0.12] hover:shadow-xl hover:shadow-black/30 hover:-translate-y-1 group/card">
            {s.heroUrl ? (
              <img
                src={s.heroUrl}
                alt=""
                className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 group-hover/card:scale-105"
                loading="lazy"
              />
            ) : (
              <div className="absolute inset-0 bg-gradient-to-br from-brand-900/80 via-brand-800/60 to-surface-950/80" />
            )}
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent pointer-events-none" />
            <div className="relative flex flex-col justify-end h-full p-4">
              <h3 className="text-base font-bold text-white leading-tight group-hover/card:text-brand-300 transition-colors truncate">
                {s.name}
              </h3>
              <p className="text-xs text-white/70 mt-1">
                {s.libraryGames}/{s.totalGames} {s.totalGames === 1 ? "game" : "games"}
                {s.consoleCount > 0 && (
                  <span className="text-white/50">
                    {" "}across {s.consoleCount} {s.consoleCount === 1 ? "console" : "consoles"}
                  </span>
                )}
              </p>
            </div>
          </div>
        </Link>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 2: Update the series-shelf test for new skeleton testId**

The old skeleton used `data-testid="series-shelf-skeleton"` which matches the new `${testId}-skeleton` pattern. Check the test file and update if needed. The testId should still be `series-shelf-skeleton` so this should work without changes. Verify by running the test.

- [ ] **Step 3: Run tests**

Run: `cd web && npx vitest run src/features/explore/components/__tests__/series-shelf.test.tsx`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/explore/components/series-shelf.tsx web/src/features/explore/components/__tests__/series-shelf.test.tsx
git commit -m "refactor: migrate SeriesShelf to shared ScrollShelf"
```

---

### Task 14: Migrate ArtworkShowcase (ScrollShelf only, no coverHeight)

**Files:**
- Modify: `web/src/features/explore/components/artwork-showcase.tsx`

- [ ] **Step 1: Rewrite to use ScrollShelf**

Replace the entire file with:

```tsx
// web/src/features/explore/components/artwork-showcase.tsx
import { Link } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import type { ArtworkItem } from "@/types/api";

interface ArtworkShowcaseProps {
  artworks: ArtworkItem[] | undefined;
  isLoading: boolean;
}

function ArtworkSkeletonContent() {
  return (
    <div className="flex gap-5 overflow-hidden">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="w-80 sm:w-96 flex-shrink-0">
          <Skeleton className="w-full rounded-xl" style={{ aspectRatio: "16/9" }} />
        </div>
      ))}
    </div>
  );
}

export function ArtworkShowcase({ artworks, isLoading }: ArtworkShowcaseProps) {
  const displayArtworks = artworks?.slice(0, 10);

  return (
    <ScrollShelf
      title="Artwork Showcase"
      testId="artwork-showcase"
      isLoading={isLoading}
      isEmpty={!displayArtworks || displayArtworks.length === 0}
      loadingSkeleton={<ArtworkSkeletonContent />}
      headerRight={
        <div className="flex items-center gap-3 text-sm text-surface-400">
          <Link to="/explore/gallery" className="hover:text-brand-400 transition-colors" data-testid="browse-screenshots-link">
            Browse Screenshots
          </Link>
          <span className="text-surface-700">|</span>
          <Link to="/explore/covers" className="hover:text-brand-400 transition-colors" data-testid="browse-covers-link">
            Browse Cover Art
          </Link>
        </div>
      }
    >
      {displayArtworks?.map((artwork, i) => (
        <Link
          key={`${artwork.gameId}-${i}`}
          to={`/games/${artwork.gameId}`}
          className="w-80 sm:w-96 flex-shrink-0 group/card"
          role="listitem"
          data-testid="artwork-card"
        >
          <div className="relative rounded-xl overflow-hidden">
            <div style={{ aspectRatio: "16/9" }}>
              <img
                src={artwork.url}
                alt={`Artwork for ${artwork.gameTitle}`}
                className="w-full h-full object-cover transition-transform duration-300 group-hover/card:scale-[1.03]"
                loading="lazy"
              />
            </div>
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent pointer-events-none" />
            <div className="absolute bottom-0 left-0 right-0 p-4">
              <p className="text-sm font-semibold text-white truncate">{artwork.gameTitle}</p>
              <p className="text-xs text-white/60 mt-0.5">{artwork.consoleName}</p>
            </div>
          </div>
        </Link>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 2: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No errors, all tests PASS.

- [ ] **Step 3: Commit**

```bash
git add web/src/features/explore/components/artwork-showcase.tsx
git commit -m "refactor: migrate ArtworkShowcase to shared ScrollShelf"
```

---

### Task 15: Migrate TopRatedRow

**Files:**
- Modify: `web/src/features/dashboard/components/top-rated-row.tsx`
- Modify: `web/src/features/dashboard/components/top-rated-game-card.tsx`

- [ ] **Step 1: Update TopRatedGameCard to accept and pass coverHeight**

In `top-rated-game-card.tsx`, add a `coverHeight` prop:

```tsx
import { Star } from "lucide-react";
import { CoverCard } from "@/components/cover-card";
import type { TopRatedGame } from "@/types/api";

/**
 * ROLE component — a top-rated game card with library availability.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Maps TopRatedGame domain data to CoverCard. Dimmed when the game
 * is not available in the local library.
 */
export function TopRatedGameCard({ game, coverHeight }: { game: TopRatedGame; coverHeight?: number }) {
  const isAvailable = game.localGameId != null;

  return (
    <CoverCard
      imageUrl={game.coverUrl}
      title={game.name}
      subtitle={game.consoleName}
      linkTo={isAvailable ? `/games/${game.localGameId}` : undefined}
      coverHeight={coverHeight}
    >
      <span className="flex items-center gap-0.5 text-xs text-amber-400">
        <Star className="h-3 w-3 fill-amber-400" />
        {game.igdbCriticsRating.toFixed(0)}
      </span>
      {!isAvailable && (
        <span className="text-xs text-surface-500">Not in library</span>
      )}
    </CoverCard>
  );
}
```

- [ ] **Step 2: Rewrite TopRatedRow to use ScrollShelf**

Replace the file with:

```tsx
// web/src/features/dashboard/components/top-rated-row.tsx
import { Star } from "lucide-react";
import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { useTopRatedGlobal } from "@/hooks/use-top-lists";
import { ScrollShelf } from "@/components/scroll-shelf";
import { GameCardSkeleton } from "@/components/ui";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import { TopRatedGameCard } from "./top-rated-game-card";

function TopRatedSkeletonContent() {
  return (
    <div className="flex gap-4 overflow-hidden">
      {Array.from({ length: 6 }, (_, i) => (
        <GameCardSkeleton key={i} coverHeight={CAROUSEL_CARD_HEIGHT} />
      ))}
    </div>
  );
}

export function TopRatedRow() {
  const { data: games, isLoading } = useTopRatedGlobal();

  if (!isLoading && (!games || games.length === 0)) return null;

  return (
    <ScrollShelf
      title="Top Rated"
      icon={Star}
      testId="top-rated-row"
      isLoading={isLoading}
      isEmpty={!games || games.length === 0}
      loadingSkeleton={<TopRatedSkeletonContent />}
      headerRight={
        <Link
          to="/top-lists"
          className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
        >
          View all
          <ChevronRight className="h-4 w-4" />
        </Link>
      }
    >
      {games?.map((game) => (
        <div key={`${game.rank}-${game.name}`} className="flex-shrink-0" role="listitem">
          <TopRatedGameCard game={game} coverHeight={CAROUSEL_CARD_HEIGHT} />
        </div>
      ))}
    </ScrollShelf>
  );
}
```

- [ ] **Step 3: Verify type checking and tests pass**

Run: `cd web && npx tsc --noEmit && npx vitest run`
Expected: No errors, all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/dashboard/components/top-rated-row.tsx web/src/features/dashboard/components/top-rated-game-card.tsx
git commit -m "refactor: migrate TopRatedRow to ScrollShelf + coverHeight"
```

---

### Task 16: Full Build and Test Verification

**Files:** None (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `cd web && npx vitest run`
Expected: All tests PASS. No regressions.

- [ ] **Step 2: Run the TypeScript compiler**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 3: Run the production build**

Run: `cd web && npm run build`
Expected: Build succeeds with no errors.

- [ ] **Step 4: Start the dev server and visually verify**

Run: `cd web && npm run dev`

Open the browser and verify:
1. **Explore page**: Game shelves show cards with varying widths based on cover art aspect ratios. Cards have fixed height. No cropping — full box art visible.
2. **Dashboard**: Top Rated row shows cards with varying widths.
3. **Scroll arrows**: Hover over a shelf and verify left/right arrows appear and work.
4. **Loading states**: Refresh the page and observe skeleton cards during loading.
5. **Empty states**: Shelves with no data don't render.
6. **Non-game shelves**: Series shelf, Artwork showcase, Active Challenges, Recently Reviewed all still render with their original fixed-width cards.

- [ ] **Step 5: Commit any final fixes if needed**
