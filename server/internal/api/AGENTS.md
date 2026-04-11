# `server/internal/api` — conventions

Package-local notes on what belongs in `internal/api` and the
invariants HTTP handlers here enforce. Not agent-workflow docs (see
the repo-root `AGENTS.md` for that).

## Gin is allowed here (and only here)

This is the HTTP layer. Handlers, router setup, and any gin-aware
helpers (middleware, context extraction) live here. If a piece of
logic needs both gin-free reuse (background jobs, etc.) AND a
context-aware wrapper, put the gin-free core in `internal/db` and the
wrapper here. See `security_event_recorder.go` for the established
pattern: `recordSecurityEventCtx` extracts IP/path/user from the gin
context and delegates to `db.RecordSecurityEvent`.

## Admin list endpoints that query growable tables

Any endpoint that lists rows from a table that grows unbounded (audit
logs, event streams, etc.) should follow these rules.

### Default time window

Default `since` to a bounded window (30d is the current convention)
when the caller omits the parameter. Clients can opt out of the bound
with `since=all`. Without this, an admin browsing the endpoint when
the table has millions of rows triggers a full-table `Count(*)` that
SQLite can't serve fast.

See `defaultSecurityEventsSince` + `parseSinceParam` in
`security_event_handler.go`.

### Validate filter parameters up front

Structured filter params (IP addresses, timestamps, enum-like fields)
should be validated before they hit the query. Reject garbage with a
`400` and a descriptive message. Silently returning zero rows on a
typo (`10.o.0.1` with letter-o instead of digit-zero) is actively
worse than a clear error — the admin can't tell the difference
between "no matches" and "I mis-typed."

See `ipFilterPattern` + `validateIPFilter`.

### Escape LIKE metacharacters

GORM parameterizes values, but it does **not** escape `%`, `_`, or `\`
inside `LIKE` patterns. If you build a LIKE pattern from user input,
escape the metacharacters first and declare the escape char in the SQL:

```go
escaped := likeEscaper.Replace(strings.ToLower(rawUsername))
q = q.Where(`username_lower LIKE ? ESCAPE '\'`, "%"+escaped+"%")
```

An admin searching for a literal `%` or `_` in a username should match
the character, not get every row back. See `likeEscaper` in
`security_event_handler.go`.

### Surface corrupt JSON instead of dropping it

When a row stores a JSON blob that your response unmarshals into a
map, a parse failure (legacy write, manual DB edit, schema drift)
must not silently drop the field. Log at `slog.Warn` with the row id,
the event/type, the error, and a truncated prefix of the raw blob
(`rawPrefix`). Also surface the raw string on the response via an
escape-hatch field like `metadataRaw` so investigators still see the
data when the UI renders it.

See `toSecurityEventResponse` in `security_event_handler.go`.
