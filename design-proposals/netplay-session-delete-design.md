# Design: Delete Netplay Sessions

## Current State

The netplay session detail page (`web/src/pages/netplay-session-page.tsx`) already has:
- A `useDeleteNetplaySession` hook that calls `DELETE /netplay/sessions/:id`
- A "Cancel Session" button with `variant="danger"` — but **only for hosts, only when status is `"waiting"`**
- A custom `Modal` for cancel confirmation (not using the shared `ConfirmDeleteModal`)

**The gap:** There is no way to delete sessions that are `"in_progress"` or `"ended"`. Once a session moves past `"waiting"`, it stays in the list forever.

## Design Recommendations

### 1. Session Detail Page — Primary Delete Location

Add a "Delete Session" button on the session detail page for **ended sessions**. This is where the user has full context about the session.

**Placement:** In the action area next to the existing "Create New Session" button that already appears for ended sessions. Use the same row.

**Visibility rules:**
| Status | Host sees | Non-host sees |
|---|---|---|
| `waiting` | "Cancel Session" (existing) | Nothing |
| `in_progress` | Nothing (cannot delete active session) | Nothing |
| `ended` | "Delete Session" | Nothing (only host can delete) |

**Rationale for not allowing in-progress deletion:** Active sessions have connected players. Deletion should require ending the session first. The existing cancel flow handles the `waiting` state.

**Component pattern:** Use the existing `ConfirmDeleteModal` from `@/components/ui/confirm-delete-modal.tsx` instead of the current inline `Modal`. This aligns with how `SessionCard`, `DeleteDeviceModal`, and `HardDeleteUserModal` handle destructive actions.

```tsx
// In the action area, after the existing "Create New Session" button:
{isHost && session.status === "ended" && (
  <Button
    variant="danger"
    size="sm"
    onClick={() => setShowDeleteModal(true)}
  >
    <Trash2 className="h-5 w-5" />
    Delete Session
  </Button>
)}

<ConfirmDeleteModal
  open={showDeleteModal}
  onClose={() => setShowDeleteModal(false)}
  title="Delete Netplay Session"
  message={`Delete the "${session.gameTitle}" session with ${session.clientUsername ?? "no opponent"}? This cannot be undone.`}
  onConfirm={handleDelete}
  isPending={deleteSession.isPending}
  actionLabel="Delete Session"
/>
```

### 2. Session List — Hover Action on Rows

Add a delete icon button on `NetplaySessionRow` for ended sessions, following the exact pattern from `SessionCard` (ghost button with `Trash2` icon, red color, revealed on hover).

**Implementation in `netplay-session-row.tsx`:**
- Accept `onDelete` and `isDeleting` props
- Show a `Trash2` ghost button on hover (right side, before the invite code area)
- Only render for ended sessions where the current user is the host
- Use `ConfirmDeleteModal` for confirmation
- Stop click propagation so the row link is not triggered

This follows the established pattern in `SessionCard` where actions appear in an `opacity-0 group-hover:opacity-100` container.

### 3. Refactor: Replace Inline Cancel Modal

The existing "Cancel Session" flow on the detail page uses an inline `Modal` with manually written confirm/cancel buttons. Refactor to use `ConfirmDeleteModal`:

```tsx
<ConfirmDeleteModal
  open={showCancelModal}
  onClose={() => setShowCancelModal(false)}
  title="Cancel Session"
  message={`Cancel "${session.gameTitle}"? The invite code will no longer work.`}
  onConfirm={handleCancel}
  isPending={deleteSession.isPending}
  actionLabel="Cancel Session"
/>
```

### 4. Feedback

On success: `toast("success", "Session deleted")` and `navigate("/netplay")` (same pattern as existing cancel flow).

On error: `toast("error", "Failed to delete session")`.

### 5. No Admin Override Needed

The existing `useDeleteNetplaySession` hook calls the same `DELETE /netplay/sessions/:id` endpoint. The backend should enforce that only the host (or an admin) can delete. No new API work is needed on the frontend — the hook already exists and invalidates the session list cache on success.

## Files to Modify

1. **`web/src/pages/netplay-session-page.tsx`** — Add delete button for ended sessions, refactor cancel modal to use `ConfirmDeleteModal`
2. **`web/src/features/netplay/components/netplay-session-row.tsx`** — Add hover delete action for ended sessions (needs `onDelete`, `isDeleting`, `currentUserId` props)
3. **`web/src/pages/netplay-page.tsx`** (or wherever `NetplaySessionRow` is rendered) — Wire up delete handler with `useDeleteNetplaySession` and pass props to rows

## Consistency Checklist

- [x] Uses `ConfirmDeleteModal` (matches `SessionCard`, `DeleteDeviceModal`)
- [x] Uses `Button variant="danger"` (matches `SharedSessionHero`, cancel flow)
- [x] Uses `Trash2` icon (matches all other delete actions in the app)
- [x] Uses `toast()` for feedback (matches cancel flow, `HardDeleteUserModal`)
- [x] Uses `useDeleteNetplaySession` hook (already exists)
- [x] Host-only restriction (matches shared session owner-only delete)
- [x] No deletion of active sessions (safe by design)
