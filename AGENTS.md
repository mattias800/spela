# Agent Instructions

## Team-First Development

All larger features, multi-file changes, and cross-component work **must** be implemented
by the full agent team defined in `AGENT_TEAM.md` — never by a single agent working alone.

**Use the team when:**
- Adding a new feature (new screens, API endpoints, database models)
- Making changes that span multiple components (server + web + player)
- Refactoring that touches shared code or crosses architectural boundaries
- Bug fixes that require investigation across multiple files or layers
- Any change that requires both implementation and test coverage across suites

**A single agent may work alone only for:**
- Trivial one-file fixes (typo, single-line bug fix)
- Documentation-only changes
- Adding a test for existing behavior
- Config or dependency updates

When in doubt, use the team. The cost of over-coordinating is low;
the cost of a single agent shipping broken, unreviewed, untested code is high.

## Agent Scope Rules

Each agent **only modifies files in its own domain**:

| Agent | Scope | Files |
|-------|-------|-------|
| `backend-dev` | Server | `server/` |
| `web-dev` | Web frontend | `web/` |
| `android-dev` | Player app | `player/` |
| `macos-dev` | Player app | `player/` |

If an agent needs a change outside its domain (e.g., a new API endpoint), it should
**document the requirement** (expected request/response shape, endpoint path) and let
the responsible agent implement it. Never reach across domain boundaries.

## Team Workflow

See `AGENT_TEAM.md` for the full team definition, roles, and workflow.
The standard workflow is:

1. **Product Owner** defines acceptance criteria
2. **Developers** implement in parallel (backend-dev, web-dev, android-dev, macos-dev)
3. **Code Reviewer** reviews all changes
4. **UI Agent** reviews all visual changes
5. **QA Engineers** run full test suites (web-qa, android-qa, macos-qa)
6. **Product Owner** does final acceptance

No feature is done until all roles have signed off and all tests pass.

## Continuous Improvement

All agents — including the orchestrating agent — must **actively look for improvements**
as they work. This is not a separate task; it's a mindset applied during every read,
review, and implementation. Improvements include but are not limited to:

- Refactoring opportunities (duplicated code, overly complex functions, god files)
- Technical debt (TODO comments, deprecated patterns, missing error handling)
- UX issues (confusing flows, missing states, inconsistent behavior)
- Missing or incorrect use of shared components (raw HTML instead of Button, raw Box instead of Sp*)
- Performance concerns (unnecessary re-renders, N+1 queries, missing indexes)
- Missing test coverage for existing behavior
- Architecture drift (layer violations, wrong dependency direction)
- Features that feel incomplete or could be extended

**When you notice something:** append it to `IMPROVEMENTS.md` in the repo root.
Always append — never remove or reorder existing entries. This creates a timeline
of observations that feeds into planning. Use the format documented at the top of
that file. Multiple entries per session are fine; there are no strict rules on
granularity. Even small observations are valuable.

**When to log:** During any task — implementation, code review, UI review, test
writing, bug investigation, or even casual file reads. If something catches your
eye and it's not part of the current task, log it and move on.

## PR Review Gate

Before presenting any PR to the user for manual testing or merge approval,
the **UI Agent** (`ui-agent`) and **Code Reviewer** (`code-reviewer`) from
`AGENT_TEAM.md` **must** review the implementation. Both agents should be
spawned (with `mode: plan` per AGENT_TEAM.md) and their findings addressed
before the PR is considered ready.

- **Code Reviewer**: Reviews correctness, security, performance, API design,
  test coverage, and adherence to CLAUDE.md conventions.
- **UI Agent**: Reviews visual quality, UX polish, loading/empty/error states,
  design system consistency, and shared component discipline. **Must use the
  Design System Review Checklist** from AGENT_TEAM.md for every UI PR.

If either agent requests changes, fix them before passing the PR to the user.
This gate applies to all PRs, not just team-built features.

## Design System Enforcement

The **Design System Principles** in `AGENT_TEAM.md` are mandatory for all UI
work. Violations are blocking — a PR with design system violations cannot be
merged even if it is functionally correct.

Key rules (see AGENT_TEAM.md for full details):

1. **Components never control their own outer spacing** — parent decides layout
2. **One visual pattern = one shared component** — no duplicated UI patterns
3. **Layout containers are standardized** — use SpSectionList, SpTitledSection, GameShelf
4. **Typography follows hierarchy** — minimum sizes, proper line spacing
5. **Contrast is non-negotiable** — all text must be readable
6. **Console names use full name by default** — abbreviation only when space is genuinely tight
7. **Cover art placeholders use transparent black** — never opaque colored gradients
8. **Sections ordered by user relevance** — Continue Playing first

When in doubt, the question is always: "Does an Sp* component for this exist?
If not, should we create one before proceeding?"

**Shared component default:** All visual elements must use shared `Sp*`
components. Custom/inline UI is only permitted when the element is truly
one-of-a-kind and must NOT be reused (e.g., a platform-specific emulation
surface). If there is any chance the pattern could appear elsewhere, it
must be a shared component. The reviewer must verify: "Is this custom
element intentionally non-reusable, or should it be an Sp* component?"
