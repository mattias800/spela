# Contributing to Spela

Thanks for your interest in contributing to Spela! This guide covers code style, commit conventions, and the pull request process.

## Getting Started

1. Fork the repository and clone your fork
2. Set up the development environment — see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
3. Create a branch from `master` for your work

## Code Style

### Go (server/)

- Standard Go formatting (`gofmt`)
- Wrap errors with context: `fmt.Errorf("doing thing: %w", err)`
- Use structured logging (`slog`)
- Tests in `_test.go` files using `testify` for assertions
- Table-driven tests preferred

### TypeScript / React (web/)

- Strict TypeScript — no `any` types
- Functional components only
- Named exports (no default exports)
- CSS via Tailwind utility classes
- File naming: kebab-case for files, PascalCase for components

### Kotlin (player/)

- Follow Kotlin coding conventions
- Compose Multiplatform for all UI
- Clean Architecture: data → domain → presentation

## Commit Messages

Use [conventional commits](https://www.conventionalcommits.org/):

```
feat: add save state sharing
fix: prevent crash on empty game library
docs: update deployment guide
test: add shared session E2E tests
refactor: extract metadata scraper interface
chore: bump Go to 1.22
```

One logical change per commit.

## Testing

A feature is **not done** until all tests pass. Run the **full** test suite, not just your new tests — catching regressions early is critical.

See [TESTING.md](TESTING.md) for the full testing guide (commands, durations, prerequisites). The Web E2E tests in particular need a running Docker environment — TESTING.md has the exact bring-up sequence.

## Pull Requests

- Keep PRs focused — one logical change per PR
- Include tests for new features and bug fixes
- Run the full test suite before opening a PR
- Write a clear description of what changed and why
