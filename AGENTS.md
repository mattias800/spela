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

**When you notice something:** create a GitHub issue using `gh issue create`.
Use a descriptive title with the appropriate conventional commit prefix
(`refactor:`, `fix:`, `feat:`, `investigate:`, etc.). Include enough context
in the body for someone to act on it later. Even small observations are
valuable — if it's not part of the current task, log it and move on.

**When to log:** During any task — implementation, code review, UI review, test
writing, bug investigation, or even casual file reads. If something catches your
eye and it's not part of the current task, create an issue and move on.

## Issue Tracking for PRs

Every PR must reference at least one GitHub issue in its body via "Closes #N",
"Fixes #N", or "Refs #N". This keeps the issue → PR → release chain traceable
for future agents and humans.

If the work fits an existing issue, reference it. If not, file an issue first
with `gh issue create`, then reference it in the PR. Multiple issues per PR is
fine when they're naturally bundled.

Drive-by fixes without an issue link are tracking debt — file a one-line issue
even for small things so the change is discoverable later.

## Claiming Issues (avoid duplicate work)

Multiple agents work this repo concurrently. Branches are the lightweight
cross-agent lock — they're visible to everyone on GitHub, unlike per-session
state.

- **Before picking an issue,** check what's already claimed by listing remote
  branches: `git ls-remote --heads origin` or
  `gh api repos/<owner>/<repo>/branches --jq '.[].name'`. Branch names follow
  `feat/<issue-number>-<slug>` (or `fix/…`, `docs/…`), so a remote branch
  maps to a claimed issue. If a branch already exists for an issue, pick
  something else.
- **When you start,** create your `feat/<issue>-<slug>` branch and **push it
  immediately** to plant the claim — before the work is finished. This is free:
  CI only runs on pushes to `master` and on PRs targeting `master`, never on
  arbitrary branch pushes, so an early claim push triggers nothing.
- **Keep the list truthful.** Merged PRs auto-delete their branch
  (`gh pr merge --delete-branch`). If you abandon a claim, delete the branch
  (`git push origin --delete <branch>`) so it doesn't block another agent.
- **Branches are claims, not archives.** Do not leave a remote branch behind
  merely to preserve history; merged PRs, issue comments, and commits already
  do that. If a PR is closed without merge, delete its branch unless the issue
  explicitly says the work is parked and still useful.
- **Parked work must be labelled.** If a branch must remain because it is
  intentionally paused, comment on the linked issue with the branch name,
  owner/status, and the next unblock condition. Prefer a clear `parked/...`
  branch name when the branch should not be treated as an active claim.
- **When cleaning stale branches, be conservative.** Delete branches that are
  provably merged, tied to a closed/abandoned PR, or explicitly released by the
  owner. Leave no-PR branches alone unless the linked issue or owner confirms
  they are obsolete.

## Updating Issues With Findings

Every time you make material progress on an issue — whether that's diagnosing
the root cause, ruling out a hypothesis, picking an implementation approach,
or shipping a fix — add a comment to the issue summarising what you learned.
Future agents (and your future self) read the issue thread to understand what
has and hasn't been tried.

**When to comment on an issue:**
- After diagnosing the root cause: explain what you found and how you found it
- After ruling out an approach: what you tried, what happened, why it didn't
  work, and what that implies for the remaining options
- After shipping a fix: link the merging PR, summarise the implementation, and
  note any limitations or follow-ups
- After discovering a new dimension to the problem (e.g. "this also affects X"):
  edit the issue body or post a comment so it's visible at a glance

**Comment shape:**
- Lead with the headline (what you learned, what you shipped, or what you ruled
  out) — readers should be able to skim
- Include concrete evidence: relevant log lines, file paths, commit SHAs
- Reference the linked PR(s) by number when they exist
- For ruled-out approaches: include enough detail (what was tried, what
  happened) that the next agent doesn't repeat the same spike

**Closing an issue:** when the merging PR uses `Closes #N` in its body,
GitHub auto-closes on merge. Still post a closing comment if the implementation
diverged from the issue's original framing — explain what shipped and why it
differs, so the closed-issue thread reflects reality.

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

### Reviewer prompt template

Generic "review this code" prompts produce shallow generic output. The
reviewer agent finds real bugs when the prompt is **focused**. Use this
template for every dispatch:

1. **What changed.** One paragraph summary of the PR's intent and which
   issue it closes. Saves the reviewer from re-deriving context.
2. **File list with line ranges** of the meaningful changes. Don't make
   the reviewer browse — point at the diff.
3. **Numbered concerns to check.** 3-7 specific questions tailored to
   the change shape. Examples that produced real findings:
   - "Are there races between the timer coroutine and the success
     handler? Could the timer fire AFTER success and overwrite
     fresh state?"
   - "Does the `LaunchedEffect` have a window where `onPlay` fires
     more than once across recompositions?"
   - "Is the default parameter value `onX = onY` semantically
     correct, or a silent footgun for future callers?"
4. **Confidence threshold.** Always: "report only issues with
   confidence ≥ 80% — nitpicks are noise."
5. **Output format.** Always: "markdown comment suitable for posting
   on the PR, with line references."

### When the gate explicitly applies

The gate is mandatory; here's the trigger list so it's hard to skip:

- Any change to a `*ViewModel.kt` or `*Repository.kt`
- Any new server endpoint, DB migration, or BIOS-registry entry
- Any change to authentication, save-state, download, or netplay flows
- Any new shared UI component (`Sp*`), or change to an existing one
- Any change > 200 lines of non-test code

For trivial edits (typo fixes, dependency bumps, comment-only changes)
the gate can be skipped — but the bar is high. When in doubt, dispatch.

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

## Key Documentation

- **`CLAUDE.md`** — Project conventions, code style, testing strategy
- **`ARCHITECTURE.md`** — Full technical architecture
- **`AGENT_TEAM.md`** — Team roles and review checklist
- **`player/RENDERING.md`** — Emulation rendering pipeline: how libretro cores render video across platforms (software vs OpenGL HW vs Vulkan HW), the GPU renderer, common video issues (garbled, flipped, black screen), and key native files
- **`docs/e2e-testing.md`** — How to run web E2E tests reliably: quick start, architecture, troubleshooting, and test patterns
