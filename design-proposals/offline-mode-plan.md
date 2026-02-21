# Offline Mode Implementation Plan

## Context

The Spela player app currently requires a server connection for most operations: browsing games, loading preferences, save states, and even session restore. However, the app already has significant local infrastructure (downloaded ROMs, cached cores, local SQLDelight DB) that makes offline play technically possible. This plan adds comprehensive offline support with the best possible UX — transparent degradation when offline, local-first saves, and automatic sync on reconnect.

The user also requested a new feature: **SRAM/save data management** with multiple save data files per game, synced to the server. Conflict resolution for both save states and SRAM follows "latest wins, but keep the loser" — the losing version is preserved as a named backup save.

---

## Phase 1: Connectivity Monitoring (Foundation)

Everything depends on knowing whether we're online or offline.

### New file: `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/ConnectivityMonitor.kt`

- Exposes `isOnline: StateFlow<Boolean>` (optimistic default: `true`)
- Polls `GET /api/health` (already exists in router.go) — 30s when online, 10s when offline
- `reportOffline()` / `reportOnline()` methods for repositories to fast-path update on network errors/successes
- Emits a reconnect signal when transitioning from offline → online (used by SyncEngine in Phase 7)

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/api/SpelaApiClient.kt`

- Add `suspend fun healthCheck()` — `GET $baseUrl/api/health`

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/di/CommonModule.kt`

- Register `ConnectivityMonitor` as `single`

---

## Phase 2: Preferences Cache

Small change, big impact — without this, emulation settings (shader, auto-save, overlay) revert to defaults when offline.

### Schema addition in `player/shared/src/commonMain/sqldelight/com/spela/player/SpelaDatabase.sq`

```sql
CREATE TABLE CachedPreferencesEntity (
    id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    json_data TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Store as JSON blob (kotlinx.serialization) since `UserPreferences` has nested maps (`consoleShaders`, `consoleKeyMappings`) that are awkward as individual columns.

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/PreferencesRepositoryImpl.kt`

- `getPreferences()`: try API → cache on success → fall back to cached JSON on network failure
- `resolveShader()`: use cached preferences instead of re-fetching from API
- Add private `cachePreferences()` and `getCachedPreferences()` helpers

---

## Phase 3: Game Catalog Cache

Without this, the user can't browse their library offline at all — Home, Library, Console screens all show empty/error.

### Schema additions in `SpelaDatabase.sq`

```sql
CREATE TABLE CachedConsoleEntity (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    abbreviation TEXT NOT NULL,
    game_count INTEGER NOT NULL DEFAULT 0,
    color_theme TEXT NOT NULL DEFAULT '#6366f1',
    cover_aspect_ratio REAL NOT NULL DEFAULT 0.75,
    default_core TEXT NOT NULL DEFAULT '',
    icon_url TEXT NOT NULL DEFAULT '',
    save_state_support INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE CachedGameEntity (
    id TEXT NOT NULL PRIMARY KEY,
    console_id TEXT NOT NULL,
    title TEXT NOT NULL,
    console_name TEXT NOT NULL DEFAULT '',
    cover_url TEXT,
    description TEXT,
    developer TEXT,
    publisher TEXT,
    release_date TEXT,
    genre TEXT,
    file_size INTEGER NOT NULL DEFAULT 0,
    file_name TEXT NOT NULL DEFAULT '',
    disc_count INTEGER NOT NULL DEFAULT 0,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    is_in_play_later INTEGER NOT NULL DEFAULT 0,
    last_played_at TEXT,
    total_play_time INTEGER NOT NULL DEFAULT 0,
    cached_at INTEGER NOT NULL
);
```

Queries: `getAllCachedConsoles`, `getAllCachedGames`, `getCachedGamesForConsole`, `getCachedGame`, `searchCachedGames` (LIKE on title), `getCachedFavorites`, `getCachedPlayLater`, `getCachedRecentGames` (ORDER BY last_played_at DESC).

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/GameRepositoryImpl.kt`

- Add `SpelaDatabase` and `ConnectivityMonitor` constructor parameters
- Every method follows: try API → cache result on success → fall back to local cache on failure
- Example: `getConsoles()` → `runCatching { api fetch + cache } .recoverCatching { local cache }`
- `searchGames()` falls back to `searchCachedGames` (LIKE query on title)

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/di/CommonModule.kt`

- Update `GameRepositoryImpl` constructor to include `database` and `connectivityMonitor`

---

## Phase 4: Offline Session Restore

Currently: token validation fails → redirect to Login. After this change: network error → proceed to Home in offline mode.

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/domain/usecase/RestoreSessionUseCase.kt`

- Add new result: `RestoreSessionResult.OfflineSuccess`
- On `getCurrentUser()` failure, distinguish network errors (ConnectException, UnknownHostException, SocketTimeoutException, ConnectTimeoutException) from auth errors (401/403)
- Network error + stored tokens → `OfflineSuccess`
- Auth error → clear tokens → `NeedsLogin`

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/navigation/NavigationViewModel.kt`

- Map `OfflineSuccess` → `SpScreen.Home` (same as `Success`)
- Set `isOffline = true` in navigation state for UI indicator

---

## Phase 5: Local-First Save States

Save locally first, sync to server when available. No more lost progress on network hiccups.

### Schema addition in `SpelaDatabase.sq`

```sql
CREATE TABLE LocalSaveStateEntity (
    id TEXT NOT NULL PRIMARY KEY,
    game_id TEXT NOT NULL,
    name TEXT NOT NULL,
    local_path TEXT NOT NULL,
    file_size INTEGER NOT NULL DEFAULT 0,
    is_auto INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    server_id INTEGER,              -- null = never uploaded
    sync_status TEXT NOT NULL DEFAULT 'pending',  -- 'pending' | 'synced' | 'conflict'
    last_synced_at INTEGER
);
```

Queries: `getLocalSaveStatesForGame`, `getLocalAutoSave`, `getPendingSyncs`, `markSynced`, `deleteLocalSaveState`.

### Local file storage convention

```
{fileStorage.getSavesDir()}/savestates/{gameId}/{localSaveId}.sav
```

Subdirectory `savestates/` avoids collision with SRAM `.srm` files that cores write to the saves root.

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/domain/repository/SaveRepository.kt`

Add methods:
- `saveLocally(gameId, name, data, isAuto): Result<SaveState>`
- `loadLocalAutoSave(gameId): Result<ByteArray>`
- `getPendingSyncs(): List<LocalSaveStateEntity>`

### Modified: `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/SaveRepositoryImpl.kt`

Major rewrite. Add `SpelaDatabase`, `FileStorage`, `ConnectivityMonitor` constructor params.

**Save flow:**
1. Write bytes to local file
2. Insert `LocalSaveStateEntity` with `sync_status = 'pending'`
3. If online: attempt server upload → on success mark `synced` with `server_id`
4. If offline or upload fails: leave as `pending` (SyncEngine handles later)

**Load flow:**
1. Try local auto-save first (from `LocalSaveStateEntity` + local file)
2. If no local save: try server download (if online)

**List flow:**
- Merge local saves + server saves, deduplicate by `server_id`

### Unchanged: `EmulationUseCases.kt`

`SaveGameStateUseCase` and `LoadGameStateUseCase` keep the same interface — the local-first behavior is entirely within `SaveRepositoryImpl`.

---

## Phase 6: SRAM / Save Data Management (New Feature)

Currently SRAM is handled implicitly by libretro cores (auto-dump to saves dir). This phase makes it explicit, synced, and manageable.

### Server-side: New GORM model

In `server/internal/db/models.go`:

```go
type SaveData struct {
    ID        uint           `gorm:"primarykey" json:"id"`
    CreatedAt time.Time      `json:"createdAt"`
    UpdatedAt time.Time      `json:"updatedAt"`
    DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
    UserID    uint           `gorm:"index;not null" json:"userId"`
    User      User           `gorm:"foreignKey:UserID" json:"-"`
    GameID    uint           `gorm:"index;not null" json:"gameId"`
    Game      Game           `gorm:"foreignKey:GameID" json:"-"`
    Name      string         `gorm:"size:255;not null" json:"name"`
    FilePath  string         `gorm:"size:1024;not null" json:"-"`
    FileSize  int64          `json:"fileSize"`
    IsActive  bool           `gorm:"default:false" json:"isActive"`
}
```

Add to auto-migration in `server/internal/db/database.go`.

### Server-side: New API endpoints

In `server/internal/api/router.go`:

```
POST   /api/games/:id/save-data              → UploadSaveData (named backup)
GET    /api/games/:id/save-data              → ListSaveData
POST   /api/games/:id/save-data/active       → UploadActiveSaveData (upsert active)
GET    /api/games/:id/save-data/active       → DownloadActiveSaveData
GET    /api/games/:id/save-data/:sdId/download → DownloadSaveData
PUT    /api/games/:id/save-data/:sdId/activate → ActivateSaveData
PUT    /api/games/:id/save-data/:sdId        → RenameSaveData
DELETE /api/games/:id/save-data/:sdId        → DeleteSaveData
```

### New file: `server/internal/api/save_data_handler.go`

Key behaviors:
- `UploadActiveSaveData`: upserts the active SRAM (like auto-save pattern). Finds existing active entry, overwrites file and updates metadata. Creates new if none exists.
- `ActivateSaveData`: sets `is_active = true` for target, `is_active = false` for all others of same user+game
- Storage path: `saves/user_{uid}/game_{gid}/sram/{filename}`

Add `SaveDataPath()` to `server/internal/storage/storage.go`.

### Player-side: Native JNI additions

`nativeGetSRAM()` already exists. Add `nativeSetSRAM(data: ByteArray)`:

- `player/shared/src/androidMain/kotlin/com/spela/player/libretro/LibretroJni.kt` — declare `external fun nativeSetSRAM(data: ByteArray): Boolean`
- `player/shared/src/desktopMain/kotlin/com/spela/player/libretro/LibretroJni.kt` — same
- `player/native/src/libretro_bridge.c` — implement via `retro_get_memory_data(RETRO_MEMORY_SAVE_RAM)` + `memcpy` into the core's SRAM buffer

### Player-side: LibretroController interface

Add to the interface (in `EmulationViewModel.kt`):
```kotlin
fun getSRAM(): ByteArray?
fun setSRAM(data: ByteArray): Boolean
```

Implement in `AndroidLibretroController` and `DesktopLibretroController`.

### Player-side: Domain model

Add to `Models.kt`:
```kotlin
data class SaveData(
    val id: Long,
    val gameId: Long,
    val name: String,
    val fileSize: Long = 0,
    val isActive: Boolean = false,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
```

### New file: `player/shared/src/commonMain/kotlin/com/spela/player/domain/repository/SaveDataRepository.kt`

```kotlin
interface SaveDataRepository {
    suspend fun getSaveDataList(gameId: String): Result<List<SaveData>>
    suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): Result<SaveData>
    suspend fun downloadActiveSaveData(gameId: String): Result<ByteArray>
    suspend fun downloadSaveData(gameId: String, saveDataId: String): Result<ByteArray>
    suspend fun activateSaveData(gameId: String, saveDataId: String): Result<Unit>
    suspend fun renameSaveData(gameId: String, saveDataId: String, name: String): Result<Unit>
    suspend fun deleteSaveData(gameId: String, saveDataId: String): Result<Unit>
    suspend fun saveLocalSRAM(gameId: String, data: ByteArray)
    suspend fun loadLocalSRAM(gameId: String): ByteArray?
}
```

### New file: `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/SaveDataRepositoryImpl.kt`

Local-first pattern. SRAM files at: `{savesDir}/sram/{gameId}/active.srm` and `{savesDir}/sram/{gameId}/{name}.srm`.

### Schema addition: `LocalSaveDataEntity`

```sql
CREATE TABLE LocalSaveDataEntity (
    id TEXT NOT NULL PRIMARY KEY,
    game_id TEXT NOT NULL,
    name TEXT NOT NULL,
    local_path TEXT NOT NULL,
    file_size INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    server_id INTEGER,
    sync_status TEXT NOT NULL DEFAULT 'pending'
);
```

### Modified: `EmulationViewModel.kt`

**On game start** (after loading core and game, before starting emulation):
```kotlin
// Load active SRAM
saveDataRepository.loadLocalSRAM(gameId)?.let { sram ->
    libretroController.setSRAM(sram)
} ?: run {
    if (connectivityMonitor.isOnline.value) {
        saveDataRepository.downloadActiveSaveData(gameId).onSuccess { sram ->
            libretroController.setSRAM(sram)
            saveDataRepository.saveLocalSRAM(gameId, sram)
        }
    }
}
```

**On game stop** (before `libretroController.stop()`, after save state):
```kotlin
val sramData = libretroController.getSRAM()
if (sramData != null && sramData.isNotEmpty()) {
    saveDataRepository.saveLocalSRAM(gameId, sramData)
    if (connectivityMonitor.isOnline.value) {
        runCatching { saveDataRepository.uploadActiveSaveData(gameId, sramData) }
    }
}
```

### API client additions in `SpelaApiClient.kt`

Add methods for all save-data endpoints (list, upload, download, activate, rename, delete).

### DTO additions

Add `SaveDataDto` in `Dtos.kt` and mapping in `DtoMappers.kt`.

---

## Phase 7: Sync Engine and Conflict Resolution

### New file: `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/SyncEngine.kt`

Observes `connectivityMonitor.isOnline` — when transitioning offline → online, triggers `syncAll()`.

```kotlin
class SyncEngine(
    connectivityMonitor, saveRepository, saveDataRepository,
    preferencesRepository, gameRepository, apiClient, fileStorage,
    dispatchers, scope
)
```

Exposes `syncState: StateFlow<SyncState>`:
```kotlin
data class SyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val pendingSaveStates: Int = 0,
    val pendingSaveData: Int = 0,
)
```

### Conflict resolution algorithm (same for save states and SRAM)

When syncing a pending local save:

1. Fetch server's save list for the same game
2. Find matching server entry (auto-save for auto-saves, or by server_id)
3. Compare timestamps:

```
IF no server version exists:
    → Simple upload, mark synced

IF local.updated_at > server.updated_at:
    → LOCAL WINS
    → Download server version, upload it as named backup:
      "Backup (server) - {timestamp}"
    → Upload local version as the active/auto save
    → Mark local as synced

IF server.updated_at > local.updated_at:
    → SERVER WINS
    → Upload local version as named backup:
      "Backup (offline) - {timestamp}"
    → Download server version, replace local file
    → Mark local as synced

IF timestamps are equal:
    → Already synced, mark as synced
```

This ensures **no data is ever lost**. The loser always becomes a named backup entry in the save states / save data list.

### DI registration

Register `SyncEngine` as `single` in `CommonModule.kt`. Start it from `NavigationViewModel` after successful session restore.

---

## Phase 8: UI Changes

### 8.1 Offline Banner

New file: `player/shared/.../presentation/ui/components/SpOfflineBanner.kt`

Subtle amber horizontal banner at top of screen: "Offline — Using cached data". Uses existing `SpConnectionBadge` styling patterns. Auto-hides when connectivity returns.

### 8.2 Sync Status Indicator

New file: `player/shared/.../presentation/ui/components/SpSyncStatusIndicator.kt`

Shows in Settings screen: spinning icon + "Syncing..." when active, badge with pending count, "Last synced: {time}".

### 8.3 Save Data Management Screen

New file: `player/shared/.../presentation/ui/screen/SaveDataScreen.kt`

New ViewModel: `SaveDataViewModel.kt`

Accessed from GameDetailScreen. Shows:
- List of SRAM save data entries for the game
- Active save data has a checkmark indicator
- Each entry: name, timestamp, file size, sync status icon (synced/pending)
- Actions: Activate, Rename, Delete
- Empty state: use `SpEmptyState` pattern ("No save data — Play a game and your progress will be saved automatically")

### 8.4 GameDetailScreen additions

Add "Save Data" section (between Save States and community sections):
- Show count of save data files
- "Manage" button → navigates to SaveDataScreen
- Each save state entry shows sync status icon (checkmark for synced, clock for pending)

### 8.5 Navigation

Add to `SpNavigation.kt`: `data class SaveDataManagement(val gameId: String) : SpScreen`
Wire in `SpelaApp.kt`.

### 8.6 Settings screen additions

Add "Sync" section to SettingsScreen:
- Online/offline status indicator
- "Sync Now" button
- Pending sync count
- Last synced timestamp

### 8.7 Downloads screen enhancement

Add "Downloaded Games" section showing all locally cached games (from `DownloadEntity` + `CachedGameEntity` for metadata). Important for offline mode — users need to see which games they can play.

---

## Files Summary

### New files (17)

| File | Purpose |
|------|---------|
| `player/shared/.../data/remote/ConnectivityMonitor.kt` | Online/offline state |
| `player/shared/.../data/remote/SyncEngine.kt` | Background sync + conflict resolution |
| `player/shared/.../domain/repository/SaveDataRepository.kt` | SRAM repository interface |
| `player/shared/.../data/repository/SaveDataRepositoryImpl.kt` | SRAM repository implementation |
| `player/shared/.../presentation/viewmodel/SaveDataViewModel.kt` | Save data management VM |
| `player/shared/.../presentation/ui/screen/SaveDataScreen.kt` | Save data management UI |
| `player/shared/.../presentation/ui/components/SpOfflineBanner.kt` | Offline indicator |
| `player/shared/.../presentation/ui/components/SpSyncStatusIndicator.kt` | Sync status UI |
| `server/internal/api/save_data_handler.go` | SRAM API endpoints |
| `server/internal/api/save_data_handler_test.go` | Server tests |
| `player/desktop/.../e2e/OfflineSessionRestoreTest.kt` | Desktop test |
| `player/desktop/.../e2e/GameCatalogCacheTest.kt` | Desktop test |
| `player/desktop/.../e2e/PreferencesCacheTest.kt` | Desktop test |
| `player/desktop/.../e2e/LocalSaveStateTest.kt` | Desktop test |
| `player/desktop/.../e2e/SaveDataManagementTest.kt` | Desktop test |
| `player/desktop/.../e2e/SyncStatusTest.kt` | Desktop test |
| `player/desktop/.../e2e/OfflineIndicatorTest.kt` | Desktop test |

### Modified files (27+)

| File | Changes |
|------|---------|
| `player/.../SpelaDatabase.sq` | +5 tables (CachedPreferences, CachedConsole, CachedGame, LocalSaveState, LocalSaveData) |
| `player/.../di/CommonModule.kt` | Register new singletons, update constructor args |
| `player/.../usecase/RestoreSessionUseCase.kt` | Add OfflineSuccess, network error detection |
| `player/.../navigation/NavigationViewModel.kt` | Handle OfflineSuccess, start SyncEngine |
| `player/.../navigation/SpNavigation.kt` | Add SaveDataManagement screen |
| `player/.../repository/GameRepositoryImpl.kt` | Add local cache fallback |
| `player/.../repository/SaveRepositoryImpl.kt` | Rewrite: local-first saves |
| `player/.../repository/PreferencesRepositoryImpl.kt` | Add cache fallback |
| `player/.../repository/SaveRepository.kt` | Add local save methods |
| `player/.../model/Models.kt` | Add SaveData model |
| `player/.../viewmodel/EmulationViewModel.kt` | SRAM load/save + ConnectivityMonitor |
| `player/.../state/GameDetailState.kt` | Add saveData fields |
| `player/.../intent/GameDetailIntent.kt` | Add save data intents |
| `player/.../viewmodel/GameDetailViewModel.kt` | Load/display save data |
| `player/.../ui/screen/GameDetailScreen.kt` | Add save data section |
| `player/.../ui/screen/DownloadsScreen.kt` | Add downloaded games library |
| `player/.../ui/screen/SettingsScreen.kt` | Add sync section |
| `player/.../viewmodel/SettingsViewModel.kt` | Add sync state/intents |
| `player/.../ui/SpelaApp.kt` | Wire SaveDataScreen |
| `player/.../api/SpelaApiClient.kt` | Add healthCheck + save-data methods |
| `player/.../dto/Dtos.kt` | Add SaveDataDto |
| `player/.../dto/DtoMappers.kt` | Add SaveData mapping |
| `player/native/src/libretro_bridge.c` | Add nativeSetSRAM implementation |
| `player/.../libretro/LibretroJni.kt` (both platforms) | Add nativeSetSRAM declaration |
| `player/.../libretro/AndroidLibretroController.kt` | Implement getSRAM/setSRAM |
| `player/.../libretro/DesktopLibretroController.kt` | Implement getSRAM/setSRAM |
| `server/internal/db/models.go` | Add SaveData model |
| `server/internal/db/database.go` | Add SaveData auto-migration |
| `server/internal/api/router.go` | Add save-data routes |
| `server/internal/storage/storage.go` | Add SaveDataPath helper |

### Test fakes to update

| File | Changes |
|------|---------|
| `player/desktop/.../e2e/TestFakes.kt` | Add FakeConnectivityMonitor, FakeSyncEngine, FakeSaveDataRepository; update FakeSaveRepository |
| `player/desktop/.../e2e/SpelaTestHarness.kt` | Wire new fakes |

---

## Testing Strategy

**Desktop tests (primary):** 7 new test files covering offline session restore, game catalog cache, preferences cache, local save states, save data management, sync status, offline indicator. All use `SpelaTestHarness` with fake repositories.

**Android smoke tests:** Offline mode smoke test (login → download → disconnect → verify cached data → play → reconnect → verify sync). SRAM round-trip test (play game with SRAM → exit → verify upload → clear local → re-download → verify).

**Server tests:** Full endpoint coverage for save-data handler (CRUD + activate + rename + access control).

---

## Verification

After implementation, verify end-to-end:

1. `cd player && ./run-desktop-tests.sh` — all desktop E2E tests pass
2. `cd server && go test ./...` — all server tests pass
3. `cd player && ./run-e2e.sh` — Android smoke tests pass
4. Manual verification: start app → download a game → kill server → restart app → verify Home screen loads with cached data → play downloaded game → save state → restart server → verify sync happens
