# Achievements Phase 1: Make Achievements Feel Valuable

**Date:** 2026-03-23
**Status:** Draft

## Problem

RetroAchievements in Spela are functional but not fun. You unlock them, they show up in a list, there's a progress bar. The experience is a checklist — there's no excitement, no bragging rights, no sense of accomplishment beyond a checkbox.

## Goal

Make every achievement unlock feel like a moment worth celebrating, and give players a reason to pursue rare achievements and show them off.

## Design

Three features that reinforce each other: rarity makes achievements feel valuable → celebrations make the moment exciting → showcase lets you display your prizes.

### Feature 1: Rare Achievement Badges

Every achievement gets a rarity tier based on global RetroAchievements.org unlock data.

**Rarity Tiers:**

| Tier | Unlock % | Visual Treatment |
|---|---|---|
| Common | > 50% | Default badge, no special styling |
| Uncommon | 20–50% | Subtle silver border |
| Rare | 5–20% | Gold border with soft glow |
| Ultra Rare | 1–5% | Diamond/purple border with shimmer |
| Legendary | < 1% | Animated gradient border with star icon |

**Data Source:**

RA's `API_GetGameInfoAndUserProgress` endpoint returns per-achievement fields that we **do not currently parse**. The raw struct in `retroachievements/client.go` (line ~164) needs these fields added:

Per-achievement (in the `Achievements` map):
- `NumAwarded` (int) — total times this achievement has been unlocked across all RA users
- `NumAwardedHardcore` (int) — total hardcore unlocks

Game-level (top-level response):
- `NumDistinctPlayersCasual` (int) — total unique players who played this game
- `NumDistinctPlayersHardcore` (int) — total unique hardcore players

**Rarity formula:** `rarityPercent = (numAwarded / numDistinctPlayers) * 100`
- `numAwarded` = per-achievement `NumAwarded` field
- `numDistinctPlayers` = game-level `max(NumDistinctPlayersCasual, NumDistinctPlayersHardcore)`
- Edge case: if `numDistinctPlayers == 0`, treat as Common (no data available)

**Server Changes:**
- Add `NumAwarded`, `NumAwardedHardcore` to the per-achievement raw struct in `GetGameInfoAndUserProgress`
- Add `NumDistinctPlayersCasual`, `NumDistinctPlayersHardcore` to the top-level raw struct
- Compute `rarityPercent` per achievement and include in the `Achievement` struct
- Store in `GameAchievementCache.AchievementJSON` alongside existing fields
- Return `rarityPercent` in the API response from `GET /games/:id/achievements`
- This is an additive API change — older player app versions will ignore the new field

**Player App Changes:**
- Add `rarityPercent: Double` to `GameAchievement` domain model, DTO, and mapper
- Achievement cards (`AchievementComponents.kt`) render colored borders based on tier
- Show rarity as small text below the badge: "2.3% of players"
- Sort/filter option: sort by rarity ascending ("rarest first")

**Web App Changes:**
- Add `rarityPercent` to TypeScript `Achievement` interface
- `AchievementCard` component renders tier-appropriate border styling
- Rarity text shown below badge

### Feature 2: Beautiful Unlock Celebrations

Enhanced in-game overlay when achievements unlock, with celebration intensity matching rarity.

**Celebration Tiers:**

| Achievement Rarity | Animation |
|---|---|
| Common (> 50%) | Badge slides in from right, gentle bounce, title + points text |
| Uncommon (20–50%) | Same slide-in + subtle golden pulse on the badge |
| Rare (5–20%) | Badge entrance with golden particle burst, brief screen-edge glow |
| Ultra Rare (1–5%) | Enhanced particles, diamond shimmer |
| Legendary (< 1%) | Full dramatic entrance — badge scales up from center with radial light burst |
| 100% Game Complete | Full-screen confetti, "100% COMPLETE" banner, total points display, 3-second hold |

**Rarity Enrichment Path:**

The native rcheevos C bridge emits `AchievementEvent` via JNI. Currently it carries `title`, `description`, `points`, and `type` but **no achievement ID**. To look up rarity:

1. **Add `achievementId: Long` to `AchievementEvent`** — requires changes to the C bridge (`libretro_achievements.c`) and JNI interface on both Android and Desktop to pass the RA achievement ID with each event.
2. **Player app caches achievement data** — when a game loads, `GET /games/:id/achievements` is called and the response (including `rarityPercent`) is cached in memory.
3. **Kotlin enrichment** — when an `AchievementEvent` arrives with an `achievementId`, the Kotlin layer looks up the cached rarity. No need to pass rarity through the C bridge.

**Implementation:**
- Enhance existing `SecondaryAchievementCelebration` composable
- Particle effects: use Compose Canvas animations (no external library needed)
- Confetti for 100% completion: reusable `SpConfetti` composable
- Sound effects: **deferred to a future iteration** — visual celebrations first

**Key constraint:** Celebrations must not interrupt gameplay. They overlay on the existing in-game UI and auto-dismiss after 2-4 seconds (longer for rarer achievements). Must handle rapid successive unlocks (queue with auto-dismiss timers).

### Feature 3: Achievement Showcase on Profile

Users pin their proudest achievements to their public profile.

**User Experience:**
- New "Featured Achievements" section on the user profile page, between stats and game lists
- Shows 3–5 pinned achievements in a prominent horizontal layout
- Each showcased achievement displays: large badge image, achievement title, game title, points, rarity tier badge, unlock date
- "Edit Showcase" button (visible only on own profile) opens a picker dialog
- Picker shows all unlocked achievements sorted by rarity (rarest first), searchable by game/title
- Reorder via up/down buttons in the picker (drag-to-reorder is complex in Compose — defer to future polish)

**Server Changes:**
- New `UserAchievementShowcase` model:
  ```
  ID              uint (primarykey)
  UserID          uint (index)
  AchievementRAID uint
  RAGameID        uint
  ShowcaseOrder   int
  CreatedAt       time.Time
  ```
  Unique constraint on `(UserID, AchievementRAID)`. Max 5 entries per user (enforced server-side).
- New endpoints:
  - `GET /user/achievements/showcase` — returns current user's showcased achievements
  - `PUT /user/achievements/showcase` — accepts ordered list of `{ achievementRAId, raGameId }`, replaces all entries. Standard auth + rate limiting applies.
  - `GET /users/:id/achievements/showcase` — returns a public user's showcased achievements
- **Enrichment:** The response parses `GameAchievementCache.AchievementJSON` (a JSON blob, not a SQL join) to find each showcased achievement by RA ID. This requires a Go-level JSON parse per request, which is fine at max 5 entries.
- **Cache miss fallback:** If the `GameAchievementCache` entry for a showcased achievement's game is expired or missing, return the entry with only `achievementRAId`, `raGameId`, and `showcaseOrder` — omit title/badge/rarity. The client shows a placeholder. A background refresh of the cache is triggered.

**Player App Changes:**
- Add `showcasedAchievements` to `PublicProfile` domain model and DTO
- New `AchievementShowcaseSection` composable — horizontal row of large achievement cards with rarity borders
- "Edit Showcase" dialog with achievement picker (filterable, sortable by rarity)
- Integrate into `UserProfileScreen` between stats and game sections

**Web App Changes:**
- New `AchievementShowcase` component on user profile page
- Edit dialog with reordering
- Same visual treatment as player app

### Shared UI Components (Design System)

New components following Design → Content → Role hierarchy:

- **`SpAchievementBadge`** — CONTENT component. Renders an achievement badge image with rarity-tier border styling. Accepts `badgeUrl`, `rarityPercent`, `size`. Used by achievement cards, showcase, and celebration overlay.
- **`SpRarityChip`** — CONTENT component. Small chip showing the rarity tier label and percentage. Composes `SpChip` with tier-appropriate color. Role wrappers can map domain models to this if needed.
- **`SpConfetti`** — DESIGN component. Reusable confetti particle animation overlay. Canvas-based.
- **`SpAchievementShowcaseCard`** — CONTENT component. Large achievement card for the profile showcase. Composes `SpAchievementBadge` + title + game + points + date.

### Navigation & Data Flow

```
Achievement unlocks during gameplay (native rcheevos)
  → AchievementEvent emitted with achievementId (new)
  → Kotlin layer enriches with rarityPercent from cached achievement data
  → SecondaryAchievementCelebration renders tier-appropriate animation
  → Progress syncs to server

User visits game detail → achievements section
  → GET /games/:id/achievements (now includes rarityPercent per achievement)
  → Achievement cards render with rarity borders via SpAchievementBadge

User visits profile
  → GET /users/:id/profile (existing)
  → GET /users/:id/achievements/showcase (new)
  → AchievementShowcaseSection renders pinned achievements

User edits showcase
  → Picker dialog loads all unlocked achievements (from cached progress data)
  → User selects up to 5, reorders via up/down buttons
  → PUT /user/achievements/showcase
```

## Scope

### In Scope
- Rarity calculation from RA API data (global, not per-server)
- Rarity tier visual treatment on achievement badges (player + web)
- Enhanced unlock celebration animations (player app only)
- 100% game completion celebration (player app only)
- Achievement showcase on user profile (player + web)
- Showcase editing (picker + reorder)
- New shared UI components (SpAchievementBadge, SpRarityChip, SpConfetti, SpAchievementShowcaseCard)
- Adding `achievementId` to `AchievementEvent` (C bridge + JNI)

### Out of Scope (Future Phases)
- Achievement Races (Phase 2: Social & Competitive)
- Weekly Achievement Spotlight (Phase 2)
- Social Achievement Feed (Phase 2)
- Head-to-Head Comparison (Phase 2)
- Shareable Achievement Cards (Phase 3: Sharing)
- Meta-Achievements / Spela Badges (Phase 3: Progression)
- Achievement XP & Leveling (Phase 3)
- Game recommendations based on achievements (Phase 4: Discovery)
- Sound effects for unlock celebrations (deferred — visual first)
- Drag-to-reorder in showcase picker (deferred — up/down buttons for Phase 1)

## Testing

- **Server:** Unit tests for rarity calculation (including edge cases: 0 players, missing data), showcase CRUD endpoints, max-5 enforcement, cache miss fallback
- **Player desktop E2E:** Rarity badge rendering on achievement cards, celebration animation display (verify composable renders), showcase section on profile, showcase editing flow
- **Web:** Achievement card rarity styling, showcase display and editing

## Technical Notes

- RA API rate limiting: the existing 24h cache on `GameAchievementCache` is sufficient. Rarity data updates with the same refresh cycle.
- Showcase entries reference `AchievementRAID` + `RAGameID`, not our internal IDs, since achievements come from RA.
- The celebration composable must handle rapid successive unlocks (queue with auto-dismiss timers).
- Adding `rarityPercent` to the API response is a non-breaking additive change — older app versions will ignore it.
- The `ShowcaseOrder` field on `UserAchievementShowcase` is distinct from `displayOrder` on `GameAchievement` (which controls the RA-defined ordering of achievements within a game).
