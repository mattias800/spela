# `web/src/types` — conventions

Shared TypeScript types, mostly API contracts. Package-local notes.

## Open-enum fallback via `<Name>Like`

When a UI consumes a string-enum value that the backend may ship
ahead of the frontend catalog (a new security event type, a new
achievement category, etc.), widen the consumer prop type to:

```ts
export type NameLike = Name | (string & {});
```

The `(string & {})` trick preserves IDE autocomplete for the known
union while still accepting arbitrary strings at the type level. A
component receiving `NameLike` can then map known values to rich
metadata and fall back to a neutral "unknown" rendering for values
the frontend catalog doesn't recognize yet — and TypeScript can't
prove the fallback branch is dead code, so it actually gets compiled
in and runs.

Example: `SecurityEventTypeLike` in `api.ts` →
`SecurityEventBadge` in `features/admin/components/` uses it to show
a neutral amber "Unknown event" badge when the backend adds a new
type we haven't styled yet. Strictly typing the prop as
`SecurityEventType` instead would let TypeScript narrow the `?? fallback`
branch away, defeating the point.

Use this pattern whenever a component is a presentation layer for a
backend-owned enum and the frontend shouldn't crash or disappear when
the enum grows.
