# Versions Card Redesign - User Stories

## Problem

The "Versions" card on the game detail page has the same visual weight as primary content sections (Sessions, Community Stats, Ratings, etc.). It sits high on the page wrapped in a full Card with a large header, pushing important gameplay information further down. For most users, versions are reference information they rarely interact with -- the default version is already loaded, and they just want to play.

## Context

The game detail page visual hierarchy should be:
1. **Hero area** (cover, title, play button) -- primary action
2. **Gameplay content** (sessions, time-to-beat, screenshots, achievements) -- what matters during play
3. **Community content** (ratings, reviews, challenges, shared sessions) -- social context
4. **Reference information** (versions, file sizes, regions) -- useful occasionally

Currently, Versions sits at position 2, competing with gameplay content.

---

## User Story 1: Collapse versions by default

**As a** player visiting a game detail page,
**I want** the versions list to be collapsed by default,
**so that** I can focus on the game's primary content (screenshots, sessions, achievements) without scrolling past a list of regional variants I don't need.

### Acceptance Criteria

- When a game has multiple versions, the Versions section is **collapsed by default**, showing only a summary line (e.g., "5 versions available" with region badges).
- Clicking/tapping the summary line expands the section to reveal the full list of version links.
- The expanded/collapsed state does not persist across page loads -- it resets to collapsed each time.
- When a game has only 1 version (besides the current one), the section is still collapsed by default.
- The collapsed summary must be visually lightweight -- no Card wrapper, no large heading. It should feel like metadata, not a content section.

---

## User Story 2: Move versions below primary content

**As a** player browsing a game's detail page,
**I want** versions to appear lower on the page,
**so that** gameplay-relevant information (sessions, time-to-beat, screenshots) isn't pushed down by secondary reference data.

### Acceptance Criteria

- The Versions section appears **after** Time to Beat and Sessions, not before them.
- ROM Hacks (if present) should stay near Versions since they are related concepts.
- The page order from top to bottom should be: Hero > Series/Franchise links > Time to Beat > Sessions > Community Stats > [Versions + ROM Hacks] > Ratings/Reviews > Screenshots > Achievements > Shared Saves/Challenges.
- The repositioning must not break any existing test assertions about element presence (only order changes).

---

## User Story 3: Reduce visual weight of the Versions section

**As a** player scanning the game detail page,
**I want** the Versions section to look like supplementary information rather than a major content block,
**so that** it doesn't draw my eye away from more important sections.

### Acceptance Criteria

- The Versions section must **not** be wrapped in a Card. It should be a simpler, flatter element that blends into the page flow.
- The section header should be smaller than primary section headers (e.g., smaller text, no colored icon, or a more subdued icon).
- Individual version rows should be compact -- tighter padding than the current card-like rows.
- The overall Versions section should occupy less vertical space than the current implementation when expanded.
- The visual treatment should clearly communicate "this is reference info" rather than "this is a key section."

---

## User Story 4: Show ROM Hacks with appropriate prominence

**As a** player interested in ROM hacks for a game,
**I want** ROM hacks to remain discoverable but not dominate the page,
**so that** I can find them when I'm looking but they don't clutter the default experience.

### Acceptance Criteria

- ROM Hacks follow the same collapsed-by-default pattern as Versions.
- ROM Hacks and Versions can be collapsed/expanded independently.
- When both Versions and ROM Hacks exist, they appear as sibling sections (not nested inside a single Card).
- The ROM Hacks section (standalone, from `romHacks` field) follows the same visual weight reduction as the inline variant-based ROM Hacks.

---

## Out of Scope

- Changing how the "default" version is selected or displayed in the Hero area.
- Adding filtering or search within the versions list.
- Changing the variant data model or API response.
- Any changes to the player app's version handling.
