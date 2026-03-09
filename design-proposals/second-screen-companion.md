# Second Screen Companion Experience

## Status: Phase 1 + Phase 2 + Phase 3 Complete

**Date:** 2026-03-09
**Contributors:** Product Owner, Android Developer, UI/UX Agent

---

## Problem Statement

When playing single-screen games (NES, SNES, GBA, etc.) on dual-screen Android devices,
the secondary screen currently shows touch gamepad controls, a game info bar, quick actions
(save/load/screenshot/fast forward), and FPS metrics.

On devices with built-in gamepads (like the AYN Thor clamshell handheld), the touch controls
are dead space. The second screen could be a genuinely valuable companion to gameplay instead
of wasted real estate.

The touch controls must remain available as an option for devices without physical gamepads.

---

## Current Implementation

**File:** `SecondaryScreenContent.kt`

The secondary screen currently shows three fixed sections for single-screen games:

1. **Game Info Bar** — Game title + elapsed session time
2. **Touch Gamepad Controls** — Platform-specific virtual button layout
3. **Quick Action Bar** — Save, Load, Screenshot, Fast Forward buttons + FPS/frame time
4. **OLED Burn-in Protection** — Fades to black after 15 seconds of inactivity

For dual-screen games (DS/3DS), the secondary screen renders the bottom screen with touch input.

**Available data during gameplay** (`EmulationState`):
- `fps`, `frameTime` — performance metrics
- `gameTitle`, `sessionElapsedSeconds` — session info
- `isRunning`, `isPaused`, `isFastForward` — game state
- `selectedShader` — current video filter
- `hasCheats`, `enabledCheatCount`, `cheats: List<Cheat>` — cheat state
- `challengeId`, `challengeObjective`, `challengeElapsedMs` — challenge mode
- `netplayPeerUsername`, `netplayPeerLatencyMs` — netplay state
- `achievementEvent` — RetroAchievements events
- `activeSlot` — current quick-save slot

**Additional data accessible** (from repositories/API):
- Game metadata (console, description, cover art, release year, genre, developer, publisher)
- Save states with screenshot thumbnails
- Play history (total play time, sessions)
- RetroAchievements (full achievement list, progress, unlock status)
- Online users and what they're playing
- Challenge leaderboards

---

## Core Design: Swipeable Pages with Persistent Header

Replace the single fixed layout with **swipeable pages** behind a persistent header.
A page indicator (dot row) at the bottom shows which page is active.

```
+------------------------------------------------------+
| * Super Mario World              01:23:45       SNES |  <- Persistent header
+------------------------------------------------------+
|                                                      |
|                                                      |
|              [ Swipeable Page Content ]               |
|                                                      |
|                                                      |
+------------------------------------------------------+
|                    *  o  o  o                        |  <- Page indicator dots
+------------------------------------------------------+
```

A mode selector in settings (or long-press on second screen) lets users pick their
default page. Devices with gamepads default to Art/Dashboard; without to Controls.

### Persistent Header

- Game title + session timer + console badge
- Thin 2dp gradient line at the top using `getConsoleGradient()` for console identity
- Adapts contextually: shows "PAUSE" when paused, peer info during netplay, etc.

### OLED Burn-in Protection

**Fade to black after 15 seconds of inactivity** — the current approach, preserved as-is.
Burn-in protection is non-negotiable. We will NOT use pixel drift or any approach that
risks burn-in on user devices. On touch, the screen wakes instantly.

---

## Page 1: Art Display (SteamGridDB Integration)

**The default page for devices with gamepads.** A visually stunning ambient display
featuring high-quality game artwork from SteamGridDB.

### SteamGridDB Art Types

SteamGridDB is a community-driven database of game artwork with four asset types:

| Art Type | Description | Use on Second Screen |
|----------|-------------|---------------------|
| **Hero** | Wide banner artwork (typically ~1920x620). Cinematic, often featuring the game's key art with logo. | Primary display — fills the screen beautifully in landscape |
| **Grid** | Vertical cover art (~600x900) or horizontal (~920x430). Box art style. | Fallback when no hero available, or as a centered element |
| **Logo** | Transparent PNG of the game's logo/title treatment. | Overlay on top of hero art, or standalone with dark background |
| **Icon** | Square game icon (~512x512). | Small accent element, not primary use |

**Hero images are the star here.** A high-quality hero banner filling the secondary screen
with the game's cinematic art, overlaid with a subtle session timer, would look incredible
on the AYN Thor's OLED.

### Layout Concept

```
+------------------------------------------------------+
|                                                      |
|           [  Hero Banner - Full Bleed  ]             |
|           [  Beautiful cinematic art   ]             |
|           [  from SteamGridDB          ]             |
|                                                      |
|                         Super Mario World            |
|                            01:23:45                  |
+------------------------------------------------------+
|                    *  o  o  o                        |
+------------------------------------------------------+
```

- Hero image fills the content area edge-to-edge with slight vignette darkening at bottom
- Game title + timer overlaid at bottom with text shadow for readability
- If no hero available, fall back to centered grid/cover art with ambient glow (extract
  dominant color with Android `Palette` API)
- Console gradient accent line in header provides system identity

### SteamGridDB Integration Details

**API:** REST API v2, requires API key (free registration)

**Server-side integration:**
- New server config: SteamGridDB API key (stored in `ServerSetting`, configured in admin)
- New endpoint: `GET /api/games/:id/artwork` returns available art URLs
- Server caches artwork URLs/images to avoid repeated API calls
- Search by game name + platform to find matches
- Store artwork references in DB, link to games during scraping or on-demand

**Matching strategy:**
- Search SteamGridDB by game name + platform during metadata scraping (alongside existing
  LibRetro Thumbnails and ScreenScraper)
- Allow manual matching in web admin for games that don't auto-match
- Cache hero/grid/logo URLs per game in a new `GameArtwork` table

**Fallback chain:**
1. SteamGridDB hero image (preferred)
2. SteamGridDB grid/cover art
3. Existing cover art from ScreenScraper/LibRetro Thumbnails
4. Console-colored background with game title text

**Player app changes:**
- New field on `GameDetail`: `heroUrl`, `logoUrl` (optional, from server API)
- `EmulationState` carries `heroUrl` for the secondary screen
- Coil/image loading for the artwork on the secondary display

---

## Page 2: Dashboard

The information-rich page. Designed like Apple Watch complications — dense but glanceable
data in a card grid with quick actions.

```
+------------------------------------------------------+
| * Super Mario World              01:23:45       SNES |
+------------------------------------------------------+
|                                                      |
|  +-------------+  +-------------+  +-------------+  |
|  |   60 FPS    |  |  Slot 3     |  |  CHEATS     |  |
|  |  16.7ms     |  |  _ _ X _ _  |  |   2 active  |  |
|  +-------------+  +-------------+  +-------------+  |
|                                                      |
|  +-------+ +-------+ +-------+ +-------+ +-------+  |
|  | Save  | | Load  | | Shot  | | Fast  | |Rewind |  |
|  +-------+ +-------+ +-------+ +-------+ +-------+  |
|                                                      |
|                    o  *  o  o                        |
+------------------------------------------------------+
```

### Stat Cards (top row)

Three cards showing glanceable info. Tap a card to cycle its content:

- **FPS Card**: FPS only -> FPS + frame time -> FPS + frame time sparkline graph
- **Slot Card**: Shows active slot (1-10) as a visual strip. Tap to cycle active slot.
- **Cheats Card**: Shows enabled count. Tap to open cheat toggle panel.

### Quick Action Row (bottom)

Same actions as current `QuickActionBar` but in a single horizontal row:
Save, Load, Screenshot, Fast Forward, Rewind

### Cheat Toggle Panel

When cheats card is tapped, a bottom sheet slides up showing the full cheat list.
Each cheat has a toggle switch. Cheats can be toggled on/off **without pausing the game**.
This is a genuine workflow improvement — e.g., toggle infinite lives on when stuck, off
when you want to play "for real."

### Shader Quick-Switch

Add a shader icon to the action row or as a fourth stat card. Tap to cycle through the
6 shader presets instantly without opening any menu.

---

## Page 3: Save States

Visual save state management with screenshot thumbnails.

```
+------------------------------------------------------+
| * Super Mario World              01:23:45       SNES |
+------------------------------------------------------+
|  Save Slots                                          |
|  +--------+ +--------+ +--------+ +--------+        |
|  | [img]  | | [img]  | | [ 3 ]  | | [img]  |  ...  |
|  | Slot 1 | | Slot 2 | | Slot 3 | | Slot 4 |        |
|  | 0:12   | | 0:45   | | ACTIVE | | 1:02   |        |
|  +--------+ +--------+ +--------+ +--------+        |
|                                                      |
|  Tap to select  |  Swipe up: save  |  Hold: load    |
|                                                      |
|                    o  o  *  o                        |
+------------------------------------------------------+
```

- Horizontal scrollable row of save slot cards
- Each card shows: screenshot thumbnail, slot number, session timestamp
- Active slot highlighted with `SpColor.Primary` border
- Empty slots show placeholder with "+" icon

### Gestures

| Gesture | Action | Feedback |
|---------|--------|----------|
| Tap a slot | Select as active slot | Border highlight changes |
| Swipe up on active slot | Quick save | Green flash confirmation |
| Long-press a filled slot | Load from that slot | Blue flash + confirmation dialog |
| Long-press empty slot | Save to that slot | Green flash confirmation |

### Data Source

- `SaveState` model already has `screenshotUrl`, `createdAt`, `slot`
- `EmulationState` already has `activeSlot`
- `SaveManager` handles save/load operations
- Need to fetch save state list at game start and keep it updated

---

## Page 4: Controls (Existing)

The current touch gamepad layout, preserved as-is for devices without physical gamepads.

**Enhancement:** Also support mapping to Player 2 input. The input system already supports
8 ports. A toggle at the top of the controls page: "Player 1 / Player 2" — this turns
the second screen into a P2 controller for local multiplayer.

---

## Additional Features (Cross-Page)

### Live Achievement Tracker

A persistent achievement overlay or dedicated sub-section (accessible from Dashboard).

- Compact list of RetroAchievements with unlock status and progress bars
- In-progress achievements highlighted, unlocked ones show checkmark
- When an achievement unlocks during gameplay, animated celebration on second screen
- Achievement unlock feed: scrolling log of session unlocks with badges and timestamps

**Data:** `AchievementsRepository`, `GameAchievement`, `AchievementProgress` models exist.
Need to wire `achievementsRepository.getGameAchievements()` into secondary screen.
May need new JNI to query `rc_client_get_achievement_list()` for full progress data.

### Input Visualization

Real-time display of which buttons are pressed. A visual controller graphic that
lights up with actual inputs.

- Console-themed layouts (SNES pad vs N64 vs PlayStation)
- For fighting games: input history log showing last N inputs as combo notation
- `nativeGetInputButton(port, id)` already exists in JNI — 20 calls at 30fps is negligible

Could be a Dashboard sub-mode or its own swipeable page.

### Performance Dashboard (Power User)

Expanded performance view beyond the stat card:

- FPS graph: rolling 60-second line chart (ring buffer of 120 frame times)
- Frame time distribution: target line at 16.67ms, min/max/avg
- Audio buffer utilization bar (needs new JNI: `nativeGetAudioBufferUtilization()`)
- Current shader name, rewind buffer status
- Core variable display (needs new JNI: `nativeGetCoreVariables()`)

### Context-Aware State Transitions

The screen adapts automatically based on game state:

**Paused:** Header shows "PAUSE" in warning color, timer pulses. Content dims.
Tap anywhere to resume.

**Challenge Mode:** Dashboard transforms — large challenge timer, objective text,
complete/restart/give up actions. Challenge leaderboard position if available.

**Netplay:** Header shows peer username + latency. Dashboard includes ping sparkline
graph, connection quality indicator, who-paused status.

**Save/Load performed:** Transient toast notification slides in (2 seconds),
overlaying current page. Green for save, blue for load.

### Who's Online

Compact display of other users on the server and what they're playing.
`PresenceService` exists, needs periodic polling during gameplay. Could be a
Dashboard card or sub-section.

### Game Info Card

Game metadata display: cover art, description, developer, publisher, release year,
genre, community rating. "Back of the box" feel.

All data available from `GameDetail` model. Nice for the Focus/Art page as
supplementary info below the hero image.

---

## Technical Feasibility Notes (Android Developer)

### Easy (existing data, pure Compose UI)

| Feature | Notes |
|---------|-------|
| Swipeable pages | `HorizontalPager` from Compose Foundation |
| Dashboard stat cards | Existing `EmulationState` fields |
| Quick action row | Existing `EmulationActionButton` components |
| Shader quick-switch | 6 `ShaderPreset` enum values |
| Input visualization | `nativeGetInputButton` already in JNI |
| Last frame on pause | `frameBitmap` StateFlow persists after pause |
| Ambient color extraction | Android `Palette` API on `frameBitmap` |
| Player 2 controls | Input system supports 8 ports |
| Performance graphs | Ring buffer + Compose Canvas |

### Medium (needs new data wiring or JNI)

| Feature | Notes |
|---------|-------|
| Save state thumbnails | Server screenshots exist, need API call during gameplay |
| Achievement tracker | Need JNI to query rcheevos achievement list |
| SteamGridDB integration | New server endpoints, API key config, DB table |
| Cheat toggling | Need new `EmulationIntent` for per-cheat toggle |
| Who's online | Need periodic API polling during gameplay |
| Challenge leaderboard | Need to fetch leaderboard at challenge start |
| RAM viewer | New JNI `nativeReadMemory()`, ~10 lines of C |

### Performance Considerations

- Reading game memory: negligible (direct pointer, achievements already do it every frame)
- Frame sharing (software cores): zero overhead (`frameBitmap` already generated)
- Frame sharing (GPU cores): `PixelCopy` at 5-10fps adds ~1ms per capture
- Input polling (20 JNI calls at 30fps): negligible
- Palette color extraction: run every 0.5s on background thread, no impact

---

## SteamGridDB Server Integration Plan

### New Database Model

```go
type GameArtwork struct {
    ID           uint   `gorm:"primaryKey"`
    GameID       uint   `gorm:"index;not null"`
    SteamGridID  int    // SteamGridDB game ID for future lookups
    HeroURL      string // Hero/banner art URL (wide, cinematic)
    GridURL      string // Vertical cover art URL
    LogoURL      string // Transparent logo URL
    IconURL      string // Square icon URL
    Source       string // "steamgriddb"
    CachedLocally bool  // Whether images are cached on server filesystem
    CreatedAt    time.Time
    UpdatedAt    time.Time
}
```

### New Server Settings

- `steamgriddb_api_key` — API key for SteamGridDB v2 API
- Configured in admin settings panel alongside existing scraper credentials

### New API Endpoint

```
GET /api/games/:id/artwork
Response: { heroUrl, gridUrl, logoUrl, iconUrl }
```

### Scraper Integration

- During metadata scraping, also query SteamGridDB by game name + platform
- SteamGridDB has excellent retro game coverage (community-contributed)
- Cache downloaded images on server filesystem (like existing cover art)
- Allow manual SteamGridDB matching in web admin for unmatched games

### Player App Changes

- `GameDetail` gains optional `heroUrl`, `logoUrl` fields
- `EmulationState` carries `heroUrl` for secondary screen
- Image loading via Coil on the secondary display Compose tree

---

## Priority & Phasing

### Phase 1: Foundation + Art Display

**Goal:** Make the second screen beautiful and useful out of the box.

1. Swipeable page infrastructure (`HorizontalPager` + page dots)
2. Persistent header with console gradient accent
3. **Art Display page** (SteamGridDB heroes) — the wow factor
4. Dashboard page (stat cards + quick actions)
5. Controls page (existing, preserved)
6. Fade-to-black burn-in protection (preserved)
7. Mode selector in settings (default page per device)

**Backend:** SteamGridDB API integration, `GameArtwork` model, artwork endpoint.

### Phase 2: Save State Management

8. Save Slots page with visual thumbnails
9. Quick slot switcher on Dashboard
10. Save/Load gestures (swipe up to save, hold to load)

**Backend:** Expose save state list during gameplay (endpoint exists, need new data flow).

### Phase 3: Achievements & Cheats

11. Live Achievement Tracker (Dashboard sub-section or overlay)
12. Cheat toggle panel (tap cheats card on Dashboard)
13. Achievement unlock animations

**Backend:** None. JNI additions for rcheevos queries.

### Phase 4: Social & Context

14. Challenge Mode dashboard transformation
15. Netplay peer info with latency graph
16. Who's Online display
17. Context-aware state transitions (pause, save confirmation toasts)

### Phase 5: Power User Features

18. Input visualization with combo history
19. Performance dashboard with frame time graphs
20. Player 2 controls toggle
21. Game Info Card with metadata

### Future Ideas (Backlog)

- Widget-based custom layout (Stream Deck style) — ship presets first, learn what users want
- RAM Watch / Cheat Search — read game memory for game-specific info
- Auto-Save Timeline — visual scrubber through auto-save history
- Frame mirroring / mini-map view
- Ambient color extraction from game frame for background tint

---

## Design System Notes

- Use `SpColor`, `SpSpacing`, `SpTypography` tokens throughout
- Dark theme primary — minimal bright pixels for OLED
- Console gradient accents via `getConsoleGradient()`
- Stat cards: `SpColor.Card` background, `SpSpacing.CardCornerRadius` (16dp)
- Action buttons: existing `EmulationActionButton` at 48dp
- Text minimum: `SpTypography.LabelSmall` (10sp) — nothing smaller on 3.92" screen
- Most-used actions in bottom-left/bottom-right thumb zones
- Page transitions: standard `HorizontalPager` slide animation
- Toast notifications: `SpColor.SuccessContainer` background, 2-second duration
- Touch latency: generous gesture windows (500ms long-press, 200ms swipe)

---

## Inspiration Sources

- **Nintendo DS** — Bottom screen as companion info/controls
- **Wii U GamePad** — Companion display for main screen gameplay
- **Elgato Stream Deck** — Customizable quick-action grid
- **Car instrument clusters** — Glanceable info while focus is elsewhere
- **Apple Watch complications** — Dense, glanceable data widgets
- **Xbox achievement sidebar** — Session unlock feed
- **RetroArch** — Performance overlay, shader switching
