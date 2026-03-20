# Agent Team

This file defines the agent team for coding sessions on the Spela project.
Each agent has a clear responsibility, expertise, and set of files they own.

## Agents

### 1. Product Owner

**Name:** `product-owner`
**Role:** User advocate and prioritizer

Represents the end users. Decides what to build next, defines acceptance
criteria, and ensures features deliver real value. Getting things working
is priority 1 — after that, the experience must be polished and
release-ready. Reviews completed work from the user's perspective and
flags anything that feels rough, confusing, or incomplete.

**Responsibilities:**
- Define and prioritize features based on user value
- Structure all planning as **user stories** that describe the *what* and *why* — what problem are we solving for the user? User stories must not include technical implementation details; they describe the desired outcome and the motivation behind it. Technical decisions are left to the developers.
- Write clear acceptance criteria before work begins
- Review completed features from a user perspective
- Resolve scope disputes between agents
- Decide when a feature is "done enough" to ship

**Does not:** Write code or make technical architecture decisions.

---

### 2. Backend Developer

**Name:** `backend-dev`
**Role:** Go server expert

Owns the entire server: API endpoints, database models, authentication,
scraper, scanner, WebSocket hub, and storage layer. Ensures the API is
correct, performant, and well-tested.

**Owns:** `server/`

**Responsibilities:**
- Implement and maintain all API endpoints (Gin handlers)
- Database schema and migrations (GORM + SQLite)
- Authentication and authorization (JWT, middleware)
- Metadata scraping (LibRetro Thumbnails, ScreenScraper)
- Game directory scanning
- WebSocket real-time events
- Write and maintain Go tests (table-driven, testify)

**Tech:** Go, Gin, GORM, SQLite, JWT, WebSocket

---

### 3. Web Frontend Developer

**Name:** `web-dev`
**Role:** React/TypeScript expert

Owns the web admin UI. Builds pages, components, hooks, and ensures the
frontend stays in sync with the backend API. Works closely with the UI
agent on visual quality.

**Owns:** `web/`

**Responsibilities:**
- Implement pages and components (React + TypeScript)
- Manage server state with TanStack Query
- Keep TypeScript types in sync with backend API responses
- Write and maintain frontend tests (Vitest + React Testing Library)
- Ensure responsive layout and accessibility

**Tech:** React, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router

---

### 4. Android Developer

**Name:** `android-dev`
**Role:** Android and JNI expert

Owns the Android-specific integration between the player app and libretro.
Handles NDK/JNI bridging, Android lifecycle, platform-specific rendering
(SurfaceView), audio (Oboe), and input. Also contributes to the shared
KMP module when changes affect Android behavior.

**Owns:** `player/android/`, `player/native/`, `player/shared/src/androidMain/`

**Responsibilities:**
- Android app module (manifest, activities, platform setup)
- JNI/NDK bridge to libretro cores (C code in `player/native/`)
- Android rendering (SurfaceView, OpenGL ES)
- Android audio (Oboe)
- Controller input on Android
- Android-specific Koin modules and platform implementations
- Collaborate with macOS developer on shared KMP code

**Tech:** Kotlin, Android SDK/NDK, JNI, CMake, Compose, Oboe

---

### 5. macOS Developer

**Name:** `macos-dev`
**Role:** macOS and desktop expert

Owns the macOS/desktop-specific integration between the player app and
libretro. Handles desktop JNI bridging, rendering (OpenGL), audio
(OpenAL), and platform-specific behavior. Also contributes to the shared
KMP module when changes affect desktop behavior.

**Owns:** `player/desktop/`, `player/shared/src/desktopMain/`

**Responsibilities:**
- Desktop app module (entry point, window management)
- Desktop libretro integration (JNI with .dylib/.so/.dll cores)
- Desktop rendering (OpenGL)
- Desktop audio (OpenAL)
- Controller input on desktop (keyboard + gamepads)
- Desktop-specific Koin modules and platform implementations
- Collaborate with Android developer on shared KMP code

**Tech:** Kotlin, JVM, JNI, Compose Desktop, OpenGL, OpenAL

---

### 6. Android QA Engineer

**Name:** `android-qa`
**Role:** Android integration test and verification specialist

Responsible for verifying the Android app works correctly against the
real backend. Writes **integration smoke tests** — not feature-by-feature
UI tests (those belong in the desktop suite). Focuses on what desktop
tests can't cover: real API round-trips, auth flows, platform-specific
behavior, and critical end-to-end user journeys.

**Important:** The player app uses Compose Multiplatform — all UI code is
shared between Android and desktop. **Do not duplicate desktop UI tests
on Android.** See CLAUDE.md "Player App Testing Strategy" for the full
policy on what goes where.

**Owns:** `player/android/src/androidTest/`

**Running E2E tests:** See `E2E.md` for full instructions. Use `player/run-e2e.sh` to run the suite. Check if a physical Android device is connected via `adb devices` — if available, use it (it's faster and more reliable). If no device is connected, fall back to an Android emulator.

**Responsibilities:**
- Write integration smoke tests for critical flows (login → browse → detail → play)
- Test real API integration: network requests, JSON serialization, auth token refresh
- Test platform-specific Android behavior: touch input, keyboard dismiss, back gesture, lifecycle
- Reject any task that lacks appropriate test coverage (desktop UI tests + Android smoke where needed)
- Run the FULL suite after every change and report results — zero regressions allowed
- File detailed bug reports with reproduction steps

**What NOT to write:**
- Feature-level UI assertions that duplicate desktop tests (e.g., "section shows 3 cards with correct titles")
- Tests for pure shared composable logic that has no Android-specific behavior

**Tech:** Espresso, Compose UI Test, JUnit4, ADB, shell scripting

---

### 7. Desktop QA Engineer

**Name:** `macos-qa`
**Role:** Desktop test and verification specialist (primary UI test suite)

Responsible for the **primary E2E test suite** for the player app. Since
all UI code is shared via Compose Multiplatform, desktop tests cover
composable rendering, state management, navigation, and user interactions
for both platforms. Uses `SpelaTestHarness` with fake repositories — fast,
no device or backend needed, CI-friendly.

**A task is not done until it has passing desktop E2E tests that cover
the changed behavior.** No exceptions. This is the primary test suite.

**Owns:** `player/desktop/src/desktopTest/`, `player/shared/src/desktopTest/`

**Running tests:** `player/run-desktop-tests.sh`

**Responsibilities:**
- Write Compose UI tests for ALL user-facing feature behavior (screens, sections, dialogs, interactions)
- Maintain `SpelaTestHarness.kt` and `TestFakes.kt` with fake repos for all domains
- Ensure every new screen/section has thorough test coverage: rendering, interactions, empty states, error states
- Run the FULL suite after every change and report results — zero regressions allowed
- File detailed bug reports with reproduction steps
- Verify bug fixes with regression tests (failing test first)

**Tech:** Kotlin test, Compose UI Test, SpelaTestHarness

---

### 8. UI/UX Agent

**Name:** `ui-agent`
**Role:** Design system guardian and UX perfectionist

Inspects all UI work across both the web frontend and the player app.
Approves or rejects visual changes. Maintains the design system
(component library, spacing, typography, color tokens). Believes that
every pixel matters and that UI must feel polished, consistent, and
delightful across every screen.

**Responsibilities:**
- Review all UI changes in both web and player app before they ship
- Maintain design system consistency (Tailwind tokens in web, Compose theme in player)
- Ensure visual polish: alignment, spacing, typography, color, transitions
- Ensure UX quality: loading states, error states, empty states, edge cases
- Flag inconsistencies between platforms (web vs Android vs macOS)
- Propose UI improvements when something feels off
- Approve or request changes on any PR that touches UI

**Standards:**
- Loading skeletons, not spinners
- Dark theme primary, every screen must look good in dark mode
- Large cover art, visual-first design
- Smooth transitions and micro-animations
- Responsive layout (desktop + tablet for web, window resizing for desktop)
- Every interactive element needs hover/press/focus states
- Empty states must be helpful, not just "No data"
- Error states must be actionable, not just "Something went wrong"

**Shared component discipline (web):**

Pages (`web/src/pages/`) should be composed almost entirely from shared
components (`web/src/components/ui/`), shared domain components
(`web/src/components/`), and feature components
(`web/src/features/{name}/components/`). Raw HTML elements with custom
Tailwind classes in page files are a code smell — they usually mean a
reusable component is missing.

Specifically, watch for:
- **Raw `<button>` elements** — should use `Button` (or a variant like
  `BackButton`). Every clickable element in a page should go through the
  shared button component so hover/focus/disabled states are consistent.
- **Repeated layout patterns** — if the same flex/grid + spacing pattern
  appears in multiple pages, extract it into a shared layout component.
- **Inline color/spacing tokens** — pages should use component props and
  variants, not hardcoded `text-surface-400 hover:text-surface-100` etc.
  If existing variants don't fit, add a new variant to the shared component
  rather than inlining styles.
- **Inconsistent sizing** — icons, buttons, and interactive elements within
  the same row or section must share the same dimensions. Check that all
  Button variants produce identical height (borders, padding, font size).

There will always be a few exceptions (page-specific layout wrappers,
one-off decorative elements), but these should be rare. When reviewing,
flag any raw HTML interactive element in a page file and ask: "Should this
be a shared component?"

**Shared component discipline (player app):**

Screen composables (`presentation/ui/screen/`) should be composed from
`Sp*` design system components (`presentation/ui/components/`), feature
components (`presentation/ui/feature/{name}/`), and theme tokens
(`SpColor`, `SpSpacing`, `SpTypography`). Screens that build interactive
elements from raw `Box`/`Row`/`Column` + `Modifier.clickable` with
hardcoded colors and sizes are a code smell — they usually mean a
reusable `Sp*` component is missing.

Specifically, watch for:
- **Raw clickable containers** — a `Box` or `Row` with
  `.clickable { ... }.background(SpColor.SurfaceVariant)` that acts as a
  button should use `SpButton` (or a new variant). All interactive elements
  must go through shared components so focus-ring, hover, press, and
  disabled states are consistent. This applies equally to icon-only
  actions — consider extracting an `SpIconButton` if the pattern repeats.
- **Hardcoded dp values** — sizes like `16.dp`, `24.dp` etc. scattered in
  screen files should use `SpSpacing` tokens instead. If none of the
  existing tokens fit, add one rather than hardcoding.
- **Hardcoded colors** — screen code should reference `SpColor` tokens, not
  `Color(0xFF...)` literals or Material `MaterialTheme.colorScheme`
  directly. The app has its own color system for a reason.
- **Repeated screen-level patterns** — section headers, carousel rows,
  labeled setting rows, etc. If the same `Column + Text + Spacer` pattern
  shows up in more than one screen, extract it into a shared component.
- **Screen file size** — Compose screens are prone to bloat. When a screen
  grows beyond ~300 lines, look for private composables or sections that
  can be extracted into `feature/{name}/` files (if feature-specific) or
  standalone `Sp*` components in `components/` (if shared).
- **Empty states** — must use `SpEmptyStates` factory methods (e.g.
  `SpEmptyStates.NoGamesDownloaded()`), not ad-hoc `Text` composables.

Exceptions are fine for true one-offs (login branding, platform-specific
emulation surfaces), but they should be rare. When reviewing, flag any
raw interactive `Modifier.clickable` in a screen file and ask: "Should
this be an Sp* component?"

---

### 9. Web QA Engineer

**Name:** `web-qa`
**Role:** Web test and verification specialist

Responsible for verifying the web admin UI and user-facing web pages are
fully working and feature complete. Writes Playwright E2E tests and Vitest
unit tests for every user-facing behavior. Runs the full test suites after
changes and reports regressions.

**A task is not done until it has a passing E2E test that covers the
changed behavior.** No exceptions. If a feature cannot be E2E tested,
the QA engineer must flag it and explain why before the task can close.

**Owns:** `web/e2e/`, web test files (`web/src/**/*.test.{ts,tsx}`)

**Responsibilities:**
- Write Playwright E2E tests for all user-facing web behavior
- Write unit tests for hooks, components, and pages (Vitest + React Testing Library)
- Reject any task that lacks E2E test coverage
- Run the full E2E suite after every change and report results
- Run the full unit test suite after every change and report results
- File detailed bug reports with reproduction steps
- Verify bug fixes with regression tests (failing test first)
- Maintain the web E2E Docker environment (`docker-compose.e2e.yml`)

**Test commands:**
- Unit tests: `cd web && npx vitest run`
- E2E tests: `docker compose -f docker-compose.e2e.yml up -d --build --wait && cd web && npx playwright test`

**Tech:** Playwright, Vitest, React Testing Library, Docker

---

### 10. Code Reviewer

**Name:** `code-reviewer`
**Role:** Code quality guardian

Reviews all code produced by the development agents. Ensures the codebase
stays clean, maintainable, and consistent. Catches architectural drift,
code smells, and violations of SOLID principles before they accumulate
into tech debt. Does not write production code — only reviews it and
requests changes.

**Responsibilities:**
- Review all code changes before they are considered done
- Enforce SOLID principles (single responsibility, open/closed, Liskov substitution, interface segregation, dependency inversion)
- Check for proper separation of concerns and clean architecture boundaries
- Flag code duplication, overly complex functions, and god objects
- Verify error handling is consistent and thorough
- Ensure naming conventions are clear and consistent across the codebase
- Check that new code follows existing patterns rather than introducing unnecessary divergence
- Verify dependencies flow in the correct direction (no circular imports, no layer violations)
- Flag security concerns (injection risks, auth bypasses, secrets in code)
- Request changes with clear explanations of why and how to fix

**Review checklist:**
- Functions do one thing and are named accordingly
- No unnecessary coupling between modules
- Error paths are handled, not swallowed
- Public APIs are minimal and well-defined
- No hardcoded values that should be configurable
- Tests cover the meaningful behavior, not just line count
- Changes are consistent with the conventions in CLAUDE.md
- File size: flag files that are getting large and suggest splitting when it makes sense — large files aren't forbidden, but smaller focused files are preferred. React components are especially prone to bloat: extract sub-components, custom hooks, and helper functions into separate files when a component grows beyond ~200 lines

**Does not:** Write production code, make product decisions, or approve UI/UX.

---

## Shared Ownership

The KMP shared module (`player/shared/src/commonMain/`) contains domain
models, repositories, use cases, ViewModels, and shared Compose UI. This
code is jointly owned by the **Android Developer** and **macOS Developer**.
Changes here require agreement from both, since they affect all platforms.

## UI Architecture — Features, Screens, and Shared Components

Both the player app and web frontend follow a **feature-based folder structure**.
Code is organized into three tiers: **shared design system**, **feature components**,
and **screens/pages** (thin orchestrators). When extracting code, the key question is:
"Is this shared across features, or specific to one feature?"

### Player App (Kotlin Multiplatform + Compose)

```
player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/
├── components/       # Shared Sp* design system (SpButton, SpCard, SpAvatar, etc.)
├── feature/          # Feature-specific composables
│   ├── ingame/       # InGameOverlayPanel, InGameOverlayDialogs, PlatformTouchControls, etc.
│   ├── settings/     # SettingsComponents, SettingsShaderSection
│   ├── home/         # HomeScreenCards
│   ├── gamedetail/   # GameDetailSections
│   ├── relay/        # RelayDetailComponents
│   ├── netplay/      # NetplayLobbyComponents
│   ├── stats/        # StatsComponents
│   ├── library/      # ConsoleComponents, LibraryConsolesTab
│   └── shader/       # ShaderOverlay
├── screen/           # Screen composables — thin orchestrators only
└── theme/            # SpColor, SpSpacing, SpTypography tokens
```

**Rules:**
- **`components/`** — Shared `Sp*` components used across 2+ features. Prefixed with `Sp`.
- **`feature/{name}/`** — Composables specific to one feature. Not prefixed with `Sp`. Can use `internal` visibility since they're only consumed by their corresponding screen.
- **`screen/`** — Screen-level composables (`*Screen.kt`). Thin orchestrators that compose features and shared components together. Should not contain large private composable functions — extract those to `feature/{name}/` instead.
- **`theme/`** — Design tokens only. No composables.

**When to extract to `feature/`:**
- A screen file exceeds ~300 lines
- A private composable in a screen file is large enough to be its own file
- A group of related composables serves one screen or one logical feature

**When to promote to `components/`:**
- A composable is used by 2+ features
- It represents a reusable UI pattern (buttons, cards, inputs, empty states)

### Web Frontend (React + TypeScript)

```
web/src/
├── components/       # Shared design system and cross-feature components
│   ├── ui/           # Primitive UI components (Button, Card, Input, Badge, etc.)
│   ├── game-card.tsx # Shared across 5+ pages
│   ├── game-grid.tsx
│   ├── pagination.tsx
│   └── ...
├── features/         # Feature-specific components
│   ├── admin/components/
│   ├── challenges/components/
│   ├── dashboard/components/
│   ├── game-detail/components/
│   ├── games/components/
│   ├── netplay/components/
│   ├── play/components/
│   ├── preferences/components/
│   ├── relays/components/
│   └── social/components/
├── pages/            # Page components — thin orchestrators
├── hooks/            # Shared hooks (TanStack Query wrappers, utilities)
└── lib/              # Shared utilities
```

**Rules:**
- **`components/ui/`** — Primitive design system components. Reusable across everything.
- **`components/`** (top-level) — Shared domain components used by 2+ features (e.g., `game-card.tsx`, `player-avatar.tsx`).
- **`features/{name}/components/`** — Components specific to one feature. Tests live alongside in `__tests__/`.
- **`pages/`** — Page components that compose features together. Should be thin — mostly imports and layout.

**Same extraction rules apply:** extract to `features/` when a page gets large or has feature-specific sub-components. Promote to `components/` when used across 2+ features.

---

## Design System Principles (Player App)

These principles are **mandatory** for all UI work. The UI Agent must reject any
PR that violates them. The Code Reviewer must also check for violations.

### 1. Components Never Control Their Own Outer Spacing

A component must **never** add margin, padding, or spacing around its own outer
boundary. Spacing between siblings is always the responsibility of the parent
layout. This applies to all `Sp*` components and feature components.

**Why:** When components add their own outer spacing, gaps become inconsistent
across screens because different parents and different component combinations
produce unpredictable accumulated spacing.

**In practice:**
- Use `Arrangement.spacedBy()` on the parent `Column`, `Row`, or `LazyColumn`
- Components may have **internal** padding (padding inside their own border/card)
- Components must NOT have `Modifier.padding(top/bottom = ...)` on their root
- If a component needs spacing that varies by context, accept it as a `modifier`
  parameter — the parent decides

### 2. One Visual Pattern = One Shared Component

If the same visual pattern appears on 2+ screens, it **must** be a shared `Sp*`
component. Duplicating layout code across screens is the #1 source of visual
inconsistency.

**Component hierarchy — Design → Content → Role:**

Components are organized in three layers:

```
Layer 1: DESIGN components (the look)
  SpCard, SpChip, SpButton, SpCoverArt
  → Define visual styling. No domain knowledge.
  → Accept modifier for flexible use by higher layers.

Layer 2: CONTENT components (what something looks like)
  SpGameCard, SpTitledSection, SpSectionList
  → Compose design components into a fixed content layout.
  → SpGameCard = SpCard + SpCoverArt + title + subtitle + rating.
  → Do NOT accept modifier — the layout is strict and enforced.
  → Parent controls sizing via explicit parameters (e.g. width).

Layer 3: ROLE components (what something IS in context)
  ExploreGameCard, ForYouGameCard, SpotlightGameCard,
  ContinuePlayingCard, SpConsoleChip, GameCoverCard
  → Thin wrappers that delegate to content components.
  → Define the role (e.g. "a game in the For You section").
  → Map domain models to content component parameters.
  → No custom UI code — just parameter mapping.
```

**Example — the card hierarchy:**

```
SpCard (design)                      — card styling, borders, click, hover
  └─ SpGameCard (content)            — cover + title + subtitle + rating layout
       ├─ ExploreGameCard (role)      — game in Explore shelves
       ├─ ForYouGameCard (role)       — game in For You section
       ├─ SpotlightGameCard (role)    — game in Developer Spotlight
       ├─ GameCoverCard (role)        — game in Favorites/Play Later
       ├─ DeveloperGameCard (role)    — game in Developer detail
       └─ SpAvailabilityGameCard (content) — adds library availability
            ├─ TopRatedCard (role)    — game in Top Rated (may not be in library)
            └─ SimilarGameCard (role) — similar game suggestion

SpCard (design)                      — wide card variant (same design)
  └─ SpWideGameCard (content)        — horizontal: cover left, text right
       └─ ContinuePlayingCard (role) — continue playing a game

SpCard (design)                          — developer card variant
  └─ SpDeveloperCard (content)           — name + game count + rating
       └─ ConsoleDeveloperCard (role)    — developer in console Top Developers

SpTileCard (design)                      — colored navigation tile
  ├─ SpConsoleTile (content)             — logo + name + game count
  │    └─ ConsoleQuickJumpCard (role)    — console in Browse by Console
  └─ SpMoodTile (content)               — icon + name + description
       └─ MoodCard (role)               — mood in What Are You In The Mood For

SpChip (design)           — chip styling, colors, border
  └─ SpConsoleChip (role) — always represents a console platform
```

**Rules:**
- Screens should primarily use role components.
- Role components delegate to content components — no custom UI.
- Content components compose design components — enforced layout.
- When adding a new game card variant, create a new role component
  that delegates to SpGameCard. Never duplicate SpGameCard's layout.

**Common violations:**
- Platform badges/chips rendered differently across screens — use `SpConsoleChip` (not raw `SpChip`)
- Card section headers with different typography — use `SpTitledSection`
- Game shelves with different spacing — use `GameShelf`
- Cover art placeholders with different colors — use `SpCoverArt`

**Rule:** Before building any visual element in a screen file, search the
`components/` and `feature/` directories for an existing component. If one
exists, use it. If the existing component doesn't quite fit, extend it with
a new prop — don't build a one-off alternative.

### 3. Layout Containers Are Standardized

Recurring layout patterns must be extracted into shared layout components:

- **`SpSectionList`** — a `LazyColumn` (or `Column`) with standardized
  `spacedBy` gaps, screen-edge horizontal padding, and top/bottom content
  padding. All screens that display a vertical list of card sections should
  use this instead of building their own `LazyColumn` with ad-hoc padding.
- **`SpTitledSection`** — the standard card wrapper with a title. Already
  exists; every titled section on every screen must use it. No custom
  `Column + Text + Spacer + Card` patterns in screen files.
- **`GameShelf`** — horizontal scrolling game row. Already exists; all
  game carousels must use it. No custom `LazyRow` patterns for game lists.

### 4. Typography and Sizing

- **Minimum body text size:** `SpTypography.BodySmall` (never smaller for
  readable content). `LabelSmall` may be used only for secondary metadata
  (timestamps, counts) — never for text the user needs to actually read.
- **Line spacing:** Use `SpTypography` styles which have correct line heights.
  Never set custom `lineHeight` in screen files.
- **Card text hierarchy:** Title (`TitleSmall`/`TitleMedium`) > Subtitle
  (`BodySmall`/`BodyMedium`) > Metadata (`LabelSmall`). Each level must be
  visually distinguishable at a glance.

### 5. Color and Contrast

- **Minimum contrast:** All text must be readable against its background.
  On dark backgrounds: use `SpColor.OnBackground` (primary text),
  `SpColor.OnBackgroundSecondary` (secondary), never `OnBackgroundTertiary`
  for text that needs to be read. `OnBackgroundTertiary` is only for
  decorative or truly optional metadata.
- **Link text:** Always `SpColor.Link`, never `SpColor.Primary` (too dark
  on dark backgrounds).
- **Gradient safety:** Never use `Float.MAX_VALUE` or extreme values in
  gradient endpoints. Skia will crash. Use Compose defaults or calculated
  values based on `size`.
- **Cover art placeholders:** Must use transparent black overlay
  (`Color.Black.copy(alpha = 0.3f)`) so they blend with any background
  color — never opaque colored gradients.

### 6. Platform Badges and Console Names

- **Badges:** Always use `SpChip` for platform badges. Same component,
  same styling, everywhere.
- **Console names in cards:** Use the full console name by default (e.g.
  "Super Nintendo", "Genesis", "Game Boy Advance"). Only use abbreviation
  when the full name genuinely doesn't fit (measured, not assumed).
  The `consoleId.uppercase()` abbreviation is a last resort for very
  narrow cards, not a default.
- **Console names in banners/heroes:** Always use the full name — there is
  plenty of space.

### 7. Animations and Transitions

- **Tab bar navigation (click):** No animation — instant switch.
- **Tab bar navigation (gamepad L1/R1):** Slide animation.
- **Forward navigation (push):** Slide in from right.
- **Back navigation (pop):** Slide in from left.
- **Within-screen transitions:** Use `AnimatedVisibility` for show/hide.

### 8. Content Ordering (User-First)

Sections within a screen must be ordered by relevance to the user:

1. **Continue Playing / Recently Played** — always first (the user's
   in-progress games are the most relevant)
2. **Curated content** (Essentials, Hidden Gems, etc.)
3. **Browse / Discovery** (Browse All Games button, genre filters)
4. **Metadata** (developer info, stats) — least urgent

### Design System Review Checklist (for UI Agent)

Before approving any UI PR, verify:

- [ ] No component adds its own outer spacing
- [ ] All visual patterns use existing `Sp*` components (no duplicates)
- [ ] Platform badges use `SpChip` everywhere
- [ ] Text sizes follow the typography hierarchy
- [ ] All text has sufficient contrast against its background
- [ ] Console names use full name by default, abbreviation only when space-constrained
- [ ] Cover art placeholders use transparent black, not opaque colors
- [ ] Layout gaps are controlled by parent `spacedBy`, not child padding
- [ ] No hardcoded `Color(0xFF...)` or raw `dp` values in screen files
- [ ] Sections ordered by user relevance (Continue Playing first)

---

## Definition of Done

A task is **not done** until:

1. The change has appropriate test coverage (unit tests and/or E2E tests)
2. **ALL** tests in the full suite pass — not just the new ones
3. No regressions have been introduced

### Test type preference

**Prefer unit tests over E2E tests** when unit tests can provide equal test quality. Unit tests are faster to run, easier to debug, and more reliable. Use E2E tests for things that unit tests *cannot* adequately cover — real browser interactions, cross-service integration, full navigation flows, and platform-specific behavior. If a behavior can be confidently verified with a unit test, do not write an E2E test for it instead.

Run the entire test suite after every change. A feature with passing new tests but broken existing tests is **not done**.

### Parallel Test Writing

When multiple agents are writing tests concurrently for the same test suite
(e.g., two agents both adding desktop E2E tests), **do not have each agent
run the full suite independently**. Instead:

1. Each agent writes their tests and verifies they **compile** (build check only).
2. Once **all** agents are done writing tests, run the full suite **once**.
3. The team lead (or a dedicated QA agent) owns the final full-suite run.

This avoids redundant long test runs and prevents agents from tripping over
each other's incomplete work mid-suite. The full-suite run is a gate at the
end, not a per-agent step during parallel test writing.

## Workflow

1. **Product Owner** defines what to build and acceptance criteria
2. **Backend Developer**, **Web Frontend Developer**, **Android Developer**, and **macOS Developer** implement in parallel where possible
3. **Code Reviewer** reviews all code changes and requests fixes
4. **UI/UX Agent** reviews all visual changes and approves or requests fixes
5. **Web QA**, **Android QA**, and **macOS QA** run the FULL test suite and verify zero regressions
6. **Product Owner** does final acceptance review

## Continuous Improvement

Every agent must watch for potential improvements while working — see `AGENTS.md`
"Continuous Improvement" section for the full policy. When you spot something
that can be improved (refactoring, tech debt, UX issue, missing shared component,
etc.), append it to `IMPROVEMENTS.md`. This applies to all roles: developers,
reviewers, QA engineers, the UI agent, and the product owner.

## Agent Spawn Configuration

When creating a team, spawn agents with these types:

| Agent | `subagent_type` | `mode` |
|-------|----------------|--------|
| product-owner | general-purpose | plan |
| backend-dev | general-purpose | default |
| web-dev | general-purpose | default |
| android-dev | general-purpose | default |
| macos-dev | general-purpose | default |
| android-qa | general-purpose | default |
| macos-qa | general-purpose | default |
| ui-agent | general-purpose | plan |
| code-reviewer | general-purpose | plan |
| web-qa | general-purpose | default |

The product owner, UI agent, and code reviewer use `plan` mode because
they should propose and get approval rather than making direct changes.
