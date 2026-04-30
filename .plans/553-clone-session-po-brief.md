# PO Brief — #553 Clone Game Session + #555 Phase 3 (core history)

## Why this exists

Removed in commit `8b7bcb49` along with the game-scoped save model.
Issue #553 reinstates the capability against the new session-scoped
model. Bundled with #555 Phase 3 because clone's promise — "same
state, save still loads" — depends on the player being able to
fetch the same core binary the source session was made with.

UX guidance from the user: cloning is a **secondary** action. The
"Clone session" entry point lives inside `…` action menus, never
as a primary call-to-action.

## Acceptance criteria — three user stories

### US-1: Clone a shared session into a personal session (primary)

**As a** member of a shared session
**I want to** continue the playthrough on my own
**So that** I can finish the game solo from where the group left off.

- **Given** I am a member of shared session `S` containing playthrough
  data with `totalPlayTime = 36000s` (10h)
- **When** I open `S`'s detail screen and tap `…` → "Clone to my library"
- **Then** a new `GameSession` is created owned by me, with:
  - `gameId` = source session's `gameId`
  - `name` = source session's `name + " (Copy)"` by default; user can rename in a confirmation dialog
  - `totalPlayTime` = source's `totalPlayTime` (inherits — "we played 10h together")
  - `pinnedCoreSha256` = source's pinned core SHA (if set)
  - All save-state byte payloads copied from source's most-recent save
  - A new `SessionSaveData` row attached to the new session
- **And** I am navigated to my new session's detail screen
- **And** the original shared session is untouched

### US-2: Clone your own session (checkpointing / branching)

**As a** player about to attempt a tough boss
**I want to** keep a safety checkpoint of my current progress
**So that** I can return to it if my next attempt goes badly.

- **Given** I own session `S` with `totalPlayTime = 60h`
- **When** I open `S`'s detail and tap `…` → "Clone session"
- **Then** I see a confirmation dialog with the default name
  `"S (Copy)"` and an editable text field
- **When** I confirm
- **Then** a new session is created with the same `gameId`, owner,
  inherited `totalPlayTime`, inherited `pinnedCoreSha256`, and a
  copy of the most recent save's bytes. Both sessions appear in
  my list.

### US-3: Clone a specific save within a session

**As a** player who wants to retry from an earlier point
**I want to** pick a specific save in a session's history and clone
  from there
**So that** I can branch off without losing my current progress.

- **Given** session `S` has multiple saves; I pick save `K`
- **When** I open the save's `…` menu and tap "Clone from this save"
- **Then** a new session is created seeded with save `K`'s bytes
  (not the most recent save). Other behavior matches US-1/US-2.

## Acceptance criteria — #555 Phase 3 (the core-history dependency)

US-1/2/3 inherit `pinnedCoreSha256`. For the pin to mean anything,
the player must be able to fetch the historical binary on demand.

- **Given** a session whose `pinnedCoreSha256` is `ab12cd…`
- **When** the player launches the session
- **Then** the player asks the server for the binary by hash
- **And** the server serves the historical binary if it's still on
  disk under retention
- **And if** the historical binary has been pruned, the player
  shows a non-blocking warning ("Original core version no longer
  available. The latest core may not load this save correctly.")
  and continues with the latest core. Save-state failures fall
  through to the existing error handling.

### Server retention policy

- After every core download from buildbot, write the new binary to
  `{CoreDir}/{name}_libretro.{ext}` AND to
  `{CoreDir}/history/{sha256}/{name}_libretro.{ext}`.
- Retention: keep the last **3 versions** OR everything from the
  last **90 days**, whichever set is larger. Background prune job
  removes the rest.
- Endpoint: `GET /api/cores/{id}/download?sha256={hex}` — serves
  the historical binary if present; returns 404 if pruned. Default
  (no `sha256`) returns the latest binary unchanged.

## Out of scope (defer to later issues)

- Admin UI for browsing core history.
- Cross-platform save compatibility checks.
- Bulk re-pin for sessions whose cores were pruned.
- "Roll back this session to an earlier core version" — the pin is
  one-shot at first save.

## Visible non-goals

- No primary "Clone" CTA anywhere. Always `…` menus.
- Cloning is silent on `User.PlayHistory` until the cloned session
  is actually played — we don't want clone spam in the activity feed.
- No notifications to the source session's owner that someone cloned
  their shared session — explicit user opt-in feature for later.

## Test coverage gates (per AGENT_TEAM.md)

- **backend-dev**: Go table tests for the clone endpoint + the
  versioned core download endpoint + the prune job. Permission
  matrix (own / member / non-member / nonexistent session). Save
  byte fidelity (round-trip).
- **macos-dev / android-dev**: desktop tests for both clone entry
  points (own session detail and shared session detail) + the
  successful navigation to the new session. Test coverage for the
  pinned-core load path including the "pruned, fall back to latest
  with warning" branch.
- **web-dev**: vitest unit tests for the clone dialog and `…` menu
  entries. Playwright E2E for the round-trip from shared-session
  detail through to the new personal session detail page.

## Reviewer gates

- **code-reviewer**: API design (idempotency, error shape),
  permission checks, save-byte path safety (no double-write, no
  filesystem races), retention policy correctness.
- **ui-agent**: confirms clone is `…`-menu only on every surface
  (web + player), uses shared `Sp*` / `web/components/ui`
  primitives, dialog matches existing rename/delete dialog
  patterns, no new one-off buttons.
