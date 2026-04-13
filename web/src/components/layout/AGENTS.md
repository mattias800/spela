# Layout Components

Core layout components for structuring page content. These components provide
consistent heading alignment, spacing, and optional visual containers across
all screens.

## Page Structure

Every route component must use `PageLayout` as its outermost element. Pages
should be pure composition of layout components — no inline padding, spacing,
or layout Tailwind on raw divs.

```
Page
  └── PageLayout              (p-6 content padding, optional renderHeader)
        ├── [header]           (full-width, flush to edges — hero banners etc.)
        ├── [floating back button]  (over header, if backButtonVariant="floating")
        └── [padded content]
              ├── [back button]  (if backButtonVariant="standard")
              └── SectionList    (margin-based vertical gaps between sections)
                    ├── TitledSection  (heading + content, optional contained bg)
                    ├── TitledSection
                    └── ScrollShelf    (heading + horizontal scroll, always contained)
```

## Padding Exception

Components in this directory are **allowed to own their own padding and spacing**.
This is an explicit exception to the general rule that components should not add
padding outside themselves.

**Why:** `PageLayout`, `TitledSection`, and `SectionList` are the structural
backbone of every page. Their padding ensures that headings, content, and gaps
are visually consistent regardless of whether a section has a card background or
not. If consumers controlled this padding, every page would drift.

## Component Inventory

- **`PageLayout`** — Page-level container. Provides `p-6` content padding and
  an optional `renderHeader` slot for full-width content (hero banners). Supports
  `backButtonVariant` (`"standard"` | `"floating"`) for optional back navigation.

- **`SectionList`** — Vertical list with standardized gaps between sections.
  Uses block layout with `space-y-8` (margin-based) so that zero-height elements
  (e.g., IntersectionObserver sentinels) don't create extra gaps via CSS margin
  collapsing. Direct child of PageLayout's content area.

- **`TitledSection`** — Section with title bar (heading + icon + renderRight).
  Has `contained` prop: when true, renders a subtle card background
  (`bg-white/[0.03] rounded-2xl`). Heading position and padding are identical
  in both modes, so headings always align across a page.

## Rules

1. **Every route returns PageLayout** as the outermost element.
2. **Do not override padding** on these components via className. The padding
   is intentional and ensures alignment.
3. **Use `contained` for carousels and stats** — any section with a visual
   grouping that benefits from a subtle background.
4. **Use non-contained for grids and lists** — content that flows naturally
   without a card wrapper (e.g., genre breakdowns, theme grids).
5. **No negative margins** — never use `-mx-*`, `-mt-*` to break out of parent
   padding. If a component needs full-width rendering (flush to viewport edges),
   use `renderHeader` on PageLayout instead.
6. **No inline spacing on pages** — use `SectionList` for vertical gaps, not
   `space-y-*` or `gap-*` on raw divs.
