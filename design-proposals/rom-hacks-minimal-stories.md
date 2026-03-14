# ROM Hacks -- Minimal Feature Set

## Context

Spela already handles pre-patched ROMs. If someone drops a patched ROM file into
their games folder, the scanner picks it up, the `[hack]` tag is parsed from the
filename, and it groups with the original game via the variant system (same
normalized GroupKey). It can be played like any other game.

The gaps are:

1. Hacks look identical to regional variants in the UI -- there is no visual
   distinction between "Super Mario Bros (Europe)" and "Super Mario Bros [hack]".
2. There is no way to create a standalone game entry for a total conversion
   (e.g. a hack that is effectively a new game and deserves its own library entry).
3. Admins cannot upload a patch file and have the server apply it to a base ROM --
   they must patch manually and drop the result into the games directory.

This proposal addresses all three gaps with the smallest possible feature set.
No RHDN integration, no archive.org downloads, no on-demand patching, no patch
metadata database.

## How It Works

The admin uploads a patch file (.ips, .bps, .ups, .xdelta) and selects a base
ROM. The server applies the patch **once** at upload time and stores the
resulting ROM file permanently. The patch is a tool, not runtime state -- the
output is a normal ROM file that the system treats like any other game. Save
states, netplay, achievements, and all existing features work automatically.

The admin chooses one of two outcomes:

- **Variant**: the patched ROM becomes a variant of the base game, with a label
  (e.g. "English Translation", "Bugfix v1.2"). It appears in a "ROM Hacks"
  sub-section on the base game's detail page, separate from regional variants.
- **Standalone game**: the patched ROM becomes its own game entry with a "Based
  on [parent game]" link. The admin provides a title and can later set cover art
  and description via existing metadata editing.

---

## Stories

### Story 1: Separate hacks from regional variants in the variants section

**As a** player
**I want to** see ROM hacks listed separately from regional variants on the game detail page
**So that** I can quickly find translations and hack variants without them being mixed in with USA/Europe/Japan versions.

**Acceptance criteria:**
- [ ] On the game detail page, variants with the `hack` tag are displayed in a "ROM Hacks" sub-section, separate from other variants
- [ ] Regional/revision variants without the `hack` tag are displayed in a "Versions" sub-section (the existing behavior, just with a heading)
- [ ] If there are no hack variants, the "ROM Hacks" sub-section is hidden
- [ ] If there are no regional variants, the "Versions" sub-section is hidden
- [ ] Hack variants still show region, revision, and file size chips where applicable
- [ ] This applies to both the web frontend and the player app

**Notes:**
- The `tags` field on `VariantResponse` already contains `"hack"` when parsed from `[hack]` in the filename -- this is a pure UI change
- The player app already has a `VariantsSection` composable that renders all variants in a single list titled "Versions" -- split it into two lists

---

### Story 2: Server-side patch application endpoint

**As a** server admin
**I want to** upload a patch file and have the server apply it to a base ROM
**So that** I do not have to manually patch ROMs on my own machine before adding them to the library.

**Acceptance criteria:**
- [ ] New admin endpoint `POST /api/admin/rom-hacks` accepts a multipart form with: patch file, base game ID, output mode ("variant" or "standalone"), label/title
- [ ] The server applies the patch to the base game's ROM file using the appropriate patching algorithm based on file extension (.ips, .bps, .ups, .xdelta)
- [ ] The patched ROM file is written to the games directory with a descriptive filename
- [ ] If the patch fails to apply (corrupt file, wrong base ROM, unsupported format), the endpoint returns a clear error message
- [ ] The endpoint returns the created game's ID on success
- [ ] Supported patch formats: IPS, BPS, UPS, xdelta

**Notes:**
- Use existing Go libraries for patch application where available; for IPS the format is simple enough to implement directly
- The patch file itself can be discarded after successful application
- The patched ROM filename should include the label/title to be human-readable in the filesystem

---

### Story 3: Create ROM hack as a variant of the base game

**As a** server admin
**I want to** create a patched ROM that appears as a variant of the base game
**So that** translations, bugfixes, and minor hacks are grouped with the original game rather than cluttering the library as separate entries.

**Acceptance criteria:**
- [ ] When the admin selects "variant" mode, the patched ROM is created as a new game entry with the same `GroupKey` and `ConsoleID` as the base game
- [ ] The variant's `tags` field includes `"hack"`
- [ ] The variant inherits the base game's metadata (description, cover art, developer, publisher, genre, release date) so it is not a blank entry
- [ ] The admin-provided label (e.g. "English Translation") is stored and visible in the variant's title or as a tag
- [ ] The variant appears in the "ROM Hacks" sub-section on the base game's detail page (per Story 1)
- [ ] The variant is playable immediately after creation

**Notes:**
- The variant should not become the primary game in the group -- the election algorithm should deprioritize hack-tagged games
- Consider whether to add the label to the title (e.g. "Super Mario Bros [English Translation]") or as a separate display field

---

### Story 4: Create ROM hack as a standalone game

**As a** server admin
**I want to** create a patched ROM that appears as its own game in the library
**So that** total conversions and major hacks that are effectively new games get their own library entry with a custom title, cover, and description.

**Acceptance criteria:**
- [ ] When the admin selects "standalone" mode, the patched ROM is created as a new game entry with a unique `GroupKey` (not grouped with the base game)
- [ ] The admin provides a title for the new game entry
- [ ] The new game entry has a `ParentGameID` field linking it to the base game
- [ ] The standalone hack is listed in the library like any other game
- [ ] The admin can later edit the game's metadata (cover art, description) using the existing metadata editing flow
- [ ] The standalone hack is playable immediately after creation

**Notes:**
- A new nullable `ParentGameID` field on the `Game` model stores the relationship
- The standalone hack inherits the base game's `ConsoleID` but gets its own `GroupKey`

---

### Story 5: "Based on" display for standalone ROM hacks

**As a** player
**I want to** see which game a standalone ROM hack is based on
**So that** I understand the hack's origin and can navigate to the original game.

**Acceptance criteria:**
- [ ] Standalone hack game entries show "Based on [parent game title]" on their detail page, with the parent title as a clickable link
- [ ] Clicking the parent game link navigates to the parent game's detail page
- [ ] The parent game's detail page shows a "ROM Hacks" section listing standalone hacks based on it (title, cover thumbnail, link)
- [ ] If the parent game is deleted, the "Based on" link is hidden gracefully (not a broken link)
- [ ] This applies to both the web frontend and the player app

**Notes:**
- The API game detail response needs a new `parentGame` field with `{ id, title, coverUrl }` when `ParentGameID` is set
- The API game detail response needs a new `romHacks` array listing standalone games where `ParentGameID` points to this game
- On the parent's detail page, the "ROM Hacks" section is distinct from the variants-based "ROM Hacks" sub-section in Story 1 -- this one lists standalone games, the other lists hack-tagged variants

---

### Story 6: Admin "Add ROM Hack" page in the web UI

**As a** server admin
**I want to** a dedicated page in the admin panel for creating ROM hacks
**So that** I can easily select a base game, upload a patch, and choose how to treat the result.

**Acceptance criteria:**
- [ ] A new "Add ROM Hack" page is accessible from the admin navigation
- [ ] The page has a game search/picker to select the base game (with cover art preview)
- [ ] The page has a file upload input for the patch file, restricted to supported extensions (.ips, .bps, .ups, .xdelta)
- [ ] The page has a mode selector: "Add as variant" or "Create standalone game"
- [ ] In variant mode, the admin provides a label (e.g. "English Translation")
- [ ] In standalone mode, the admin provides a game title
- [ ] A "Create" button submits the form and shows a progress/loading state while the patch is being applied
- [ ] On success, the admin is navigated to the resulting game's detail page
- [ ] On error, the error message is displayed clearly (e.g. "Patch failed: CRC mismatch")

**Notes:**
- Reuse the existing `GamePicker` component (`web/src/components/game-picker.tsx`) for base game selection
- The existing admin upload page (`upload-roms-page.tsx`) can serve as a reference for the file upload UX pattern

---

### Story 7: Player app support for "Based on" and "ROM Hacks" sections

**As a** player (using the native player app)
**I want to** see the "Based on" link and "ROM Hacks" section on game detail screens
**So that** I can discover and navigate between original games and their standalone hacks.

**Acceptance criteria:**
- [ ] The player app's game detail screen shows "Based on [parent game title]" when the game has a parent, with a tap target that navigates to the parent game
- [ ] The player app's game detail screen shows a "ROM Hacks" section listing standalone hacks based on this game, each tappable to navigate to the hack's detail page
- [ ] The "ROM Hacks" section is hidden when there are no standalone hacks
- [ ] No other player app changes are needed -- standalone hacks are regular game entries that work with downloads, saves, and all existing features

**Notes:**
- The player app's `GameDetail` model and DTO mappers need to include the new `parentGame` and `romHacks` fields from the API
- Standalone ROM hacks are regular game entries from the player app's perspective -- they download, save, and play through the existing code paths
