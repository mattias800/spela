# Core Upgrade Decision - User Stories

> Issue #672. Part of the broader #555 effort that fingerprinted cores
> (sha256), pinned them per-session + per-save, and kept historical
> binaries under `CoreDir/history/{sha}/`.
>
> Product Owner: Mattias Andersson. UI Agent is drafting the visual
> layer in parallel; this document defines the user-facing behaviour
> those visuals must support.

---

## Summary

When a user goes to resume a game session whose saves were created with
a different core binary than the one the player currently has, we must
**stop and surface the situation** so the user can make an informed
choice. The options are not a binary "new vs old" — the user may
reasonably want to:

- Test their save against the new core, then decide.
- Keep the old (pinned) core for this session's lifetime.
- Accept the new core even knowing their old save may no longer load.
- Not be asked at all, because they don't care about cores.

Today the player either silently auto-updates (risk: break a 60-hour
save) or silently stays on an old binary (risk: missing a crash fix).
The goal of this feature is to replace "silently" with "informed".

### Product stance on the brief

The user brief leads with "test your save, then choose: keep or lock".
We honour that path but treat it as **one of three branches** in the
decision flow, not the whole flow. Specifically, we push back on two
implicit assumptions:

1. **Testing is not mandatory.** Many users will recognise the core
   version change (e.g. "Snes9x 2010 → 2005") and decide instantly
   without wanting to boot the game twice. Forcing a test step would
   feel paternalistic. It is offered, never required.
2. **The choice is per-session, not global.** A user might want the
   new core for *this* game but the old one for another session on
   the same console. Pinning is scoped to the `GameSession`, which
   matches how saves are already keyed.

There is a **global escape hatch** — `User.autoUpdateCoresEnabled` —
for users who just want "always use the newest, don't ask". That
pref already exists; we respect it, and it sits orthogonal to the
per-session decision captured here.

---

## User Stories

### Story 1: User resumes a session on a fresh core version (default case)

**As a** player returning to a session I started on an older core
version,
**I want to** be told before the game boots that the core has
changed, and given a clear choice,
**so that** I'm not surprised when my 20-hour save fails to load or
crashes mid-play.

#### Acceptance Criteria

- AC-1.1: When I select "Continue" on a `GameSession` where
  `PinnedCoreSha256` differs from the current core sha on the server,
  the emulator does **not** start immediately.
- AC-1.2: Instead, a "Core has been updated" screen/dialog appears
  before the emulation screen, naming the core, showing the pinned
  version identifier (human-readable, e.g. "v1.62.1" or the short
  sha), and showing the new version identifier.
- AC-1.3: If a changelog/release note is available from the server's
  core manifest, it is displayed. If not, we simply state "No release
  notes available".
- AC-1.4: The prompt offers three primary actions (exact copy is the
  UI agent's call):
  1. **Test my save on the new core** — try-before-deciding path.
  2. **Keep the previous core for this session** — use the pinned
     sha; the new core is not loaded.
  3. **Use the new core** — proceed with the new binary; warns that
     old saves may not load.
- AC-1.5: A secondary "Cancel" / "Back" option returns to the
  previous screen without starting the game.
- AC-1.6: The decision persists for the rest of this session's life
  — see Story 6 for re-prompt rules.

---

### Story 2: User chooses to test the save on the new core

**As a** cautious player,
**I want to** try loading my most recent save with the new core
before committing,
**so that** I can see whether my playthrough still works without
risking a bad autosave overwrite.

#### Acceptance Criteria

- AC-2.1: Selecting "Test my save" loads the new core and attempts
  to load the session's most recent save state.
- AC-2.2: During the test, **auto-save is disabled** and the test
  run is clearly marked in the UI as a test (not normal play) — the
  user cannot accidentally overwrite their real save.
- AC-2.3: The test runs in the actual emulator (not a hidden
  sandbox). The user sees the game boot and their save load just as
  they would in a normal session. See Open Questions §1 for the
  alternative.
- AC-2.4: While the test is running, an on-screen overlay offers two
  buttons: **"Save works — use new core"** and **"Save is broken —
  keep old core"**.
- AC-2.5: If the save fails to load (core crash, corrupt state,
  incompatible header), the test screen surfaces the failure clearly
  and still offers both choices plus "Try again" and "Cancel".
- AC-2.6: Exiting the test (either choice, or cancel) returns
  control to the decision screen from Story 1 — the final action is
  not taken until the user confirms.
- AC-2.7: No save or play-time metrics are written to the server
  during the test.

---

### Story 3: User chooses to keep the previous core

**As a** player mid-playthrough who doesn't want to risk save
incompatibility,
**I want to** pin this session to the older core version I've been
using,
**so that** I can finish my playthrough without any binary changes.

#### Acceptance Criteria

- AC-3.1: Selecting "Keep the previous core" downloads (if not
  already cached locally) the pinned sha from
  `/api/cores/{id}/download?sha256=…` and uses that binary for this
  session.
- AC-3.2: The `GameSession` is marked as **locked to this sha** on
  the server (see Open Questions §2 for the exact field).
- AC-3.3: Subsequent resumes of this session do NOT re-prompt as
  long as the locked sha is still available on the server.
- AC-3.4: If the pinned binary has aged out of history retention (90
  days / 3 versions) and is no longer downloadable, we fall into
  Story 7.
- AC-3.5: The session detail screen (and game detail's "Continue"
  tile) shows a small badge or chip indicating "Pinned to core
  v1.62.1" so the user knows the session is locked.
- AC-3.6: The user can unlock from the session detail screen —
  selecting unlock shows the Story 1 prompt on next resume.

---

### Story 4: User accepts the new core, knowing saves may break

**As a** player who trusts the new version (bug fixes, feature
parity, etc.),
**I want to** switch this session to the new core and live with
whatever happens to my existing saves,
**so that** I get the latest behaviour without the UI second-guessing
me.

#### Acceptance Criteria

- AC-4.1: Selecting "Use the new core" boots the emulator with the
  current server core sha.
- AC-4.2: Before booting, a compact warning is shown: "Your existing
  save states were made with an older core. They may fail to load or
  behave differently. You can still start a new save."
- AC-4.3: The warning requires a single confirmation (not a deep
  multi-step dialog — the user already made the primary decision in
  Story 1).
- AC-4.4: The session is **not** locked — future upgrades will
  prompt again unless auto-update is turned on.
- AC-4.5: The server updates `GameSession.PinnedCoreSha256` to the
  new sha ONLY if the user successfully loads or creates a new save
  state on this session with the new core. Until that happens, the
  pinned sha stays at the old value so "Go back" paths still work.
  (See Open Questions §3.)

---

### Story 5: User has enabled auto-update (opt-out)

**As a** user who has turned on `autoUpdateCoresEnabled` in
settings,
**I want** the player to silently upgrade cores without prompting me
every single time,
**so that** I'm not nagged if I've already told the app I don't care.

#### Acceptance Criteria

- AC-5.1: When `User.autoUpdateCoresEnabled = true`, the Story 1
  prompt is suppressed.
- AC-5.2: The player uses the new core directly.
- AC-5.3: **Exception — save incompatibility has been observed
  before**: if the session has a record of a prior failed load on a
  different sha (see Open Questions §4), we still prompt regardless
  of auto-update. Rationale: the user said "upgrade silently", not
  "break saves silently".
- AC-5.4: If the user resumes a session that was explicitly
  **locked** via Story 3, auto-update does NOT override the lock.
  The lock always wins. Unlocking requires an explicit action.

---

### Story 6: User starts a brand-new session on a game

**As a** player starting a game I've never played (or whose session
has no save states yet),
**I want** the player to just boot the game with the current core,
**so that** I'm not interrupted with a prompt that has no meaning
for me yet.

#### Acceptance Criteria

- AC-6.1: If the `GameSession` has no `PinnedCoreSha256`, or the
  session has zero associated `SessionSaveState` rows, the Story 1
  prompt is NOT shown.
- AC-6.2: The game boots with the current server core sha.
- AC-6.3: On the first save written in this session, the server
  records that sha as the session's pin and as the save's
  `CoreSha256`. From that point forward, core version changes will
  trigger Story 1.

---

### Story 7: The pinned core binary is no longer available

**As a** player whose session was pinned months ago to a core binary
the server has since aged out,
**I want to** be told clearly that the old binary is gone and what
my options are,
**so that** I understand why I can't "just keep the old core" and
can make a reasonable next step.

#### Acceptance Criteria

- AC-7.1: If `PinnedCoreSha256` is set but the server returns 404 or
  "not retained" for that sha, we show a variant of the Story 1
  prompt with different copy: "The core version this session was
  pinned to is no longer available on this server."
- AC-7.2: The "Keep the previous core" option is disabled (or hidden)
  with an explanation ("retention window: 90 days or 3 versions").
- AC-7.3: The user can still test the save against the current core
  (Story 2) or accept the new core (Story 4).
- AC-7.4: If the user cancels, the session is not modified — they
  can try again later (though the binary won't come back).

---

### Story 8: User re-prompt behaviour within a session's life

**As a** player who already decided what to do for this session
last time,
**I want** the app to remember my decision and not ask me the same
question every time I press "Continue",
**so that** I'm not harassed by repeated prompts.

#### Acceptance Criteria

- AC-8.1: Once the user has picked "Keep previous core" (Story 3),
  the prompt does not reappear on subsequent resumes **unless** the
  pinned binary becomes unavailable (Story 7) or the user explicitly
  unlocks the session.
- AC-8.2: Once the user has picked "Use new core" (Story 4), and a
  save has been written on the new core, the session's pinned sha
  is updated and the prompt does not reappear **unless** the server
  core changes again (a new upgrade).
- AC-8.3: If the user picked "Use new core" but never wrote a save
  (quit out, app crashed, etc.), the next resume will re-prompt
  because the pinned sha was never updated.
- AC-8.4: "Test my save" (Story 2) is never a final decision — the
  follow-up Story 3 or Story 4 outcome is what determines whether
  the prompt comes back.

---

### Story 9: User-initiated review from session detail

**As a** player who wants to change my mind about a locked session,
**I want to** review and change the core-version decision from the
session's detail page without waiting until I press Continue,
**so that** I can plan ahead (e.g. unlock before starting a play
session so I'm not interrupted).

#### Acceptance Criteria

- AC-9.1: The session detail screen shows the current pinned core
  version and whether the session is locked.
- AC-9.2: An action lets the user "Check for core updates" — if
  there's no update, this is a no-op with a "You're up to date"
  confirmation. If there is an update, it opens the Story 1 prompt.
- AC-9.3: For locked sessions, an "Unlock" action is available; it
  does not immediately upgrade anything, it just clears the lock
  so the next Continue will re-prompt.

---

## Decision Flow

The flow below is triggered on **resume** (user hits "Continue" on a
session). Fresh-boot is covered in Story 6 and skips the whole flow.

```
[Continue pressed]
      │
      ▼
Does this session have a PinnedCoreSha256 AND at least one save state?
      │
      ├─ No ──► Boot with current core. Record sha on first save. (Story 6)
      │
      └─ Yes
           │
           ▼
       Fetch current server core sha for this core id.
           │
           ▼
       Does current server sha == PinnedCoreSha256?
           │
           ├─ Yes ──► Boot with pinned sha. No prompt.
           │
           └─ No (core has changed)
                │
                ▼
       Is the session LOCKED (user previously chose Story 3)?
                │
                ├─ Yes
                │    │
                │    ▼
                │   Is the pinned sha still available on server?
                │    │
                │    ├─ Yes ──► Download historical binary, boot. No prompt.
                │    │
                │    └─ No ──► Show Story 7 prompt (binary aged out).
                │
                └─ No
                     │
                     ▼
              Does user have autoUpdateCoresEnabled = true
              AND no known-bad-compat record on this session?
                     │
                     ├─ Yes ──► Boot with new core silently. (Story 5)
                     │
                     └─ No ──► Show Story 1 prompt.
                                   │
                                   ├─ Test save (Story 2) ──► returns here with user's follow-up choice
                                   ├─ Keep previous (Story 3) ──► lock + boot with pinned sha
                                   ├─ Use new (Story 4) ──► confirm warning + boot with new core
                                   └─ Cancel ──► back to previous screen; no state change
```

### Re-prompt rules (summary)

| User's last action | Re-prompt on next resume? |
|---|---|
| Locked to previous core (Story 3) | No, unless binary aged out or user unlocked |
| Accepted new core + saved (Story 4) | No, unless server core changes again |
| Accepted new core, never saved | Yes (pinned sha unchanged) |
| Tested, then locked (Story 2 → 3) | No |
| Tested, then accepted (Story 2 → 4) | Same as Story 4 |
| Auto-update silent upgrade (Story 5) | No |
| Cancelled the prompt | Yes, same prompt on next resume |

---

## Scope

### In scope (v1)

- Decision prompt on session resume when the core sha has changed.
- Three primary actions: test, keep previous, use new (plus cancel).
- Test-save path that boots the new core with auto-save disabled.
- Per-session lock that pins to a specific sha until explicitly
  unlocked.
- Graceful handling when the pinned binary has aged out of retention.
- Honour `autoUpdateCoresEnabled` as a silent-upgrade opt-out, with
  the known-bad-compat exception.
- Session detail UI elements: current pinned version, lock badge,
  unlock action, "check for updates" action.
- Server needs a way to record the lock state and (optionally) any
  known-bad-compat observation — see Open Questions.

### Explicitly out of scope (follow-ups)

- **Per-save-state compatibility flags.** The feature focuses on the
  session's pin. Marking individual saves as "last known to work on
  sha X" is a future improvement.
- **Crowdsourced compatibility data.** "Users report this core works
  with SNES9x-era saves" — interesting, out of scope.
- **Core downgrade from the UI.** The user can pin to the old sha
  but cannot pick an arbitrary historical sha from a list. Keeping
  the choice binary (pinned vs current) keeps v1 shippable.
- **Test-in-sandbox / headless save validation.** See Open Questions
  §1 — v1 uses a real boot with auto-save disabled.
- **Web admin mirror of this flow.** The admin UI shows core
  metadata but users play in the player app. Web admin getting the
  same prompt is a future enhancement if needed.
- **Migrating old saves.** We surface incompatibility, we do not
  attempt to translate save formats.
- **Batch prompting across multiple sessions.** Each session is
  handled on its own resume. "You have 8 sessions affected by this
  core upgrade — review all?" is a future enhancement.

---

## Open Questions

Questions for the engineering side — these need answers before the
backend/Android/macOS developers can start implementing. I have my
preferred answer in parentheses but defer to the engineers.

1. **Can we test-load a save state in a sandbox (headless, no video/
   audio) to validate compatibility before the real emulator boots?**
   — (Preferred v1 answer: no, we boot the real emulator with
   auto-save disabled; a headless-validation subsystem is a future
   optimisation. Needs libretro API confirmation.)

2. **How do we record "this session is locked to sha X"?**
   — Is `GameSession.PinnedCoreSha256` sufficient on its own, or do
   we need an additional `IsLocked bool` / `LockedAt time.Time` so
   we can distinguish "pinned because that's the sha of the first
   save" from "pinned because the user explicitly locked"? The
   distinction matters for Story 8 re-prompt rules.

3. **When exactly do we update `PinnedCoreSha256` on a Story 4 path?**
   — My preference: only after a successful save-write on the new
   core, so the user can change their mind mid-session. Backend
   needs to confirm this is cheap and race-free.

4. **Known-bad-compat memory (Story 5, AC-5.3).**
   — Do we want a small table like `SessionCoreFailure(session_id,
   core_sha, observed_at)` that records "this save failed to load
   on sha X", so we can stop silently upgrading for that session
   even when auto-update is on? Alternative: skip this for v1 and
   always silent-upgrade when auto-update is on. (Preferred v1:
   skip — simpler, revisit if users complain.)

5. **Cancel / back semantics on the prompt.**
   — Where does "Cancel" take the user? Session detail page? Back
   to game detail? This is more of a UX question; flagged here so
   the UI agent and engineers agree.

6. **Core sha availability check: cheap endpoint or rely on 404?**
   — Story 7 needs to know quickly whether an old sha is still
   retained. Should `/api/cores/{id}/manifest` include a list of
   retained historical shas, or do we just attempt download and
   catch 404? The first is nicer UX (we can pre-disable the option);
   the second is simpler.

7. **Testing path + pinned-binary download on slow networks.**
   — If the user picks "Keep previous core" and we need to download
   a historical binary, what's the loading UX? This matters for
   networks on handhelds. Needs UX + engineering agreement.

8. **Multi-device interaction.**
   — User locks session to sha X on desktop. Android tries to
   resume the same session. The pin is server-side so it should
   carry — confirmation please, and specifically: if Android doesn't
   have that sha cached locally, does it download from
   `/api/cores/{id}/download?sha256=…` transparently? (Likely yes;
   needs confirmation.)

9. **What does "test my save" pick as the save to test?**
   — Most recent save state on this session seems obvious. Do we
   give the user a picker? (Preferred v1: most-recent, no picker.)

10. **Core manifest changelog / release notes field — does it exist?**
    — AC-1.3 mentions it. If the server doesn't currently expose
    release notes from the libretro core registry, we ship without
    this in v1 and add it when data is available.

---

## Notes for the UI Agent

- The Story 1 prompt is the defining surface of this feature. It
  needs to look calm and informative, not alarming. This is not an
  error state — it's a choice the user has the right to make.
- "Test my save" is the brief's lead path but NOT the default
  emphasis. The three buttons should be visually equal. Nudging
  toward test unnecessarily slows users who don't want it.
- The "Use new core" confirmation (AC-4.2) should be compact — one
  sentence + confirm. Not another full dialog.
- The lock badge on session detail (AC-3.5) should match the visual
  vocabulary of existing session-state chips/badges. Consult the
  design system for a "pinned / locked" treatment.
- Consider whether the prompt lives in the player app only, or
  whether the web admin needs an equivalent. For v1, player-only is
  fine — see Scope.
