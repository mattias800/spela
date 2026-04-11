# `server/internal/db` — conventions

Package-local notes on what belongs in `internal/db` and the invariants
code here enforces. Not agent-workflow docs (see the repo-root
`AGENTS.md` for that).

## Keep gin out

This package is the data/persistence layer. It must not import `gin`,
`net/http`, or anything HTTP-shaped. Rationale: any non-HTTP caller
(background jobs, WebSocket auth, future netplay session auth, CLI
tools) needs to be able to use the same storage functions without
dragging a web framework in.

Pattern: expose a plain-Go input struct and a plain function.

```go
// internal/db/security_event_recorder.go
type SecurityEventInput struct { /* ... */ }
func RecordSecurityEvent(database *gorm.DB, in SecurityEventInput) { /* ... */ }
```

The gin-aware wrapper that extracts IP/path/user from a `*gin.Context`
lives in `internal/api/security_event_recorder.go` as
`recordSecurityEventCtx`. If you find yourself tempted to import gin
here, stop and put the context-extraction code in `internal/api`
instead.

## Single source of truth for enum-like catalogs

When the server has a closed set of string values that multiple layers
need to know about (security event types, scrape sources, etc.),
declare the canonical list exactly once as a slice in `models.go` and
have every consumer iterate it. See `AllSecurityEventTypes` — it backs
both the database enum validation and the `GetSecurityEventTypes` HTTP
handler, so adding a new event type is a one-line change in exactly
one file on the Go side.

Note: the TypeScript mirror in `web/src/types/api.ts` is still synced
manually. Cross-language codegen from this slice is an open problem
we haven't tackled.

## Audit-log write-amplification guard

`security_event_recorder.go` has an in-memory dedup LRU that suppresses
near-duplicate writes within a time window. It's a reusable pattern for
any audit-log table where the same event can fire rapidly (login
retries, etc.), but **do not generalize it prematurely** — extract a
shared `audit_dedup.go` only once a second audit stream actually needs
it. Candidates: netplay session events, shared session events, shared
save events.
