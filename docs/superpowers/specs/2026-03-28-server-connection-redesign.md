# Server Connection Screen Redesign

**Date:** 2026-03-28
**Status:** Approved

## Overview

Redesign the server connection screen (first screen users see) with a responsive layout, SNES-inspired ambient glow styling, improved input visibility, and a welcoming first impression.

## Responsive Layout

Three tiers based on available width (same breakpoints as the nav rail):

### Phone (<600dp) — Centered Card

```
┌──────────────────────┐
│                      │
│  ┌────────────────┐  │
│  │    [icon]      │  │
│  │  Welcome to    │  │
│  │    Spela       │  │
│  │                │  │
│  │  [Server List] │  │
│  │  or [Form]     │  │
│  │                │  │
│  └────────────────┘  │
│                      │
└──────────────────────┘
```

- Vertically and horizontally centered glass card
- Dark gradient background with SNES ambient glow (color blobs)
- Icon + "Welcome to Spela" + tagline above the form
- Card: `rgba(255,255,255,0.04)` background, `1px solid rgba(255,255,255,0.08)` border, 24dp radius

### Handheld Landscape (600-840dp) — Split 35/65

```
┌──────────┬───────────────┐
│          │               │
│  [icon]  │ [Server List] │
│  Spela   │  or [Form]    │
│ tagline  │               │
│          │               │
│  (35%)   │    (65%)      │
└──────────┴───────────────┘
```

- Hero panel: 35% width, compact sizing (56dp icon, tighter padding)
- Everything above the fold — no scrolling on 1080p landscape

### Tablet/Desktop (>840dp) — Split 40/60

Same as handheld but hero panel is 40% width with full-size elements (80dp icon, larger text).

## Hero Panel — SNES Ambient Glow

Dark base color (`#0f0f1a`) with four soft radial color blobs positioned in corners, using the SNES button colors at low opacity:

- **Red** (`rgba(204,34,50,0.25)`) — top-left
- **Blue** (`rgba(44,44,170,0.25)`) — bottom-right
- **Green** (`rgba(0,132,61,0.2)`) — bottom-center
- **Yellow** (`rgba(196,178,8,0.15)`) — top-right

The effect is atmospheric colored lights behind a dark panel — visible but not overwhelming. White text remains highly readable.

On mobile (<600dp), the ambient glow is applied to the full background behind the centered card.

### Hero Content

- App icon: rounded square (16dp radius) with `rgba(255,255,255,0.08)` background and subtle border. "S" in white bold text as placeholder until a proper SVG logo is available.
- "Spela" title: large, bold, white
- Tagline: "Your personal retro gaming library" in `rgba(255,255,255,0.6)`
- Small SNES button dots (4 colored circles, 12dp, 60% opacity) below the tagline as a subtle nod

## Right Panel Content

### No saved servers (first launch)

Show the "Add Server" form directly:
- "Add Server" heading + "Enter your server details to connect" subtitle
- Server Name field
- Server URL field
- "Connect" button (gradient, full width)

### Saved servers exist

Show server list as cards:
- Each card: server name (bold), URL (small, muted), active indicator (green dot)
- Delete button (trash icon) on each card
- "Add Server" button at the bottom (secondary style)
- Tapping a server card connects immediately

## Input Field Improvements

These apply to `SpTextField` globally (not just this screen):

- Background: `rgba(255,255,255,0.06)` (currently too transparent)
- Border: `1px solid rgba(255,255,255,0.12)` (currently invisible)
- Border radius: 10dp
- Labels: positioned above the field, uppercase, small size, `OnBackgroundTertiary` color
- Placeholder text: `OnBackgroundTertiary` color (currently too dark)

## Implementation Notes

- Uses `BoxWithConstraints` for responsive breakpoints (existing pattern)
- The ambient glow effect uses positioned `Box` elements with radial gradients — no custom drawing needed
- The glass card effect uses the existing `SpCard` component or a simple `Box` with the frosted glass styling
- Detection: `maxWidth > 840.dp` → full split, `maxWidth > 600.dp` → compact split, else → centered card

## Testing

Desktop E2E tests:
- Server list renders saved servers
- Tapping a server card triggers connection
- "Add Server" button shows the form
- Form submits with name and URL
- Delete button removes a server

No visual tests for the ambient glow (purely cosmetic).

## Out of Scope

- Real SVG logo (placeholder "S" icon for now)
- Login screen redesign (separate task)
- Input field improvements on other screens (SpTextField changes will apply globally)
