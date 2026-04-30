# Code Review: BIOS Feature Implementation

**Verdict: ISSUES FOUND** -- 1 bug, several minor issues

---

## BUG (Must Fix)

### 1. `SpelaApiClient.listBiosFiles()` deserializes wrong type

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/data/remote/api/SpelaApiClient.kt:382-383`

```kotlin
suspend fun listBiosFiles(): List<BiosFileDto> {
    return client.get("$baseUrl/api/bios").body()
}
```

The `GET /api/bios` endpoint returns `{"files": [...], "consoles": [...]}` (a `BiosListResponse` object), not a raw `List<BiosFileDto>`. This will fail at runtime with a Ktor deserialization error. The `getBiosStatus()` method on line 386-387 correctly deserializes as `BiosStatusResponse`.

**Fix:** Change `listBiosFiles()` to return `BiosStatusResponse` and extract the `.files` list:

```kotlin
suspend fun listBiosFiles(): List<BiosFileDto> {
    val response: BiosStatusResponse = client.get("$baseUrl/api/bios").body()
    return response.files
}
```

Or remove `listBiosFiles()` entirely and use `getBiosStatus().files` in `BiosRepository.syncBiosFiles()`.

---

## Minor Issues (Should Fix)

### 2. Dead assignment `_ = info` in bios_handler.go

**File:** `server/internal/api/bios_handler.go:206-207`

```go
info, onDisk := diskFiles[e.FileName]
_ = info
```

The `info` variable is fetched from the map but immediately discarded. This is dead code in the per-console summary loop. The file-level loop on line 127 does use `info` for the size, but this second loop at line 206 does not. Since we only need the boolean `onDisk`, use `_, onDisk := diskFiles[e.FileName]`.

### 3. Dead field `GameDetailState.showMissingBiosDialog`

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/presentation/state/GameDetailState.kt:67`

The field `showMissingBiosDialog` is declared in `GameDetailState` but is never read or written to. The actual missing BIOS dialog is driven by `EmulationState.showMissingBiosDialog` in `SpelaApp.kt`. Remove this dead field to avoid confusion.

### 4. Raw `<button>` in `bios-warning-banner.tsx`

**File:** `web/src/features/bios/components/bios-warning-banner.tsx:47-49`

```tsx
<button onClick={onDismiss} className="p-1 rounded-lg ...">
```

Per CLAUDE.md shared component discipline: "Raw `<button>` elements should use `Button`." This dismiss button uses a raw `<button>` with custom Tailwind classes. It should use the shared `Button` component (likely `variant="ghost"` with `size="sm"`).

### 5. MD5 computed on every `GET /api/bios` request

**File:** `server/internal/api/bios_handler.go:97-273`

The `ListBiosFiles` handler computes MD5 checksums for every BIOS file on disk on every request. MD5 computation involves reading entire files from disk. For the current small set of BIOS files (~5-15 files, each ~512KB), this is acceptable. But it's worth noting: if the BIOS directory grows, this will become a performance bottleneck. Consider caching checksums (e.g., compute once and store, invalidate on upload/delete). Not urgent, but worth a TODO comment.

---

## Observations (No Action Required)

### Architecture and Design: Clean

- **Backend registry (`bios/registry.go`)**: Good separation. The `All()` function returns a defensive copy. Package structure is clean. Table-driven tests with solid coverage.

- **Handler tests**: Comprehensive -- covers auth, admin enforcement, path traversal, upload, delete, list enrichment, and the `GetConsoleStatus` helper. The path traversal test (`TestGetBiosFile_PathTraversal`, `TestUploadBiosFile_PathTraversal`) is important and present.

- **Path traversal protection**: Properly implemented via `storage.BiosFilePath()` -> `sanitizeFilename()` which uses `filepath.Base()`. This is the correct approach.

- **File upload security**: Has `MaxBytesReader` (16 MB limit), checks `header.Size`, and sanitizes filenames. Solid.

- **Web types**: `BiosFile`, `BiosConsole`, `BiosResponse` types match the backend response shape precisely. The union type `BiosFileStatus` and `BiosConsoleStatus` are clean reuse.

- **React hooks**: Clean, focused hooks. `useBiosStatus()`, `useUploadBiosFile()`, `useDeleteBiosFile()` each do one thing. Query invalidation on mutation success is correct.

- **Web component structure**: Follows the feature-based folder structure (`features/bios/components/`). `AdminBiosPage` is the orchestrator, `BiosConsoleCard`, `BiosUploadResults`, `BiosWarningBanner` are focused feature components.

- **Player app**: Clean architecture boundaries respected. `BiosRepository` is in the data layer, `BiosConsoleStatus`/`BiosMissingFile` are domain models, `BiosWarningChip`/`MissingBiosDialog` are presentation. Dependencies flow inward.

- **MissingBiosDialog**: Uses shared design system components (`SpButton`, `SpTypography`, `SpColor`, `SpSpacing`). No hardcoded dp values or colors. Good.

- **Desktop E2E tests**: 7 tests covering all BIOS UX flows -- chip display, dialog appearance, Go Back, Try Anyway, and console list warning. Consistent with the test harness pattern.

- **File sizes**: All new files are reasonably sized. `bios_handler.go` is ~420 lines which is moderate but acceptable given it contains both handler logic and the `GetConsoleStatus` helper.

- **Admin route protection**: The web route for `/admin/bios` correctly wraps with `<ProtectedRoute requireAdmin>`. The backend routes are behind `AdminMiddleware()`.

- **Sidebar warning dot**: Clean integration -- the BIOS missing status is checked in `AppLayout` and passed as a `warning` prop on the sidebar link.

---

## Summary

| Severity | Count | Items |
|----------|-------|-------|
| Bug | 1 | `listBiosFiles()` wrong return type |
| Minor | 4 | Dead assignment, dead state field, raw button, MD5 perf note |
| Clean | -- | Everything else |

The implementation is solid overall. The bug in `SpelaApiClient.listBiosFiles()` is the only must-fix -- it will cause `syncBiosFiles()` to fail at runtime. The minor issues are cleanup items that should be addressed but don't block functionality (except the dead field which could cause confusion later).
