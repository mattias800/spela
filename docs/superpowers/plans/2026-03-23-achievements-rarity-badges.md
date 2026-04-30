# Achievement Rarity Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add global rarity percentages to every RetroAchievement, with tier-based visual treatment (Common → Legendary) on achievement badges across player app and web.

**Architecture:** Parse `NumAwarded` and `NumDistinctPlayers` from RA's API response (already returned but not captured). Compute rarity percentage server-side, cache in existing `GameAchievementCache`, pass through to clients. Clients render tier-appropriate borders on achievement badges via a new `SpAchievementBadge` component.

**Tech Stack:** Go (server), Kotlin Multiplatform + Compose (player), React + TypeScript (web)

**Spec:** `docs/superpowers/specs/2026-03-23-achievements-phase1-design.md` (Feature 1)

---

### Task 1: Server — Parse rarity fields from RA API

**Files:**
- Modify: `server/internal/retroachievements/client.go` (lines 29-36, 161-174)

- [ ] **Step 1: Add rarity fields to Achievement struct**

Add `RarityPercent` to the `Achievement` struct (line 29):

```go
type Achievement struct {
    ID            uint    `json:"id"`
    Title         string  `json:"title"`
    Description   string  `json:"description"`
    Points        int     `json:"points"`
    BadgeURL      string  `json:"badgeUrl"`
    Type          string  `json:"type"`
    RarityPercent float64 `json:"rarityPercent"`
}
```

- [ ] **Step 2: Parse NumAwarded from RA API response**

In `GetGameInfoAndUserProgress` (line 161), add fields to the raw struct:

Top-level (add after existing fields):
```go
NumDistinctPlayersCasual   int `json:"NumDistinctPlayersCasual"`
NumDistinctPlayersHardcore int `json:"NumDistinctPlayersHardcore"`
```

Per-achievement (add to the Achievements map value struct):
```go
NumAwarded         int `json:"NumAwarded"`
NumAwardedHardcore int `json:"NumAwardedHardcore"`
```

- [ ] **Step 3: Compute rarity percentage**

After parsing achievements (around line 187), compute rarity:

```go
// Use the larger of casual/hardcore distinct players as denominator
numDistinctPlayers := raw.NumDistinctPlayersCasual
if raw.NumDistinctPlayersHardcore > numDistinctPlayers {
    numDistinctPlayers = raw.NumDistinctPlayersHardcore
}

// For each achievement, compute rarity
rarityPercent := 0.0
if numDistinctPlayers > 0 {
    rarityPercent = float64(a.NumAwarded) / float64(numDistinctPlayers) * 100.0
}
```

Set `RarityPercent` on each `Achievement` in the loop.

- [ ] **Step 4: Build and verify**

Run: `cd server && go build ./...`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
feat: parse achievement rarity data from RA API
```

---

### Task 2: Server — Write tests for rarity calculation

**Files:**
- Modify: `server/internal/api/ra_handler_test.go`

- [ ] **Step 1: Add test for rarity percentage in achievement response**

Add a test that creates a mock RA server returning `NumAwarded`, `NumAwardedHardcore`, `NumDistinctPlayersCasual`, `NumDistinctPlayersHardcore` in the achievement response. Verify that `GET /games/:id/achievements` returns achievements with `rarityPercent` calculated correctly.

Test cases:
- Normal: 50 awarded out of 1000 players → 5.0%
- Zero players: 0 distinct players → 0.0% (Common)
- All unlocked: 1000 out of 1000 → 100.0%
- Hardcore higher: use hardcore count when it's larger than casual

- [ ] **Step 2: Run tests**

Run: `cd server && go test ./internal/api/ -run TestGetGameAchievements -v`
Expected: All tests pass

- [ ] **Step 3: Commit**

```
test: add rarity percentage tests for achievement endpoint
```

---

### Task 3: Player app — Add rarityPercent to data layer

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/dto/Dtos.kt` (line 840)
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/domain/model/Models.kt` (line 520)
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/dto/DtoMappers.kt` (line 364)

- [ ] **Step 1: Add rarityPercent to DTO**

In `GameAchievementDto` (line 840), add:
```kotlin
val rarityPercent: Double = 0.0,
```

- [ ] **Step 2: Add rarityPercent to domain model**

In `GameAchievement` (line 520), add:
```kotlin
val rarityPercent: Double = 0.0,
```

- [ ] **Step 3: Update mapper**

In `GameAchievementDto.toDomain()` (line 364), add:
```kotlin
rarityPercent = rarityPercent,
```

- [ ] **Step 4: Build**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
feat: add rarityPercent to achievement data model (DTO + domain + mapper)
```

---

### Task 4: Player app — Create SpAchievementBadge component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpAchievementBadge.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.spela.player.presentation.ui.components

/**
 * CONTENT component — renders an achievement badge image with rarity-tier border.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Displays the RA badge image with a colored border based on rarity tier:
 * Common (>50%) = default, Uncommon (20-50%) = silver, Rare (5-20%) = gold,
 * Ultra Rare (1-5%) = purple/diamond, Legendary (<1%) = animated gradient.
 */
@Composable
fun SpAchievementBadge(
    badgeUrl: String?,
    rarityPercent: Double,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isUnlocked: Boolean = true,
)
```

Implementation:
- Determine tier from `rarityPercent`
- Draw border with tier-appropriate color (silver, gold, purple, or animated gradient)
- Render badge image via `SubcomposeAsyncImage` or `SpCoverArt`
- Apply `alpha(0.5f)` when `!isUnlocked`
- Fallback: show points text when no badge URL

Tier colors:
- Common: `SpColor.Divider` (default border)
- Uncommon: `Color(0xFFC0C0C0)` (silver)
- Rare: `Color(0xFFFFD700)` (gold)
- Ultra Rare: `SpColor.Primary` (indigo/purple)
- Legendary: animated gradient of `SpColor.Primary` → `Color(0xFFFFD700)` → `SpColor.Primary`

- [ ] **Step 2: Build**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat: add SpAchievementBadge component with rarity tier borders
```

---

### Task 5: Player app — Create SpRarityChip component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpRarityChip.kt`

- [ ] **Step 1: Create the component**

A small chip showing the rarity tier and percentage. Uses `SpChip` with tier-appropriate color.

```kotlin
@Composable
fun SpRarityChip(
    rarityPercent: Double,
    modifier: Modifier = Modifier,
)
```

Display: tier label + percentage (e.g. "Rare · 8.2%", "Legendary · 0.3%")
Color: matches tier (silver/gold/purple/gradient for chip background)
For Common tier: don't render (return nothing — not interesting enough to show)

- [ ] **Step 2: Build and commit**

```
feat: add SpRarityChip component
```

---

### Task 6: Player app — Update AchievementCard to use SpAchievementBadge

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/gamedetail/AchievementComponents.kt` (lines 44-97)

- [ ] **Step 1: Update AchievementCard**

The existing `AchievementCard` composable (line 44) accepts `GameAchievement` and `isUnlocked`. Replace the current badge placeholder (colored circle with points text) with `SpAchievementBadge`:

```kotlin
SpAchievementBadge(
    badgeUrl = achievement.badgeUrl,
    rarityPercent = achievement.rarityPercent,
    isUnlocked = isUnlocked,
    size = 48.dp,
)
```

Add `SpRarityChip` below the title when unlocked:
```kotlin
if (isUnlocked && achievement.rarityPercent > 0) {
    SpRarityChip(rarityPercent = achievement.rarityPercent)
}
```

- [ ] **Step 2: Update TimelineEntryRow**

In `TimelineEntryRow` (line 103), replace the badge display with `SpAchievementBadge` using the timeline entry's data. Note: `AchievementTimelineEntry` doesn't have `rarityPercent` yet — for Phase 1, timeline entries show badges without rarity borders. This can be enhanced later by adding `rarityPercent` to the timeline response.

- [ ] **Step 3: Build and run**

Run: `cd player && ./gradlew :desktop:run`
Verify: Navigate to a game with achievements → badges show rarity borders

- [ ] **Step 4: Commit**

```
feat: use SpAchievementBadge in achievement grid with rarity borders
```

---

### Task 7: Web app — Add rarityPercent to types and AchievementCard

**Files:**
- Modify: `web/src/types/api.ts` (line 271)
- Modify: `web/src/features/game-detail/components/achievement-card.tsx` (lines 13-50)

- [ ] **Step 1: Add rarityPercent to Achievement interface**

In `web/src/types/api.ts`, add to the `Achievement` interface:
```typescript
rarityPercent: number;
```

- [ ] **Step 2: Update AchievementCard component**

Add rarity tier border styling based on `achievement.rarityPercent`:
- Determine tier from percentage
- Apply border color class (Tailwind): `border-gray-500` (common), `border-gray-300` (silver/uncommon), `border-yellow-400` (gold/rare), `border-purple-500` (ultra rare), gradient border for legendary
- Show rarity text below badge: "X.X% of players"

- [ ] **Step 3: Build and verify**

Run: `cd web && npm run build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```
feat: add rarity badges to web achievement cards
```

---

### Task 8: Desktop E2E tests for rarity badges

**Files:**
- Modify: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/GameDetailAchievementsTest.kt`

- [ ] **Step 1: Update test fixtures**

Add `rarityPercent` to the achievement test fixtures used in the test harness.

- [ ] **Step 2: Add test for rarity badge rendering**

Test that achievement cards render with the correct rarity treatment:
- Achievement with rarityPercent = 3.0 (Ultra Rare) should have the badge visible
- Achievement with rarityPercent = 75.0 (Common) should render without special border

- [ ] **Step 3: Run tests**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All tests pass

- [ ] **Step 4: Commit**

```
test: add desktop E2E tests for rarity badge rendering
```
