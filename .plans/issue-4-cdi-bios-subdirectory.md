# Plan: CD-i BIOS Subdirectory Support (Issue 4)

## Problem

The `same_cdi` core (MAME-based) expects BIOS zips at `<system_dir>/same_cdi/bios/cdimono1.zip`, but Spela stores all BIOS files flat at `<system_dir>/cdimono1.zip`. CD-i games fail to start because the core can't find its BIOS.

## Design Decision

- **Server stores files flat** — it's a file repository, not an emulation host
- **Server exposes `subDir` in API** — so the player knows where to place files
- **Player places files in subdirectories** — it's the emulation host with the system directory
- **Backward compatible** — existing flat entries work unchanged (`subDir` defaults to empty)

## Implementation Steps

### Step 1: Add `SubDir` field to BIOS registry

**File:** `server/internal/bios/registry.go`

```go
type Entry struct {
    ConsoleID   string
    FileName    string
    Description string
    MD5         string
    Required    bool
    OverrideURL string
    SubDir      string // relative path within system_dir, e.g. "same_cdi/bios"
}
```

Update CD-i entries:
```go
{ConsoleID: "cdi", FileName: "cdimono1.zip", ..., SubDir: "same_cdi/bios"},
{ConsoleID: "cdi", FileName: "cdimono2.zip", ..., SubDir: "same_cdi/bios"},
{ConsoleID: "cdi", FileName: "cdibios.zip",  ..., SubDir: "same_cdi/bios"},
```

### Step 2: Expose `subDir` in BIOS API responses

**File:** `server/internal/api/bios_handler.go`

Add `SubDir string json:"subDir,omitempty"` to `BiosFileResponse` and `ConsoleFileStatus`. Populate from registry entry.

### Step 3: Server downloader handles subdirectories

**File:** `server/internal/bios/downloader.go`

When `entry.SubDir != ""`:
- `destPath = filepath.Join(biosDir, entry.SubDir, entry.FileName)`
- `os.MkdirAll(filepath.Dir(destPath), 0755)` before writing
- Skip check uses the same subdirectory-aware path

### Step 4: Server BIOS file serving handles subdirectories

**File:** `server/internal/api/bios_handler.go`

`GetBiosFile` should look up the registry entry by filename. If it has a `SubDir`, check `biosDir/subDir/filename` first, then fall back to `biosDir/filename` for backward compat.

### Step 5: Player DTOs add `subDir`

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/dto/Dtos.kt`

Add `val subDir: String? = null` to `BiosFileDto` and `BiosConsoleFileDto`.

### Step 6: Player BiosRepository uses subdirectories

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/data/repository/BiosRepository.kt`

In `syncBiosFiles()` and `preLaunchBiosCheck()`:
```kotlin
val localDir = if (!file.subDir.isNullOrEmpty()) "$biosDir/${file.subDir}" else biosDir
val localPath = "$localDir/${file.name}"
if (!fileStorage.fileExists(localPath)) {
    if (!file.subDir.isNullOrEmpty()) fileStorage.createDirectory(localDir)
    val data = apiClient.downloadBiosFile(file.name)
    fileStorage.writeFile(localPath, data)
}
```

### Step 7: Tests

- **Server:** Test CDI entries have SubDir set; test downloader creates subdirectories; test API returns subDir in JSON
- **Player:** Test BiosRepository places files in subdirectories when subDir is present

## Files to Modify

| File | Change |
|------|--------|
| `server/internal/bios/registry.go` | Add `SubDir` field, set for CDI entries |
| `server/internal/bios/downloader.go` | Subdirectory-aware file placement |
| `server/internal/api/bios_handler.go` | Expose `subDir` in responses, serve from subdirs |
| `player/.../data/remote/dto/Dtos.kt` | Add `subDir` to BIOS DTOs |
| `player/.../data/repository/BiosRepository.kt` | Place files in subdirectories |
| `player/.../domain/model/Models.kt` | Add `subDir` to `BiosMissingFile` |

## Future-Proofing

Other MAME-based cores with similar subdirectory requirements can simply set `SubDir` on their registry entries. No code changes needed.
