# Save State Storage Management

## Problem

Save state sizes vary dramatically by system (NES: ~50KB, N64: ~16MB, PS2: ~40MB). The server enforces a per-user storage quota (default 1024 MB), but:

1. **No visibility** — users have no way to see their storage usage or the quota limit.
2. **Silent failures** — upload failures were silently swallowed (fixed: `expectSuccess = true` on Ktor client).
3. **No management tools** — users can't delete old save states to free space.
4. **No smart pruning** — the server keeps up to 5 auto-saves per session regardless of save size, so a PS2 game burns 200 MB on auto-saves alone.

## Proposed Solution

### 1. Smart Auto-Save Retention (Server)

Scale the number of retained auto-saves by save state size:

| Save size | Max auto-saves per session |
|-----------|---------------------------|
| < 1 MB    | 5 (current default)       |
| 1-10 MB   | 3                         |
| > 10 MB   | 1                         |

Applied during the existing prune step in `UploadAutoSave` (`session_handler.go:738-748`). The threshold could also be configurable per-console on the server if needed.

### 2. Storage Usage Visibility (Web + Player)

**New API endpoint:** `GET /api/user/storage`
```json
{
  "usedBytes": 1075575533,
  "quotaBytes": 1073741824,
  "byConsole": [
    { "consoleId": "ps2", "consoleName": "PlayStation 2", "bytes": 520000000, "saveCount": 13 },
    { "consoleId": "n64", "consoleName": "Nintendo 64", "bytes": 64000000, "saveCount": 4 },
    ...
  ],
  "bySession": [
    { "sessionId": 87, "gameName": "SSX", "consoleName": "PS2", "bytes": 40000000, "saveCount": 1 },
    ...
  ]
}
```

**Player app:** Show storage bar in Settings screen (used / quota), with a link to manage saves.

**Web UI:** Storage management page under user profile, showing breakdown by console and game.

### 3. Save State Deletion (Web + Player)

Users should be able to delete save states at multiple granularities:

- **Single save state** — delete one specific save from a session.
- **All saves for a session** — wipe a session's save history but keep the session itself.
- **All saves for a console** — bulk cleanup (e.g. "delete all PS2 saves").
- **All saves except current** — keep only the most recent auto-save per session, delete the rest. Useful as a "compact" action.

**New API endpoints:**
- `DELETE /api/sessions/:id/saves/:saveId` (already exists)
- `DELETE /api/sessions/:id/saves` — delete all saves for a session
- `DELETE /api/user/saves?consoleId=ps2` — bulk delete by console
- `POST /api/user/saves/compact` — keep only latest auto-save per session, delete rest

### 4. Quota Exceeded UX

When auto-save fails due to quota:
- **Player app:** Show a toast/banner: "Auto-save failed: storage full. Free up space in Settings > Storage."
- **Web UI:** Show warning on game detail page if quota is near-full (>90%).
- **Proactive:** When quota is >80%, show a subtle indicator in the player's settings screen.

## Files Involved

- **Server quota check:** `server/internal/api/middleware.go:24-43`
- **Auto-save prune logic:** `server/internal/api/session_handler.go:738-748`
- **Player API client:** `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/api/SpelaApiClient.kt`
- **Player SaveManager:** `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/SaveManager.kt`
- **Web session pages:** `web/src/features/sessions/`

## Current State (as of 2026-03-25)

- Quota: 1024 MB per user (`defaultMaxSaveStorageMB`, overridable via `SPELA_MAX_SAVE_STORAGE_MB`)
- Test user: 95 saves, 1025.7 MB used — over quota
- Biggest offenders: SSX (PS2) with 13 sessions at 40 MB each = 520 MB
- Bug fixed in this branch: `expectSuccess = true` on Ktor HttpClient so upload failures are no longer silent
