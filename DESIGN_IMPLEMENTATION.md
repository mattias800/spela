# Design Implementation Guide

This document defines the component architecture and design system for all UI work across both the **web frontend** (React/TypeScript/Tailwind) and the **player app** (Kotlin/Compose Multiplatform). These principles are not platform-specific — they apply equally to both codebases.

The player app's design system principles are also documented in `AGENT_TEAM.md`. This document extends those principles with implementation guidance and records decisions made during the design system refactoring.

## Component Hierarchy: Design → Content → Role

All UI components follow a strict three-layer hierarchy. This is not optional.

```
Layer 1: DESIGN components (the look)
  Badge, Card, Button, CoverImage
  → Define visual styling. No domain knowledge.
  → Accept className for flexible use by higher layers.

Layer 2: CONTENT components (what something looks like)
  GameCard, GameSummaryCard, SectionHeader
  → Compose design components into a fixed content layout.
  → Do NOT accept className — the layout is strict and enforced.
  → Parent controls sizing via explicit parameters.

Layer 3: ROLE components (what something IS in context)
  ConsoleBadge, RatingDisplay, TopRatedGameCard
  → Thin wrappers that delegate to content/design components.
  → Map domain models to component parameters.
  → Own domain-specific logic (color mapping, formatting).
  → No custom UI code — just parameter mapping.
```

### Why This Matters

Without this hierarchy, every screen becomes a bespoke layout with inline Tailwind that drifts from every other screen. When you need to change how console badges look, you edit 30 files instead of 1. When a new developer joins, they have no idea which pattern to follow because there are 5 different ones.

## Rules

### 1. Components Own Their Domain Logic

A component that represents a domain concept (console, rating, verification status) must own the mapping from domain data to visual presentation.

**Example: Console colors**

```tsx
// WRONG — caller controls colors, every call site can diverge
<Badge style={{ backgroundColor: `${game.consoleColor}20`, color: ensureContrast(game.consoleColor) }}>
  {game.consoleName}
</Badge>

// RIGHT — component owns the color mapping
<ConsoleBadge abbreviation="snes" />
```

**Why:** If the caller provides colors, then:
- 30 call sites each have their own opacity/contrast logic
- Some use `ensureContrast()`, some use `"white"`, some use the raw color
- Changing the color scheme requires editing every call site
- A new developer will copy-paste the wrong pattern

The component should accept a **platform code** (abbreviation), look up the color internally from the authoritative source (`console-metadata.ts`), and render consistently. The caller should never need to think about colors.

### 2. Components Never Accept `style` for Visual Overrides

If you find yourself passing `style={{ backgroundColor: ... }}` to override a component's visuals, that's a sign the component is missing a variant or the wrong component is being used.

**Allowed:** `className` for layout concerns (margin, width) controlled by the parent.
**Not allowed:** `style` props that override the component's internal visual design.

### 3. No Inline Badge/Card/Rating Patterns in Feature Files

Feature files (`web/src/features/`) and page files (`web/src/pages/`) should compose shared components. They should not contain:
- Raw `<Badge>` with inline color styling
- Cover image + fallback placeholder patterns
- Rating star + number patterns
- Card container patterns (`rounded-xl bg-surface-*/border-*`)

If a pattern appears in 2+ files, it must be a shared component.

### 4. Derive State, Don't Sync It

Prefer derived values over `useEffect` state synchronization:

```tsx
// WRONG — syncing derived state via useEffect
const [displayName, setDisplayName] = useState("");
useEffect(() => {
  setDisplayName(user.firstName + " " + user.lastName);
}, [user]);

// RIGHT — derive it
const displayName = user.firstName + " " + user.lastName;

// RIGHT — memoize if expensive
const displayName = useMemo(() => computeExpensiveName(user), [user]);
```

`useEffect` is appropriate for: subscriptions, event listeners, DOM measurements, external system synchronization. It is NOT appropriate for: transforming props into state, formatting data, computing derived values.

## Component Reference

### ConsoleBadge (Role component)

Wraps `Badge` to display a console/platform name with the correct brand color.

```tsx
interface ConsoleBadgeProps {
  abbreviation: string;  // Console abbreviation: "snes", "nes", "gba", etc.
  className?: string;    // Layout concerns only (margin, positioning)
}
```

**Owns:** Color lookup via `getConsoleStyle()` from `console-metadata.ts`.
**Does not accept:** `color`, `style`, or any visual override props.

### RatingDisplay (Role component)

*Planned — not yet implemented.*

### CoverImage (Design component)

*Planned — not yet implemented.*

## Refactoring Process

This codebase is being incrementally refactored to follow these principles. The process:

1. **One component type at a time** — e.g., all console badges first, then all ratings
2. **Create the shared component** with the correct API
3. **Migrate all usages** — every file that has the inline pattern
4. **Review** — verify no regressions, no remaining inline patterns
5. **Next component type**

This avoids large PRs that are impossible to review and ensures each step produces a working, testable codebase.
