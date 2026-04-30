# Save Features - User Stories & Acceptance Criteria

## Feature 1: Save State Screenshots

**Priority: P0 (must-have)**

**User story:** As a player, I want to see a screenshot thumbnail for each save state so that I can quickly identify which save to load without guessing from names alone.

**Acceptance criteria:**
- When a save state is created (manual or auto), a screenshot of the current game frame is captured and stored alongside the save data
- The save state list on the game detail screen displays a thumbnail image for each save that has a screenshot
- Save states without screenshots (e.g., older saves created before this feature) display a placeholder icon instead of a broken image
- Screenshots are visible for both personal save states and relay save states
- Shared saves include the screenshot when shared with the community
- Screenshot thumbnails load without noticeably slowing down the save state list

**Scope notes:**
- In scope: Capturing the frame at save time, storing it on the server, displaying thumbnails in save lists
- Out of scope: Full-resolution screenshot viewing, screenshot editing, retroactively generating screenshots for existing saves

---

## Feature 2: Rename Save States

**Priority: P1 (should-have)**

**User story:** As a player, I want to rename my save states so that I can give them meaningful names instead of generic labels like "Save 1".

**Acceptance criteria:**
- Each save state in the save state list has an option to rename it
- The rename action opens an inline text field or dialog pre-filled with the current name
- After confirming the rename, the new name is immediately reflected in the save state list
- Renaming works for both regular save states and relay save states
- Empty names or whitespace-only names are rejected with a clear validation message
- The auto-save cannot be renamed (it retains its system-generated name)

**Scope notes:**
- In scope: Rename UI and server-side persistence for personal and relay saves
- Out of scope: Renaming shared saves (those already have names set at share time), batch renaming

---

## Feature 3: Sync Status Indicator

**Priority: P1 (should-have)**

**User story:** As a player, I want to see which of my saves are synced to the server and which are local-only so that I know whether my progress is safe if I switch devices.

**Acceptance criteria:**
- Each save state in the save list displays a visual indicator showing its sync status (e.g., a cloud icon for synced, a cloud-with-strikethrough for local-only)
- The indicator updates in real time when a save finishes syncing to the server
- When the device is offline, newly created saves show as local-only
- When connectivity is restored and saves sync, their indicators update to synced
- The sync status applies to both save states and SRAM save data
- A summary count of unsynced saves is visible somewhere accessible (e.g., on the game detail screen or a settings/status area)

**Scope notes:**
- In scope: Visual sync indicators on each save, real-time status updates, summary count
- Out of scope: Manual sync trigger button, conflict resolution UI, sync progress bars

---

## Feature 4: Save State Notes

**Priority: P2 (nice-to-have)**

**User story:** As a player, I want to attach a short note to my save states so that I can remember context like "right before boss fight" or "good grinding spot" when I have many saves for a game.

**Acceptance criteria:**
- When creating a manual save state, the user can optionally enter a note (free-form text)
- Existing save states can have notes added or edited after creation
- Notes are displayed below the save state name in the save list
- Notes are limited to a reasonable length (e.g., 200 characters) with a visible character counter
- Notes are synced to the server alongside the save state
- Save states without notes display normally (no empty note placeholder cluttering the UI)

**Scope notes:**
- In scope: Note creation, editing, display, and sync for personal and relay saves
- Out of scope: Rich text formatting, markdown support, notes on auto-saves, notes on SRAM saves

---

## Feature 5: Quick-Save Slots

**Priority: P1 (should-have)**

**User story:** As a player, I want numbered quick-save slots (1 through 10) that I can save to and load from with simple controls during gameplay so that I get the classic emulator save/load experience without interrupting my session.

**Acceptance criteria:**
- During gameplay, the player can access 10 numbered save slots (1-10)
- Saving to a slot overwrites any previous save in that slot without a confirmation dialog
- Loading from a slot instantly restores the game state from that slot
- The currently selected slot number is visible in the in-game overlay
- Each slot shows whether it is empty or occupied (and when it was last saved to)
- Quick-save slots are per-game and per-user
- Quick-save slots are separate from the existing flat save list (they do not appear as regular save states)
- On desktop, keyboard shortcuts allow quick-save (e.g., F5) and quick-load (e.g., F8) to/from the active slot, and slot switching (e.g., F1-F10 or number keys when the overlay is open)

**Scope notes:**
- In scope: Numbered slots, save/load during gameplay, slot selection UI in overlay, keyboard shortcuts on desktop
- Out of scope: Gamepad shortcuts for slot selection, custom slot count configuration, thumbnail previews for slots (can be added later alongside Feature 1)

---

## Feature 6: Multiple Auto-Save History

**Priority: P1 (should-have)**

**User story:** As a player, I want the game to keep the last several auto-saves (not just the most recent one) so that if the game auto-saves in a bad state, I can recover from an earlier auto-save.

**Acceptance criteria:**
- The system keeps a rolling history of the last N auto-saves per game (e.g., 5), rather than overwriting a single auto-save
- The auto-save history is visible in the save state list on the game detail screen, clearly labeled with timestamps (e.g., "Auto Save - 2 minutes ago", "Auto Save - 15 minutes ago")
- The player can load any auto-save from the history, not just the most recent one
- When the history is full, the oldest auto-save is automatically deleted to make room for the new one
- The most recent auto-save is still the one automatically loaded when the game starts (if auto-load is enabled)
- The auto-save history count (N) is reasonable and does not need to be user-configurable initially

**Scope notes:**
- In scope: Rolling auto-save history, display in save list, load any entry, automatic cleanup of oldest
- Out of scope: User-configurable history size, auto-save interval configuration, auto-save history for relay saves

---

## Feature 7: Bulk Delete Saves

**Priority: P2 (nice-to-have)**

**User story:** As a player with many saves for a game, I want to select and delete multiple save states at once so that I can clean up without tediously deleting them one by one.

**Acceptance criteria:**
- The save state list provides a "select" or "edit" mode that allows multiple saves to be selected via checkboxes or similar UI
- A "Delete Selected" action is available when one or more saves are selected, with a confirmation dialog showing how many saves will be deleted
- After bulk deletion, the save list updates immediately to reflect the removed saves
- The auto-save (if present) cannot be selected for bulk deletion
- A "Select All" option is available for convenience
- The selection mode can be cancelled without deleting anything

**Scope notes:**
- In scope: Multi-select UI, bulk delete action with confirmation, select-all
- Out of scope: Bulk operations other than delete (e.g., bulk export, bulk rename), bulk delete across multiple games

---

## Feature 8: Save State Storage Usage

**Priority: P2 (nice-to-have)**

**User story:** As a self-hosted server admin or a player mindful of disk space, I want to see how much storage my save states are using per game and in total so that I can manage disk usage on my server.

**Acceptance criteria:**
- The game detail screen shows the total storage used by save states for that game
- A dedicated storage usage view (accessible from settings or profile) shows the user's total save state storage across all games
- The storage view shows a breakdown by game, sorted by largest first
- Storage values are displayed in human-readable format (KB, MB, GB)
- The storage information includes both save states and SRAM save data
- Storage data refreshes when saves are created or deleted

**Scope notes:**
- In scope: Per-game storage display, total storage view with per-game breakdown, human-readable formatting
- Out of scope: Storage quota enforcement, admin-side aggregate storage views for all users, automatic cleanup suggestions, storage alerts

---

## Feature 9: Save State Export/Import

**Priority: P2 (nice-to-have)**

**User story:** As a player, I want to export my save states as standard files so that I can use them in other emulators, and import save files from other emulators into Spela so that I can continue my progress after migrating.

**Acceptance criteria:**
- Each save state in the save list has an "Export" option that downloads the raw save state file to the device
- An "Import Save" option on the game detail screen allows uploading a save state file from the device
- Imported saves appear in the save state list with a user-provided name
- The export format is the standard libretro save state format (compatible with RetroArch and other libretro-based emulators)
- Export and import also work for SRAM save data
- Invalid or corrupted files are rejected with a clear error message during import
- On desktop, export uses a native file save dialog; on Android, it uses the system file picker

**Scope notes:**
- In scope: Export individual saves, import individual saves, standard libretro format, both save states and SRAM
- Out of scope: Bulk export/import, format conversion for non-libretro emulators, save state format validation beyond basic file integrity

---

## Feature 10: Rewind

**Priority: P2 (nice-to-have)**

**User story:** As a player, I want to rewind gameplay by a few seconds when I make a mistake so that I can quickly recover from deaths or wrong moves without manually loading a save.

**Acceptance criteria:**
- A rewind button is available in the in-game overlay during gameplay
- Pressing rewind steps the game state backwards in time by a small increment (e.g., a few seconds per step)
- Holding or repeatedly pressing rewind continues stepping backwards through the rewind buffer
- The rewind buffer holds a reasonable amount of history (e.g., 30-60 seconds of gameplay)
- When the rewind buffer is exhausted, the rewind action has no further effect (does not crash or produce errors)
- Rewind is disabled during netplay sessions and challenge attempts (to ensure fair play)
- Rewind can be toggled on/off in the emulation settings (since it uses memory and may affect performance on lower-end devices)
- On desktop, a keyboard shortcut triggers rewind (e.g., holding a key to continuously rewind)

**Scope notes:**
- In scope: Rewind button in overlay, frame-stepping backwards, configurable on/off toggle, keyboard shortcut on desktop, disabled in netplay/challenges
- Out of scope: Visual rewind timeline/scrubber, audio during rewind playback, rewind speed configuration, per-game rewind buffer size settings
