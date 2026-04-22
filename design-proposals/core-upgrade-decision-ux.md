# Core Upgrade Decision — UX Proposal

> Issue #672. Context: #555 (core pinning + versioned binaries + `autoUpdateCoresEnabled`), #664 (server-side retention: 3 versions / 90 days), #667 (admin cores page).

## Summary

When a user resumes a session whose save was written with a different core
binary than the one they currently have (server's latest, a substituted
platform build, or a user-locked version), we surface a **single calm,
informative sheet before the emulator initialises**. The sheet explains
what changed, offers a **"Try with my save"** rehearsal loop, and ends with
one of three durable choices: **keep the new version**, **lock to the old
version** (download the pinned binary from the server), or **update and
start fresh** (ignore the old save state, in-game SRAM stays intact).

Design goals, in priority order:

1. **Never silently break a save.** The sheet is blocking on the very first
   time we see a mismatch for a session. It is not blocking on subsequent
   sessions once the user has decided.
2. **Respect the user's time.** If the user has already answered for this
   session (pinned to old, or accepted the new), never prompt again until
   the environment changes.
3. **Non-alarming.** Spela is a hobby server. A core update is not a crash,
   a loss, or a threat. Copy reads like a neighbour telling you they
   repainted the hallway.
4. **One shared Compose surface.** Works on Android phones, desktop
   (mouse/keyboard/gamepad), and gamepad handhelds. No per-platform
   dialogs.
5. **Reuse existing `Sp*` components.** The existing `CoreMismatchDialog`
   is replaced by a new `SpDecisionSheet` content component built on
   `SpDialog` + `SpCard` + `SpButton`. Raw `Box + clickable + Scrim` usage
   in `CoreMismatchDialog.kt` / `CoreMismatchSaveDialog.kt` is to be
   retired as part of this work.

This replaces the current post-hoc `showCoreMismatchDialog` (which fires
after the emulator has already loaded and auto-load has tried and failed)
and the silent `coreVersionWarning` toast (used when the pinned binary
was pruned). Both become subtypes of one decision surface.

---

## Surfaces & Triggers

### Trigger matrix

| Scenario | Session has saves? | Current core matches pin? | Pin pruned by server? | What we show |
|---|---|---|---|---|
| First ever launch of a new session | No | n/a | n/a | Nothing. Straight to play. |
| Returning to session, current sha == pin | Any | Yes | n/a | Nothing. Straight to play. |
| Returning to session, no pin set yet (legacy) but saves exist | Yes | n/a | n/a | Nothing (silent best-effort — old behaviour; the post-load `CoreMismatchDialog` may still fire if auto-load detects it). |
| Server has a newer core, user has `autoUpdateCoresEnabled = true`, session is pinned to older sha | Yes | No (newer available) | No | **Sheet A — "Core updated"** (this is the primary case for #672). |
| Same as above, but session has no saves at all | No | No | No | Silent upgrade + repin. No sheet. |
| User had locked-to-old, but the pinned binary was pruned server-side | Yes | No (pin no longer available) | Yes | **Sheet B — "Pinned version no longer available"**. |
| `autoUpdateCoresEnabled = false` globally, newer sha detected | Yes | No | No | Nothing at session start (user opted out). Admin-cores page shows the update; session detail page has a "Check for core update" affordance. |
| User explicitly tapped "Check for core update" from session detail | Any | No | No | **Sheet A**. |

### Where the sheet is mounted

- **Compose layer:** inside `EmulationScreen` (the same layer that hosts
  `CoreMismatchDialog` today), but gated **before `libretroController.loadCore`
  is called**. The `EmulationViewModel.startEmulation` flow gets a new
  pre-flight step: after `prepareGameUseCase` resolves a
  `PrepareGameResult`, if the result is flagged as a mismatch-decision-needed
  we short-circuit into `EmulationState.coreDecision = …` and do not load
  the core. The rest of the init (`loadCore` → `loadGame` → `start`) only
  runs when the user resolves the sheet.

- **Not a full-screen route.** The emulator surface is already composed;
  showing a full-screen route would require an extra navigation push that
  the user has to back out of. A modal sheet over the black
  pre-initialisation canvas is lighter and matches the existing pattern
  (BIOS missing dialog, auto-load mismatch dialog).

- **Web admin (`/admin/cores`).** This UI lives entirely in the player
  app. The admin page shows server-side state (which cores have new
  versions, who has old pins) but does not host the decision UI — the
  decision belongs to the individual player, per session. We propose the
  admin page gains a read-only "N sessions still pinned to older
  versions" row for awareness (separate issue).

### Why block before emulator init, not after

The current `CoreMismatchDialog` fires **after** auto-load fails. That's
reactive and depends on the save actually failing in a detectable way.
Many saves load but then diverge (the user's brief: "the save loaded but
the game ran weird for 30 seconds"). We can't catch that from a try/catch
around `retro_unserialize`. So we pre-empt with knowledge we already have
— the sha mismatch is a cheap check at `prepareGameUseCase` time — and
offer the user the **chance to rehearse** before the decision is sticky.

---

## Dialog Specs

All dialogs are implemented via a new shared content component,
`SpDecisionSheet` (details in "Design System Mapping" section). Both
sheets share the same skeleton: title, body paragraph, optional
secondary line, primary action column, and an optional "More options"
disclosure.

### Sheet A — "Core updated" (primary flow, #672)

**Trigger:** session has saves, server core sha ≠ pinned sha, pin binary
is still downloadable, user has not previously decided for this session.

**Anatomy:**

```
┌──────────────────────────────────────────────────────┐
│  [core logo / generic cog icon]                      │
│                                                      │
│  We updated Mednafen PSX HW                          │   ← HeadlineMedium, OnBackground
│                                                      │
│  Your save for Final Fantasy VII was made with an    │   ← BodyMedium,
│  earlier version of the core. It will probably load  │     OnBackgroundSecondary
│  fine — but we'd like you to try it first so you     │
│  can decide what to do.                              │
│                                                      │
│  [  Try with my save  ]           ← primary, gradient (SpButton.Primary)
│  [  Keep the new version anyway  ]← secondary (SpButton.Outlined)
│  [  More options ▾  ]             ← ghost, expands inline
│                                                      │
│  When expanded:                                      │
│   • Lock this session to the older version           │
│   • Remind me the next time I play this              │
└──────────────────────────────────────────────────────┘
```

**Copy:**

- Title: `"We updated {CoreDisplayName}"`
  - If no nice display name exists, fall back to
    `"The {ConsoleName} core has a new version"`.
- Body paragraph 1: `"Your save for {GameTitle} was made with an earlier
  version of the core. It will probably load fine — but we'd like you to
  try it first so you can decide what to do."`
- Hairline info row (only when we have data): `"Last played {relative
  time}  ·  Saved with version {abbrev sha, e.g. v‑a3f9}"`
  — rendered as `LabelSmall`, `OnBackgroundTertiary`. Suppress when any
  field is unknown; never render an empty chip.

**Primary action — "Try with my save"**
Kicks off the Test-Save Flow (see next section). This is the button that
gets initial focus (gamepad-friendly).

**Secondary action — "Keep the new version anyway"**
Starts emulation with the new core and the existing auto-load. If
auto-load then fails, we fall back to the post-load `CoreMismatchDialog`
flow that already exists (unchanged). Choosing this **repins the session
to the new sha** so we don't prompt again.

**Ghost — "More options"** expands inline (no separate dialog) to reveal:
  - `"Lock this session to the older version"` — keeps the pin, downloads
    the historical binary from `/api/cores/{id}?sha256=`. Plays immediately.
  - `"Remind me the next time I play this"` — dismisses the sheet without
    a decision; emulation starts with the new core (same as
    "Keep the new version anyway") **but** we do **not** repin. The sheet
    will fire again on the next `startEmulation` call for this session.

**Dismiss affordance:** back-button / Esc / gamepad-B is equivalent to
"Remind me next time". We do not allow dismiss-to-play-silently without
repinning, because that invites the "why does this keep nagging me" UX.

**Focus order (gamepad):**
1. Primary ("Try with my save")
2. Secondary ("Keep the new version anyway")
3. Disclosure toggle ("More options")
4. (When expanded) "Lock this session…"
5. (When expanded) "Remind me next time"

### Sheet B — "Pinned version no longer available"

**Trigger:** `PrepareGameResult.coreVersionWarning != null`. Server
returned 410/404 for `?sha256={pin}` because the binary was pruned.

**Anatomy:** same skeleton, different copy, no "Lock to old" option.

**Copy:**

- Title: `"The older version isn't available anymore"`
- Body: `"We used to keep the exact core version your save was made with,
  but it's been rotated out of the server's history. We'll use the latest
  {CoreDisplayName} instead. Try your save first — if it looks wrong you
  can start fresh."`
- Primary: `"Try with my save"`
- Secondary: `"Start fresh anyway"`
- No "Lock to old" — there's nothing to lock to.
- "More options" collapsed by default, contains only "Remind me next
  time" (because the user may want to contact the admin).

This replaces the existing silent toast driven by
`EmulationState.coreVersionWarning`.

### Sheet C — post-rehearsal "Did it work?" (new, belongs to the Test-Save Flow)

See the Test-Save Flow section — this is a mid-game overlay with a
different shape than A/B. It is described there so the flow reads in
order.

---

## Test-Save Flow

Goal: let the user try loading their save with the new core binary, see
and feel the result, then commit to a decision — without risking the
durable save file.

### Step-by-step

1. User taps **"Try with my save"** on Sheet A (or B).
2. Sheet A dismisses. We show a thin top-bar banner (`SpInGameBanner`,
   new) over the emulator surface:

   > `[ ★ ] Trying {CoreDisplayName} — your save is untouched. Tap "Did
   > this work?" when you've checked.`

   The banner has a single trailing button: **"Did this work?"**.
3. The emulator initialises normally with the **new** core. `SaveManager`
   loads the save state **but writes nothing back** — we enter a scoped
   "rehearsal" mode: all save writes (auto-save, SRAM flush on exit, slot
   saves) are redirected to a throwaway in-memory buffer for the duration
   of the rehearsal. Manual save attempts from the overlay show an
   explanation (see "Rehearsal save attempt" below).
4. The user plays for as long as they want.
5. At any point they tap **"Did this work?"** on the banner (gamepad:
   Select / desktop: top bar button). This pauses the emulator and opens
   **Sheet C**.

### Sheet C — "Did your save load correctly?"

```
┌──────────────────────────────────────────────────────┐
│  Did your save load correctly?                       │
│                                                      │
│  If the screen looks right and the controls feel     │
│  normal, the new version works.                      │
│                                                      │
│  [  Yes, keep the new version  ]    ← primary
│  [  No, lock to the older version  ]← secondary
│  [  Let me try a bit longer  ]      ← ghost (closes sheet, resumes)
└──────────────────────────────────────────────────────┘
```

Copy is intentionally prescriptive about what to check (framing the
decision around observable signal, not feelings). We avoid asking the
user to evaluate "correctness" in the abstract.

**"Yes, keep the new version"** exits rehearsal mode, repins the session
to the new sha, flushes any pending in-rehearsal state to disk, and
resumes.

**"No, lock to the older version"** backs out: we discard the
in-rehearsal state, tear down the emulator, download the pinned binary,
and restart emulation with the old core. We show an inline banner for
2s on relaunch: `"Locked to version {abbrev}."`.

**"Let me try a bit longer"** dismisses Sheet C and resumes rehearsal.

### Crash during rehearsal

Some platforms run the emulator in-process. A core crash can take the
app down. We mitigate in layers:

1. **Signal handler scope** (desktop / Android native): the existing
   libretro bridge already handles core-initiated aborts by surfacing an
   error state. In rehearsal mode, surface a dedicated
   `EmulationIntent.RehearsalCrashed` that routes to **Sheet D** instead
   of the generic error screen.
2. **App-level crash**: unavoidable on some platforms (Android with a
   misbehaving core that SIGSEGVs before the signal handler is
   registered). We set a sentinel flag
   `rehearsal_crash_pending=true` in the session record **before**
   entering rehearsal. On next launch of the app, if the flag is set,
   route the user directly to Sheet D (after login + session restore)
   instead of straight to the session.

### Sheet D — "It didn't work"

```
┌──────────────────────────────────────────────────────┐
│  That didn't go well                                 │
│                                                      │
│  {CoreDisplayName} ran into a problem while loading  │
│  your save. Your save itself is fine — we'll go      │
│  back to the older version.                          │
│                                                      │
│  [  Lock to the older version  ]    ← primary
│  [  Start fresh on the new version  ]← secondary (outlined)
│  [  More options ▾  ]               ← ghost
│                                                      │
│  More options:                                       │
│   • Report this to the server admin                  │
│   • Just go back — I'll try again later              │
└──────────────────────────────────────────────────────┘
```

Copy is deliberately not scary. We say "ran into a problem", not
"crashed" or "corrupted" — the save is *not* corrupted.

### Ambiguous "it ran, but felt off for a while"

Addressed by:

- **Sheet C's third option** ("Let me try a bit longer") keeping the
  rehearsal alive with no time pressure.
- **A "Compare" affordance on Sheet C's primary screen.** Tapping the
  "★ Trying…" banner chevron exposes a **"Switch to older version for
  comparison"** link. Selecting it ends the rehearsal with no decision,
  downloads the old binary, reloads the game at the pinned version, and
  shows the rehearsal banner again on the *old* core. Now the user can
  compare side-by-side, then answer the "Did it work?" question with
  context. This is the escape hatch for "the save loaded but the game
  ran weird for 30 seconds".

### Rehearsal save attempt (edge case)

If the user hits a quick-save or manual save while in rehearsal mode, we
show a small, non-blocking `SpSnackbar`:

> `"You're in a trial run — saves are paused until you keep this
> version. Tap 'Did this work?' above to decide."`

We do not write the save to disk — this protects the pre-rehearsal
state. On "Yes, keep the new version" we do **not** auto-flush the
rehearsal state (the user didn't explicitly ask to save); we just
re-enable writes going forward.

---

## The Options and Copy

The user's brief was "maybe they want to keep the updated core even if
the savestate doesn't work, they can just load the game normally".
That's Sheet D's **"Start fresh on the new version"** — so we cover it.

### Final button labels (copy library)

Short table so developers don't retype any of this.

| Key | String |
|---|---|
| `core_upd.sheet_a.title` | `"We updated {core}"` |
| `core_upd.sheet_a.title_fallback` | `"The {console} core has a new version"` |
| `core_upd.sheet_a.body` | `"Your save for {game} was made with an earlier version of the core. It will probably load fine — but we'd like you to try it first so you can decide what to do."` |
| `core_upd.sheet_a.meta` | `"Last played {relative}  ·  Saved with version {abbrev}"` |
| `core_upd.sheet_a.try` | `"Try with my save"` |
| `core_upd.sheet_a.keep` | `"Keep the new version anyway"` |
| `core_upd.sheet_a.more` | `"More options"` |
| `core_upd.sheet_a.lock` | `"Lock this session to the older version"` |
| `core_upd.sheet_a.remind` | `"Remind me the next time I play this"` |
| `core_upd.sheet_b.title` | `"The older version isn't available anymore"` |
| `core_upd.sheet_b.body` | `"We used to keep the exact core version your save was made with, but it's been rotated out of the server's history. We'll use the latest {core} instead. Try your save first — if it looks wrong you can start fresh."` |
| `core_upd.sheet_b.try` | `"Try with my save"` |
| `core_upd.sheet_b.fresh` | `"Start fresh anyway"` |
| `core_upd.banner.trying` | `"Trying {core} — your save is untouched."` |
| `core_upd.banner.did_work_btn` | `"Did this work?"` |
| `core_upd.banner.compare` | `"Switch to older version for comparison"` |
| `core_upd.banner.locked_2s` | `"Locked to version {abbrev}."` |
| `core_upd.sheet_c.title` | `"Did your save load correctly?"` |
| `core_upd.sheet_c.body` | `"If the screen looks right and the controls feel normal, the new version works."` |
| `core_upd.sheet_c.yes` | `"Yes, keep the new version"` |
| `core_upd.sheet_c.no` | `"No, lock to the older version"` |
| `core_upd.sheet_c.longer` | `"Let me try a bit longer"` |
| `core_upd.sheet_d.title` | `"That didn't go well"` |
| `core_upd.sheet_d.body` | `"{core} ran into a problem while loading your save. Your save itself is fine — we'll go back to the older version."` |
| `core_upd.sheet_d.lock` | `"Lock to the older version"` |
| `core_upd.sheet_d.fresh` | `"Start fresh on the new version"` |
| `core_upd.sheet_d.report` | `"Report this to the server admin"` |
| `core_upd.sheet_d.later` | `"Just go back — I'll try again later"` |
| `core_upd.snack.rehearsal_save` | `"You're in a trial run — saves are paused until you keep this version. Tap 'Did this work?' above to decide."` |
| `core_upd.session_detail.pinned_chip` | `"Locked to version {abbrev}"` |
| `core_upd.session_detail.unlock_action` | `"Use the latest version instead"` |
| `core_upd.session_detail.check_update` | `"Check for core update"` |
| `core_upd.settings.auto_update_label` | `"Automatically update cores"` |
| `core_upd.settings.auto_update_desc` | `"When off, we'll only switch cores when you say so."` |

### Tone principles we're holding to

- No `"warning"`, `"danger"`, `"corrupted"`, `"broken"`, `"critical"`.
- No exclamation marks anywhere in this copy library.
- The user is "we" or "you"; the system is never "I".
- Core version abbreviation is always rendered `v‑{first 4 chars of sha}`
  with a real hyphen, not a dash-made-to-look-like-a-version-number.
  Example: `v‑a3f9`. This reads as a version label without lying about
  semver.

---

## State Persistence

### What we remember, where it lives, for how long

| Decision | Stored where | Scope | Cleared when |
|---|---|---|---|
| "Keep the new version anyway" / "Yes, keep the new version" | Server: `GameSession.PinnedCoreSha256` updated to the new sha | Per session | Next core update against this new sha. |
| "Lock to the older version" / "No, lock to the older version" | Server: `GameSession.PinnedCoreSha256` keeps the old sha; new `GameSession.UserLockedCoreVersion: bool` set to `true` | Per session | User explicitly unlocks from session detail, or pinned binary is pruned (then Sheet B). |
| "Remind me the next time I play this" | **Nowhere persistent.** In-memory only on the ViewModel. | Per app run | App relaunch. |
| "Start fresh on the new version" (Sheet D) | Session repinned to new sha; `GameSession.AutoLoadSuppressed: bool` set to `true` for this session | Per session | User manually loads a save state from the overlay. |

### New session fields

We add two fields to `GameSession` (backend-dev's call, proposing the
shape):

- `UserLockedCoreVersion bool` — when `true`, even if the user later
  flips global `autoUpdateCoresEnabled` on, this session stays pinned. Also
  flips Sheet A to not show on this session (until unlocked).
- `AutoLoadSuppressed bool` — when `true`, skip auto-load for this
  session's next launch (set after Sheet D "Start fresh"). Cleared on the
  first successful manual save from the overlay.

These are transport fields; per the memory note, they stay as plain
booleans (not `omitempty`).

### Unlocking a lock

Lives in **session detail** (already exists at
`SessionDetailScreen.kt`). Two surfaces:

1. **Chip** at the top of the session detail header:
   - If locked: `[ 🔒 Locked to version v‑a3f9 ]` (non-interactive
     `SpChip` + `SpLink`-styled secondary line: "Use the latest version
     instead").
   - If on latest: no chip (don't add noise).
2. **"Check for core update" ghost button** in the session detail
   overflow menu. Runs the sha check against `/api/cores/{id}/manifest`
   on-demand and, on mismatch, routes directly to Sheet A — this is the
   "let the user opt back in when they feel ready" affordance for the
   `autoUpdateCoresEnabled=false` cohort.

### Global setting

No change to the existing `autoUpdateCoresEnabled`. We clarify its
Settings description (see copy library: `core_upd.settings.auto_update_desc`)
because the current label is ambiguous about what "off" means.

---

## Edge / Empty States

Exhaustive list:

| State | Behaviour |
|---|---|
| Session has no saves at all (brand new) | No sheet. Silently adopt the current sha as the pin on first successful launch. Document #555 Phase 3 already does this — we're explicit that we do not prompt. |
| Current sha == pinned sha | No sheet. Straight to play. |
| Pinned sha pruned (`CorePrunedException`) + session has saves | Sheet B. |
| Pinned sha pruned + session has no saves | Silent repin to latest. No sheet. |
| User is on `autoUpdateCoresEnabled=false` and session is pinned | No sheet at session start (user opted out). They can trigger Sheet A from session detail's "Check for core update" button. |
| Platform core substitution applies (macOS Metal, Android variant names) | The sha of the substituted binary won't match the pin regardless of upstream changes. This case **does not** trigger Sheet A — substitutions are deterministic per-platform, not upgrades. Existing `PrepareGameUseCase` logic stays; we pass an extra flag through `PrepareGameResult.decisionKind = PlatformSubstitution` so the VM doesn't prompt. |
| Network offline when resolving `/manifest` | Don't block. Fall through to cached behaviour; we'll catch the update next launch. |
| Server returns 410 during rehearsal load (rare: pruned mid-flight) | Abort rehearsal to Sheet D ("That didn't go well"). |
| User has multiple sessions with the same game, and answers "keep new" on one | The pin is per-session, not per-game. Each session prompts once. This is intentional — they may have sessions in very different states. |
| Dialog appears during netplay / shared session | **Don't prompt.** Netplay requires binary parity across peers and is handled by a separate negotiation in #555. Skip Sheet A and start the session; if the save fails, fall back to the existing post-load `CoreMismatchDialog`. Log a warning. |
| Challenge mode | Don't prompt. Challenges pin their own core at creation; Sheet A is irrelevant. |
| User dismissed the sheet, game is running, server pushes a new manifest mid-session | Do nothing until next `startEmulation`. |

---

## Design System Mapping

### Existing components we compose from

- `SpDialog` — base modal shell (title + content + button column).
  Sheets A/B/C/D inherit the shell. Current `SpDialog` only supports 2
  buttons; we extend (see "New components" below).
- `SpButton` (Primary, Outlined, Ghost), `SpSecondaryButton`.
- `SpCard` / `SpInnerCard` — the decision sheets sit on
  `SurfaceElevated`; no custom card background needed.
- `SpChip` — for the session detail "Locked to v‑a3f9" chip and for
  the in-banner version chip.
- `SpSnackbar` — for the "You're in a trial run" save-attempt feedback.
- `SpColor.SurfaceElevated`, `SpColor.OnBackground`,
  `SpColor.OnBackgroundSecondary`, `SpColor.OnBackgroundTertiary`,
  `SpColor.Link`. **No `SpColor.Warning` anywhere in this feature** —
  the existing `CoreMismatchDialog` uses it and reads too alarmist for
  the "nothing is broken yet" framing.
- `SpSpacing.XLarge`, `SpSpacing.Default`, `SpSpacing.Small`,
  `SpSpacing.RadiusLarge`.
- `SpTypography.HeadlineMedium`, `BodyMedium`, `LabelSmall`,
  `LabelLarge`.

### New shared components (to be built with this feature)

1. **`SpDecisionSheet`** (Content — Layer 2)
   - Location: `presentation/ui/components/SpDecisionSheet.kt`.
   - Composes `SpDialog` + icon slot + title + body + optional meta row +
     primary/secondary action column + disclosure ("More options") slot.
   - Does NOT accept `Modifier`. Layout is strict.
   - Parameters:
     ```kotlin
     data class SpDecisionAction(
         val label: String,
         val onClick: () -> Unit,
         val style: SpDecisionActionStyle, // Primary, Secondary, Ghost, Destructive
     )

     @Composable
     fun SpDecisionSheet(
         title: String,
         body: String,
         primary: SpDecisionAction,
         secondary: SpDecisionAction? = null,
         metaLine: String? = null,
         icon: @Composable (() -> Unit)? = null,
         moreOptions: List<SpDecisionAction> = emptyList(),
         onDismiss: () -> Unit,
     )
     ```
   - Why reusable: we have the same shape in the planned save-state-slot
     mismatch dialog, the BIOS-missing dialog, and the pending
     "unverified netplay host" flow. Three+ callers is enough to earn
     a shared component.
   - Retires the raw `Box + Scrim + clickable + SurfaceElevated +
     clip(RoundedCornerShape)` pattern in `CoreMismatchDialog.kt` and
     `CoreMismatchSaveDialog.kt` — both get ported.

2. **`SpInGameBanner`** (Design — Layer 1)
   - Location: `presentation/ui/components/SpInGameBanner.kt`.
   - A thin, top-aligned, semi-transparent banner with left icon, text,
     and up to 2 trailing actions. Pauses to host the "Trying …" and
     "Locked to …" messaging over the emulator surface.
   - Already-existing near-equivalent: `SpOfflineBanner` — but that one
     is bottom-aligned, has no action slot, and lives outside the
     emulation surface. We don't extend it; we add a sibling and clearly
     document when each applies. If three banners appear, we'll unify.
   - Parameters:
     ```kotlin
     @Composable
     fun SpInGameBanner(
         text: String,
         icon: ImageVector,
         primaryAction: SpDecisionAction? = null,
         secondaryAction: SpDecisionAction? = null,
         modifier: Modifier = Modifier,
     )
     ```

3. **Role component: `SessionCoreLockChip`**
   - Location: `presentation/ui/feature/sessiondetail/SessionCoreLockChip.kt`.
   - Thin `SpChip` wrapper: lock icon + "Locked to v‑a3f9" + trailing
     "Use the latest version instead" link. Delegates colour/typography
     entirely to `SpChip` and `SpLinkText`.

### Extension to existing component

- **`SpDialog` grows a third button slot** (`tertiaryText`,
  `onTertiary`, `tertiaryStyle`) or exposes a `footer` composable slot
  and the dialogs above use the slot version. Current `SpDialog` hard-codes
  Confirm + Cancel. Rather than special-case, we let `SpDecisionSheet`
  own the button column and use `SpDialog`'s `content` slot for
  everything below the title. This keeps `SpDialog` simple and avoids
  a leaky API.

### Violations to retire as part of this work

- `CoreMismatchDialog.kt` — raw `Box.fillMaxSize().background(Scrim).clickable{}`
  plus raw `Column.fillMaxWidth(0.75f).clip(RoundedCornerShape).background(SurfaceElevated).padding(XLarge)`.
  Port to `SpDecisionSheet`. The dialog remains for the post-load
  auto-load-failed path (it fires after `PrepareGame` succeeded but
  `retro_unserialize` failed) but its copy gets aligned with our new
  tone (no `"Save State Compatibility Warning"` in `SpColor.Warning`).
- `CoreMismatchSaveDialog.kt` — same treatment.

### Design System Review Checklist (self-audit for this proposal)

- [x] No component adds its own outer spacing (`SpDecisionSheet` uses
      `SpDialog`'s padding; all spacing between its rows comes from
      `Arrangement.spacedBy` inside the sheet).
- [x] All visual patterns map to existing or newly shared `Sp*`
      components. Two new shared components are justified by 3+ call
      sites each.
- [x] Platform badges use `SpChip` (lock chip).
- [x] Typography uses `HeadlineMedium` / `BodyMedium` / `LabelSmall`
      from `SpTypography` — no custom line heights, no sizes below
      `BodySmall` for readable content.
- [x] All text uses `OnBackground`, `OnBackgroundSecondary`,
      `OnBackgroundTertiary`. No `Color(0xFF…)` in this spec.
- [x] Cover / icon placeholders follow transparent-black convention
      (icon badge mirrors `SpEmptyState`'s 80dp rounded black-alpha
      container).
- [x] Layout gaps are controlled by the parent `Column`'s
      `Arrangement.spacedBy`; children have no top/bottom padding.
- [x] No hardcoded `dp` or colour literals in any screen file.
- [x] Sections ordered by user relevance (Continue Playing first) — n/a
      for dialogs, but the session detail surface respects it.

---

## Open questions for the Product Owner (flag, don't block)

1. **Default for "Remind me next time"**: should we cap this so the sheet
   doesn't reappear forever? I propose: after 3 dismissals, we default
   the primary to "Keep the new version anyway" and show a quieter
   inline banner instead of a full sheet. Easy to agree to, easy to
   change.
2. **Do we surface "N of your sessions are using a version older than the
   server's current" on the web admin cores page?** I think yes as a
   read-only count — it helps the admin understand why their disk has
   historical binaries hanging around. Separate PR. Flagging for PO
   scoping.
3. **Rehearsal time limit.** I intentionally did not add one. Should we?
   My position: no time limit, because "I put the controller down for
   lunch" is a very real flow. If we ever need to reclaim memory, pause
   the rehearsal but don't kill it.
4. **Should Sheet A's "Lock to older version" require confirmation?** I
   say no — the decision is reversible from session detail and the user
   can always "Check for core update" again. One tap, one outcome.

## Handoff notes to the developers

- `EmulationViewModel.startEmulation` grows a new preflight state that
  emits `EmulationState.coreDecision: CoreDecision?` (a sealed type with
  `UpdateAvailable`, `PinPruned`, `RehearsalPrompt`, `RehearsalCrashed`).
  The existing `showCoreMismatchDialog` fields migrate into this type —
  no new parallel state pile.
- `PrepareGameUseCase` gains `PrepareGameResult.decisionKind` (enum) so
  the VM doesn't re-derive the decision from sha comparisons.
- `SaveManager` gains a `rehearsalMode: Boolean` guard: when true, all
  `writeSram`, `saveState`, `autoSave` calls route to an in-memory
  buffer and emit a `RehearsalSaveBlocked` event for the snackbar.
- Desktop E2E tests (primary): every sheet, the rehearsal flow, the
  compare flow, the crash-to-Sheet-D flow, and the locked-chip on
  session detail. Android smoke test: one end-to-end "core updated →
  try → keep" against the real `/manifest` endpoint.

End of proposal.
