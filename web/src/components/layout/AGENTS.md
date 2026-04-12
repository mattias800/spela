# Layout Components

Core layout components for structuring page content. These components provide
consistent heading alignment, spacing, and optional visual containers across
all screens.

## Padding Exception

Components in this directory are **allowed to own their own padding and spacing**.
This is an explicit exception to the general rule that components should not add
padding outside themselves.

**Why:** `TitledSection` and `SectionList` are the structural backbone of every
page. Their padding ensures that headings, content, and gaps are visually
consistent regardless of whether a section has a card background or not. If
consumers controlled this padding, every page would drift.

## Component Inventory

- **`SectionList`** — Vertical list with standardized gaps between sections.
  Direct child of the page component. Children should be `TitledSection`.

- **`TitledSection`** — Section with title bar (heading + icon + renderRight).
  Has `contained` prop: when true, renders a subtle card background
  (`bg-white/[0.03] rounded-2xl`). Heading position and padding are identical
  in both modes, so headings always align across a page.

## Rules

1. **Do not override padding** on these components via className. The padding
   is intentional and ensures alignment.
2. **Use `contained` for carousels and stats** — any section with a visual
   grouping that benefits from a subtle background.
3. **Use non-contained for grids and lists** — content that flows naturally
   without a card wrapper (e.g., genre breakdowns, theme grids).
4. **No negative margins** — never use `-mx-*`, `-mt-*` to break out of parent
   padding. If a component needs full-width rendering (flush to viewport edges),
   it must be a direct child of `<main>`, not nested inside a padded container
   with negative margins to escape. Restructure the component hierarchy instead.
