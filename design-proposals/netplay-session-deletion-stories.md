# Netplay Session Deletion - User Stories

## Context

Users currently have **no way to remove ended netplay sessions** from their session list. The existing "Cancel Session" action only works for sessions in the "waiting" status and is restricted to the host. Once a session moves to "in_progress" or "ended", there is no delete or hide mechanism - sessions accumulate indefinitely in the user's list.

### Current State Summary

- **API**: `DELETE /api/netplay/sessions/:id` exists but only allows the **host** to cancel **waiting** sessions. It transitions the session to "ended" status rather than removing it.
- **Web UI**: A "Cancel Session" button appears on the session detail page for the host when `status === "waiting"`. There is no button for ended or in-progress sessions, and no button for non-host participants.
- **Session list**: The netplay list page (`/netplay`) shows all sessions where the user is host or client, plus public waiting sessions. Ended sessions remain visible forever.
- **Database**: The `NetplaySession` model has a `DeletedAt` soft-delete field (GORM convention), but it is never used - cancellation sets `status = "ended"` instead.
- **Associated data**: `NetplayInvite` records reference the session. There are no save states, chat messages, or replay data tied to netplay sessions (unlike SharedSessions which have `SharedSessionSave`).
- **Admin**: No admin-specific netplay management page exists. Netplay sessions are only cleaned up as a side effect of admin user deletion.

---

## Story 1: Host Deletes an Ended Session

**As a** session host,
**I want to** permanently remove an ended netplay session from my session list,
**so that** my netplay page stays clean and only shows sessions I care about.

### Acceptance Criteria

- AC-1.1: On the session detail page for an ended session where I am the host, a "Delete Session" button is visible.
- AC-1.2: Clicking "Delete Session" opens a confirmation dialog that names the game and warns the action is permanent.
- AC-1.3: Confirming the dialog removes the session from the database (soft delete) and navigates back to the netplay list.
- AC-1.4: A success toast confirms the session was deleted.
- AC-1.5: The deleted session no longer appears in the session list for either player.
- AC-1.6: If the deletion fails, an error toast is shown and the session remains unchanged.

---

## Story 2: Client Hides an Ended Session

**As a** player who joined someone else's netplay session,
**I want to** remove an ended session from my session list,
**so that** I don't have to look at old sessions I no longer need.

### Acceptance Criteria

- AC-2.1: On the session detail page for an ended session where I am the client (not the host), a "Remove from My List" button is visible.
- AC-2.2: Clicking "Remove from My List" opens a confirmation dialog explaining the session will be hidden from my list but not deleted for the host.
- AC-2.3: Confirming hides the session from my session list only. The host can still see the session.
- AC-2.4: A success toast confirms the session was removed.
- AC-2.5: If I navigate to the session URL directly after hiding it, I can still view it (it is not deleted, just hidden from my list).

---

## Story 3: Host Cancels an Active (In-Progress) Session

**As a** session host,
**I want to** end an in-progress netplay session from the web UI,
**so that** I can clean up sessions that are stuck or that I no longer want to continue.

### Acceptance Criteria

- AC-3.1: On the session detail page for an in-progress session where I am the host, an "End Session" button is visible.
- AC-3.2: Clicking "End Session" opens a confirmation dialog that warns both players will be disconnected and the game will stop.
- AC-3.3: Confirming the dialog ends the session (status becomes "ended", end reason is "host_left").
- AC-3.4: If the other player is connected via WebSocket, they receive a real-time notification that the session has ended.
- AC-3.5: Pending invites for the session are expired.
- AC-3.6: A success toast confirms the session was ended.

---

## Story 4: Session Deletion from the List Page

**As a** user with many past netplay sessions,
**I want to** delete or hide ended sessions directly from the session list,
**so that** I don't have to open each session's detail page one by one.

### Acceptance Criteria

- AC-4.1: Each ended session row in the netplay list shows a delete/remove action (e.g., a trash icon button or a context menu option).
- AC-4.2: Clicking the action opens a confirmation dialog (same behavior as on the detail page).
- AC-4.3: For sessions where I am the host, the action is "Delete" (removes for everyone).
- AC-4.4: For sessions where I am the client, the action is "Remove" (hides from my list only).
- AC-4.5: The session row disappears from the list after successful deletion/removal.

---

## Story 5: Admin Force-Deletes Any Session

**As an** admin,
**I want to** delete any netplay session regardless of who created it or its status,
**so that** I can clean up abandoned, stuck, or problematic sessions.

### Acceptance Criteria

- AC-5.1: Admins can delete any netplay session, not just their own, from the session detail page.
- AC-5.2: The confirmation dialog for admins warns that this will permanently remove the session for all participants.
- AC-5.3: If the session is in-progress, connected players are disconnected and notified via WebSocket.
- AC-5.4: All associated invites for the session are deleted.
- AC-5.5: The admin action works regardless of session status (waiting, in_progress, or ended).

---

## Story 6: WebSocket Notification on Session Deletion

**As a** player connected to a netplay session,
**I want to** be notified immediately if the session is deleted or ended by the host or an admin,
**so that** my player app can gracefully disconnect and show me a clear message instead of hanging.

### Acceptance Criteria

- AC-6.1: When a session is deleted while players are connected, a WebSocket event (`netplay_session_deleted` or `netplay_session_ended`) is broadcast to all connected peers.
- AC-6.2: The event payload includes the reason (e.g., "host_cancelled", "admin_deleted").
- AC-6.3: The player app handles this event by stopping emulation and showing an informational message (not an error).
- AC-6.4: The web UI session detail page shows a "session not found" state if the user navigates to a deleted session.

---

## Out of Scope

- **Bulk delete**: Selecting and deleting multiple sessions at once. This can be a future enhancement.
- **Auto-cleanup**: Automatically deleting old ended sessions after a retention period. Worth considering later but not part of this work.
- **Shared Sessions (turn-based)**: These are a separate feature with their own deletion model (`SharedSession` with `DeletedAt`). This proposal covers only netplay (real-time multiplayer) sessions.
