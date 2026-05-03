# Game Challenges — Feature Specification

## Overview

Users create challenges from save states while playing retro games. Other users attempt those challenges — loading the exact save state and trying to complete the goal. Results are tracked on per-challenge leaderboards.

## Key Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Attempts per user | **Multiple, keep best** | Standard speedrun behavior — users should be able to retry and improve |
| 2 | Scoring model | **Time-based, server-computed** | Server records StartedAt on attempt start, CompletedAt on completion. Duration = CompletedAt - StartedAt. Client timer is display-only. Prevents spoofed times. |
| 3 | Web creation | **Yes, from existing saves** | Web users can create challenges from their own save states via a modal on the game detail page. Player app creates from live gameplay. |
| 4 | Expiration | **Optional** | `ExpiresAt` is nullable. Challenges can be evergreen or time-limited. Lazy expiration check on read. |
| 5 | Restrictions during attempts | **Save/Load/FastForward disabled** | Modified overlay during challenge mode: "Mark Complete" + "Restart" + "Give Up". No save/load/FF to keep leaderboards fair. |
| 6 | Timer pauses on overlay | **Yes** | Timer pauses when overlay is open. Friendlier UX — users aren't penalized for checking the goal. Acceptable tradeoff for V1. |
| 7 | Navigation | **Web: new sidebar item. Player: contextual only** | Web gets "Challenges" in sidebar between Activity and Relays. Player app: no new bottom nav item — challenges discovered from game detail + home screen carousel. |

## User Stories

### US-1: Create Challenge (Player App)
As a player, while playing a game, I can open the overlay and tap "Challenge" to create a challenge from my current save state. I provide a title (required), pick a type (Completion/Speedrun/Survival) and difficulty (Easy/Medium/Hard), and optionally add a description. A screenshot is auto-captured. The challenge is published immediately.

**Acceptance criteria:**
- "Challenge" button appears in emulation overlay (gated on `supportsSaveStates`)
- Hidden in netplay mode
- Auto-captures save state + screenshot on tap
- Creation form: title (required, pre-filled suggestion), type (default: Completion), difficulty (default: Medium), description (optional)
- On success: toast "Challenge created!", game resumes
- Activity event `challenge_created` emitted
- Core name stored with challenge for compatibility tracking

### US-2: Create Challenge (Web)
As a web user, I can create a challenge from one of my existing save states on a game's detail page.

**Acceptance criteria:**
- "Create Challenge" button on game detail page (in challenges section)
- Opens modal: save state picker, title, type, difficulty, description
- On success: toast + redirect to challenge detail page
- Activity event `challenge_created` emitted

### US-3: Browse Challenges
As a user, I can browse all challenges, filter by game/console/difficulty, and sort by newest/most attempted.

**Acceptance criteria:**
- Web: `/challenges` page with tabs (Popular / Recent / My Challenges), grid of ChallengeCards, filters
- Web: "Challenges" sidebar nav item with Flag icon
- Web: Dashboard page (`/`) gets a "Trending Challenges" section alongside existing sections
- Player: Challenges section on home screen (carousel of popular challenges)
- Player: "Challenges" section on game detail screen
- Both: ChallengeCard shows screenshot (16:10), title, difficulty badge, type icon, creator, attempt count
- Both: Loading skeletons, empty states per tab/section

### US-4: View Challenge Detail
As a user, I can view a challenge's full details including description, rules, game info, and leaderboard.

**Acceptance criteria:**
- Web: `/challenges/:id` page with hero (screenshot, title, description, creator, difficulty, type, game link), leaderboard section, "Attempt" CTA
- Player: ChallengeDetailScreen with same info
- Leaderboard: ranked by fastest time, gold/silver/bronze for top 3, current user highlighted
- Empty leaderboard: "No attempts yet. Be the first!"
- Creator/admin can delete challenge

### US-5: Attempt a Challenge
As a player, I can attempt a challenge. The game loads the challenge's save state, a timer starts, and I play until I tap "Mark Complete" or "Give Up."

**Acceptance criteria:**
- Tapping "Attempt" downloads challenge save state, loads game with that state
- **Attempt start transition:** Show "Loading challenge..." with progress while save downloads, then a 3-2-1 countdown overlay (reuse existing `SpCountdownOverlay` pattern) before gameplay begins. Server-side StartedAt is recorded after countdown completes.
- Two-step API: `POST /attempts/start` (server records StartedAt) → play → `POST /attempts/:id/complete` (server records CompletedAt)
- Modified overlay during attempt: "Mark Complete", "Restart", "Give Up" (no Save/Load/FF)
- Timer displayed during gameplay (display-only, server computes actual duration)
- Timer pauses when overlay is open
- **Completion result screen:** "Mark Complete" submits the attempt, then shows a celebratory result overlay with: large time display (monospace), "Your rank: #N" with rank badge styling, "New Personal Best!" if applicable, gold/silver/bronze celebration for top 3. Buttons: "View Leaderboard" / "Try Again" / "Exit"
- "Give Up" abandons attempt (status: abandoned, no leaderboard entry)
- "Restart" reloads original challenge save state, resets timer (new attempt started server-side)
- Activity events: `challenge_completed` on completion, `challenge_record` if new best

### US-6: Leaderboard
As a user, I can see who completed a challenge fastest.

**Acceptance criteria:**
- **Speedrun/Completion:** ranked by duration ascending (fastest wins). **Survival:** ranked by duration descending (longest wins)
- One entry per user (best attempt only)
- Top 3: gold/silver/bronze rank badges
- Current user's rank always visible (highlighted row)
- Time displayed as `M:SS.s` for times under 1 hour, `H:MM:SS.s` for longer. Monospace font.
- Paginated (default 50 per page)
- Real-time updates via WebSocket when new attempts complete

### US-7: Delete Challenge
As a challenge creator or admin, I can delete my challenge.

**Acceptance criteria:**
- Creator and admin/owner can delete
- Confirmation dialog
- Deletes challenge + all attempts + save file from disk
- Removes from all listings

## Data Model

### Challenge
| Field | Type | Notes |
|-------|------|-------|
| ID | uint | Primary key |
| CreatorID | uint | FK → User |
| GameID | uint | FK → Game |
| Name | string(255) | Required |
| Description | text | Optional |
| Type | string(32) | "completion", "speedrun", "survival" |
| Difficulty | string(32) | "easy", "medium", "hard" |
| Status | string(32) | "active", "closed", "expired" |
| SaveFilePath | string(1024) | Path to challenge save state |
| SaveFileSize | int64 | |
| ScreenshotPath | string(512) | Auto-captured screenshot |
| CoreName | string(128) | libretro core used |
| AttemptCount | int | Denormalized counter |
| CompletionCount | int | Denormalized counter |
| ExpiresAt | *time.Time | Optional deadline |

### ChallengeAttempt
| Field | Type | Notes |
|-------|------|-------|
| ID | uint | Primary key |
| ChallengeID | uint | FK → Challenge |
| UserID | uint | FK → User |
| Status | string(32) | "in_progress", "completed", "abandoned" |
| StartedAt | time.Time | Set by server on attempt start |
| CompletedAt | *time.Time | Set by server on completion |
| DurationMs | int64 | Server-computed: CompletedAt - StartedAt |
| IsBest | bool | True if this is the user's best attempt for this challenge |

Index: `(challenge_id, user_id, status)` for leaderboard queries.

## API Endpoints

```
POST   /api/challenges                              # Create (multipart: save file + screenshot + metadata)
GET    /api/challenges                              # List (paginated, filter: gameId, consoleId, difficulty, sort)
GET    /api/challenges/:id                          # Detail
PUT    /api/challenges/:id                          # Update metadata (creator only)
DELETE /api/challenges/:id                          # Delete (creator/admin)
GET    /api/challenges/:id/save/download            # Download save state
GET    /api/challenges/:id/screenshot               # Get screenshot

POST   /api/challenges/:id/attempts/start           # Start attempt (server records StartedAt, returns attemptId)
POST   /api/challenges/:id/attempts/:aid/complete   # Complete attempt (server records CompletedAt, computes duration)
POST   /api/challenges/:id/attempts/:aid/abandon    # Abandon attempt
GET    /api/challenges/:id/attempts/mine            # My attempts for this challenge

GET    /api/challenges/:id/leaderboard              # Ranked results (paginated)

GET    /api/games/:id/challenges                    # Challenges for a specific game
GET    /api/user/challenges                         # Challenges I created
```

## File Storage

Challenge saves stored at: `{SaveDir}/challenges/challenge_{id}/save`
Challenge screenshots at: `{SaveDir}/challenges/challenge_{id}/screenshot.png`

Separate from shared saves. Deleted when challenge is deleted.

## Activity Feed Events

- `challenge_created` — "{user} created a challenge for {game}"
- `challenge_completed` — "{user} completed {challenge} in {time}"
- `challenge_record` — "{user} set a new record on {challenge}"

## Design Language

- **Primary icon:** Flag (not Trophy — Trophy is for achievements)
- **Color accent:** Amber/gold palette for challenge-specific elements
- **Difficulty colors:** Easy=green (success), Medium=amber (warning), Hard=red (danger)
- **Type icons:** Completion=CheckCircle, Speedrun=Timer, Survival=Shield
- **Card aspect ratio:** 16:10 (wider than game covers, showcases gameplay moments)
- **Leaderboard:** Gold/silver/bronze for top 3, current user highlighted with brand accent

## Security Requirements

- Server-side timing (StartedAt/CompletedAt set by server, never trust client)
- Challenge save states immutable after creation
- Path traversal protection on file storage (sanitizeFilename, filepath.Abs checks)
- File size limit: 10MB for save states
- Rate limit: max 1 attempt start per 30 seconds per user per challenge
- Authorization: create=any user, edit/delete=creator+admin, attempt=any user on active challenges

## Out of Scope (V1)

- Score-based challenges (generic points/coins) — time-based only for V1
- Proof screenshots on attempt completion
- "Report suspicious time" feature
- Challenge comments/discussion
- Challenge tags/categories beyond type+difficulty
