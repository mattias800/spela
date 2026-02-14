# Spela Web Frontend

React + TypeScript web frontend for the Spela self-hosted game emulation service.

## Prerequisites

- Node.js 20+
- npm 10+
- The Spela backend server running on `http://localhost:8080`

## Development

```bash
# Install dependencies
npm install

# Start dev server (proxies /api to backend on port 8080)
npm run dev
```

The dev server starts at `http://localhost:5173` with hot module replacement.

## Testing

```bash
# Run tests once
npm test

# Run tests in watch mode
npm run test:watch
```

Tests use Vitest with React Testing Library and jsdom. Test files are co-located with source files using the `.test.ts` / `.test.tsx` suffix.

## Build

```bash
# Type-check and build for production
npm run build

# Preview the production build
npm run preview
```

Output is written to `dist/`.

## Tech Stack

| Library               | Purpose                   |
| --------------------- | ------------------------- |
| React 19              | UI framework              |
| TypeScript 5.9        | Type safety               |
| Vite 7                | Build tool and dev server |
| Tailwind CSS 4        | Utility-first styling     |
| TanStack Query 5      | Server state management   |
| React Router 7        | Client-side routing       |
| Lucide React          | Icons                     |
| Vitest 4              | Test runner               |
| React Testing Library | Component testing         |

## Project Structure

```
src/
  components/
    ui/           Design system primitives
    app-layout    Main layout with sidebar
    console-card  Console grid card
    game-card     Game grid card with favorite toggle
    protected-route  Auth guard wrapper
  features/       Feature modules (reserved for growth)
  hooks/
    use-auth      Authentication context and login/register/logout
    use-games     Game queries, favorites, saves, toggle mutations
    use-consoles  Console list and console game queries
    use-admin     Admin users, settings, scan, scrape, metadata
  lib/
    api-client    HTTP client with JWT auth and refresh token rotation
    cn            Tailwind class merge utility (clsx + tailwind-merge)
    console-metadata  Console icons, gradients, and color themes
    format        File size, date, time, and relative time formatters
  pages/
    login-page / register-page   Auth screens
    dashboard-page               Recent games and favorites
    consoles-page                Console grid browser
    console-detail-page          Games for a single console
    games-page                   Full game library with search, filters, grid/list
    game-detail-page             Game info, screenshots, save states
    favorites-page               Favorite games grid
    admin/
      users-page                 User management table
      settings-page              Server configuration
      scan-page                  Library scan and metadata scrape triggers
      metadata-fix-page          Side-by-side metadata comparison
  types/
    api           TypeScript interfaces matching backend response DTOs
```

## Component Library

The design system lives in `src/components/ui/` and is re-exported from `src/components/ui/index.ts`.

| Component                           | Description                                                                                                                         |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `Button`                            | Primary, secondary, ghost, and danger variants. Three sizes. Loading state with spinner.                                            |
| `Card`, `CardHeader`, `CardContent` | Container with optional hover effect.                                                                                               |
| `Input`                             | Text input with label and error message support.                                                                                    |
| `Badge`                             | Inline label with default, brand, success, warning, and danger variants.                                                            |
| `Skeleton`                          | Animated loading placeholder. Includes `GameCardSkeleton`, `ConsoleCardSkeleton`, `GameDetailSkeleton`, `TableRowSkeleton` presets. |
| `Modal`                             | Dialog overlay with escape key and backdrop click dismissal. Small, medium, and large sizes.                                        |
| `ToastProvider` / `useToast`        | Toast notification system with success, error, and info types. Auto-dismiss.                                                        |
| `Sidebar`                           | Navigation sidebar with `NavLink` active state styling.                                                                             |
| `SearchInput`                       | Search field with icon.                                                                                                             |
| `Select`                            | Dropdown select with label support.                                                                                                 |
| `EmptyState`                        | Centered icon + title + description for empty lists.                                                                                |

## API Integration

All API calls go through `src/lib/api-client.ts`, which handles:

- JWT access token injection via `Authorization` header
- Automatic refresh token rotation on 401 responses
- Token storage in `localStorage`

Hooks in `src/hooks/` wrap TanStack Query for data fetching and mutations. The Vite dev server proxies `/api` requests to the backend at `http://localhost:8080`.

## Design Principles

- Dark theme primary with custom color tokens (surface, brand, success, warning, danger)
- Large cover art, visual-first game browsing
- Skeleton loaders instead of spinners
- Smooth hover effects and micro-animations
- Responsive layout for desktop and tablet
