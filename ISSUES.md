# Known Issues

## Console detail page polish (from code review)

### No focus-visible on "Browse all" links (High - a11y)
- The "Browse all N games" `<Link>` elements lack `focus-visible` ring styles. Should create a reusable `LinkButton` component or add focus styles matching the `Button` component.

### No loading skeleton for showcase sections (Medium)
- Large library path: page flashes empty between hero banner and bottom "Browse all" link while showcase data loads. Each showcase component silently returns null when data is undefined.

### Logo onError uses DOM manipulation instead of React state (Medium)
- `console-detail-page.tsx:127-133` — The `onError` handler performs direct DOM manipulation. Should use `useState` for image error state instead, matching the pattern in `hero-carousel.tsx`.

### BackButton is a button, not a link (Low - codebase-wide)
- `BackButton` renders a `<button>` with `onClick` navigation instead of a `<Link>`. Right-click/middle-click/status bar preview don't work. Affects 30+ instances across the codebase.

### Showcase components each independently call useConsoleShowcase (Low)
- Six components each call `useConsoleShowcase(consoleId)`. TanStack Query deduplicates the request, but the parent can't control loading/empty states. Consider lifting the fetch to the parent.
