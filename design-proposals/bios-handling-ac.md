# BIOS/Firmware File Management -- Acceptance Criteria

## Context

Spela uses libretro cores for emulation. Some cores **require** BIOS/firmware files to function (e.g., PlayStation requires `scph5501.bin`). Others have **optional** BIOS files that improve accuracy.

### Current State

**What exists:**
- Server: `BiosHandler` with `GET /api/bios` (list files) and `GET /api/bios/:filename` (download file). Returns `{name, size}` only -- no checksums, no upload, no console association, no known-BIOS metadata.
- Server: `Storage.BiosDir` maps to a filesystem directory. Files are served as-is.
- Player app: `BiosRepository` with `syncBiosFiles()` (downloads all server BIOS files to local `bios/` dir) and `getLocalBiosFiles()`. The bios directory is set as the libretro `system_dir` via `nativeSetSystemDir()`.
- Player app: `syncBiosFiles()` is **never called** -- it is wired in DI but unused.
- Web: `useBiosFiles()` hook exists and is used in `PlayPage` to pass BIOS file URLs to EmulatorJS. No management UI exists.
- No upload capability anywhere. No checksum validation. No console-to-BIOS mapping. No warnings for missing BIOS.

**What happens today when BIOS is missing:**
- Player app: The libretro core fails silently or crashes. The user sees `"Failed to start emulation: <native crash message>"` -- completely unhelpful.
- Web: EmulatorJS may show its own cryptic error or just a black screen.

---

## Feature 1: BIOS Metadata Database (Server)

The server must have a built-in database of known BIOS files -- which consoles need them, expected filenames, MD5 checksums, whether they are required or optional, and a human-readable description.

### AC 1.1: Known BIOS Registry

- [ ] The server has a hardcoded (in-code) registry of known BIOS files with the following fields per entry:
  - `consoleId` -- the console abbreviation (e.g., `"psx"`, `"gba"`, `"nds"`, `"dc"`, `"sat"`, `"pce"`)
  - `fileName` -- the expected filename (e.g., `"scph5501.bin"`)
  - `description` -- human-readable label (e.g., `"PlayStation BIOS (North America)"`)
  - `md5` -- the expected MD5 checksum
  - `required` -- boolean: `true` if the console cannot function without it, `false` if optional
- [ ] The registry covers at minimum:
  - **PlayStation (PSX)**: `scph5500.bin` (JP), `scph5501.bin` (NA, required), `scph5502.bin` (EU)
  - **Sega Saturn (SAT)**: `saturn_bios.bin` (required)
  - **Sega CD (SCD)**: `bios_CD_U.bin`, `bios_CD_E.bin`, `bios_CD_J.bin` (region BIOS)
  - **Dreamcast (DC)**: `dc_boot.bin` (required), `dc_flash.bin`
  - **Game Boy Advance (GBA)**: `gba_bios.bin` (optional -- mgba has HLE)
  - **Nintendo DS (NDS)**: `bios7.bin`, `bios9.bin`, `firmware.bin` (optional for DeSmuME)
  - **PC Engine / TurboGrafx-16 (PCE)**: `syscard3.pce` (required for CD games)
  - **PlayStation Portable (PSP)**: no BIOS needed (PPSSPP is HLE)
  - Additional consoles as appropriate
- [ ] Entries that do not match any supported console (from `SeedConsoles`) are ignored.

### AC 1.2: BIOS Status Endpoint

- [ ] `GET /api/bios` is enhanced to return enriched data. The response is an object:
  ```json
  {
    "files": [
      {
        "name": "scph5501.bin",
        "size": 524288,
        "md5": "924e392ed05558ffdb115408c263dccf",
        "consoleId": "psx",
        "consoleName": "PlayStation",
        "description": "PlayStation BIOS (North America)",
        "required": true,
        "status": "valid"
      }
    ],
    "consoles": [
      {
        "consoleId": "psx",
        "consoleName": "PlayStation",
        "biosRequired": true,
        "status": "ready",
        "requiredPresent": 1,
        "requiredTotal": 1,
        "optionalPresent": 2,
        "optionalTotal": 2,
        "files": [
          {
            "fileName": "scph5501.bin",
            "description": "PlayStation BIOS (North America)",
            "required": true,
            "md5": "924e392ed05558ffdb115408c263dccf",
            "status": "valid"
          },
          {
            "fileName": "scph5500.bin",
            "description": "PlayStation BIOS (Japan)",
            "required": false,
            "md5": "...",
            "status": "present"
          }
        ]
      }
    ]
  }
  ```
- [ ] Each file's `status` is one of:
  - `"valid"` -- file present and MD5 matches a known entry
  - `"present"` -- file present, MD5 matches (or file not in known registry but exists on disk)
  - `"invalid"` -- file present but MD5 does NOT match the expected checksum
  - `"missing"` -- file not present on disk
- [ ] Each console's `status` is one of:
  - `"ready"` -- all required BIOS files are `valid` or `present`
  - `"missing"` -- one or more required BIOS files are `missing`
  - `"invalid"` -- one or more required BIOS files have wrong checksums
  - `"not_required"` -- console has no required BIOS files (may have optional ones)
- [ ] Unknown files in the bios directory (not in the registry) are included in the `files` array with `consoleId: null`, `status: "present"`, `required: false`.
- [ ] The endpoint remains authenticated (not admin-only) so the player app can query it.

### AC 1.3: BIOS Upload Endpoint (Admin Only)

- [ ] `POST /api/admin/bios` accepts a multipart file upload.
- [ ] The uploaded file is saved to `Storage.BiosDir` using the original filename.
- [ ] If a file with the same name already exists, it is overwritten.
- [ ] After saving, the server computes the MD5 and includes validation info in the response:
  ```json
  {
    "name": "scph5501.bin",
    "size": 524288,
    "md5": "924e392ed05558ffdb115408c263dccf",
    "consoleId": "psx",
    "consoleName": "PlayStation",
    "description": "PlayStation BIOS (North America)",
    "required": true,
    "status": "valid"
  }
  ```
- [ ] If the file does not match any known BIOS entry (by filename or MD5), the response still succeeds but returns `consoleId: null`, `status: "present"`.
- [ ] Path traversal protection: filenames are sanitized through `sanitizeFilename()`.
- [ ] Files larger than 16 MB are rejected with `413 Payload Too Large`.
- [ ] Only admin users can upload (uses `AdminMiddleware`).

### AC 1.4: BIOS Delete Endpoint (Admin Only)

- [ ] `DELETE /api/admin/bios/:filename` removes a BIOS file from disk.
- [ ] Returns 404 if the file does not exist.
- [ ] Returns 200 on success.
- [ ] Only admin users can delete.

### AC 1.5: Per-Game BIOS Requirements

- [ ] `GET /api/games/:id` response is extended with a `biosStatus` field:
  ```json
  {
    "biosStatus": "ready" | "missing" | "invalid" | "not_required"
  }
  ```
- [ ] This reflects the BIOS status for the game's console. A game on PlayStation where `scph5501.bin` is missing returns `"missing"`.
- [ ] Games on consoles with no required BIOS return `"not_required"`.

---

## Feature 2: BIOS Management Web UI (Admin)

### AC 2.1: Admin BIOS Page

- [ ] A new admin page at `/admin/bios` is added to the admin sidebar (between "Settings" and "Library Scan").
- [ ] The sidebar item shows a warning indicator (orange dot or badge) if any console with games has missing required BIOS files.
- [ ] The page title is "BIOS Files" with subtitle "Manage firmware and BIOS files required by emulation cores."

### AC 2.2: Console BIOS Status Dashboard

- [ ] The page shows a card-grid of consoles that either (a) have BIOS requirements or (b) have games in the library.
- [ ] Each console card shows:
  - Console name and icon
  - Status badge: "Ready" (green), "Missing BIOS" (red), "Invalid BIOS" (orange), "Optional" (gray), "Not Required" (subtle)
  - Count: "2/3 files present" or similar
  - Expandable list of individual BIOS files with their status
- [ ] Consoles with missing required BIOS are sorted to the top.
- [ ] Consoles that have no games in the library are de-emphasized (lower opacity or collapsed section).

### AC 2.3: File Upload

- [ ] A prominent "Upload BIOS Files" button (or drag-and-drop zone) at the top of the page.
- [ ] Supports multiple file upload in a single action.
- [ ] After upload, each file shows immediate validation feedback:
  - Checkmark + "Matched: PlayStation BIOS (North America)" for recognized files
  - Warning + "Unrecognized file -- saved to BIOS directory" for unknown files
  - Error + "Checksum mismatch: expected X, got Y" for files with wrong checksums that match a known filename
- [ ] The console status cards update in real-time after upload (invalidate TanStack Query cache).

### AC 2.4: File Management

- [ ] Each BIOS file in the list has a delete button (with confirmation).
- [ ] Deleting a file updates the console status immediately.
- [ ] Files can be identified by their match status:
  - Green checkmark: valid (correct file, correct checksum)
  - Orange warning: wrong checksum (right filename, wrong content)
  - Gray: unrecognized (not in known registry)

### AC 2.5: Empty State

- [ ] When no BIOS files are uploaded, the page shows a helpful empty state:
  - Icon (e.g., chip/CPU icon)
  - "No BIOS files uploaded"
  - "Some consoles require BIOS/firmware files to play games. Upload the required files to get started."
  - Upload button
  - Link or expandable section showing which consoles need BIOS files and their expected filenames

---

## Feature 3: Missing BIOS Warnings (Web)

### AC 3.1: Game Detail Page Warning

- [ ] On the game detail page, if the game's console has missing required BIOS files, a warning banner appears between the hero section and the game content:
  - Orange/amber background
  - Icon: AlertTriangle or similar
  - Text: "Missing BIOS: PlayStation requires firmware files to play. [filename(s)] not found."
  - For admin users: a "Go to BIOS Management" link/button
  - For non-admin users: "Contact your server admin to upload the required BIOS files."
- [ ] The warning does NOT appear for consoles where BIOS is optional and the game can still play.
- [ ] The "Play" button is NOT disabled -- the user can still attempt to play (some cores have partial HLE). But the warning is prominent.

### AC 3.2: Play Page Error Handling

- [ ] On the web play page, if EmulatorJS fails to start and the game's console has missing BIOS, the error overlay shows a specific message:
  - "This game requires BIOS files that are not available on the server."
  - Lists the missing files by name
  - For admins: "Upload BIOS files" button linking to `/admin/bios`
  - For non-admins: "Contact your server admin."
- [ ] This replaces the generic "Emulator error" toast.

### AC 3.3: Dashboard Warning

- [ ] The main dashboard shows a warning banner (admin-only) if any console with games in the library has missing required BIOS:
  - "Some consoles are missing required BIOS files. Games may not work correctly."
  - "Go to BIOS Management" link
- [ ] This banner is dismissible per session but returns on page reload if the issue persists.

---

## Feature 4: Player App BIOS Integration

### AC 4.1: Automatic BIOS Sync

- [ ] When the player app connects to a server (on login and on app launch), it calls `syncBiosFiles()` to download any BIOS files from the server that are not already local.
- [ ] Sync runs in the background and does not block navigation or game browsing.
- [ ] Files already present locally (same filename) are not re-downloaded. (Future enhancement: checksum-based re-download if the file changed on the server.)
- [ ] Sync errors are logged but do not show errors to the user.

### AC 4.2: Pre-Launch BIOS Check

- [ ] Before launching a game, the player app checks whether the console has required BIOS files by querying `GET /api/bios` (enriched endpoint from AC 1.2).
- [ ] If required BIOS files are missing **locally** (not yet synced):
  - Attempt to download them from the server on-the-fly.
  - If the server also doesn't have them, show a warning dialog (see AC 4.3).
- [ ] If all required BIOS files are present locally, proceed to launch normally.
- [ ] This check should be part of the `PrepareGameUseCase` or called by the `EmulationViewModel.startGame()` flow -- not a separate screen the user has to navigate to.

### AC 4.3: Missing BIOS Dialog

- [ ] When required BIOS files are missing (both locally and on server), a dialog appears:
  - Title: "Missing BIOS Files"
  - Body: "**[Console Name]** requires firmware files to run games. The following files are missing:"
  - List of missing filenames with descriptions (e.g., "scph5501.bin -- PlayStation BIOS (North America)")
  - "BIOS files are system firmware that must be obtained separately. Ask your server admin to upload them."
  - Primary action: "Go Back" (returns to game detail)
  - Secondary action: "Try Anyway" (attempts to launch -- some cores may work with HLE)
- [ ] The dialog uses `SpDialog` or equivalent shared design system component.
- [ ] If the user chooses "Try Anyway" and the core fails, the error message in `EmulationState.error` should say "Emulation failed -- this is likely because required BIOS files are missing" rather than the raw native crash message.

### AC 4.4: Game Detail Screen BIOS Indicator

- [ ] On the game detail screen in the player app, if the game's console has missing required BIOS files:
  - A warning chip/badge appears near the Play button: "BIOS Required"
  - The chip uses a warning color (amber/orange) consistent with the design system.
  - Tapping the chip shows a tooltip or small info panel listing the missing files.
- [ ] If BIOS is present and valid, no indicator is shown (clean state by default).

### AC 4.5: Console List BIOS Indicator

- [ ] In the library console list, consoles with missing required BIOS show a small warning icon next to the console name.
- [ ] This is a subtle indicator (small icon, not a banner) -- it should inform without being alarming.

---

## Feature 5: Testing

### AC 5.1: Server Tests

- [ ] Unit tests for the known BIOS registry (correct entries, lookup by filename, lookup by console).
- [ ] Unit tests for `GET /api/bios` enriched response (empty dir, valid files, invalid checksums, unknown files, mixed states).
- [ ] Unit tests for `POST /api/admin/bios` (successful upload, checksum validation, overwrite, size limit, path traversal, non-admin rejection).
- [ ] Unit tests for `DELETE /api/admin/bios/:filename` (success, not found, non-admin rejection).
- [ ] Unit tests for `biosStatus` field on game response.

### AC 5.2: Web Tests

- [ ] Playwright E2E test: Admin can navigate to BIOS management page.
- [ ] Playwright E2E test: Upload a BIOS file and see it appear in the list with validation status.
- [ ] Playwright E2E test: Delete a BIOS file and see the status update.
- [ ] Playwright E2E test: Game detail page shows missing BIOS warning when applicable.
- [ ] Vitest unit tests for any new hooks or components.

### AC 5.3: Player App Desktop Tests

- [ ] Desktop E2E test: Game detail screen shows BIOS warning chip when BIOS is missing (using fake repository that reports missing BIOS).
- [ ] Desktop E2E test: Launching a game with missing BIOS shows the missing BIOS dialog.
- [ ] Desktop E2E test: "Try Anyway" button in the dialog proceeds to emulation attempt.
- [ ] Desktop E2E test: Console list shows BIOS warning indicator.
- [ ] No Android-specific E2E tests needed (shared UI logic, no platform-specific BIOS behavior).

### AC 5.4: Full Suite Pass

- [ ] All existing server tests pass (`go test ./...`).
- [ ] All existing web E2E tests pass (Playwright).
- [ ] All existing web unit tests pass (Vitest).
- [ ] All existing desktop E2E tests pass (`player/run-desktop-tests.sh`).
- [ ] Zero regressions.

---

## Non-Goals (Out of Scope)

- **Automatic BIOS detection by content**: We do not scan uploaded files against a universal checksum database to auto-identify them. We match by filename and verify by MD5.
- **BIOS file redistribution**: Spela never bundles or downloads BIOS files from the internet. Users must provide their own legally obtained BIOS files.
- **Per-game BIOS selection**: Some cores support multiple region BIOS files. We don't provide a UI to pick which region BIOS to use per game -- libretro cores handle this automatically.
- **BIOS file encryption/DRM**: Files are stored as-is in the BIOS directory.

---

## Implementation Order (Suggested)

1. **Server: BIOS metadata registry + enriched endpoints** (AC 1.1 - 1.5) -- Foundation that everything else depends on.
2. **Web: Admin BIOS management page** (AC 2.1 - 2.5) -- Admins need to upload files before users can play.
3. **Web: Missing BIOS warnings** (AC 3.1 - 3.3) -- Web users get informed.
4. **Player App: BIOS sync + pre-launch check + warnings** (AC 4.1 - 4.5) -- Player app users get informed.
5. **Testing** (AC 5.1 - 5.4) -- Throughout, but full suite verification at the end.

---

## UX Principles

- **Never show a cryptic error.** If a game fails because of missing BIOS, say exactly that and name the files.
- **Admin-actionable warnings.** Admins see upload links. Non-admins see "contact your admin."
- **Don't block unnecessarily.** Missing BIOS shows a warning, not a hard block. Some cores have HLE fallbacks.
- **Automatic sync in player app.** Users should not have to manually download BIOS files -- if the server has them, the app gets them automatically.
- **Validation on upload.** Admins know immediately if they uploaded the right file.
