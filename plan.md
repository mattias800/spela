# ROM Upload Feature — User Stories & Acceptance Criteria

Upload ROMs through the web admin UI. After upload, each ROM is identified, matched against IGDB for metadata, and presented for admin review before being added to the library.

---

## Priority: MVP (must-have for first release)

---

### Story 1: Upload ROMs via the admin UI

**As an** admin,
**I want** to upload one or more ROM files through the web admin UI,
**so that** I can add games to my Spela server without needing SSH or filesystem access.

#### Acceptance Criteria

1. **Dedicated upload page**
   - A new "Upload ROMs" page is accessible from the admin section navigation.
   - The page has a prominent drag-and-drop zone that accepts files.
   - Clicking the drop zone opens a file picker as an alternative to drag-and-drop.
   - The drop zone accepts multiple files in a single operation (batch upload of dozens of files).

2. **Upload behavior**
   - Each file is uploaded to the server via multipart/form-data.
   - Files are stored in a temporary staging area on the server (not in the game library directory).
   - The upload shows per-file progress (uploading / complete / failed).
   - Failed uploads show an error message and can be retried individually.
   - The server rejects files with extensions that are not recognized as ROM files (using the existing `romExtensions` set or equivalent). Rejected files show a clear reason.
   - There is a reasonable maximum file size limit (large enough for disc-based games, e.g. 4 GB) communicated to the user before upload.

3. **Filename sanitization**
   - Uploaded filenames are sanitized to prevent path traversal attacks (following the same pattern as the existing BIOS upload handler).

---

### Story 2: Identify uploaded ROMs and detect console

**As an** admin who just uploaded ROM files,
**I want** the server to automatically detect which console each ROM belongs to,
**so that** I don't have to manually categorize every file.

#### Acceptance Criteria

1. **Automatic console detection from extension**
   - After upload, the server identifies the console for each ROM using its file extension (using the same extension-to-console mapping that the existing scanner uses).
   - For unambiguous extensions (`.nes`, `.sfc`, `.gba`, `.n64`, `.nds`, etc.), the console is assigned automatically with no user input required.

2. **Ambiguous extension handling**
   - For file extensions that map to multiple consoles (`.bin`, `.iso`), the server cannot automatically determine the console.
   - These files are flagged as "console unknown" and the admin must select the correct console from a dropdown before proceeding.
   - The dropdown only shows consoles that support that file extension (e.g., for `.bin` show Genesis, Sega CD, Saturn, PlayStation, Atari 2600, Atari 5200, Atari 7800; for `.iso` show PSX, PS2, PSP, Saturn, Sega CD, Dreamcast, PC-FX).
   - The admin can select a console for multiple ambiguous files at once if they are all for the same system (batch console assignment).

3. **Companion file grouping**
   - `.cue` files and their companion `.bin` files are recognized as belonging together.
   - If a `.cue` file and its referenced `.bin` files are all uploaded in the same batch, they are grouped as a single game entry.
   - If a `.cue` file is uploaded without its companion `.bin` files (or vice versa), the user sees a clear warning that the game is incomplete.

---

### Story 3: Scrape metadata for uploaded ROMs

**As an** admin reviewing uploaded ROMs,
**I want** each ROM to be automatically matched against IGDB for metadata,
**so that** I can see game names, cover art, and ratings before deciding whether to add them to the library.

#### Acceptance Criteria

1. **Automatic metadata scrape**
   - After upload and console detection (Story 2), the server automatically attempts to scrape metadata for each ROM from IGDB (if configured) and LibRetro Thumbnails.
   - The scrape uses the same scraper logic that already exists for library games (IGDB search by cleaned filename + platform ID, LibRetro boxart, No-Intro CRC verification).

2. **CRC32 verification**
   - For cartridge-based ROMs, the server computes CRC32 and checks against No-Intro DAT files (same as the existing scraper does).
   - If a match is found, the ROM is marked as "verified" with the canonical No-Intro name.
   - If no match is found, the ROM is marked as "unverified".
   - For disc-based systems, verification is marked as "not applicable".

3. **Scrape progress feedback**
   - The user sees progress as each ROM is being scraped (e.g., "Scraping 3 of 12...").
   - Scraping failures for individual ROMs do not block the entire batch. Failed ROMs show a warning but can still be accepted into the library (with whatever metadata was found, or none).

---

### Story 4: Review uploaded ROMs before adding to library

**As an** admin,
**I want** to see a preview of each uploaded ROM with its scraped metadata before it gets added to my library,
**so that** I can verify the matches are correct and reject unwanted files.

#### Acceptance Criteria

1. **Game summary card for each upload**
   - After scraping, each uploaded ROM is displayed as a game summary card showing:
     - Cover art (from IGDB or LibRetro, if found)
     - Game title (canonical name from IGDB/No-Intro if matched, otherwise cleaned filename)
     - Console/system name and icon
     - Rating (from IGDB, if available)
     - Verification status badge ("Verified", "Unverified", or "N/A") using the existing VerificationBadge component
     - File name and file size
   - If no metadata was found, the card still shows the filename, file size, detected console, and verification status, with a note that no metadata match was found.

2. **Reusable game summary component**
   - The game summary card is built as a reusable component that can be used in other parts of the app in the future (e.g., search results, import previews).
   - It receives game data as props and does not depend on upload-specific state.

3. **Duplicate detection**
   - Before showing the review screen, the server checks if any uploaded ROM already exists in the library (by CRC32 match or by matching filename + console).
   - Duplicates are clearly tagged with a "Duplicate" badge and a note indicating which existing library game they match.
   - Duplicates are pre-selected for rejection (the admin can still override and accept if desired).

4. **Accept / Reject actions per ROM**
   - Each game summary card has "Accept" and "Reject" buttons.
   - **Accept**: The ROM file is moved from the staging area to the correct library path (`{GameDir}/{console_folder_name}/{filename}`). If the ROM was verified by No-Intro CRC, the file is renamed to the canonical No-Intro name. A Game record is created in the database with all scraped metadata. The game is immediately available in the library.
   - **Reject**: The ROM file is deleted from the staging area. No database record is created.
   - Duplicates that the admin does not override are automatically rejected (file deleted from staging).

5. **Batch actions**
   - An "Accept All" button accepts all non-duplicate ROMs at once.
   - A "Reject All" button rejects all ROMs at once (clears the staging area).
   - The admin can still override individual decisions after using a batch action (e.g., accept all, then reject specific ones).

---

### Story 5: Cleanup staging area

**As an** admin,
**I want** the staging area for uploaded ROMs to be cleaned up automatically,
**so that** unreviewed uploads don't consume disk space indefinitely.

#### Acceptance Criteria

1. **Automatic cleanup**
   - Files in the staging area that have not been accepted or rejected within 24 hours are automatically deleted.
   - The cleanup runs periodically (e.g., on server startup and every hour).

2. **Manual cleanup**
   - The upload page shows the current state of the staging area (number of pending files, total size).
   - A "Clear Staging Area" button deletes all pending uploads at once.

---

## Priority: Important (should-have, implement after MVP)

---

### Story 6: Multi-disc game upload support

**As an** admin uploading disc-based games,
**I want** multi-disc games to be recognized and grouped correctly,
**so that** games like "Final Fantasy VII" (3 discs) are added as a single library entry with an auto-generated `.m3u` playlist.

#### Acceptance Criteria

1. **Disc pattern detection**
   - When multiple uploaded files have the same base name but different disc numbers (e.g., `Final Fantasy VII (Disc 1).cue`, `Final Fantasy VII (Disc 2).cue`, `Final Fantasy VII (Disc 3).cue`), they are automatically grouped as a single multi-disc game.
   - The disc pattern detection uses the same regex as the existing scanner (`(Disc N)`, `[Disk N]`, `(CD N)`, etc.).

2. **M3U playlist generation**
   - When a multi-disc game is accepted, the server automatically generates a `.m3u` playlist file listing all discs in order (same as the existing scanner does for multi-disc games found during directory scanning).
   - The `.m3u` file is stored alongside the disc files in the library.

3. **Single game entry**
   - A multi-disc game appears as a single game summary card in the review screen.
   - The card shows the total file size across all discs and the number of discs.
   - Accepting the game moves all disc files and creates the `.m3u` file in the library.

4. **Incomplete multi-disc uploads**
   - If not all discs of a multi-disc game are uploaded (e.g., Disc 1 and Disc 3 but not Disc 2), the user sees a warning that the set appears incomplete.
   - The admin can still accept an incomplete set if they choose to.

---

### Story 7: Upload progress and large batch UX

**As an** admin uploading a large number of ROMs (20-50+),
**I want** clear progress feedback and a smooth experience,
**so that** I can upload my entire collection without confusion or having to babysit the process.

#### Acceptance Criteria

1. **Upload queue**
   - Files are uploaded sequentially (or with limited concurrency, e.g., 3 at a time) to avoid overwhelming the server.
   - A progress bar shows overall batch progress (e.g., "Uploaded 12 of 47 files").
   - Each file in the queue shows its individual status: queued, uploading (with progress %), complete, or failed.

2. **Resumable batch**
   - If the admin navigates away from the upload page and returns, pending uploads that completed are still visible in the review screen.
   - Failed uploads can be retried without re-uploading the entire batch.

3. **Real-time status via WebSocket**
   - Scraping progress for uploaded ROMs is communicated via WebSocket events (using the existing Hub infrastructure), so the UI updates in real-time without polling.
   - Events include: upload_complete, scrape_progress, scrape_complete for each ROM.

---

## Priority: Nice-to-have (defer to future release)

---

### Story 8: M3U upload support

**As an** admin who has pre-organized multi-disc games with `.m3u` playlists,
**I want** to upload `.m3u` files alongside disc images and have them recognized,
**so that** my existing multi-disc organization is preserved.

#### Acceptance Criteria

1. If an `.m3u` file is uploaded along with the disc files it references, the server uses the `.m3u` as the primary entry point (same as the scanner does).
2. The `.m3u` is parsed to verify all referenced files are present in the upload batch.
3. Missing referenced files produce a clear warning.

---

### Story 9: ZIP/7z archive upload

**As an** admin who has ROMs stored in compressed archives,
**I want** to upload `.zip` or `.7z` files and have them extracted automatically,
**so that** I don't have to extract them manually before uploading.

#### Acceptance Criteria

1. The server extracts uploaded `.zip` and `.7z` archives in the staging area.
2. Extracted files go through the same identification and scraping pipeline as directly uploaded ROMs.
3. The original archive is deleted from staging after extraction.
4. Archives containing non-ROM files (e.g., NFO, TXT, images) have those non-ROM files discarded.

---

## Non-functional Requirements

1. **Security**: All upload endpoints require admin authentication. File paths are sanitized to prevent path traversal. Uploaded files cannot be executed by the server.
2. **Disk space**: The staging area has a configurable maximum size. Uploads that would exceed the limit are rejected with a clear error message.
3. **Performance**: Uploading and scraping should not block other server operations. Scraping of uploaded ROMs should use the same rate limiting as the existing bulk scraper.
4. **Consistency**: The ROM upload flow produces the exact same library structure as the existing directory scanner — same folder paths, same naming conventions, same database records. A game added via upload is indistinguishable from a game added via scan.

---

## Out of Scope

- **Player app changes**: This is purely a web admin feature. No changes to the Kotlin player app.
- **User (non-admin) uploads**: Only admins can upload ROMs. User-facing upload is a separate feature.
- **Automatic library organization**: Uploaded files go to the standard `{GameDir}/{console_folder}/` path. There is no option to customize the destination.
- **ROM patching or conversion**: ROMs are stored as-is. No BPS/IPS patching or format conversion.
