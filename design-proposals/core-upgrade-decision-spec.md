# Core Upgrade Decision — Locked Spec

> Issue #672. Consolidates the product-owner stories
> (`core-upgrade-decision-stories.md`) and the UX agent proposal
> (`core-upgrade-decision-ux.md`) into a single buildable spec.
>
> Read both source docs for the long-form rationale. This file is the
> contract developers build against.

---

## Feature summary

Before starting a game session whose save was made with a different core
binary than what the player currently has, show the user the situation
and let them make an informed decision. Default path is a "try with my
save" rehearsal that lets the user verify compatibility before
committing to either the new core or a lock to the old one.

This feature supersedes the existing post-hoc `CoreMismatchDialog` and
the silent `coreVersionWarning` toast. Both retire.

## Scope — v1 shippable

In:

- Pre-emulator decision sheet when `GameSession.PinnedCoreSha256` ≠
  current server sha256 for the core.
- Four durable decisions: **try**, **keep new**, **lock old**, **remind
  me later** (remind = in-memory only).
- Rehearsal mode: player loads save with new core while all disk writes
  (autosave, SRAM flush, slot saves) route to an in-memory buffer.
- "Did it work?" mid-game prompt driven by a `SpInGameBanner` with a
  single trailing button.
- Crash-during-rehearsal recovery → Sheet D.
- Graceful fallback (Sheet B) when the pinned binary has aged out of
  retention.
- Session-detail surface: lock chip + "Check for core update" +
  "Use latest version instead" (unlock).
- Honour `User.autoUpdateCoresEnabled = true` as an opt-out: skip the
  sheet, silent-upgrade. Explicit user-locked sessions always beat
  auto-update.

Out (follow-ups):

- Web admin mirror of the flow (player-app only for v1).
- Crowdsourced compatibility data, core downgrade UI, save-format
  migration.
- Known-bad-compat memory (a `SessionCoreFailure` table). If users ask
  we'll add it.
- Batch prompting across multiple sessions.
- Changelog / release-notes in the sheet (add when data is available).

## Decision flow (normative)

Triggered from `EmulationViewModel.startEmulation` after
`prepareGameUseCase` resolves and **before** `libretroController.loadCore`.

```
startEmulation()
  │
  ▼
prepareGameUseCase() resolves PrepareGameResult
  │
  ▼
Does session.pinnedCoreSha256 exist AND session has ≥1 SessionSaveState?
  │
  ├─ No ────────────────────────────► proceed to loadCore() directly
  │
  └─ Yes
       │
       ▼
  Is session.userLockedCoreVersion == true?
       │
       ├─ Yes
       │    │
       │    ▼
       │  Is session.pinnedCoreSha256 still available from the server?
       │    │
       │    ├─ Yes ────────────────► download pinned binary, loadCore(pinned)
       │    │
       │    └─ No ─────────────────► Sheet B (pinned version gone)
       │
       └─ No
            │
            ▼
       currentServerSha == session.pinnedCoreSha256?
            │
            ├─ Yes ─────────────────► proceed to loadCore() with current
            │
            └─ No
                 │
                 ▼
            user.autoUpdateCoresEnabled == true?
                 │
                 ├─ Yes ────────────► silent repin to current + loadCore()
                 │
                 └─ No
                      │
                      ▼
                  Sheet A (core updated)
```

## Sheet A — "We updated {core}"

Trigger: above decision flow lands here. Most common case.

Anatomy (reproduced from UX doc so it lives with this spec):

- Title: `"We updated {CoreDisplayName}"` (fallback
  `"The {ConsoleName} core has a new version"`).
- Body: `"Your save for {GameTitle} was made with an earlier version of
  the core. It will probably load fine — but we'd like you to try it
  first so you can decide what to do."`
- Meta row (optional, suppress if any field unknown):
  `"Last played {relative}  ·  Saved with version v‑{abbrev sha}"`.
- Primary (initial gamepad focus): `"Try with my save"` — enters
  rehearsal mode.
- Secondary: `"Keep the new version anyway"` — proceeds with the new
  core; session stays pinned to the old sha until a successful save
  writes on the new core (see "Pin advancement" below).
- "More options ▾" — inline disclosure revealing:
  - `"Lock this session to the older version"` — downloads historical
    binary, sets `UserLockedCoreVersion = true`, loads.
  - `"Remind me the next time I play this"` — in-memory dismiss; runs
    with the new core without touching the pin. Re-prompts on next
    `startEmulation` until the user makes a durable choice.
- Back / Esc / gamepad-B is equivalent to "Remind me next time".

## Sheet B — "The older version isn't available anymore"

Trigger: `UserLockedCoreVersion == true` but the server returns 404/410
for `?sha256={pin}`.

- Title: `"The older version isn't available anymore"`.
- Body: `"We used to keep the exact core version your save was made
  with, but it's been rotated out of the server's history. We'll use
  the latest {core} instead. Try your save first — if it looks wrong
  you can start fresh."`
- Primary: `"Try with my save"`.
- Secondary: `"Start fresh anyway"` — sets `AutoLoadSuppressed = true`,
  repins to the current sha, loads.
- No "Lock" option (nothing to lock to).
- "More options" contains only `"Remind me next time"`.

## Rehearsal mode

State: `SaveManager` gains `rehearsalMode: Boolean`. While true:

- All `writeSram`, `saveState`, `autoSave`, slot-save writes are
  redirected to an in-memory buffer keyed by session id.
- Attempts to manually save emit a `RehearsalSaveBlocked` event that
  surfaces the `core_upd.snack.rehearsal_save` snackbar.
- The emulator runs with the **new** core and loads the most recent
  `SessionSaveState` (ordered by `CreatedAt DESC`) at startup.

UI: `SpInGameBanner` is shown over the emulator surface with copy:
`"Trying {core} — your save is untouched."` and a trailing
`"Did this work?"` button.

"Did this work?" opens **Sheet C**.

Chevron on the banner exposes a secondary link: `"Switch to older
version for comparison"` — ends rehearsal with no decision, downloads
the pinned binary, re-enters rehearsal on the **old** core. Next "Did
this work?" tap operates on the old version.

### Sheet C — "Did your save load correctly?"

- Title: `"Did your save load correctly?"`.
- Body: `"If the screen looks right and the controls feel normal, the
  new version works."`.
- Primary: `"Yes, keep the new version"` — exits rehearsal, discards
  the in-memory buffer (no auto-flush), resumes emulation normally.
  Pin advancement: see rules below.
- Secondary: `"No, lock to the older version"` — tears down emulator,
  downloads pinned binary, restarts emulation with pinned core, sets
  `UserLockedCoreVersion = true`, flashes a 2s inline banner
  `"Locked to version v‑{abbrev}"`.
- Ghost: `"Let me try a bit longer"` — closes Sheet C, resumes
  rehearsal.

### Sheet D — "That didn't go well"

Trigger: core crash / `retro_unserialize` failure / emulator panic
detected during rehearsal. Also fired on next app launch if the
`rehearsal_crash_pending` sentinel is set (see below).

- Title: `"That didn't go well"`.
- Body: `"{CoreDisplayName} ran into a problem while loading your save.
  Your save itself is fine — we'll go back to the older version."`.
- Primary: `"Lock to the older version"` (same behaviour as Sheet C
  secondary).
- Secondary: `"Start fresh on the new version"` — repins to current
  sha, sets `AutoLoadSuppressed = true`, loads new core without
  attempting save-state load.
- "More options" contains `"Report this to the server admin"` and
  `"Just go back — I'll try again later"` (no state change).

### Crash recovery

- **Signal handler scope**: the libretro bridge already surfaces core
  aborts as an error state. Introduce
  `EmulationIntent.RehearsalCrashed` which routes into Sheet D via the
  ViewModel.
- **App-level SIGSEGV**: before entering rehearsal, set
  `GameSession.rehearsalCrashPending = true`. On successful Sheet C /
  Sheet D resolution, clear it. On next app launch, if any session has
  the flag set, route the user directly to Sheet D for that session
  after login + session restore. Document tradeoff: the user has to
  manually resume the session to hit the gate — session-restore path
  checks the flag.

## Pin advancement rules

- **Sheet A → "Lock to older version"**: `PinnedCoreSha256` unchanged,
  `UserLockedCoreVersion = true`.
- **Sheet A → "Keep new version anyway"**: `PinnedCoreSha256`
  **unchanged** at decision time. The pin advances only after a
  successful save-write on the new core (auto-save, manual, or slot).
  This lets the user quit and come back to the old pin if they change
  their mind before writing new state.
- **Sheet A → "Remind me next time"**: no server state change. Session
  plays with the new core for this run; re-prompts on next
  `startEmulation`.
- **Sheet B → "Start fresh anyway"**: repin to current,
  `AutoLoadSuppressed = true`.
- **Sheet C → "Yes, keep the new version"**: identical to Sheet A's
  "Keep new" — pin advances on first successful save.
- **Sheet C → "No, lock to the older version"**: pin unchanged,
  `UserLockedCoreVersion = true`.
- **Sheet D → "Lock to the older version"**: pin unchanged,
  `UserLockedCoreVersion = true`.
- **Sheet D → "Start fresh on the new version"**: repin to current,
  `AutoLoadSuppressed = true`.
- **Auto-update silent path**: repin to current immediately (no saves
  are being risked because the user opted in to silent upgrades).

## Edge cases

| State | Behaviour |
|---|---|
| Session has no saves (brand new) | Skip sheet. Adopt current sha as pin on first successful save (existing Phase 3 behaviour). |
| Current sha == pinned sha | Skip sheet. Straight to loadCore. |
| Pinned sha pruned + session has saves | Sheet B. |
| Pinned sha pruned + session has no saves | Silent repin to latest. No sheet. |
| `autoUpdateCoresEnabled = false`, newer sha detected | Skip sheet at session start. User can trigger Sheet A manually from session detail "Check for core update". |
| Platform core substitution (macOS Metal, Android variant) | Skip sheet. `PrepareGameResult.decisionKind = PlatformSubstitution` so the VM knows the sha mismatch is legitimate and not an upgrade. |
| Network offline when resolving `/manifest` | Skip sheet, fall through to cached behaviour. Try again next launch. |
| Server returns 410 during rehearsal load | Abort rehearsal to Sheet D. |
| Dialog would fire during netplay or shared session | **Skip.** Netplay requires binary parity across peers; that negotiation is separate. Log a warning. |
| Dialog would fire during challenge mode | Skip. Challenges pin their own core. |

## Session detail surface

New chrome on `SessionDetailScreen.kt`:

1. **Lock chip at the top of the header** — visible only when
   `UserLockedCoreVersion == true`. `[ 🔒 Locked to version v‑{abbrev} ]`
   via a new thin role component `SessionCoreLockChip` (delegates to
   `SpChip`). Trailing link: `"Use the latest version instead"` → clears
   the lock and shows Sheet A on next `startEmulation`.
2. **Overflow menu entry**: `"Check for core update"`. Calls
   `/api/cores/{id}/manifest` on demand. No update → show `SpSnackbar`
   `"You're up to date"`. Update present → open Sheet A.

## Settings copy refresh

The existing `autoUpdateCoresEnabled` toggle's description is updated
to `"When off, we'll only switch cores when you say so."` (current copy
is ambiguous about what "off" means).

## Re-prompt rules (summary table)

| Last user action | Re-prompt on next resume? |
|---|---|
| Locked to older (Sheet A or C or D) | No, unless binary aged out (→ Sheet B) or user unlocks from session detail. |
| Accepted new + saved | No, unless server core changes again. |
| Accepted new, never saved | **Yes** — pin never advanced. |
| Rehearsal → "Yes keep new" + saved | Same as "Accepted new + saved". |
| Rehearsal → "Let me try a bit longer" then quit | Yes — no decision was made. |
| Auto-update silent upgrade | No. |
| "Remind me next time" | Yes. |
| Sheet dismissed via back button | Yes (same as "Remind me next time"). |

After 3 consecutive "Remind me next time" dismissals for the same
session, degrade Sheet A to a non-blocking inline banner on the
session detail. Still reachable via "Check for core update". (UX's
proposal; product accepts.)

## Copy library (canonical)

| Key | String |
|---|---|
| `core_upd.sheet_a.title` | `We updated {core}` |
| `core_upd.sheet_a.title_fallback` | `The {console} core has a new version` |
| `core_upd.sheet_a.body` | `Your save for {game} was made with an earlier version of the core. It will probably load fine — but we'd like you to try it first so you can decide what to do.` |
| `core_upd.sheet_a.meta` | `Last played {relative}  ·  Saved with version v‑{abbrev}` |
| `core_upd.sheet_a.try` | `Try with my save` |
| `core_upd.sheet_a.keep` | `Keep the new version anyway` |
| `core_upd.sheet_a.more` | `More options` |
| `core_upd.sheet_a.lock` | `Lock this session to the older version` |
| `core_upd.sheet_a.remind` | `Remind me the next time I play this` |
| `core_upd.sheet_b.title` | `The older version isn't available anymore` |
| `core_upd.sheet_b.body` | `We used to keep the exact core version your save was made with, but it's been rotated out of the server's history. We'll use the latest {core} instead. Try your save first — if it looks wrong you can start fresh.` |
| `core_upd.sheet_b.try` | `Try with my save` |
| `core_upd.sheet_b.fresh` | `Start fresh anyway` |
| `core_upd.banner.trying` | `Trying {core} — your save is untouched.` |
| `core_upd.banner.did_work_btn` | `Did this work?` |
| `core_upd.banner.compare` | `Switch to older version for comparison` |
| `core_upd.banner.locked_2s` | `Locked to version v‑{abbrev}.` |
| `core_upd.sheet_c.title` | `Did your save load correctly?` |
| `core_upd.sheet_c.body` | `If the screen looks right and the controls feel normal, the new version works.` |
| `core_upd.sheet_c.yes` | `Yes, keep the new version` |
| `core_upd.sheet_c.no` | `No, lock to the older version` |
| `core_upd.sheet_c.longer` | `Let me try a bit longer` |
| `core_upd.sheet_d.title` | `That didn't go well` |
| `core_upd.sheet_d.body` | `{core} ran into a problem while loading your save. Your save itself is fine — we'll go back to the older version.` |
| `core_upd.sheet_d.lock` | `Lock to the older version` |
| `core_upd.sheet_d.fresh` | `Start fresh on the new version` |
| `core_upd.sheet_d.report` | `Report this to the server admin` |
| `core_upd.sheet_d.later` | `Just go back — I'll try again later` |
| `core_upd.snack.rehearsal_save` | `You're in a trial run — saves are paused until you keep this version. Tap 'Did this work?' above to decide.` |
| `core_upd.snack.up_to_date` | `You're up to date.` |
| `core_upd.session_detail.pinned_chip` | `Locked to version v‑{abbrev}` |
| `core_upd.session_detail.unlock_action` | `Use the latest version instead` |
| `core_upd.session_detail.check_update` | `Check for core update` |
| `core_upd.settings.auto_update_label` | `Automatically update cores` |
| `core_upd.settings.auto_update_desc` | `When off, we'll only switch cores when you say so.` |

Tone rules:
- No "warning", "danger", "corrupted", "broken", "critical".
- No exclamation marks.
- Version abbrev always `v‑{first 4 chars of sha}` with a real hyphen.

## Design system

New shared components (built as part of this feature):

- **`SpDecisionSheet`** (Content — Layer 2). Hosts sheets A/B/C/D.
  Signature in `core-upgrade-decision-ux.md` §"Design System Mapping"
  is normative. Retires the raw `Box + Scrim + clickable +
  SurfaceElevated` pattern currently in `CoreMismatchDialog.kt` and
  `CoreMismatchSaveDialog.kt`.
- **`SpInGameBanner`** (Design — Layer 1). Used for the rehearsal
  banner and the 2-second "Locked to…" confirmation.
- **Role component `SessionCoreLockChip`** in
  `feature/sessiondetail/`.

Reuse: `SpDialog`, `SpButton` (Primary/Outlined/Ghost), `SpCard`,
`SpChip`, `SpSnackbar`, all `SpColor` / `SpSpacing` / `SpTypography`
tokens. **No `SpColor.Warning` in this feature.**

Retires: `CoreMismatchDialog.kt` is ported to `SpDecisionSheet`; it
stays for the post-load `retro_unserialize` failure path but the copy
is aligned with the new tone. `CoreMismatchSaveDialog.kt` same
treatment.

## Data model changes (server — backend-dev scope)

Additions to `GameSession`:

```go
// True when the user explicitly locked this session to
// PinnedCoreSha256 via Sheet A / C / D. Separate from
// "pin was seeded from the first save". Lock always beats
// autoUpdateCoresEnabled.
UserLockedCoreVersion bool `gorm:"default:false" json:"userLockedCoreVersion"`

// True when the next loadCore for this session should skip
// auto-load-save (e.g. user picked "Start fresh on the new
// version" from Sheet D). Cleared on the first successful manual
// save from the overlay.
AutoLoadSuppressed bool `gorm:"default:false" json:"autoLoadSuppressed"`

// True when a rehearsal was active and an app-level crash is
// suspected. Set before entering rehearsal, cleared on clean
// Sheet C/D resolution. Drives "after app relaunch, go to Sheet D"
// recovery.
RehearsalCrashPending bool `gorm:"default:false" json:"rehearsalCrashPending"`
```

New endpoints:

- **`PATCH /api/sessions/{id}/core-lock`** — body `{ "locked": bool }`.
  Sets / clears `UserLockedCoreVersion`. Requires session ownership.
  Returns updated session.
- **`PATCH /api/sessions/{id}/auto-load-suppressed`** — body
  `{ "suppressed": bool }`. Mirrors above. (Could collapse into one
  "session flags" endpoint — backend-dev's call.)
- **`POST /api/sessions/{id}/rehearsal-crash-sentinel`** — body
  `{ "pending": bool }`. Sets / clears. Same auth.

Alternative: a single `PATCH /api/sessions/{id}` that accepts any
combination of mutable session flags. Backend-dev picks whichever is
more consistent with the existing API surface.

Changes to `/api/cores/{id}/manifest`: add a
`retainedHistoricalShas []string` field so the player can pre-disable
the "Lock to older version" option when the pin is gone, instead of
attempting a download and catching 404. Optional — if the effort is
high, v1 ships with the 404-catch path and we add the field in a
follow-up.

## Data model changes (player — android-dev + macos-dev shared scope)

- `EmulationViewModel.EmulationState` grows
  `coreDecision: CoreDecision?` where `CoreDecision` is a sealed
  class:

  ```kotlin
  sealed class CoreDecision {
      data class UpdateAvailable(
          val coreName: String,
          val coreDisplayName: String,
          val gameTitle: String,
          val oldSha: String,
          val newSha: String,
          val lastPlayedAt: Instant?,
      ) : CoreDecision()

      data class PinPruned(
          val coreName: String,
          val coreDisplayName: String,
          val gameTitle: String,
          val prunedSha: String,
      ) : CoreDecision()

      data class RehearsalPrompt(
          val coreName: String,
          val usingNewSha: Boolean,
      ) : CoreDecision()

      data class RehearsalCrashed(
          val coreName: String,
      ) : CoreDecision()
  }
  ```

- `PrepareGameUseCase.PrepareGameResult` gains
  `decisionKind: DecisionKind` where `DecisionKind` enumerates the
  causes a VM would want to differentiate:

  ```kotlin
  enum class DecisionKind {
      None,
      UpgradeAvailable,
      PinPruned,
      PlatformSubstitution, // do not surface as an "upgrade"
  }
  ```

- `SaveManager` gains `rehearsalMode: Boolean` and redirects
  `writeSram`, `saveState`, `autoSave`, `writeSlotSave` to an
  in-memory buffer while true. Emits `RehearsalSaveBlocked` events
  through a new flow for the snackbar.

- New `CoreLockRepository` (or extend `SessionRepository`) wrapping
  the three new endpoints.

- `SessionDetailViewModel` gains intents for:
  `CheckForCoreUpdate` → calls `/api/cores/{id}/manifest`, compares.
  `UnlockCoreVersion` → calls `/api/sessions/{id}/core-lock` with
  `locked: false`.

## Data model changes (web — web-dev scope)

Minimal for v1:

- Regen generated types pick up the new `GameSession` flags + endpoint.
- `web-dev` adds a read-only **"Core version"** row to the existing
  session detail card in `web/src/features/sessions/…` with
  `"Locked to v‑{abbrev}"` or `"Using version v‑{abbrev}"` — no
  actions for v1. Admin-only "unlock" from the web is a follow-up.
- `autoUpdateCoresEnabled` description refresh on
  `web/src/features/preferences/components/emulation-settings-card.tsx`
  (one-line copy change).

## Testing strategy

**Primary: desktop E2E suite** (`macos-qa`). One test per sheet and
per decision path:

- Sheet A renders with correct copy + pin + current sha data.
- Sheet A → "Try" enters rehearsal; `SpInGameBanner` renders; any
  save attempt during rehearsal surfaces snackbar; "Did this work?"
  opens Sheet C.
- Sheet C → "Yes keep new" exits rehearsal; next save advances pin.
- Sheet C → "No lock old" tears down, downloads pinned binary, reloads;
  `UserLockedCoreVersion = true`.
- Sheet A → "Keep new anyway" proceeds with new core; pin unchanged
  until next save; next `startEmulation` does NOT re-prompt.
- Sheet A → "More options → Lock"; equivalent to Sheet C's secondary.
- Sheet A → "More options → Remind me next time"; no server change;
  next `startEmulation` re-prompts.
- Sheet B renders when `/api/cores/{id}/download?sha256=…` returns
  404/410; no "Lock" option present.
- Sheet D renders on rehearsal crash via
  `EmulationIntent.RehearsalCrashed`.
- `autoUpdateCoresEnabled = true` path skips the sheet.
- Platform substitution path skips the sheet even when sha differs.
- Netplay / challenge mode paths skip the sheet.
- Session detail: lock chip renders; "Use the latest version instead"
  clears the lock; "Check for core update" pings manifest and routes
  to Sheet A when mismatched.
- After 3 "Remind me next time" dismissals, Sheet A degrades to the
  inline session-detail banner.

**Secondary: Android smoke** (`android-qa`). One end-to-end test
against the real `/api/cores/{id}/manifest` + docker backend:
- Session with pin ≠ server sha → Sheet A → "Try" → "Yes keep new" →
  save writes → pin advances. Verify on reload: no prompt.

**Server**: `backend-dev` writes Go tests for the 3 new endpoints
(or 1 consolidated endpoint, their call) covering: owner auth,
non-owner 403, flag round-trip, idempotency.

**Web**: `web-dev` writes one vitest per new UI element (copy
refresh, session-detail lock badge render).

## Open questions retired

From PO doc:
- §1 headless sandbox: **No for v1.** Rehearsal is the real emulator
  with in-memory save redirect.
- §2 lock-state modelling: **Add `UserLockedCoreVersion bool`** as
  above.
- §3 pin advancement on "Keep new": **After first successful save**, as PO
  preferred.
- §4 known-bad-compat memory: **Out of scope for v1.** Flag for
  follow-up.
- §5 cancel semantics: Cancel / back ≡ "Remind me next time".
- §6 retention check: **Optional — add `retainedHistoricalShas` to
  manifest response if easy, else fall back to 404-catch.**
- §7 slow-network download UX: Covered by existing core-download
  progress UI from #555 Phase 2. No new work.
- §8 multi-device: Pin is server-side and carries across devices.
  Downloads historical binary transparently via existing
  `downloadCoreByHash`.
- §9 which save to test: Most recent `SessionSaveState` by
  `CreatedAt DESC`. No picker.
- §10 release notes: Not in v1.

From UX doc:
- §1 "Remind me" limit: **3 dismissals**, then degrade to inline
  banner.
- §2 web admin "sessions on old versions" count: **Follow-up PR.**
- §3 rehearsal time limit: **None.**
- §4 Sheet A "Lock" confirmation: **No.** Reversible from session
  detail.

## Build order (proposed)

1. **PR #1 (backend-dev)**: schema migration + 3 new session flags +
   endpoints + tests. Also the `retainedHistoricalShas` manifest
   extension if low effort.
2. **PR #2 (shared player — android-dev + macos-dev together)**:
   `SaveManager.rehearsalMode` + `CoreDecision` sealed class +
   `PrepareGameResult.decisionKind` + `CoreLockRepository`. No UI
   yet. Desktop unit tests for the VM state machine and
   SaveManager rehearsal routing.
3. **PR #3 (shared player — UI)**: `SpDecisionSheet`, `SpInGameBanner`,
   `SessionCoreLockChip`, Sheet A / B / C / D composables,
   SessionDetailScreen chrome, Settings copy refresh. Retire
   `CoreMismatchDialog`'s raw-Box pattern. Desktop E2E tests for every
   path listed in "Testing strategy".
4. **PR #4 (android-qa smoke)**: one Android instrumented test
   covering the happy path against the real backend.
5. **PR #5 (web-dev)**: session detail "Core version" row + copy
   refresh on the preferences card.

PRs #1 and #2 can proceed in parallel. PR #3 depends on both. PR #4
depends on #3. PR #5 can go any time after #1 lands.

Each PR must reference #672 and #555 in its body. Each PR must be
reviewed by `code-reviewer` and (for PRs touching UI) `ui-agent`
before user handoff.
