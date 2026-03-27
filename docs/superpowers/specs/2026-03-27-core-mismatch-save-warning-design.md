# Core Mismatch Save State Warning

**Date:** 2026-03-27
**Status:** Approved

## Overview

When a user plays a game on a different device/core than the one that created the session's save state, warn them before any save state operation that saving will overwrite the original save state with one incompatible with the original device. SRAM (in-game saves) is always saved regardless — it's cross-core compatible.

## Problem

1. User plays on desktop (core A), creates save state + SRAM
2. User opens same game on Android (core B)
3. Existing mismatch dialog at launch gives three options: Try Loading Anyway, Start with Game Save Only, Start Fresh
4. User picks any option and plays the game
5. On exit, auto-save silently overwrites the save state with core B's format
6. User goes back to desktop — save state is now incompatible, and the old one is gone

## Solution

**SRAM is always saved first** — the user's in-game progress is never at risk.

**Save state operations show a warning dialog** when `session.coreName != currentCoreName`:

```
┌─ Save State Compatibility ───────────────────────┐
│                                                   │
│  This session's save state was created with       │
│  [original core]. Saving now will replace it      │
│  with a save state from [current core], which     │
│  won't work on devices using [original core].     │
│                                                   │
│  Your in-game save (game progress) has been       │
│  saved and works on all cores.                    │
│                                                   │
│        [Save State Anyway]  [Skip Save State]     │
│                                                   │
└───────────────────────────────────────────────────┘
```

**Triggers on all save state operations:**
- Auto-save on exit
- Manual save (slot save)
- Quick save

**Always warns** — regardless of which launch mismatch option the user chose (Try Loading Anyway, Game Save Only, or Start Fresh). The fact remains that saving will break compatibility with the original core.

## Implementation

**EmulationState changes:**
- `isCoreMismatched: Boolean = false` — set during game launch when core mismatch is detected, persists for the entire session
- `mismatchedOriginalCore: String = ""` — the core name from the session (for display in the dialog)

**SaveManager changes:**
- Before any save state write, check `isCoreMismatched`
- If true: save SRAM first, then signal ViewModel to show warning dialog
- On "Save State Anyway": proceed with save state write
- On "Skip Save State": complete exit (SRAM already saved)

**New dialog:** `CoreMismatchSaveDialog` composable with two buttons.

**New intents:**
- `ShowCoreMismatchSaveDialog` — triggered by SaveManager when mismatch detected
- `ConfirmCoreMismatchSave` — user chose "Save State Anyway"
- `SkipCoreMismatchSave` — user chose "Skip Save State"

**Exit flow:** When auto-save on exit triggers and `isCoreMismatched` is true, the game pauses, SRAM is saved, dialog is shown. Exit completes after the user's choice.

## What Doesn't Change

- Launch-time core mismatch dialog (3 options) — stays as-is
- SRAM handling — always saved, no changes
- Server-side save state storage — no changes
- Core resolution logic — no changes

## Testing

Desktop E2E tests:
- `isCoreMismatched` is set when session core differs from current core
- Save dialog appears during save state operation when mismatched
- SRAM is saved regardless of dialog choice
- "Save State Anyway" proceeds with save state write
- "Skip Save State" completes without save state write
- Dialog appears for auto-save, manual save, and quick save

## Out of Scope

- Core alias system (treating "mednafen_psx" and "mednafen_psx_hw" as compatible)
- Automatic session forking for different-core play
- Server-side core compatibility database
- User-friendly core name display (showing "PSX Emulator" instead of "mednafen_psx")
