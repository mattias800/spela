# Versions Card Redesign: Reducing Visual Dominance

## Problem

The Versions section on the game detail page (`game-detail-page.tsx`, lines 287-291) is wrapped in a full-width `<Card className="p-6">`, giving it the same visual weight as primary sections like Sessions, Community Stats, and Ratings. For what is essentially reference metadata (alternative ROM regions/revisions), it commands too much attention and vertical space.

Current rendering:
```
<Card className="p-6">           ← full bordered card, same as Sessions
  <GameVariantsSection>          ← icon + "Versions" heading (text-lg font-semibold)
    <VariantRow> per variant      ← each row is px-4 py-3 rounded-xl bg-surface-800/30
  </GameVariantsSection>
</Card>
```

Each variant row occupies ~56px of height. With 5 variants, the card is ~350px tall — taller than the TimeToBeat card, and nearly as tall as Sessions.

---

## Recommended Approach: Collapsible Inline Section (No Card)

### What changes

1. **Remove the `<Card>` wrapper** from around `<GameVariantsSection>` in `game-detail-page.tsx`.
2. **Make the section collapsible**, defaulting to **collapsed** when there are 3+ variants, and **expanded** when there are 1-2.
3. **Shrink the heading** from `text-lg font-semibold` to `text-sm font-medium text-surface-400` — match the secondary metadata tone of MetaItem labels.
4. **Compact the variant rows** — reduce padding from `px-4 py-3` to `px-3 py-2`, use `text-xs` for file size, and `text-sm` for the title (already `text-sm`, keep it).
5. **Add a chevron toggle** (ChevronDown from lucide-react) that rotates on expand.

### Visual structure (collapsed, 5 variants)

```
▸ Versions (5)                          ← no card border, muted heading, clickable
```

### Visual structure (expanded)

```
▾ Versions (5)
  Super Mario World (USA)               12.4 MB
  Super Mario World (Europe)            12.1 MB
  Super Mario World (Japan)             11.8 MB
  Super Mario World (USA) (Rev 1)       12.4 MB
  Super Mario World (Brazil)            12.2 MB
```

### Why this is better than alternatives

| Alternative | Why not |
|---|---|
| **Dropdown/select** | Versions are navigational links (they link to `/games/:id`). A `<select>` can't hold `<Link>` elements and doesn't show badges for region/revision. |
| **Inline chips/badges** | Works for 2-3 versions, but with 8+ it wraps across multiple lines and becomes messy. Also loses the file size info which some users want. |
| **Small text list (always visible)** | Still takes significant vertical space with many variants. Collapsible is strictly better. |
| **Move inside GameHero metadata grid** | The metadata grid is 2-column `MetaItem` pairs. Variants are a list of links, structurally different. Mixing them would break the grid pattern. |
| **Tabs** | Over-engineered for a secondary metadata list. |

### Implementation details

**Files to modify:**

1. **`web/src/pages/game-detail-page.tsx`** (lines 287-291):
   - Remove the `<Card className="p-6">` wrapper
   - Render `<GameVariantsSection>` directly (it already handles empty state)

2. **`web/src/features/game-detail/components/game-variants-section.tsx`**:
   - Add `useState` for collapse/expand
   - Default: collapsed if `versionVariants.length >= 3`, expanded otherwise
   - Replace the `h2` heading with a `button` that toggles state
   - Reduce heading styles: `text-sm font-medium text-surface-400` instead of `text-lg font-semibold text-surface-100`
   - Use `Layers` icon at `h-4 w-4` instead of `h-5 w-5`
   - Add `ChevronRight` icon that rotates to `ChevronDown` on expand (use `transition-transform rotate-90`)
   - Compact variant rows: `px-3 py-2` padding, keep existing badge and file size display
   - Apply the same collapsible pattern to the ROM Hacks subsection within GameVariantsSection

**Pattern reference — series/franchise links** (game-detail-page.tsx lines 253-285):
These use no card wrapper, just `flex flex-wrap` with inline pill-style elements. The versions section should feel similarly lightweight — metadata that's there when you need it, not visually competing with Sessions or Ratings.

**Pattern reference — MetaItem** (components/meta-item.tsx):
Uses `text-surface-500` for labels and `h-4 w-4` icons. The collapsed versions header should match this secondary-metadata tone.

### Responsive behavior

- On mobile, the collapsible section works well — collapsed by default saves screen space, user can tap to expand.
- The compact rows (`px-3 py-2`) fit comfortably on narrow screens since they already use `truncate` on titles.
- No layout changes needed for the responsive grid since versions are not in a grid column.

### Accessibility

- The toggle should be a `<button>` with `aria-expanded` attribute
- Use `aria-controls` pointing to the list container id
- The chevron rotation provides visual feedback; `aria-expanded` provides screen reader feedback

### Space savings estimate

| Variants | Current height | After (collapsed) | After (expanded) |
|---|---|---|---|
| 2 | ~170px (card + heading + 2 rows) | ~30px | ~110px |
| 5 | ~310px | ~30px | ~230px |
| 10 | ~560px | ~30px | ~430px |

The collapsed state is consistently ~30px regardless of variant count. This is the key win.
