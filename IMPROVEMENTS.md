# Improvements

Open TODO list for work that's been noticed but not yet done. Append
new items as they come up. When an item gets done, delete it from this
file — historical record lives in git/PR history, not here.

Durable architectural knowledge that used to accumulate in this file
has moved into per-package `AGENTS.md` files colocated with the code
it describes. See:

- `server/internal/db/AGENTS.md` — gin-free data layer rules,
  enum-catalog SoT pattern, audit-log dedup notes
- `server/internal/api/AGENTS.md` — admin list endpoint conventions
  (default `since` window, filter validation, LIKE escaping, corrupt
  JSON escape hatch)
- `web/src/types/AGENTS.md` — `<Name>Like` open-enum fallback pattern
- `player/shared/src/desktopTest/kotlin/com/spela/player/presentation/viewmodel/AGENTS.md`
  — ViewModel test dispatcher and `cleanup()` rules

Format: `- YYYY-MM-DD [area] Description`

Areas: `server`, `web`, `player`, `infra`, `ux`, `testing`, `docs`, `architecture`

---

- 2026-03-20 [player] SecondaryArtPage.kt and SecondaryScreenContent.kt still use `consoleId.uppercase()` for console labels — should be reviewed to determine if those contexts are genuinely space-constrained or should also use `consoleName`.
- 2026-04-11 [web] No shared `<Divider>` / `<SectionSeparator>` component exists in `web/src/components/ui/`. Admin pages that need in-modal or in-section dividers currently write `border-t border-surface-800/50 pt-4` inline (examples: `security-event-detail-modal.tsx`, `bios-console-card.tsx`, `scrape-status-card.tsx`). Worth extracting once a fourth occurrence shows up. Noticed during PR #360 ui-agent review.
- 2026-04-11 [web] No shared hook for URL-persisted filter state. `security-events-page.tsx` defines a local `updateParams` helper with a null-delete convention that other admin pages with filters (users, games, challenges) would also benefit from. If a second page needs the same pattern, extract `useUrlFilterState()` with typed filter shape + the null-delete convention documented. Noticed while implementing PR #357.
- 2026-04-11 [architecture] The security event recorder's write-amplification guard (in-memory dedup LRU) is a generally useful pattern for any audit-log table. Consider extracting it into a generic `db/audit_dedup.go` if we add a second audit stream (netplay session events, shared session events, shared save events all look like future candidates). Until then, the current shape is fine — don't generalize prematurely. Noticed during PR #361. (Existing pattern documented in `server/internal/db/AGENTS.md`.)
- 2026-04-11 [testing] While validating PR #(tbd) I observed the full `:shared:desktopTest` suite hang for 16 minutes once across 8 runs (then recover and pass). `ChallengeManagerTest` itself is stable 5/5 in isolation. There's a separate, much rarer latent flake somewhere else in the shared desktop suite — possibly related to another VM test that leaks a background coroutine into `runTest`'s scheduler. Worth investigating if any single test starts showing up in CI hang reports. Not urgent right now since 7/8 runs passed quickly. (Dispatcher + cleanup rules that prevent this class of hang are documented in `player/shared/src/desktopTest/kotlin/com/spela/player/presentation/viewmodel/AGENTS.md`.)
