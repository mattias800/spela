# Save State Feature Ideas

## Quick wins (small scope)

1. **Save state screenshots** — The `screenshotUrl` field exists on the model but screenshots don't appear to be captured during emulation. Capturing a frame when saving and showing thumbnails in the save list would make picking the right save much easier.
2. **Rename save states** — SRAM saves support renaming, but regular save states don't have a rename endpoint or UI. Users end up with generic names like "Save 1".
3. **Sync status indicator** — `getPendingSyncCount()` exists in the repos but the UI doesn't seem to surface which saves are local-only vs synced. A small cloud icon with a strikethrough for unsynced saves would help.
4. **Save state notes** — Shared saves have descriptions, but personal saves don't. A short note ("right before boss fight", "good grinding spot") is useful for games with many saves.

## Medium features

5. **Quick-save slots** — Classic emulator UX: numbered slots (1-10) with hotkeys during gameplay. Currently saves are just a flat list with names.
6. **Multiple auto-save history** — Right now auto-save upserts a single save. Keeping the last N auto-saves (e.g., 5) as a rolling history protects against saving in a bad state.
7. **Bulk delete** — Users with dozens of saves for a game need a way to select and delete multiple at once.
8. **Save state storage usage** — Show per-game and total storage used by saves, especially useful for self-hosted instances with limited disk.

## Larger features

9. **Save state export/import** — Export saves as standard files for use in other emulators, or import saves from other emulators. Useful for migration.
10. **Rewind** — Frequent background auto-saves (every N seconds) enabling frame-rewind during gameplay. This is a beloved feature in modern emulators like RetroArch.
