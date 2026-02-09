# Spela - Project Conventions

## Overview
Spela is a self-hosted game emulation service with three components:
1. **Backend server** (Go) - Game hosting, user management, metadata, saves
2. **Web frontend** (React + TypeScript) - Server administration and game management
3. **Player app** (Kotlin Multiplatform + Compose Multiplatform) - Native game player

## Rules
1. **No web technology in the player app** - No HTML, CSS, or JavaScript. Must be fully native.
2. **Web UI uses React + TypeScript** - Vite build, Tailwind CSS, TanStack Query.
3. **Automated tests whenever reasonable** - For bugs: write failing test first, then fix.
4. **libretro only** - No custom emulation code. All emulation via libretro cores.
5. **E2E tests are mandatory for player app changes** - Any change to the player app that affects user-facing behavior must have a corresponding Maestro E2E test. Run the E2E suite after making changes to verify nothing is broken.

## Code Style

### Go (server/)
- Standard Go formatting (gofmt)
- Error handling: wrap errors with context using fmt.Errorf("doing thing: %w", err)
- Use structured logging (slog)
- Tests in _test.go files, use testify for assertions
- Table-driven tests preferred

### TypeScript/React (web/)
- Strict TypeScript, no `any` types
- Functional components only
- Named exports (no default exports)
- CSS via Tailwind utility classes
- Tests with Vitest + React Testing Library
- File naming: kebab-case for files, PascalCase for components

### Kotlin (player/)
- Follow Kotlin coding conventions
- Compose Multiplatform for all UI
- Clean Architecture: data → domain → presentation
- Unit tests with kotlin.test + JUnit5
- E2E tests with Maestro (see player/README.md for details)

## Architecture Decisions
- See ARCHITECTURE.md for full technical architecture
- SQLite as default database (self-hosted friendly)
- JWT authentication with refresh token rotation
- REST API + WebSocket for real-time events
- libretro cores loaded dynamically per platform

## Git
- Conventional commits: feat:, fix:, docs:, test:, refactor:, chore:
- One logical change per commit
